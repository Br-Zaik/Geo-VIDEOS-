package com.zeta.fondo.engine

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.zeta.fondo.model.Effects
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Vista previa OpenGL.
 *
 * v41: el hilo de dibujo NUNCA compila el shader de efectos. Al abrir un proyecto se
 * dibuja la imagen con el shader ligero desde el primer cuadro y la variante con los
 * efectos del proyecto se compila en [ShaderCompiler] (contexto compartido, otro hilo).
 * Mientras tanto se sigue dibujando la última variante lista (o la imagen sola).
 */
class PreviewRenderer(
    private val hub: RenderHub,
    private val vertSrc: String,
    private val fragSrc: String,
    private val liteSrc: String? = null,
    private val shaderCacheDir: java.io.File? = null
) : GLSurfaceView.Renderer {

    private var gl: FondoGl? = null
    private var compiler: ShaderCompiler? = null
    private var vw = 1
    private var vh = 1
    private var lastImage: Bitmap? = null
    private var lastFlow: FlowField.Data? = null
    private var lastChar: Bitmap? = null
    private var charUploaded = false
    private var lastDepth: DepthMap? = null
    private var depthUploaded = false
    private var lastPlate: PlateMap? = null
    private var plateUploaded = false
    private var lastMasks: MaskLayers? = null
    private val maskVer = IntArray(MaskLayers.MAX_MASKS) { -1 }
    private val view = FloatArray(4)
    private var failed = false
    private var firstFrameSent = false
    private var maxTextureSize = 2048
    private var compiling: Boolean? = null
    private var syncRetryScheduled = false
    private var reportedError: String? = null
    @Volatile private var disposed = false
    // Parámetros neutros: comparación Original/Animado sin tocar el proyecto ni las máscaras.
    private val originalParams = RenderParams.from(
        Effects.defaults().mapValues { (key, value) ->
            if (key.endsWith(".on")) 0f else value
        }, 1f)

    /** La vista se descarta (salir del editor): se detiene el hilo del compilador. */
    fun dispose() {
        disposed = true
        hub.diagnostics = false
        compiler?.stop()
        compiler = null
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        // Contexto nuevo: hay que volver a subir todo y los programas anteriores ya no existen.
        lastImage = null
        lastFlow = null
        lastChar = null
        charUploaded = false
        lastDepth = null
        depthUploaded = false
        lastPlate = null
        plateUploaded = false
        lastMasks = null
        maskVer.fill(-1)
        failed = false
        firstFrameSent = false
        compiling = null
        compiler?.stop()
        compiler = null
        fbo = 0; fboTex = 0; fboW = 0; fboH = 0
        val maxTex = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        maxTextureSize = maxTex[0].coerceAtLeast(2048)
        // v44: diagnóstico. El temporizador de GPU no bloquea; si no existe se dice «No disponible».
        hub.gpuInfo = ((GLES30.glGetString(GLES30.GL_RENDERER) ?: "?") + " · " + (GLES30.glGetString(GLES30.GL_VERSION) ?: "?"))
        gpuTimer.init()
        hub.gpuTimerAvailable = gpuTimer.available
        lastDrawNs = 0L
        scaler.reset()
        hub.onFramePending?.invoke()
        gl = try {
            FondoGl(vertSrc, fragSrc, liteSrc, shaderCacheDir, syncCompile = false).also { it.init(deferFull = true) }
        } catch (t: Throwable) {
            failed = true
            hub.onError?.invoke(t.message ?: t.toString())
            null
        }
        if (gl != null && !disposed) {
            val c = ShaderCompiler(vertSrc, fragSrc, shaderCacheDir) { hub.invalidate() }
            compiler = if (c.start()) c else null
        }
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        vw = width
        vh = height
    }

    override fun onDrawFrame(unused: GL10?) {
        val frameStart = System.nanoTime()
        frameScale = 1f
        timing = hub.diagnostics && gpuTimer.available
        if (timing) gpuTimer.begin()
        try {
            drawFrame()
        } finally {
            if (timing) {
                gpuTimer.end()
                gpuTimer.poll { ms ->
                    hub.stats.gpu(ms)
                    if (hub.isPlaying()) scaler.onGpuTime(ms, (1000f / hub.previewFps.coerceIn(12, 60)) * .72f, frameScale)
                }
            }
            val end = System.nanoTime()
            val target = 1000f / hub.previewFps.coerceIn(12, 60)
            hub.lastRenderScale = frameScale
            if (hub.isPlaying()) {
                hub.stats.frame(end, (end - frameStart) / 1e6f, frameScale, target)
                // Intervalo real entre cuadros: si la GPU no llega, crece (sin glFinish).
                if (lastDrawNs != 0L) scaler.onInterval((frameStart - lastDrawNs) / 1e6f, target, frameScale)
                lastDrawNs = frameStart
            } else {
                lastDrawNs = 0L
            }
        }
    }

    private var lastWarm: List<Pair<String, Set<String>>>? = null
    private val gpuTimer = GpuTimer()
    private val scaler = com.zeta.fondo.diag.AdaptiveScale(MIN_SCALE, 1f)
    private var lastDrawNs = 0L
    private var frameScale = 1f
    private var timing = false

    private fun drawFrame() {
        syncRetryScheduled = false
        val g = gl
        if (g == null || failed || !hub.renderEnabled) {
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }
        try {
            val img = hub.image
            if (img != null && img !== lastImage && !img.isRecycled) {
                if (img.width <= maxTextureSize && img.height <= maxTextureSize) {
                    g.uploadImage(img)
                } else {
                    // Seguridad para GPUs antiguas: la app sigue funcionando aunque el
                    // dispositivo tenga un GL_MAX_TEXTURE_SIZE menor que la preview adaptativa.
                    val longSide = maxOf(img.width, img.height).toFloat()
                    val k = maxTextureSize / longSide
                    val w = maxOf(1, (img.width * k).toInt())
                    val h = maxOf(1, (img.height * k).toInt())
                    val safe = Bitmap.createScaledBitmap(img, w, h, true)
                    try { g.uploadImage(safe) } finally { if (safe !== img) safe.recycle() }
                }
                lastImage = img
            }
            val f = hub.flow
            if (f != null && f !== lastFlow) {
                g.uploadFlow(f)
                lastFlow = f
            }
            val ch = hub.character
            if (!charUploaded || ch !== lastChar) {
                g.uploadCharacter(ch)
                lastChar = ch
                charUploaded = true
            }
            val dm = hub.depth
            if (!depthUploaded || dm !== lastDepth) {
                g.uploadDepth(dm?.w ?: 0, dm?.h ?: 0, dm?.map)
                lastDepth = dm
                depthUploaded = true
            }
            g.depthView = hub.depthView
            val pl = hub.plate
            if (!plateUploaded || pl !== lastPlate) {
                g.uploadPlate(pl?.w ?: 0, pl?.h ?: 0, pl?.argb)
                lastPlate = pl
                plateUploaded = true
            }
            val m = hub.masks
            if (m != null) {
                if (m !== lastMasks) {
                    lastMasks = m
                    maskVer.fill(-1)
                }
                synchronized(m.lock) {
                    if (m.count > 0 && g.prepareMasks(m.w, m.h)) maskVer.fill(-1)
                    for (i in m.bitmaps.indices) {
                        if (m.versions[i] != maskVer[i]) {
                            // Solo las filas tocadas por el pincel; una capa nueva o un
                            // contexto recreado se sube completo.
                            val rows = m.takeDirtyRows(i)
                            if (maskVer[i] == -1) g.uploadMask(i, m.bitmaps[i])
                            else if (rows[1] > rows[0]) g.uploadMask(i, m.bitmaps[i], rows[0], rows[1])
                            maskVer[i] = m.versions[i]
                        }
                    }
                }
            }
            if (lastImage == null) {
                GLES30.glClearColor(0f, 0f, 0f, 1f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                return
            }
            view[0] = hub.viewX
            view[1] = hub.viewY
            view[2] = hub.viewWidth
            view[3] = hub.viewHeight
            // Encuadre centrado dentro de la superficie: cambiar de formato no recrea la vista.
            val a = hub.displayAspect.takeIf { it > 0.01f } ?: (vw.toFloat() / vh.coerceAtLeast(1))
            val inset = (vh * hub.bottomInset.coerceIn(0f, 0.7f)).toInt()
            val availH = (vh - inset).coerceAtLeast(1)
            var dw = vw
            var dh = availH
            if (vw.toFloat() / availH > a) dw = (availH * a).toInt().coerceIn(1, vw)
            else dh = (vw / a).toInt().coerceIn(1, availH)

            // Variante del shader para los efectos actuales: ya lista, pedida al compilador
            // o (sin contexto compartido) compilada aquí cuando no hay gestos.
            val params = if (hub.showOriginal) originalParams else hub.params
            compiler?.drain { key, prog -> g.adoptVariant(key, prog) }
            hub.warmVariants.let { w -> if (w !== lastWarm) { lastWarm = w; compiler?.prewarm(w) } }
            val key = params.variantKey
            if (!g.hasVariant(key)) {
                val c = compiler
                if (c != null) {
                    c.request(key, params.features)
                    // Un fallo de compilación se informa una vez; se sigue mostrando la imagen.
                    c.error(key)?.let { err -> if (reportedError != key) { reportedError = key; hub.onError?.invoke(err) } }
                } else if (firstFrameSent && hub.idleMs() >= IDLE_BEFORE_SYNC_COMPILE_MS) {
                    g.ensureVariant(params.features)
                } else scheduleRetry()
            }
            val busy = !g.hasVariant(key)
            if (!busy) hub.readyVariantKey = key
            if (busy != compiling) {
                compiling = busy
                if (!busy) hub.onFullReady?.invoke()
                hub.onCompiling?.invoke(busy)
            }

            // ¿Shader ligero o de efectos?
            //  - ligero: si aún no hay ninguna variante, al pintar una máscara y en la
            //    comparación con el original;
            //  - de efectos: el resto. v42: también durante el zoom y el arrastre (antes los
            //    efectos desaparecían mientras había dos dedos en la pantalla). Para que el
            //    gesto siga fluido, en movimiento se dibuja a una resolución adaptativa.
            val painting = hub.showMask && hub.activeMask >= 0 && !hub.showOriginal
            // v44.23: capas de imagen (no al comparar con el original).
            g.layerStack = if (hub.showOriginal) null else hub.layers
            g.liteT = hub.currentT()
            g.zones = params.zones
            val lite = g.hasLite && (!g.fullReady || painting || hub.showOriginal)
            val x0 = (vw - dw) / 2
            val y0 = inset + (availH - dh) / 2
            lastBox[0] = dw; lastBox[1] = dh; lastBox[2] = x0; lastBox[3] = y0
            val drawn = if (lite) {
                g.drawLite(dw, dh, view, painting, hub.activeMask, x0, y0, hub.maskAlpha)
                true
            } else drawEffects(g, params, dw, dh, x0, y0, painting)
            if (!drawn) {
                hub.blankFrames++
                g.drawLite(dw, dh, view, painting, hub.activeMask, x0, y0, hub.maskAlpha)
            }
            hub.cineInfo = g.cineStatus
            if (!firstFrameSent) {
                firstFrameSent = true
                hub.onFrameReady?.invoke()
            }
            errorsInRow = 0
        } catch (t: Throwable) {
            // v44.22: un fallo suelto ya NO deja la vista previa en negro para siempre: ese
            // cuadro muestra la imagen sola y se sigue. Solo tras 3 fallos seguidos se rinde.
            errorsInRow++
            hub.blankFrames++
            com.zeta.fondo.diag.ZetaLog.error("Vista previa", "Cuadro fallido ($errorsInRow): ${t.message ?: t}")
            if (errorsInRow >= 3) {
                failed = true
                hub.onError?.invoke(t.message ?: t.toString())
            } else {
                try {
                    var guard = 0
                    while (GLES30.glGetError() != GLES30.GL_NO_ERROR && guard++ < 16) { }
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                    if (lastBox[0] > 0) gl?.takeIf { lastImage != null }?.drawLite(lastBox[0], lastBox[1], view, false, -1, lastBox[2], lastBox[3], hub.maskAlpha)
                } catch (_: Throwable) { }
                hub.invalidate()
            }
        }
    }
    private var errorsInRow = 0
    /** Último encuadre dibujado (ancho, alto, x, y): para el cuadro de emergencia. */
    private val lastBox = IntArray(4)

    // ------------------------------------------------------------ resolución adaptativa

    private var fbo = 0
    private var fboTex = 0
    private var fboW = 0
    private var fboH = 0
    private var fboBroken = false

    private fun moving(): Boolean = hub.isPlaying() || hub.gestureActive || hub.resizing || hub.highFpsRequested()

    /**
     * v42 · Dibuja los efectos. Quieto: a la resolución completa de la vista (con un tope de
     * píxeles). En movimiento: a una escala adaptativa en un búfer intermedio que luego se
     * escala con filtro lineal.
     * v44: la escala ya NO se mide con glFinish (bloqueaba el hilo). La ajusta [scaler] con
     * el intervalo real entre cuadros y, si el teléfono lo admite, con el temporizador de GPU.
     */
    private fun drawEffects(g: FondoGl, params: RenderParams, dw: Int, dh: Int, x0: Int, y0: Int, painting: Boolean): Boolean {
        val t = if (hub.showOriginal) 0f else hub.currentT()
        val base = kotlin.math.min(1f, kotlin.math.sqrt(MAX_PIXELS / (dw.toFloat() * dh).coerceAtLeast(1f)))
        val inMotion = moving()
        if (hub.gestureActive) scaler.clampTo(GESTURE_START_SCALE)
        var s = if (inMotion) kotlin.math.min(base, scaler.value) else base
        s = (kotlin.math.round(s * 20f) / 20f).coerceIn(MIN_SCALE, 1f)
        val ok: Boolean
        if (fboBroken) s = 1f
        if (s >= .999f) {
            ok = g.draw(params, t, dw, dh, view, false, false, painting, false, hub.activeMask, x0, y0)
        } else {
            val rw = (dw * s).toInt().coerceAtLeast(1)
            val rh = (dh * s).toInt().coerceAtLeast(1)
            if (!ensureFbo((dw * base).toInt() + 1, (dh * base).toInt() + 1)) {
                return g.draw(params, t, dw, dh, view, false, false, painting, false, hub.activeMask, x0, y0)
            }
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            ok = g.draw(params, t, rw, rh, view, false, false, painting, false, hub.activeMask, 0, 0)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, vw, vh)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            if (ok) {
                GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, fbo)
                GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
                while (GLES30.glGetError() != GLES30.GL_NO_ERROR) { /* errores previos */ }
                GLES30.glBlitFramebuffer(0, 0, rw, rh, x0, y0, x0 + dw, y0 + dh,
                    GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_LINEAR)
                // Si el driver no admite el escalado, desde ahora se dibuja directo (como la v41).
                if (GLES30.glGetError() != GLES30.GL_NO_ERROR) {
                    fboBroken = true; scaler.reset(); hub.invalidate()
                    hub.onGlWarning?.invoke("glBlitFramebuffer no admitido: la vista previa se dibuja sin escalar")
                }
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            }
        }
        frameScale = if (fboBroken) 1f else s / base.coerceAtLeast(.01f)
        // Al quedarse quieto se repite el cuadro a resolución completa.
        val reduced = s < base - .001f
        if (reduced && !hub.isPlaying()) hub.onRenderLater?.invoke(SETTLE_MS)
        return ok
    }

    private fun ensureFbo(w: Int, h: Int): Boolean {
        if (fbo != 0 && w <= fboW && h <= fboH) return true
        releaseFbo()
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        fboTex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTex)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glGenFramebuffers(1, ids, 0)
        fbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboTex, 0)
        val ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (!ok) { releaseFbo(); fboBroken = true; return false }
        fboW = w; fboH = h
        return true
    }

    private fun releaseFbo() {
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (fboTex != 0) GLES30.glDeleteTextures(1, intArrayOf(fboTex), 0)
        fbo = 0; fboTex = 0; fboW = 0; fboH = 0
    }

    private fun scheduleRetry() {
        if (syncRetryScheduled) return
        syncRetryScheduled = true
        hub.onRenderLater?.invoke(IDLE_BEFORE_SYNC_COMPILE_MS)
    }

    private companion object {
        const val IDLE_BEFORE_SYNC_COMPILE_MS = 300L
        /** Tope de píxeles de la vista previa quieta (≈ 1080 × 1300). */
        const val MAX_PIXELS = 1_400_000f
        const val MIN_SCALE = .45f
        const val GESTURE_START_SCALE = .8f
        const val SETTLE_MS = 160L
    }
}
