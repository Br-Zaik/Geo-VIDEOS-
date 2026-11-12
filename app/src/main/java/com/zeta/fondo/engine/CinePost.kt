package com.zeta.fondo.engine

import android.opengl.GLES30

/**
 * v44.19 · «Calidad cine»: cadena corta de post-proceso con FBOs.
 *
 *   escena (uber-shader, alfa = luz que añadieron los efectos)
 *     → prefiltro a 1/2 (solo la luz de los efectos + un poco de las luces de la imagen)
 *     → bajada dual-filter (ARM, Bjørge 2015) 1/4, 1/8, 1/16, 1/32…
 *     → subida acumulativa (pirámide estilo Jimenez / Call of Duty AW)
 *     → composición: brillo real en espacio lineal (mezcla «pantalla», nunca quema),
 *       halo cálido de película (halation), grano fino y dither.
 *
 * Nada guarda estado entre cuadros: el resultado depende solo de uT, así el bucle sigue
 * perfecto y la exportación es idéntica a la vista previa.
 *
 * Si algo falla (FBO incompleto, error de GL o de compilación) [broken] pasa a true y
 * FondoGl vuelve a dibujar directo, como antes. Debe usarse en el hilo de OpenGL.
 */
class CinePost(private val vertSrc: String) {

    var broken = false
        private set
    /** Formato elegido para la pirámide (para Diagnóstico). */
    var formatLabel = "—"
        private set

    private var progPre = 0
    private var progDown = 0
    private var progUp = 0
    private var progComp = 0
    private val locs = HashMap<Int, HashMap<String, Int>>()
    private var vao = 0
    private var vbo = 0

    // Escena (tamaño de salida, RGBA8: la escena es LDR y el alfa lleva la emisión).
    private var sceneFbo = 0
    private var sceneTex = 0
    private var sceneW = 0
    private var sceneH = 0

    // Pirámide: down[i] y up[i] (up[n-1] = down[n-1]).
    private var levels = IntArray(0)          // ancho, alto intercalados
    private var downFbo = IntArray(0)
    private var downTex = IntArray(0)
    private var upFbo = IntArray(0)
    private var upTex = IntArray(0)
    private var pyramidFor = 0L
    private var halfFloat: Boolean? = null
    /** true = la pirámide quedó en RGBA16F; false = RGBA8 guardada ×4 (uEnc = 1). */
    private var pyramidHalf = false

    private var savedFbo = 0
    private var drawnW = 1
    private var drawnH = 1

    /**
     * Prepara la escena de [w]×[h] y deja enlazado su FBO. Devuelve false si no se puede
     * (entonces no se toca nada y hay que dibujar directo).
     */
    fun begin(w: Int, h: Int): Boolean {
        if (broken || w < 2 || h < 2) return false
        return try {
            clearErrors()
            if (progComp == 0) buildPrograms()
            ensureScene(w, h)
            ensurePyramid()
            drawnW = w; drawnH = h
            val cur = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_DRAW_FRAMEBUFFER_BINDING, cur, 0)
            savedFbo = cur[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
            if (GLES30.glGetError() != GLES30.GL_NO_ERROR) fail("enlazar escena")
            !broken
        } catch (t: Throwable) {
            fail(t.message ?: t.toString())
            restore()
            false
        }
    }

    /**
     * Tras dibujar la escena: pirámide de brillo y composición sobre el framebuffer que
     * estaba enlazado antes de [begin], en el rectángulo ([x0],[y0],[vw],[vh]).
     * [post] = CinePlan (activo, brillo, luces de la imagen, halo, grano); [t] = fase del bucle.
     */
    fun finish(x0: Int, y0: Int, vw: Int, vh: Int, post: FloatArray, t: Float, rotated: Boolean = false): Boolean {
        return try {
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glBindVertexArray(vao)
            val n = levels.size / 2
            val bloomOn = CinePlan.bloomVisible(post) && n > 0
            // Parte usada de las texturas (la escena crece por escalones; la vista previa
            // cambia de tamaño al moverse y no se recrea nada por eso).
            val fx = drawnW.toFloat() / sceneW
            val fy = drawnH.toFloat() / sceneH
            fun usedW(i: Int) = kotlin.math.ceil(levels[i * 2] * fx).toInt().coerceIn(1, levels[i * 2])
            fun usedH(i: Int) = kotlin.math.ceil(levels[i * 2 + 1] * fy).toInt().coerceIn(1, levels[i * 2 + 1])
            if (bloomOn) {
                // 1) prefiltro: escena → down[0] (1/2)
                pass(downFbo[0], usedW(0), usedH(0), progPre)
                bindTex(0, sceneTex)
                GLES30.glUniform1i(loc(progPre, "uSrc"), 0)
                GLES30.glUniform2f(loc(progPre, "uFrac"), fx, fy)
                GLES30.glUniform2f(loc(progPre, "uTexel"), 1f / sceneW, 1f / sceneH)
                GLES30.glUniform2f(loc(progPre, "uPre"), CinePlan.imageAmount(post), CinePlan.THRESHOLD)
                GLES30.glUniform1f(loc(progPre, "uEnc"), if (pyramidHalf) 0f else 1f)
                draw()
                // 2) bajada
                for (i in 1 until n) {
                    pass(downFbo[i], usedW(i), usedH(i), progDown)
                    bindTex(0, downTex[i - 1])
                    GLES30.glUniform1i(loc(progDown, "uSrc"), 0)
                    GLES30.glUniform2f(loc(progDown, "uFrac"), fx, fy)
                    GLES30.glUniform1f(loc(progDown, "uEnc"), if (pyramidHalf) 0f else 1f)
                    GLES30.glUniform2f(loc(progDown, "uTexel"), 1f / levels[(i - 1) * 2], 1f / levels[(i - 1) * 2 + 1])
                    draw()
                }
                // 3) subida acumulativa: up[i] = mezcla(down[i], tienda(up[i+1]))
                for (i in n - 2 downTo 0) {
                    pass(upFbo[i], usedW(i), usedH(i), progUp)
                    bindTex(0, if (i + 1 == n - 1) downTex[i + 1] else upTex[i + 1])
                    bindTex(1, downTex[i])
                    GLES30.glUniform1i(loc(progUp, "uSmall"), 0)
                    GLES30.glUniform1i(loc(progUp, "uSame"), 1)
                    GLES30.glUniform2f(loc(progUp, "uFrac"), fx, fy)
                    GLES30.glUniform2f(loc(progUp, "uTexel"), 1f / levels[(i + 1) * 2], 1f / levels[(i + 1) * 2 + 1])
                    GLES30.glUniform1f(loc(progUp, "uMix"), CinePlan.UP_MIX)
                    GLES30.glUniform1f(loc(progUp, "uEnc"), if (pyramidHalf) 0f else 1f)
                    draw()
                }
            }
            // 4) composición en el destino original
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, savedFbo)
            GLES30.glViewport(x0, y0, vw, vh)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            GLES30.glUseProgram(progComp)
            bindTex(0, sceneTex)
            bindTex(1, if (!bloomOn) sceneTex else if (n == 1) downTex[0] else upTex[0])
            bindTex(2, if (!bloomOn) sceneTex else downTex[maxOf(0, n - 2)])
            GLES30.glUniform1i(loc(progComp, "uScene"), 0)
            GLES30.glUniform1i(loc(progComp, "uBloom"), 1)
            GLES30.glUniform1i(loc(progComp, "uWide"), 2)
            // v44.20 · luz sobre la escena, look, fuerza, franjas
            GLES30.glUniform4f(loc(progComp, "uPost2"),
                if (bloomOn) CinePlan.spill(post) else 0f,
                CinePlan.look(post).toFloat(), CinePlan.lookAmount(post), CinePlan.bars(post))
            val outAspect = if (rotated) vh.toFloat() / vw else vw.toFloat() / vh
            GLES30.glUniform2f(loc(progComp, "uBars"),
                CinePlan.barFraction(outAspect, CinePlan.bars(post)), if (rotated) 1f else 0f)
            GLES30.glUniform2f(loc(progComp, "uSceneScale"), fx, fy)
            GLES30.glUniform1f(loc(progComp, "uEnc"), if (pyramidHalf) 0f else 1f)
            GLES30.glUniform4f(loc(progComp, "uPost"),
                if (bloomOn) CinePlan.bloomGain(post) else 0f,
                if (bloomOn) CinePlan.haloGain(post) else 0f,
                CinePlan.grainAmount(post), t)
            draw()
            GLES30.glBindVertexArray(0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            val e = GLES30.glGetError()
            if (e != GLES30.GL_NO_ERROR) { fail("error GL 0x${Integer.toHexString(e)}"); false } else true
        } catch (t2: Throwable) {
            fail(t2.message ?: t2.toString())
            restore()
            false
        }
    }

    /** Vuelve al framebuffer de antes (tras un fallo entre begin y finish). */
    fun restore() {
        try { GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, savedFbo) } catch (_: Throwable) { }
    }

    fun release() {
        releasePyramid()
        releaseScene()
        for (p in intArrayOf(progPre, progDown, progUp, progComp)) if (p != 0) GLES30.glDeleteProgram(p)
        progPre = 0; progDown = 0; progUp = 0; progComp = 0
        locs.clear()
        if (vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        vbo = 0; vao = 0
    }

    // ------------------------------------------------------------------ interno

    private fun fail(why: String) {
        if (!broken) com.zeta.fondo.diag.ZetaLog.error("Cine", "Calidad cine desactivada: $why")
        broken = true
        formatLabel = "No disponible ($why)".take(80)
    }

    private fun pass(fbo: Int, w: Int, h: Int, prog: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, w, h)
        // Sin contenido previo que recargar desde la RAM (regla de ancho de banda ARM/Qualcomm).
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(prog)
    }

    private fun draw() = GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

    private fun bindTex(unit: Int, tex: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
    }

    private fun loc(prog: Int, name: String): Int = locs.getOrPut(prog) { HashMap() }
        .getOrPut(name) { GLES30.glGetUniformLocation(prog, name) }

    private fun buildPrograms() {
        progPre = FondoGl.buildProgram(vertSrc, CineShaders.PREFILTER)
        progDown = FondoGl.buildProgram(vertSrc, CineShaders.DOWN)
        progUp = FondoGl.buildProgram(vertSrc, CineShaders.UP)
        progComp = FondoGl.buildProgram(vertSrc, CineShaders.COMPOSITE)
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0); vbo = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val fb = java.nio.ByteBuffer.allocateDirect(quad.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(quad).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /** Textura + FBO. Devuelve [fbo, tex] o null si el FBO no queda completo. */
    private fun makeTarget(w: Int, h: Int, half: Boolean): IntArray? {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val tex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        // Formato CON tamaño (GL_RGBA16F + GL_HALF_FLOAT): el formato «estilo ES2» falla en Adreno.
        if (half) GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, w, h, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
        else GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glGenFramebuffers(1, ids, 0)
        val fbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
        val ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE &&
            GLES30.glGetError() == GLES30.GL_NO_ERROR
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (!ok) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
            clearErrors()
            return null
        }
        return intArrayOf(fbo, tex)
    }

    private fun ensureScene(w: Int, h: Int) {
        // v44.22: solo CRECE. La vista previa baja y sube de resolución al moverse; antes, al
        // cruzar la mitad del área, se recreaban la escena y la pirámide en cada gesto
        // (asignaciones de memoria en mitad de la animación). Se usa la parte ocupada.
        if (sceneFbo != 0 && w <= sceneW && h <= sceneH) return
        releaseScene()
        val t = makeTarget(w, h, false) ?: throw IllegalStateException("FBO de escena incompleto")
        sceneFbo = t[0]; sceneTex = t[1]; sceneW = w; sceneH = h
        pyramidFor = 0L
    }

    private fun ensurePyramid() {
        val key = (sceneW.toLong() shl 32) or sceneH.toLong()
        if (pyramidFor == key) return
        releasePyramid()
        val lv = CinePlan.levels(sceneW, sceneH)
        if (halfFloat == null) halfFloat = CinePlan.halfFloatCandidate(extensions())
        fun build(half: Boolean): Boolean {
            val n = lv.size / 2
            val df = IntArray(n); val dt = IntArray(n); val uf = IntArray(n); val ut = IntArray(n)
            for (i in 0 until n) {
                val d = makeTarget(lv[i * 2], lv[i * 2 + 1], half) ?: run { free(df, dt, uf, ut); return false }
                df[i] = d[0]; dt[i] = d[1]
                if (i < n - 1) {
                    val u = makeTarget(lv[i * 2], lv[i * 2 + 1], half) ?: run { free(df, dt, uf, ut); return false }
                    uf[i] = u[0]; ut[i] = u[1]
                }
            }
            levels = lv; downFbo = df; downTex = dt; upFbo = uf; upTex = ut
            return true
        }
        // Si el intento en 16F falla, [free] apaga halfFloat y desde entonces se usa RGBA8.
        val half = halfFloat == true && build(true)
        if (!half && !build(false)) throw IllegalStateException("FBO de brillo incompleto")
        pyramidHalf = half
        formatLabel = "Activa · ${if (half) "RGBA16F" else "RGBA8"} · ${lv.size / 2} niveles"
        pyramidFor = key
    }

    private fun free(df: IntArray, dt: IntArray, uf: IntArray, ut: IntArray) {
        for (f in df + uf) if (f != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(f), 0)
        for (x in dt + ut) if (x != 0) GLES30.glDeleteTextures(1, intArrayOf(x), 0)
        halfFloat = false
    }

    private fun extensions(): Set<String> {
        val out = HashSet<String>()
        try {
            val n = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_NUM_EXTENSIONS, n, 0)
            for (i in 0 until n[0]) GLES30.glGetStringi(GLES30.GL_EXTENSIONS, i)?.let { out += it }
        } catch (_: Throwable) { }
        clearErrors()
        return out
    }

    private fun releaseScene() {
        if (sceneFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(sceneFbo), 0)
        if (sceneTex != 0) GLES30.glDeleteTextures(1, intArrayOf(sceneTex), 0)
        sceneFbo = 0; sceneTex = 0; sceneW = 0; sceneH = 0
    }

    private fun releasePyramid() {
        for (f in downFbo + upFbo) if (f != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(f), 0)
        for (x in downTex + upTex) if (x != 0) GLES30.glDeleteTextures(1, intArrayOf(x), 0)
        downFbo = IntArray(0); downTex = IntArray(0); upFbo = IntArray(0); upTex = IntArray(0)
        levels = IntArray(0); pyramidFor = 0L
    }

    private fun clearErrors() {
        var guard = 0
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR && guard < 16) guard++
    }
}

/**
 * v44.19 · Decisiones de «Calidad cine» sin Android (se prueban en la JVM).
 * post = [activo, brillo, luces de la imagen, halo cálido, grano] (ver RenderParams.post).
 */
object CinePlan {
    /** Por debajo de esta luminancia la imagen no brilla (las luces de los efectos sí). */
    const val THRESHOLD = 0.78f
    /** Peso de la escala grande en cada subida (0,7 = halo amplio, como de lente real). */
    const val UP_MIX = 0.7f
    /** El nivel más pequeño de la pirámide no baja de esto (píxeles del lado corto). */
    const val MIN_SIDE = 6
    const val MAX_LEVELS = 6
    /** «Brillo de las luces» 1,0 → ganancia real del brillo (ajustada viendo cuadros reales). */
    const val BLOOM_GAIN = 4.5f

    val OFF = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    fun active(post: FloatArray): Boolean = post.isNotEmpty() && post[0] > .5f

    fun bloomVisible(post: FloatArray): Boolean = active(post) &&
        (post.getOrElse(1) { 0f } > .001f || post.getOrElse(3) { 0f } > .001f || spill(post) > .001f)

    fun bloomGain(post: FloatArray): Float = post.getOrElse(1) { 0f }.coerceIn(0f, 2f) * BLOOM_GAIN
    fun haloGain(post: FloatArray): Float = post.getOrElse(3) { 0f }.coerceIn(0f, 1f)
    fun imageAmount(post: FloatArray): Float = post.getOrElse(2) { 0f }.coerceIn(0f, 1f)
    fun grainAmount(post: FloatArray): Float = if (active(post)) post.getOrElse(4) { 0f }.coerceIn(0f, 1f) else 0f
    /** v44.20 · Estela de cámara (0 si Calidad cine está apagada). */
    fun motionBlur(post: FloatArray): Float = if (active(post)) post.getOrElse(5) { 0f }.coerceIn(0f, 1f) else 0f
    /** v44.20 · La luz de los efectos tiñe las superficies cercanas. */
    fun spill(post: FloatArray): Float = if (active(post)) post.getOrElse(6) { 0f }.coerceIn(0f, 1f) else 0f
    /** v44.20 · Look de color: 0 natural, 1 Kodak, 2 Fuji, 3 Teal & Orange, 4 Noche azul, 5 B/N. */
    fun look(post: FloatArray): Int = if (active(post)) post.getOrElse(7) { 0f }.toInt().coerceIn(0, LOOKS - 1) else 0
    fun lookAmount(post: FloatArray): Float = if (look(post) == 0) 0f else post.getOrElse(8) { 0f }.coerceIn(0f, 1f)
    /** v44.20 · Franjas de cine (0..1): en horizontal llegan a 2,39:1; en vertical, 10 % por lado. */
    fun bars(post: FloatArray): Float = if (active(post)) post.getOrElse(9) { 0f }.coerceIn(0f, 1f) else 0f
    const val LOOKS = 6
    /** Alto de cada franja (fracción de la altura) para un cuadro de [aspect] = ancho/alto. */
    fun barFraction(aspect: Float, amount: Float): Float =
        (if (aspect > 1f) ((1f - aspect / 2.39f) * 0.5f).coerceAtLeast(0f) else 0.10f) * amount.coerceIn(0f, 1f)

    /**
     * Tamaños de la pirámide [w0, h0, w1, h1, …]: empieza en la mitad de la escena y se
     * divide por 2 hasta que el lado corto bajaría de [MIN_SIDE] (máx. [MAX_LEVELS]).
     */
    fun levels(w: Int, h: Int): IntArray {
        val out = ArrayList<Int>()
        var lw = (w + 1) / 2
        var lh = (h + 1) / 2
        while (out.size / 2 < MAX_LEVELS && minOf(lw, lh) >= MIN_SIDE) {
            out += lw; out += lh
            lw = (lw + 1) / 2; lh = (lh + 1) / 2
        }
        if (out.isEmpty()) { out += maxOf(1, (w + 1) / 2); out += maxOf(1, (h + 1) / 2) }
        return out.toIntArray()
    }

    /** RGBA16F solo se intenta si el driver anuncia que se puede dibujar en él. */
    fun halfFloatCandidate(ext: Set<String>): Boolean =
        "GL_EXT_color_buffer_half_float" in ext || "GL_EXT_color_buffer_float" in ext

    /** Memoria aproximada (bytes) de escena + pirámide, para Diagnóstico. */
    fun memoryBytes(w: Int, h: Int, half: Boolean): Long {
        val lv = levels(w, h)
        var px = 0L
        for (i in 0 until lv.size / 2) px += lv[i * 2].toLong() * lv[i * 2 + 1] * (if (i < lv.size / 2 - 1) 2 else 1)
        return w.toLong() * h * 4 + px * (if (half) 8 else 4)
    }
}

/** v44.19 · Shaders de la cadena cine (pequeños: compilan en milisegundos). */
object CineShaders {
    /** Escena → luz que debe brillar, en espacio lineal, a 1/2 con 4 lecturas (cubre 4×4). */
    const val PREFILTER = """#version 300 es
precision mediump float;
in vec2 vScreen;
out vec4 fragColor;
uniform sampler2D uSrc;
uniform vec2 uTexel;
uniform vec2 uFrac;  // parte usada de las texturas
uniform vec2 uPre;   // x = luces de la imagen (0..1), y = umbral
uniform float uEnc;  // 1 = pirámide RGBA8: se guarda ×4 para no perder el brillo tenue (probado: lo más fiel)
vec3 dec(vec3 c) { return uEnc > 0.5 ? c * 0.25 : c; }
vec3 enc(vec3 c) { return uEnc > 0.5 ? c * 4.0 : c; }
vec3 pick(vec2 uv) {
    vec4 c = texture(uSrc, uv);
    vec3 lin = c.rgb * c.rgb;                       // sRGB → lineal (aprox. rápida)
    float l = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
    float mx = max(c.r, max(c.g, c.b));
    float sat = mx > 1e-3 ? (mx - min(c.r, min(c.g, c.b))) / mx : 0.0;
    // Umbral suave («soft knee»): la imagen solo aporta sus luces fuertes, y menos si son
    // blancas sin color (papel de manga, cielos lavados) para no empañar toda la imagen.
    float hi = smoothstep(uPre.y, 1.0, l) * (0.04 + 0.96 * smoothstep(0.08, 0.45, sat));
    float w = clamp(c.a + uPre.x * hi, 0.0, 1.0);   // alfa = luz añadida por los efectos
    return lin * w;
}
void main() {
    vec2 o = uTexel;
    vec2 uv = vScreen * uFrac;
    vec3 a = pick(uv + vec2(-o.x, -o.y));
    vec3 b = pick(uv + vec2( o.x, -o.y));
    vec3 c = pick(uv + vec2(-o.x,  o.y));
    vec3 d = pick(uv + vec2( o.x,  o.y));
    // Promedio de Karis: un píxel muy brillante aislado no parpadea («fireflies»).
    float wa = 1.0 / (1.0 + dot(a, vec3(0.333))), wb = 1.0 / (1.0 + dot(b, vec3(0.333)));
    float wc = 1.0 / (1.0 + dot(c, vec3(0.333))), wd = 1.0 / (1.0 + dot(d, vec3(0.333)));
    vec3 s = (a * wa + b * wb + c * wc + d * wd) / max(wa + wb + wc + wd, 1e-4);
    fragColor = vec4(enc(s), 1.0);
}
"""

    /** Bajada «dual filter»: centro ×4 + 4 diagonales, ÷8. */
    const val DOWN = """#version 300 es
precision mediump float;
in vec2 vScreen;
out vec4 fragColor;
uniform sampler2D uSrc;
uniform vec2 uTexel;   // texel de la textura de origen (más grande)
uniform vec2 uFrac;
uniform float uEnc;  // 1 = pirámide RGBA8: se guarda ×4 para no perder el brillo tenue (probado: lo más fiel)
vec3 dec(vec3 c) { return uEnc > 0.5 ? c * 0.25 : c; }
vec3 enc(vec3 c) { return uEnc > 0.5 ? c * 4.0 : c; }
void main() {
    vec2 o = uTexel;
    vec2 uv = vScreen * uFrac;
    vec3 s = dec(texture(uSrc, uv).rgb) * 4.0;
    s += dec(texture(uSrc, uv - o).rgb);
    s += dec(texture(uSrc, uv + o).rgb);
    s += dec(texture(uSrc, uv + vec2(o.x, -o.y)).rgb);
    s += dec(texture(uSrc, uv - vec2(o.x, -o.y)).rgb);
    fragColor = vec4(enc(s * 0.125), 1.0);
}
"""

    /** Subida «dual filter» (8 lecturas en tienda) sumada al nivel de la misma escala. */
    const val UP = """#version 300 es
precision mediump float;
in vec2 vScreen;
out vec4 fragColor;
uniform sampler2D uSmall;   // nivel siguiente (más pequeño), ya acumulado
uniform sampler2D uSame;    // nivel de bajada de esta escala
uniform vec2 uTexel;        // texel de uSmall
uniform float uMix;
uniform vec2 uFrac;
uniform float uEnc;  // 1 = pirámide RGBA8: se guarda ×4 para no perder el brillo tenue (probado: lo más fiel)
vec3 dec(vec3 c) { return uEnc > 0.5 ? c * 0.25 : c; }
vec3 enc(vec3 c) { return uEnc > 0.5 ? c * 4.0 : c; }
void main() {
    vec2 h = uTexel * 0.5;
    vec2 uv = vScreen * uFrac;
    vec3 s = dec(texture(uSmall, uv + vec2(-h.x * 2.0, 0.0)).rgb);
    s += dec(texture(uSmall, uv + vec2(-h.x, h.y)).rgb) * 2.0;
    s += dec(texture(uSmall, uv + vec2(0.0, h.y * 2.0)).rgb);
    s += dec(texture(uSmall, uv + vec2(h.x, h.y)).rgb) * 2.0;
    s += dec(texture(uSmall, uv + vec2(h.x * 2.0, 0.0)).rgb);
    s += dec(texture(uSmall, uv + vec2(h.x, -h.y)).rgb) * 2.0;
    s += dec(texture(uSmall, uv + vec2(0.0, -h.y * 2.0)).rgb);
    s += dec(texture(uSmall, uv + vec2(-h.x, -h.y)).rgb) * 2.0;
    s /= 12.0;
    fragColor = vec4(enc(mix(dec(texture(uSame, uv).rgb), s, uMix)), 1.0);
}
"""

    /** Composición final: brillo en lineal (mezcla pantalla), halation, grano y dither. */
    const val COMPOSITE = """#version 300 es
precision highp float;
in vec2 vScreen;
out vec4 fragColor;
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform vec2 uSceneScale;   // parte usada de la textura de escena (crece por escalones)
uniform vec4 uPost;         // brillo, halo, grano, t (fase del bucle)
uniform float uEnc;         // 1 = el brillo viene guardado ×4 (RGBA8)
uniform sampler2D uWide;    // nivel muy desenfocado de la luz (para teñir la escena)
uniform vec4 uPost2;        // luz sobre la escena, look, fuerza del look, franjas
uniform vec2 uBars;         // alto de cada franja, eje (0 = arriba/abajo, 1 = lados)
vec3 cineLook(vec3 c, float look) {
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    if (look < 1.5) {            // Kodak cálido: sombras algo frías, luces doradas, curva S
        c = mix(c, c * vec3(0.92, 1.0, 1.06), 1.0 - smoothstep(0.0, 0.45, l));
        c *= mix(vec3(1.0), vec3(1.07, 1.0, 0.87), smoothstep(0.35, 0.95, l));
        c = clamp(c, 0.0, 1.0);
        c = c * c * (3.0 - 2.0 * c) * 0.35 + c * 0.65;
    } else if (look < 2.5) {     // Fuji suave: menos contraste, negros levantados, verde-cian
        c = mix(vec3(l), c, 0.85);
        c = c * 0.92 + 0.04;
        c *= vec3(0.97, 1.02, 1.0);
        c = mix(c, c * vec3(0.93, 1.02, 1.04), 1.0 - smoothstep(0.0, 0.5, l));
    } else if (look < 3.5) {     // Teal & Orange: sombras turquesa, pieles y luces naranjas
        c *= mix(vec3(0.80, 1.0, 1.08), vec3(1.12, 0.98, 0.82), smoothstep(0.25, 0.75, l));
        c = mix(vec3(l), c, 1.15);
        c = clamp(c, 0.0, 1.0);
        c = c * c * (3.0 - 2.0 * c) * 0.3 + c * 0.7;
    } else if (look < 4.5) {     // Noche azul («día por noche» del cine)
        c = mix(vec3(l), c, 0.45) * vec3(0.62, 0.74, 1.0) * 0.72;
        c = pow(max(c, 0.0), vec3(1.15));
    } else {                     // Blanco y negro de cine, papel algo cálido
        float g = l * l * (3.0 - 2.0 * l) * 0.5 + l * 0.5;
        c = vec3(g) * vec3(1.03, 1.0, 0.95);
    }
    return clamp(c, 0.0, 1.0);
}
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
void main() {
    vec2 uv = vScreen * uSceneScale;
    vec3 c = texture(uScene, uv).rgb;
    vec3 lin = c * c;
    // v44.20 · La luz de los efectos ILUMINA lo que tiene cerca: multiplica el color de las
    // superficies (el fuego tiñe de naranja las paredes y caras cercanas, la energía de azul).
    if (uPost2.x > 0.0) {
        vec3 w = texture(uWide, uv).rgb;
        if (uEnc > 0.5) w *= 0.25;
        lin += lin * min(w * uPost2.x * 7.0, vec3(2.5));
    }
    if (uPost.x > 0.0 || uPost.y > 0.0) {
        vec3 b = texture(uBloom, uv).rgb;
        if (uEnc > 0.5) b *= 0.25;
        // Halation: la película se «quema» en rojo-naranja alrededor de las luces fuertes.
        float bl = dot(b, vec3(0.2126, 0.7152, 0.0722));
        vec3 halo = vec3(1.0, 0.36, 0.14) * bl * uPost.y * 1.4;
        vec3 add = b * uPost.x + halo;
        // Mezcla «pantalla» en lineal: ilumina sin pasar de 1 y sin lavar a gris.
        lin = 1.0 - (1.0 - lin) * exp(-add * 1.15);
    }
    vec3 outc = sqrt(max(lin, 0.0));
    if (uPost2.z > 0.0) outc = mix(outc, cineLook(clamp(outc, 0.0, 1.0), uPost2.y), uPost2.z);
    // Grano de película: más en los medios tonos; semilla = fase del bucle (determinista).
    if (uPost.z > 0.0) {
        float l = dot(outc, vec3(0.299, 0.587, 0.114));
        vec2 cell = floor(gl_FragCoord.xy / 1.6);
        float g = hash12(cell + fract(uPost.w * 61.7) * vec2(431.0, 197.0)) - 0.5;
        g += hash12(cell * 1.37 + vec2(17.0, 3.0) + fract(uPost.w * 37.3) * vec2(97.0, 311.0)) - 0.5;
        outc += g * uPost.z * 0.09 * (0.35 + 2.6 * l * (1.0 - l));
    }
    outc += (hash12(gl_FragCoord.xy + 0.37) - 0.5) / 255.0;   // dither: sin bandas en cielos
    // v44.20 · franjas de cine (negras, borde nítido)
    if (uBars.x > 0.0) {
        float e = uBars.y > 0.5 ? vScreen.x : vScreen.y;
        if (e < uBars.x || e > 1.0 - uBars.x) outc = vec3(0.0);
    }
    fragColor = vec4(clamp(outc, 0.0, 1.0), 1.0);
}
"""
}
