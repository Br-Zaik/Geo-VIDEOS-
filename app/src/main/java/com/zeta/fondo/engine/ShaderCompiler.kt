package com.zeta.fondo.engine

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * v41 · Compila las variantes del shader en un hilo propio, con un contexto OpenGL
 * COMPARTIDO con el de la vista previa (los programas se pueden usar en los dos).
 *
 * Así el hilo de dibujo nunca se queda parado compilando: la imagen aparece al instante,
 * el zoom responde y Android no muestra «no responde» (la v40 compilaba ~140 KB de GLSL
 * en el hilo de OpenGL y, si la vista cambiaba de tamaño mientras tanto, el hilo
 * principal quedaba esperando).
 *
 * Si el teléfono no permite el contexto compartido, [start] devuelve false y la vista
 * previa compila en su propio hilo (las variantes son pequeñas).
 */
class ShaderCompiler(
    private val vertSrc: String,
    private val fragSrc: String,
    private val cacheDir: java.io.File?,
    /** Se llama (desde el hilo del compilador) cuando hay un programa listo o un error. */
    private val onReady: () -> Unit
) {
    private val queue = LinkedBlockingDeque<String>()
    /** v44.22 · cola de BAJA prioridad: variantes que el usuario probablemente toque pronto. */
    private val warm = LinkedBlockingDeque<Pair<String, Set<String>>>()
    private val warmed = ConcurrentHashMap.newKeySet<String>()
    private val requested = ConcurrentHashMap<String, Set<String>>()
    private val done = ConcurrentLinkedQueue<Pair<String, Int>>()
    /** v44.25 · Programas ya entregados al hilo de dibujo (solo se toca desde ese hilo). */
    private val delivered = HashSet<String>()
    private val errors = ConcurrentHashMap<String, String>()
    @Volatile private var alive = false
    private var thread: Thread? = null

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE

    /** Llamar en el hilo de OpenGL de la vista previa, con su contexto actual. */
    fun start(): Boolean {
        try {
            val d = EGL14.eglGetCurrentDisplay()
            val share = EGL14.eglGetCurrentContext()
            if (d == EGL14.EGL_NO_DISPLAY || share == EGL14.EGL_NO_CONTEXT) return false
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE)
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(d, attribs, 0, configs, 0, 1, count, 0) || count[0] < 1) return false
            val config = configs[0] ?: return false
            val ctx = EGL14.eglCreateContext(d, config, share,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) return false
            val surf = EGL14.eglCreatePbufferSurface(d, config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            if (surf == null || surf == EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroyContext(d, ctx)
                return false
            }
            display = d; context = ctx; surface = surf
            alive = true
            thread = Thread({ loop() }, "ZetaShaders").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
                start()
            }
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    /**
     * Pide una variante; la más reciente se compila primero. Se llama desde el hilo de dibujo
     * solo cuando éste NO tiene el programa.
     *
     * v44.25:
     *  - si la variante ya se había entregado y se vuelve a pedir, es que el dibujo la descartó
     *    (solo guarda las últimas [FondoGl] 6): se vuelve a preparar. Antes se ignoraba y esa
     *    combinación de efectos se quedaba sin dibujarse hasta reiniciar la app;
     *  - al tocar efectos rápido, los pedidos viejos que siguen en cola (combinaciones que ya
     *    no se ven) se descartan: no gastan batería ni retrasan la que sí se ve.
     */
    fun request(key: String, features: Set<String>) {
        if (!alive) return
        if (requested.containsKey(key)) {
            if (!delivered.remove(key)) return
            queue.remove(key)
        }
        requested[key] = features
        queue.offerFirst(key)
        while (queue.size > MAX_PENDING) {
            val stale = queue.pollLast() ?: break
            requested.remove(stale)
        }
    }

    /**
     * v44.22 · Prepara por adelantado (solo en el caché de disco, sin ocupar memoria de la GPU)
     * las variantes de los efectos que se están viendo en el catálogo: al tocar uno, carga en
     * milisegundos en vez de compilar 1–3 s. Sustituye la lista anterior (solo lo visible ahora).
     */
    fun prewarm(items: List<Pair<String, Set<String>>>) {
        if (!alive) return
        warm.clear()
        for (it in items) if (it.first !in warmed && !requested.containsKey(it.first)) warm.offerLast(it)
    }

    /** Programas terminados (los adopta el hilo de dibujo). */
    fun drain(each: (String, Int) -> Unit) {
        while (true) {
            val (k, p) = done.poll() ?: return
            delivered.add(k)
            each(k, p)
        }
    }

    fun error(key: String): String? = errors[key]

    /** Detiene el hilo; su contexto se libera allí mismo. */
    fun stop() {
        alive = false
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            alive = false
            release()
            onReady()
            return
        }
        try {
            while (alive) {
                val key = try { queue.pollFirst(if (warm.isEmpty()) 500L else 120L, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { null }
                if (key == null) {
                    // Sin pedidos urgentes: una variante «probable» (se compila, se guarda en
                    // disco y se libera). Un pedido real siempre pasa delante.
                    val w = warm.pollFirst() ?: continue
                    if (w.first in warmed || requested.containsKey(w.first)) continue
                    warmed.add(w.first)
                    try {
                        val src = ShaderVariants.source(fragSrc, w.second)
                        if (!ProgramCache.isCached(vertSrc, src, cacheDir, w.first)) {
                            val prog = ProgramCache.build(vertSrc, src, cacheDir, w.first)
                            GLES30.glFinish()
                            GLES30.glDeleteProgram(prog)
                        }
                    } catch (_: Throwable) { /* se compilará al pedirla de verdad */ }
                    continue
                }
                val features = requested[key] ?: continue
                try {
                    val prog = ProgramCache.build(vertSrc, ShaderVariants.source(fragSrc, features), cacheDir, key)
                    GLES30.glFinish()          // el programa queda completo antes de usarlo en el otro contexto
                    done.add(key to prog)
                } catch (t: Throwable) {
                    errors[key] = t.message ?: t.toString()
                    com.zeta.fondo.diag.ZetaLog.error("Shader", "No compiló la variante ${key.take(120)}: ${t.message ?: t}")
                }
                onReady()
            }
        } finally {
            release()
        }
    }

    private fun release() {
        try {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
        } catch (_: Throwable) { }
        surface = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
    }

    private companion object {
        /** v44.25 · Pedidos en espera como máximo (la variante actual y la anterior). */
        const val MAX_PENDING = 2
        /** EGL_OPENGL_ES3_BIT_KHR */
        const val EGL_OPENGL_ES3_BIT = 0x40
    }
}

/**
 * v41 · Compilación de programas con caché de binarios en disco (una entrada por
 * variante). La segunda vez que se usa una combinación de efectos carga en milisegundos.
 */
object ProgramCache {
    private const val PREFIX = "zeta_v41_"
    /** v44.22: 48 (antes 24): caben las variantes preparadas por adelantado del catálogo. */
    private const val MAX_FILES = 48

    fun build(vert: String, frag: String, dir: java.io.File?, key: String): Int {
        val file = fileFor(dir, vert, frag, key)
        file?.let { loadBinary(it) }?.let { return it }
        val prog = FondoGl.buildProgram(vert, frag, retrievable = file != null)
        if (file != null) saveBinary(prog, file)
        return prog
    }

    /** v44.22 · ¿Ya hay binario en disco para esta variante? (no lo carga) */
    fun isCached(vert: String, frag: String, dir: java.io.File?, key: String): Boolean =
        fileFor(dir, vert, frag, key)?.let { it.isFile && it.length() >= 8 } ?: false

    private fun fileFor(dir: java.io.File?, vert: String, frag: String, key: String): java.io.File? {
        dir ?: return null
        return try {
            val id = (vert.hashCode().toLong() * 31 + frag.hashCode()) * 31 +
                ((GLES30.glGetString(GLES30.GL_RENDERER) ?: "") + (GLES30.glGetString(GLES30.GL_VERSION) ?: "")).hashCode()
            dir.mkdirs()
            java.io.File(dir, PREFIX + java.lang.Long.toHexString(id) + "_" + Integer.toHexString(key.hashCode()) + ".bin")
        } catch (_: Throwable) { null }
    }

    private fun loadBinary(file: java.io.File): Int? {
        if (!file.isFile || file.length() < 8) return null
        return try {
            val bytes = file.readBytes()
            val format = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).int
            val data = ByteBuffer.allocateDirect(bytes.size - 4).order(ByteOrder.nativeOrder())
            data.put(bytes, 4, bytes.size - 4).position(0)
            val prog = GLES30.glCreateProgram()
            GLES30.glProgramBinary(prog, format, data, bytes.size - 4)
            val ok = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0 || GLES30.glGetError() != GLES30.GL_NO_ERROR) {
                GLES30.glDeleteProgram(prog); file.delete(); clearErrors(); null
            } else { file.setLastModified(System.currentTimeMillis()); prog }
        } catch (_: Throwable) { file.delete(); clearErrors(); null }
    }

    private fun saveBinary(prog: Int, file: java.io.File) {
        try {
            val len = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_PROGRAM_BINARY_LENGTH, len, 0)
            if (len[0] <= 0) return
            val buf = ByteBuffer.allocateDirect(len[0]).order(ByteOrder.nativeOrder())
            val outLen = IntArray(1)
            val format = IntArray(1)
            GLES30.glGetProgramBinary(prog, len[0], outLen, 0, format, 0, buf)
            if (GLES30.glGetError() != GLES30.GL_NO_ERROR || outLen[0] <= 0) { clearErrors(); return }
            val bytes = ByteArray(outLen[0])
            buf.position(0); buf.get(bytes, 0, outLen[0])
            val tmp = java.io.File(file.path + ".tmp")
            tmp.outputStream().use { o ->
                o.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(format[0]).array())
                o.write(bytes)
            }
            if (!tmp.renameTo(file)) tmp.delete()
            // Solo las variantes más recientes; los binarios de versiones anteriores se borran.
            val all = file.parentFile?.listFiles()?.filter { it.name.endsWith(".bin") } ?: return
            all.filter { !it.name.startsWith(PREFIX) }.forEach { it.delete() }
            all.filter { it.name.startsWith(PREFIX) }.sortedByDescending { it.lastModified() }
                .drop(MAX_FILES).forEach { it.delete() }
        } catch (_: Throwable) { clearErrors() }
    }

    private fun clearErrors() {
        var guard = 0
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR && guard < 16) guard++
    }
}
