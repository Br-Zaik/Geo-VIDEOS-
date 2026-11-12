package com.zeta.fondo.engine

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Dibuja la imagen animada con el shader principal. Se usa igual en la vista
 * previa (GLSurfaceView) y en la exportación (superficie del codificador o FBO),
 * así lo que ves es lo que se exporta.
 * Debe crearse y usarse siempre en el hilo que tiene el contexto OpenGL.
 *
 * v36: los parámetros de los efectos viven en un Uniform Buffer (bloque std140
 * "ZetaParams"). Antes eran ~250 uniforms sueltos, al límite de las GPU Adreno/Mali
 * económicas (GL_MAX_FRAGMENT_UNIFORM_VECTORS = 224-256), lo que podía impedir el
 * enlace del shader en algunos teléfonos. Ahora el límite es el tamaño del bloque
 * (mínimo garantizado 16 KB; usamos ~3-4 KB) y se sube con una sola llamada.
 * Las máscaras base (proteger, zona azul y zona verde) se leen del mismo atlas
 * 2D-array que las máscaras propias: menos memoria y una sola subida por trazo.
 */
class FondoGl(private val vertSrc: String, private val fragSrc: String,
              private val liteSrc: String? = null,
              /** Carpeta para guardar los shaders ya compilados (null = sin caché). */
              private val binaryCacheDir: java.io.File? = null,
              /**
               * v41: true = si falta la variante del shader se compila aquí mismo (exportación).
               * La vista previa usa false: las variantes llegan de [ShaderCompiler].
               */
              var syncCompile: Boolean = true) {

    // v41: un programa por combinación de efectos (variante). Todas comparten el mismo
    // bloque de parámetros std140 (misma distribución), así que el búfer es único.
    private var program = 0
    private val programs = LinkedHashMap<String, Int>(8, .75f, true)
    private val locsByProgram = HashMap<Int, HashMap<String, Int>>()
    private val layout: Std140Layout = layoutFor(fragSrc)
    // v38: programa ligero (imagen + máscara). Compila en milisegundos y se usa en el
    // primer cuadro, durante el zoom y al pintar. El programa completo (uber-shader)
    // puede compilarse después, en un momento sin gestos (ver PreviewRenderer).
    private var liteProgram = 0
    private val liteLocs = HashMap<String, Int>()
    /** true cuando hay al menos una variante del shader de efectos lista. */
    val fullReady: Boolean get() = program != 0
    /** ¿Está lista la variante [key]? */
    fun hasVariant(key: String): Boolean = programs.containsKey(key)
    private var vao = 0
    private var vbo = 0
    private val tex = IntArray(3) // 0 imagen, 1 flujo, 2 atlas de máscaras (2D array)
    private var uploadedParams: RenderParams? = null
    private var maskBuf: ByteBuffer? = null
    private var atlasW = 0
    private var atlasH = 0

    // ---- Uniform buffer de parámetros
    private var ubo = 0
    private var blockSize = 0
    private var block: ByteBuffer = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
    private var blockDirty = true

    private var lastCharAspect = Float.NaN
    private val charTex = IntArray(1)
    // v45: mapa de profundidad IA (R8, unidad 3). Sin mapa → el shader usa el 2.5D manual.
    private val depthTex = IntArray(1)
    /** true cuando hay un mapa de profundidad subido. */
    var depthReady = false
        private set
    // v46: placa de fondo (RGBA8, unidad 4).
    private val plateTex = IntArray(1)
    var plateReady = false
        private set
    /** Solo vista previa: muestra el mapa de profundidad en lugar de la animación. */
    var depthView = false
    var charAspect = 1f
        private set

    var texW = 1
        private set
    var texH = 1
        private set

    /**
     * [deferFull] = true: solo se prepara el programa ligero; el completo se compila con
     * [buildFull] cuando convenga. La exportación usa init() normal (todo de inmediato).
     */
    fun init(deferFull: Boolean = false) {
        clearErrors()
        if (liteSrc != null) {
            liteProgram = try { buildProgram(vertSrc, liteSrc) } catch (_: Throwable) { 0 }
        }
        // v41: las variantes del shader de efectos se compilan aparte (ver [ensureVariant]
        // y ShaderCompiler); aquí solo se crea el búfer de parámetros, que es común.
        createParamBlock()

        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val fb = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(quad).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        GLES30.glGenTextures(3, tex, 0)

        // Texturas por defecto (vacías) para que el shader siempre tenga algo válido
        val px = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        px.put(byteArrayOf(20, 24, 48, -1)).position(0)
        setupTex(0, 0, false)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, px)
        uploadFlowRaw(1, 1, ShortArray(2))
        prepareMasks(4, 4)
        GLES30.glGenTextures(1, charTex, 0)
        uploadCharacter(null)
        GLES30.glGenTextures(1, depthTex, 0)
        uploadDepth(0, 0, null)
        GLES30.glGenTextures(1, plateTex, 0)
        uploadPlate(0, 0, null)
        checkGl("init")
    }

    /** Compatibilidad: compila la variante con TODOS los efectos (solo pruebas/diagnóstico). */
    fun buildFull() { ensureVariant(ShaderVariants.declaredFeatures(fragSrc)) }

    /** Compila (o carga de la caché) la variante con [features] en ESTE hilo. */
    fun ensureVariant(features: Set<String>): Boolean {
        val key = ShaderVariants.key(features)
        if (programs.containsKey(key)) return true
        clearErrors()
        val prog = ProgramCache.build(vertSrc, ShaderVariants.source(fragSrc, features), binaryCacheDir, key)
        adoptVariant(key, prog)
        return true
    }

    /** Incorpora un programa compilado (aquí o en el contexto compartido del compilador). */
    fun adoptVariant(key: String, prog: Int) {
        if (prog == 0) return
        programs.remove(key)?.let { old -> if (old != prog) deleteProgram(old) }
        val index = GLES30.glGetUniformBlockIndex(prog, BLOCK_NAME)
        if (index != GL_INVALID_INDEX) GLES30.glUniformBlockBinding(prog, index, BLOCK_BINDING)
        programs[key] = prog
        if (program == 0) program = prog
        // Pocas variantes en memoria: se borran las menos usadas (nunca la que se dibuja).
        while (programs.size > MAX_PROGRAMS) {
            val victim = programs.entries.firstOrNull { it.value != program } ?: break
            programs.remove(victim.key)
            deleteProgram(victim.value)
        }
        clearErrors()
    }

    private fun deleteProgram(prog: Int) {
        GLES30.glDeleteProgram(prog)
        locsByProgram.remove(prog)
    }

    /**
     * Cuadro ligero: la imagen con el zoom actual y, si se está pintando, la máscara
     * activa. Mismo encuadre y mismas coordenadas que [draw].
     */
    fun drawLite(vw: Int, vh: Int, view: FloatArray, showMask: Boolean, activeMask: Int,
                 vx0: Int = 0, vy0: Int = 0, maskAlpha: Float = .45f) {
        if (liteProgram == 0) return
        GLES30.glViewport(vx0, vy0, vw, vh)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(liteProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glActiveTexture(GLES30.GL_TEXTURE5)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tex[2])
        GLES30.glUniform1i(liteLoc("uImage"), 0)
        GLES30.glUniform1i(liteLoc("uMasks"), 5)
        GLES30.glUniform2f(liteLoc("uViewOffset"), view[0], view[1])
        GLES30.glUniform2f(liteLoc("uViewScale"), view[2], view[3])
        GLES30.glUniform1f(liteLoc("uFlipY"), 0f)
        GLES30.glUniform1f(liteLoc("uShowMask"), if (showMask) 1f else 0f)
        GLES30.glUniform1i(liteLoc("uActiveMask"), activeMask)
        GLES30.glUniform1f(liteLoc("uMaskAlpha"), maskAlpha)
        // v44.23: zonas con forma (se ven en morado al pintar su zona).
        GLES30.glUniform4fv(liteLoc("uZoneA"), 12, zones, 0)
        GLES30.glUniform4fv(liteLoc("uZoneB"), 12, zones, 48)
        GLES30.glUniform1f(liteLoc("uImgAspect"), texW.toFloat() / texH.coerceAtLeast(1))
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
        // v44.23: mientras los efectos se preparan, las capas ya se ven (no al pintar).
        if (!showMask) compositeLayers(liteT, vx0, vy0, vw, vh, view, false, false, false, parallax = false)
    }

    val hasLite: Boolean get() = liteProgram != 0

    private fun liteLoc(name: String): Int = liteLocs.getOrPut(name) { GLES30.glGetUniformLocation(liteProgram, name) }

    private fun createParamBlock() {
        blockSize = layout.size
        val maxBlock = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_UNIFORM_BLOCK_SIZE, maxBlock, 0)
        if (maxBlock[0] in 1 until blockSize) {
            throw RuntimeException("La GPU admite bloques de ${maxBlock[0]} bytes y el shader necesita $blockSize")
        }
        block = ByteBuffer.allocateDirect(blockSize).order(ByteOrder.nativeOrder())
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        ubo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, ubo)
        GLES30.glBufferData(GLES30.GL_UNIFORM_BUFFER, blockSize, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, BLOCK_BINDING, ubo)
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0)
        uploadedParams = null
        blockDirty = true
    }

    /** [offset en bytes, salto entre elementos de array, 1 si es int] o null si no existe. */
    private fun member(name: String): IntArray? = layout.members[name]

    /** Escribe un valor del bloque. Acepta "uNombre" y "uNombre[i]". */
    private fun putFloats(name: String, a: FloatArray, count: Int = a.size) {
        var base = name
        var element = 0
        val bracket = name.indexOf('[')
        if (bracket > 0) {
            base = name.substring(0, bracket)
            element = name.substring(bracket + 1, name.length - 1).toIntOrNull() ?: 0
        }
        val m = member(base) ?: member("$base[0]") ?: return
        val start = m[0] + element * m[1]
        for (i in 0 until count.coerceAtMost(a.size)) {
            if (start + i * 4 + 4 > blockSize) break
            block.putFloat(start + i * 4, a[i])
        }
        blockDirty = true
    }

    private fun put4(name: String, x: Float, y: Float, z: Float, w: Float) {
        val m = member(name) ?: return
        val o = m[0]
        if (o + 16 > blockSize) return
        block.putFloat(o, x); block.putFloat(o + 4, y); block.putFloat(o + 8, z); block.putFloat(o + 12, w)
        blockDirty = true
    }

    private fun put3(name: String, x: Float, y: Float, z: Float) {
        val m = member(name) ?: return
        val o = m[0]
        if (o + 12 > blockSize) return
        block.putFloat(o, x); block.putFloat(o + 4, y); block.putFloat(o + 8, z)
        blockDirty = true
    }

    private fun put2(name: String, x: Float, y: Float) {
        val m = member(name) ?: return
        if (m[0] + 8 > blockSize) return
        block.putFloat(m[0], x); block.putFloat(m[0] + 4, y)
        blockDirty = true
    }

    private fun put1(name: String, x: Float) {
        val m = member(name) ?: return
        if (m[0] + 4 > blockSize) return
        block.putFloat(m[0], x)
        blockDirty = true
    }

    private fun putInt(name: String, v: Int) {
        val m = member(name) ?: return
        if (m[0] + 4 > blockSize) return
        if (m[2] == 1) block.putInt(m[0], v) else block.putFloat(m[0], v.toFloat())
        blockDirty = true
    }

    private fun flushBlock() {
        if (!blockDirty) return
        block.position(0)
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, ubo)
        GLES30.glBufferSubData(GLES30.GL_UNIFORM_BUFFER, 0, blockSize, block)
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0)
        blockDirty = false
    }

    private fun setupTex(unit: Int, slot: Int, mipmap: Boolean) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[slot])
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER,
            if (mipmap) GLES30.GL_LINEAR_MIPMAP_LINEAR else GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    /** Sube la imagen a su resolución real, con mipmaps para reducir sin serrucho. */
    fun uploadImage(bmp: Bitmap) {
        clearErrors()
        setupTex(0, 0, true)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        texW = bmp.width
        texH = bmp.height
        checkGl("uploadImage")
    }

    /** Personaje PNG con transparencia para el efecto Aparición (null = vacío). */
    fun uploadCharacter(bmp: Bitmap?) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE6)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, charTex[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        if (bmp == null || bmp.isRecycled) {
            val z = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, z)
            charAspect = 1f
        } else {
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            charAspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    /**
     * v45: sube el mapa de profundidad (un byte por píxel, fila 0 = arriba, 255 = cerca),
     * en las mismas coordenadas de imagen que las máscaras. null = sin mapa (2.5D manual).
     */
    fun uploadDepth(w: Int, h: Int, map: ByteArray?) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTex[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val ok = map != null && w > 0 && h > 0 && map.size >= w * h
        val buf = ByteBuffer.allocateDirect(if (ok) w * h else 1).order(ByteOrder.nativeOrder())
        if (ok) buf.put(map!!, 0, w * h) else buf.put(0)
        buf.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, if (ok) w else 1, if (ok) h else 1, 0,
            GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        depthReady = ok
        depthCpu = if (ok) DepthMap(w, h, map!!) else null
        uploadedParams = null
    }

    /** v46: sube la placa de fondo (ARGB, fila 0 = arriba). null = sin placa. */
    fun uploadPlate(w: Int, h: Int, argb: IntArray?) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, plateTex[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val ok = argb != null && w > 0 && h > 0 && argb.size >= w * h
        val n = if (ok) w * h else 1
        val buf = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
        if (ok) for (i in 0 until n) {
            val c = argb!![i]
            buf.put((c shr 16).toByte()); buf.put((c shr 8).toByte()); buf.put(c.toByte()); buf.put(-1)
        } else buf.putInt(0)
        buf.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, if (ok) w else 1, if (ok) h else 1, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        plateReady = ok
        uploadedParams = null
    }

    fun uploadFlow(d: FlowField.Data) = uploadFlowRaw(d.w, d.h, d.halfs)

    private fun uploadFlowRaw(w: Int, h: Int, halfs: ShortArray) {
        val buf = ByteBuffer.allocateDirect(halfs.size * 2).order(ByteOrder.nativeOrder())
        buf.asShortBuffer().put(halfs)
        buf.position(0)
        setupTex(1, 1, false)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RG16F, w, h, 0, GLES30.GL_RG, GLES30.GL_HALF_FLOAT, buf)
        checkGl("uploadFlow")
    }

    /** Reserva todas las máscaras en una única textura-array GLES3, sin aumentar las unidades de sampler. */
    fun prepareMasks(w: Int, h: Int): Boolean {
        if (w == atlasW && h == atlasH) return false
        atlasW = w
        atlasH = h
        GLES30.glActiveTexture(GLES30.GL_TEXTURE5)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tex[2])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val empty = ByteBuffer.allocateDirect(w * h * MaskLayers.MAX_MASKS)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_R8,
            w, h, MaskLayers.MAX_MASKS, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, empty)
        checkGl("prepareMasks")
        return true
    }

    /**
     * Sube una capa ALPHA_8 al atlas. Con [rowFrom]/[rowTo] solo se envían las filas
     * que cambió el pincel (antes se reenviaba la capa completa dos veces por trazo).
     */
    fun uploadMask(layer: Int, bmp: Bitmap, rowFrom: Int = 0, rowTo: Int = bmp.height) {
        val size = bmp.rowBytes * bmp.height
        var buf = maskBuf
        if (buf == null || buf.capacity() < size) {
            buf = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            maskBuf = buf
        }
        buf!!.position(0)
        buf.limit(size)
        bmp.copyPixelsToBuffer(buf)
        if (layer == 0) keepProtect(buf, bmp.width, bmp.height, bmp.rowBytes)
        prepareMasks(bmp.width, bmp.height)
        val y0 = rowFrom.coerceIn(0, bmp.height)
        val y1 = rowTo.coerceIn(y0, bmp.height)
        if (y1 <= y0) return
        buf.position(y0 * bmp.rowBytes)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ROW_LENGTH, bmp.rowBytes)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE5)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tex[2])
        GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_2D_ARRAY, 0, 0, y0, layer,
            bmp.width, y1 - y0, 1, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ROW_LENGTH, 0)
        buf.position(0)
        checkGl("uploadMask")
    }

    private fun loc(name: String): Int = locsByProgram.getOrPut(program) { HashMap() }
        .getOrPut(name) { GLES30.glGetUniformLocation(program, name) }

    private fun studioName(id: String) = id.replaceFirstChar { it.uppercaseChar() }

    // v40: la línea de tiempo (bloque dominante, ajustes propios de cada bloque y
    // visibilidad) se calcula en FrameUniforms, código puro compartido con las pruebas.
    private val frame = FrameUniforms()
    private val sink = object : FrameUniforms.Sink {
        override fun putFloats(name: String, values: FloatArray) {
            // v44.24: la cámara del 2.5D también mueve las capas de imagen (LayerParallax).
            if (name == "uParallax") lastParallax = values.copyOf()
            else if (name == "uParallaxStyle") lastParallaxStyle = values.copyOf()
            this@FondoGl.putFloats(name, values)
        }
        override fun putInt(name: String, value: Int) = this@FondoGl.putInt(name, value)
    }

    /** Escribe todos los parámetros (solo cuando cambia el objeto RenderParams). */
    private fun writeParams(p: RenderParams) {
        frame.writeBase(p, sink)
        blockDirty = true
    }

    /**
     * view = [offsetX, offsetY, scaleX, scaleY] en coordenadas de imagen.
     * flipY: para leer píxeles con glReadPixels (PNG). rotate: vídeo vertical guardado en horizontal.
     */
    fun draw(
        p: RenderParams, t: Float, vw: Int, vh: Int, view: FloatArray,
        flipY: Boolean, rotate: Boolean, showMask: Boolean, overlayOnly: Boolean,
        activeMask: Int = -1,
        vx0: Int = 0,
        vy0: Int = 0,
        fast: Boolean = false
    ): Boolean {
        // v44.19 · «Calidad cine»: la escena va a un FBO y después pasa por la cadena de brillo
        // real, halo y grano. Al pintar, en el modo rápido, con «solo efectos» o viendo el
        // mapa de profundidad se dibuja directo. Cualquier fallo → directo, como antes.
        val wantCine = cineEnabled && CinePlan.active(p.post) && !showMask && !fast && !overlayOnly && !depthView
        if (wantCine) {
            val post = cine ?: CinePost(vertSrc).also { cine = it }
            if (!post.broken && hasProgramFor(p) && post.begin(vw, vh)) {
                val ok = try {
                    drawScene(p, t, vw, vh, view, flipY, rotate, false, false, activeMask, 0, 0, false, true)
                } catch (e: Throwable) { post.restore(); throw e }
                if (!ok) { post.restore(); return false }
                // v44.23: las capas de imagen entran ANTES del brillo, el grano y el look: se
                // integran con la escena (una luz en «Pantalla» también brilla).
                compositeLayers(t, 0, 0, vw, vh, view, flipY, rotate, true)
                if (post.finish(vx0, vy0, vw, vh, p.post, t, rotate)) return true
                // La composición falló: se repite el cuadro directo (y ya no se intenta más).
            }
        }
        val ok = drawScene(p, t, vw, vh, view, flipY, rotate, showMask, overlayOnly, activeMask, vx0, vy0, fast, false)
        if (ok && !showMask && !overlayOnly && !depthView) compositeLayers(t, vx0, vy0, vw, vh, view, flipY, rotate, false)
        return ok
    }

    // ------------------------------------------------------------ v44.23 · capas de imagen

    /** Capas de imagen a dibujar encima (null o vacía = ninguna). */
    @Volatile var layerStack: LayerStack? = null

    // v44.24 · Lo que necesitan las capas para moverse con la «Profundidad 2.5D».
    private var lastParallax: FloatArray? = null
    private var lastParallaxStyle: FloatArray? = null
    private var depthWanted = false
    private var depthCpu: DepthMap? = null
    private var protectSmall: ByteArray? = null
    private var protectW = 0
    private var protectH = 0

    /** Copia pequeña (≤ 96 px) de Proteger: una capa sobre el personaje protegido no se mueve. */
    private fun keepProtect(buf: ByteBuffer, w: Int, h: Int, rowBytes: Int) {
        if (w <= 0 || h <= 0) return
        val s = minOf(1f, 96f / maxOf(w, h))
        val sw = maxOf(1, (w * s).toInt()); val sh = maxOf(1, (h * s).toInt())
        val out = ByteArray(sw * sh)
        for (y in 0 until sh) {
            val yy = minOf(h - 1, ((y + .5f) / s).toInt())
            for (x in 0 until sw) {
                val xx = minOf(w - 1, ((x + .5f) / s).toInt())
                out[y * sw + x] = buf.get(yy * rowBytes + xx)
            }
        }
        protectSmall = out; protectW = sw; protectH = sh
    }

    /** Profundidad 0..1 del fondo en (u, v), como la ve el shader (null = las zonas manuales). */
    private fun sceneDepthAt(u: Float, v: Float): Float? {
        if (!depthWanted) return null
        val uu = u.coerceIn(0f, 1f); val vv = v.coerceIn(0f, 1f)
        val pr = protectSmall
        if (pr != null && protectW > 0) {
            val p = (pr[minOf(protectH - 1, (vv * protectH).toInt()) * protectW + minOf(protectW - 1, (uu * protectW).toInt())].toInt() and 0xFF) / 255f
            if (p > .5f) return Float.NaN   // sobre Proteger: plano de foco
        }
        val d = depthCpu ?: return vv   // mapa aún calculándose: el mismo relieve provisional del shader
        val i = minOf(d.h - 1, (vv * d.h).toInt()) * d.w + minOf(d.w - 1, (uu * d.w).toInt())
        return (d.map[i].toInt() and 0xFF) / 255f
    }

    /** Cámara del 2.5D de este cuadro para las capas (null = no actúa). */
    private fun layerCam(t: Float): com.zeta.fondo.model.LayerParallax.Cam? =
        com.zeta.fondo.model.LayerParallax.cam(lastParallax, lastParallaxStyle, t)
    /** v44.23: zonas con forma del último cuadro (el cuadro ligero las usa al pintar). */
    var zones: FloatArray = RenderParams.ZONES_OFF
    /** Fase del bucle para las capas en el cuadro ligero (la pone quien dibuja). */
    var liteT = 0f
    private var layerComp: LayerCompositor? = null
    /** Estado de las capas para Diagnóstico. */
    val layerStatus: String get() = layerComp?.lastError ?: if (layerComp != null) "Bien" else "Sin usar todavía"

    /** Exportación: sube una capa ya decodificada (luego se puede reciclar el bitmap). */
    fun uploadLayer(key: String, bmp: Bitmap) {
        val comp = layerComp ?: LayerCompositor(vertSrc).also { layerComp = it }
        comp.upload(key, bmp)
    }

    private fun compositeLayers(t: Float, x0: Int, y0: Int, vw: Int, vh: Int, view: FloatArray,
                                flipY: Boolean, rotate: Boolean, emit: Boolean, parallax: Boolean = true) {
        val stack = layerStack ?: return
        if (stack.isEmpty) return
        val comp = layerComp ?: LayerCompositor(vertSrc).also { layerComp = it }
        try {
            // El cuadro ligero no tiene 2.5D: las capas tampoco se mueven ahí.
            val cam = if (parallax) layerCam(t) else null
            val move: ((com.zeta.fondo.model.ImageLayer, com.zeta.fondo.model.LayerFrame) -> com.zeta.fondo.model.LayerFrame)? =
                if (cam == null) null else { layer, f ->
                    val raw = com.zeta.fondo.model.LayerParallax.rawDepth(layer, f) { u, v -> sceneDepthAt(u, v) }
                    val d = if (raw != null && raw.isNaN()) -cam.focus
                        else com.zeta.fondo.model.LayerParallax.depthFor(raw, cam, !depthWanted)
                    com.zeta.fondo.model.LayerParallax.apply(f, cam, d, view)
                }
            comp.composite(stack, t, x0, y0, vw, vh, view, flipY, rotate, emit, vao, tex[2], move)
        } catch (e: Throwable) {
            // Un fallo de las capas nunca deja el cuadro en negro: la escena ya está dibujada.
            clearErrors()
            com.zeta.fondo.diag.ZetaLog.error("Capas", "No se pudieron dibujar las capas: ${e.message}")
        }
    }

    /** v44.19 · ¿Hay programa para dibujar estos parámetros (o se puede compilar aquí)? */
    private fun hasProgramFor(p: RenderParams): Boolean = syncCompile || programs.containsKey(p.variantKey) || program != 0

    /** v44.19 · Cadena cine (se crea al primer uso). null = aún no usada. */
    private var cine: CinePost? = null
    /** v44.19 · Interruptor global (Diagnóstico puede apagarlo para comparar). */
    var cineEnabled = true
    /** v44.19 · Estado de la cadena cine para Diagnóstico. */
    val cineStatus: String get() = cine?.formatLabel ?: "Sin usar todavía"

    private fun drawScene(
        p: RenderParams, t: Float, vw: Int, vh: Int, view: FloatArray,
        flipY: Boolean, rotate: Boolean, showMask: Boolean, overlayOnly: Boolean,
        activeMask: Int, vx0: Int, vy0: Int, fast: Boolean, emitAlpha: Boolean
    ): Boolean {
        // v41: la variante exacta de estos parámetros; si aún no está (vista previa), la
        // última disponible (el efecto nuevo aparece en cuanto termina de compilarse).
        val wanted = programs[p.variantKey]
        if (wanted == null && syncCompile) ensureVariant(p.features)
        val prog = programs[p.variantKey] ?: program
        if (prog == 0) return false
        program = prog
        GLES30.glViewport(vx0, vy0, vw, vh)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[1])
        GLES30.glUniform1i(loc("uImage"), 0)
        GLES30.glUniform1i(loc("uFlow"), 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE5)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tex[2])
        GLES30.glUniform1i(loc("uMasks"), 5)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE6)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, charTex[0])
        GLES30.glUniform1i(loc("uCharacter"), 6)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTex[0])
        GLES30.glUniform1i(loc("uDepth"), 3)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, plateTex[0])
        GLES30.glUniform1i(loc("uPlate"), 4)
        // v44.21 · libro de efectos (fuego simulado, hojas y pétalos pintados): solo si se usa.
        if (fxTex == 0 && !fxTried && FxBook.needed(p.features)) loadFxBook()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE7)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fxTex)
        GLES30.glUniform1i(loc("uFxBook"), 7)
        GLES30.glUniform1f(loc("uFxReady"), if (fxTex != 0) 1f else 0f)

        GLES30.glUniform2f(loc("uViewOffset"), view[0], view[1])
        GLES30.glUniform2f(loc("uViewScale"), view[2], view[3])
        GLES30.glUniform1f(loc("uFlipY"), if (flipY) 1f else 0f)
        GLES30.glUniform1f(loc("uRotate"), if (rotate) 1f else 0f)
        GLES30.glUniform1f(loc("uT"), t)
        GLES30.glUniform2f(loc("uTexSize"), texW.toFloat(), texH.toFloat())
        GLES30.glUniform1f(loc("uShowMask"), if (showMask) 1f else 0f)
        GLES30.glUniform1i(loc("uActiveMask"), activeMask)
        GLES30.glUniform1f(loc("uOverlayOnly"), if (overlayOnly) 1f else 0f)
        GLES30.glUniform1f(loc("uFast"), if (fast) 1f else 0f)
        GLES30.glUniform1f(loc("uEmitAlpha"), if (emitAlpha) 1f else 0f)
        GLES30.glUniform1f(loc("uMotionBlur"), if (fast || showMask) 0f else CinePlan.motionBlur(p.post))
        // v44.23: zonas con forma (círculo / rectángulo) de cada máscara propia.
        zones = p.zones
        GLES30.glUniform4fv(loc("uZoneA"), 12, p.zones, 0)
        GLES30.glUniform4fv(loc("uZoneB"), 12, p.zones, 48)
        // Los parámetros son inmutables; el bloque completo solo se reescribe cuando
        // cambia RenderParams. La línea de tiempo modifica unos pocos valores por cuadro.
        if (uploadedParams !== p) {
            writeParams(p)
            lastCharAspect = Float.NaN
            uploadedParams = p
        }
        frame.apply(p, t, sink)
        // v45: la IA solo cuenta si hay mapa; «ver mapa» solo existe en la vista previa.
        // v44.24: con la IA pedida y el mapa aún sin subir, DEPTH_PENDING = relieve provisional.
        p.studio["uDepthInfo"]?.let { d ->
            put4("uDepthInfo", if (d[0] > .5f) (if (depthReady) 1f else DEPTH_PENDING) else 0f, d[1], d[2], if (depthView) 1f else 0f)
        }
        p.studio["uParallaxStyle"]?.let { s -> lastParallaxStyle = s; put4("uParallaxStyle", s[0], s[1], s[2], if (plateReady && s[3] > .5f) 1f else 0f) }
        p.studio["uDepthInfo"]?.let { d -> depthWanted = d[0] > .5f }
        if (lastCharAspect != charAspect) { put1("uCharAspect", charAspect); lastCharAspect = charAspect }
        flushBlock()
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, BLOCK_BINDING, ubo)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
        return true
    }

    fun maxTextureSize(): Int {
        val a = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, a, 0)
        return if (a[0] > 0) a[0] else 2048
    }

    /** v44.21 · textura del libro de efectos (0 = no cargada). */
    private var fxTex = 0
    private var fxTried = false

    private fun loadFxBook() {
        fxTried = true
        val bmp = FxBook.decode() ?: return
        // v44.22: cualquier fallo aquí deja los efectos en su versión procedural; nunca debe
        // llegar al dibujo (antes una excepción podía dejar la vista previa en negro).
        try {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE7)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            if (GLES30.glGetError() == GLES30.GL_NO_ERROR) fxTex = ids[0]
            else GLES30.glDeleteTextures(1, ids, 0)
        } catch (t: Throwable) {
            fxTex = 0
            var guard = 0
            while (GLES30.glGetError() != GLES30.GL_NO_ERROR && guard++ < 16) { /* limpiar */ }
            com.zeta.fondo.diag.ZetaLog.error("FxBook", "Libro de efectos no subido a la GPU: ${t.message}")
        } finally {
            bmp.recycle()
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        }
    }

    fun release() {
        layerComp?.release()
        layerComp = null
        cine?.release()
        cine = null
        if (fxTex != 0) GLES30.glDeleteTextures(1, intArrayOf(fxTex), 0)
        fxTex = 0
        fxTried = false
        programs.values.forEach { GLES30.glDeleteProgram(it) }
        programs.clear()
        locsByProgram.clear()
        if (liteProgram != 0) GLES30.glDeleteProgram(liteProgram)
        liteProgram = 0
        GLES30.glDeleteTextures(3, tex, 0)
        GLES30.glDeleteTextures(1, charTex, 0)
        GLES30.glDeleteTextures(1, depthTex, 0)
        GLES30.glDeleteTextures(1, plateTex, 0)
        plateReady = false
        depthReady = false
        GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (ubo != 0) GLES30.glDeleteBuffers(1, intArrayOf(ubo), 0)
        GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        program = 0
        ubo = 0
    }

    private fun clearErrors() {
        var guard = 0
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR && guard < 16) guard++
    }

    private fun checkGl(where: String) {
        val e = GLES30.glGetError()
        if (e != GLES30.GL_NO_ERROR) {
            throw RuntimeException("OpenGL error 0x${Integer.toHexString(e)} en $where")
        }
    }

    companion object {
        const val BLOCK_NAME = "ZetaParams"
        /** v44.24 · uDepthInfo.x mientras el mapa automático se calcula (el shader usa un relieve provisional). */
        const val DEPTH_PENDING = 0.25f
        /** Variantes que se guardan en la GPU a la vez. */
        private const val MAX_PROGRAMS = 6
        private val layouts = HashMap<String, Std140Layout>()
        /** La distribución del bloque se calcula una vez por shader. */
        fun layoutFor(frag: String): Std140Layout = synchronized(layouts) {
            layouts.getOrPut(frag) { Std140Layout.parse(frag) }
        }
        private const val BLOCK_BINDING = 0
        /** GL_INVALID_INDEX (0xFFFFFFFF) visto como Int de Java. */
        private const val GL_INVALID_INDEX = -1

        fun buildProgram(v: String, f: String, retrievable: Boolean = false): Int {
            val vs = compile(GLES30.GL_VERTEX_SHADER, v)
            val fs = compile(GLES30.GL_FRAGMENT_SHADER, f)
            val prog = GLES30.glCreateProgram()
            GLES30.glAttachShader(prog, vs)
            GLES30.glAttachShader(prog, fs)
            if (retrievable) GLES30.glProgramParameteri(prog, GLES30.GL_PROGRAM_BINARY_RETRIEVABLE_HINT, GLES30.GL_TRUE)
            GLES30.glLinkProgram(prog)
            val ok = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, ok, 0)
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            if (ok[0] == 0) {
                val log = GLES30.glGetProgramInfoLog(prog)
                GLES30.glDeleteProgram(prog)
                throw RuntimeException("Error al enlazar shaders: $log")
            }
            return prog
        }

        private fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES30.glGetShaderInfoLog(s)
                GLES30.glDeleteShader(s)
                val kind = if (type == GLES30.GL_VERTEX_SHADER) "vértices" else "fragmentos"
                throw RuntimeException("Error en el shader de $kind: $log")
            }
            return s
        }
    }
}
