package com.zeta.fondo.engine

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import com.zeta.fondo.model.BlendModes
import com.zeta.fondo.model.ImageLayer
import com.zeta.fondo.model.ImageLayers
import com.zeta.fondo.model.LayerFrame
import java.io.File
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * v44.23 · Lo que la vista previa o la exportación necesitan para dibujar las capas de imagen.
 * Inmutable: se reemplaza entero cuando algo cambia (como RenderParams).
 *  - [bitmaps]: imágenes ya decodificadas por archivo (vista previa);
 *  - [files]: archivos para decodificar a resolución de exportación (exportación).
 */
class LayerStack(
    val layers: List<ImageLayer>,
    val loopSec: Float,
    val baseW: Int,
    val baseH: Int,
    val bitmaps: Map<String, Bitmap> = emptyMap(),
    val files: Map<String, File> = emptyMap()
) {
    val visible: List<ImageLayer> get() = layers.filter { !it.hidden }
    val isEmpty: Boolean get() = layers.none { !it.hidden }
    fun withBitmaps(b: Map<String, Bitmap>) = LayerStack(layers, loopSec, baseW, baseH, b, files)
}

/**
 * v44.23 · Dibuja las capas de imagen sobre la escena ya dibujada (en pantalla, en el FBO de
 * «Calidad cine» o en la superficie del codificador), de abajo arriba.
 *
 * Dos caminos, con el MISMO resultado:
 *  - mezcla por hardware (sin copias) para Normal, Multiplicar, Máscara, Excluir y Pantalla:
 *    las fórmulas exactas de esos modos se pueden escribir con glBlendFunc;
 *  - copia + fórmula para el resto: se copia SOLO el rectángulo que ocupa la capa en
 *    pantalla (glCopyTexSubImage2D) y el shader calcula el modo con el fondo real.
 * Fuera del rectángulo de la capa no se toca ningún píxel (tijera), salvo en Máscara.
 *
 * Debe usarse en el hilo con el contexto OpenGL. Cualquier fallo deja la escena sin capas,
 * nunca en negro.
 */
class LayerCompositor(private val vertSrc: String) {
    private var program = 0
    private var broken = false
    private val locs = HashMap<String, Int>()
    private class Tex(val id: Int, val source: Bitmap, val w: Int, val h: Int)
    private val textures = HashMap<String, Tex>()
    private var backTex = 0
    private var backW = 0
    private var backH = 0
    private var backAlpha = false

    /** Último error (Diagnóstico). */
    var lastError: String? = null
        private set

    private fun ensureProgram(): Boolean {
        if (program != 0) return true
        if (broken) return false
        return try {
            program = FondoGl.buildProgram(vertSrc, FRAG)
            true
        } catch (t: Throwable) {
            broken = true
            lastError = t.message
            com.zeta.fondo.diag.ZetaLog.error("Capas", "Shader de capas no compiló: ${t.message}")
            false
        }
    }

    private fun loc(n: String) = locs.getOrPut(n) { GLES30.glGetUniformLocation(program, n) }

    /** Sube las imágenes nuevas y libera las que ya no están. */
    fun sync(bitmaps: Map<String, Bitmap>) {
        val gone = textures.keys.filter { it !in bitmaps }
        for (k in gone) textures.remove(k)?.let { GLES30.glDeleteTextures(1, intArrayOf(it.id), 0) }
        for ((k, bmp) in bitmaps) upload(k, bmp)
    }

    /** Sube una imagen (si cambió). Después se puede reciclar el bitmap: se compara por identidad. */
    fun upload(k: String, bmp: Bitmap) {
        run {
            val old = textures[k]
            if (old != null && old.source === bmp) return
            if (bmp.isRecycled) return
            val id = old?.id ?: IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
            GLES30.glActiveTexture(GLES30.GL_TEXTURE8)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
            // Android sube los bitmaps con alfa premultiplicado: el shader lo deshace.
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            textures[k] = Tex(id, bmp, bmp.width, bmp.height)
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    /**
     * Dibuja [stack] en el instante [t] dentro del área (x0, y0, vw, vh) del framebuffer activo.
     * [view] = encuadre de la imagen (igual que FondoGl.draw); [emit] = el destino guarda la
     * luz de los efectos en el alfa (FBO de «Calidad cine»).
     */
    fun composite(stack: LayerStack, t: Float, x0: Int, y0: Int, vw: Int, vh: Int, view: FloatArray,
                  flipY: Boolean, rotate: Boolean, emit: Boolean, vao: Int, masksTex: Int,
                  move: ((ImageLayer, LayerFrame) -> LayerFrame)? = null) {
        if (stack.isEmpty || vw <= 0 || vh <= 0) return
        if (!ensureProgram()) return
        sync(stack.bitmaps)
        // Solo el FBO de «Calidad cine» necesita el alfa del fondo (luz de los efectos). Copiar RGB
        // es válido desde CUALQUIER destino (pantalla RGB565/RGB888, FBO RGBA8, codificador).
        val alphaTarget = emit
        GLES30.glUseProgram(program)
        GLES30.glViewport(x0, y0, vw, vh)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUniform1i(loc("uLayer"), 8)
        GLES30.glUniform1i(loc("uBack"), 9)
        GLES30.glUniform1i(loc("uMasks"), 10)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE10)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, masksTex)
        GLES30.glUniform2f(loc("uViewOffset"), view[0], view[1])
        GLES30.glUniform2f(loc("uViewScale"), view[2], view[3])
        GLES30.glUniform1f(loc("uFlipY"), if (flipY) 1f else 0f)
        GLES30.glUniform1f(loc("uRotate"), if (rotate) 1f else 0f)
        GLES30.glUniform2f(loc("uBasePx"), stack.baseW.toFloat(), stack.baseH.toFloat())
        GLES30.glUniform1f(loc("uEmit"), if (emit) 1f else 0f)
        GLES30.glBindVertexArray(vao)
        try {
            for (layer in stack.layers) {
                if (layer.hidden) continue
                val tex = textures[layer.file] ?: continue
                val f0 = ImageLayers.frame(layer, t, stack.loopSec, stack.baseW, stack.baseH)
                // v44.24: con la «Profundidad 2.5D», la capa se mueve con su plano.
                val f = move?.invoke(layer, f0) ?: f0
                if (f.opacity <= .001f && layer.blend != BlendModes.MASK) continue
                drawLayer(layer, f, tex, stack, x0, y0, vw, vh, view, flipY, rotate, emit, alphaTarget)
            }
        } finally {
            GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glBindVertexArray(0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        }
    }

    private fun drawLayer(layer: ImageLayer, f: LayerFrame, tex: Tex, stack: LayerStack, x0: Int, y0: Int, vw: Int, vh: Int,
                          view: FloatArray, flipY: Boolean, rotate: Boolean, emit: Boolean, alphaTarget: Boolean) {
        val mode = layer.blend.coerceIn(0, BlendModes.COUNT - 1)
        // Rectángulo en pantalla (más 2 px por el borde suavizado).
        val box = if (BlendModes.coversWholeCanvas(mode)) intArrayOf(x0, y0, vw, vh)
            else screenBox(f, stack, x0, y0, vw, vh, view, flipY, rotate) ?: return
        val hardware = hardwarePath(mode, emit)
        GLES30.glUniform2f(loc("uCenter"), f.x, f.y)
        GLES30.glUniform2f(loc("uHalf"), f.halfW, f.halfH)
        GLES30.glUniform2f(loc("uRot"), cos(f.rot), sin(f.rot))
        GLES30.glUniform1f(loc("uFlipX"), if (f.flipX) 1f else 0f)
        GLES30.glUniform1f(loc("uOpacity"), f.opacity)
        GLES30.glUniform1i(loc("uMode"), mode)
        GLES30.glUniform1i(loc("uPath"), if (hardware) 1 else 0)
        GLES30.glUniform1f(loc("uBehind"), if (layer.behind) 1f else 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE8)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex.id)
        GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
        GLES30.glScissor(box[0], box[1], box[2], box[3])
        if (hardware) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
            when (mode) {
                BlendModes.NORMAL -> GLES30.glBlendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA,
                    GLES30.GL_ZERO, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                BlendModes.SCREEN -> GLES30.glBlendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_COLOR,
                    GLES30.GL_ZERO, GLES30.GL_ONE)
                else -> GLES30.glBlendFuncSeparate(GLES30.GL_ZERO, GLES30.GL_SRC_COLOR,
                    GLES30.GL_ZERO, GLES30.GL_SRC_ALPHA)
            }
        } else {
            if (!copyBackdrop(box, alphaTarget)) return
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glUniform4f(loc("uBackRect"), box[0].toFloat(), box[1].toFloat(), 0f, 0f)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE9)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backTex)
        }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    /** Copia el fondo del rectángulo [box] a una textura (crece si hace falta, nunca encoge). */
    private fun copyBackdrop(box: IntArray, alpha: Boolean): Boolean {
        val w = box[2]; val h = box[3]
        GLES30.glActiveTexture(GLES30.GL_TEXTURE9)
        if (backTex == 0) backTex = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backTex)
        if (w > backW || h > backH || alpha != backAlpha) {
            val nw = max(w, if (alpha == backAlpha) backW else 0)
            val nh = max(h, if (alpha == backAlpha) backH else 0)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            // Formato sin tamaño (GL_RGB / GL_RGBA): el driver elige uno compatible con el destino
            // (pantalla RGB565/RGB888, FBO RGBA8…). Así la copia siempre es válida en GLES 3.
            GLES30.glCopyTexImage2D(GLES30.GL_TEXTURE_2D, 0, if (alpha) GLES30.GL_RGBA else GLES30.GL_RGB,
                box[0], box[1], nw, nh, 0)
            if (GLES30.glGetError() != GLES30.GL_NO_ERROR) {
                lastError = "No se pudo copiar el fondo para la mezcla"
                return false
            }
            backW = nw; backH = nh; backAlpha = alpha
        }
        GLES30.glCopyTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, box[0], box[1], w, h)
        return true
    }

    private fun screenBox(f: LayerFrame, stack: LayerStack, x0: Int, y0: Int, vw: Int, vh: Int,
                          view: FloatArray, flipY: Boolean, rotate: Boolean): IntArray? =
        LayerMath.screenBox(f, stack.baseW, stack.baseH, x0, y0, vw, vh, view, flipY, rotate)

    fun release() {
        for (t in textures.values) GLES30.glDeleteTextures(1, intArrayOf(t.id), 0)
        textures.clear()
        if (backTex != 0) GLES30.glDeleteTextures(1, intArrayOf(backTex), 0)
        backTex = 0; backW = 0; backH = 0
        if (program != 0) GLES30.glDeleteProgram(program)
        program = 0
        locs.clear()
    }

    companion object {
        /**
         * ¿Este modo se puede hacer con glBlendFunc con la fórmula EXACTA? Pantalla solo cuando el
         * destino no guarda la luz en el alfa (con «Calidad cine» el alfa necesita la fórmula).
         */
        fun hardwarePath(mode: Int, emit: Boolean): Boolean = when (mode) {
            BlendModes.NORMAL, BlendModes.MULTIPLY, BlendModes.MASK, BlendModes.EXCLUDE -> true
            BlendModes.SCREEN -> !emit
            else -> false
        }

        /** Shader de capas (GLSL ES 3.00). Las fórmulas son las de [BlendModes]. */
        val FRAG = """
#version 300 es
precision highp float;
precision highp int;
in vec2 vScreen;
out vec4 fragColor;
uniform sampler2D uLayer;
uniform sampler2D uBack;
uniform highp sampler2DArray uMasks;
uniform vec4 uBackRect;
uniform vec2 uViewOffset;
uniform vec2 uViewScale;
uniform float uFlipY;
uniform float uRotate;
uniform vec2 uBasePx;
uniform vec2 uCenter;
uniform vec2 uHalf;
uniform vec2 uRot;
uniform float uFlipX;
uniform float uOpacity;
uniform int uMode;
uniform int uPath;
uniform float uBehind;
uniform float uEmit;

float lum(vec3 c) { return dot(c, vec3(0.3, 0.59, 0.11)); }
vec3 clipColor(vec3 c) {
    float l = lum(c);
    float n = min(min(c.r, c.g), c.b);
    float x = max(max(c.r, c.g), c.b);
    if (n < 0.0) c = l + (c - l) * (l / max(l - n, 1e-6));
    if (x > 1.0) c = l + (c - l) * ((1.0 - l) / max(x - l, 1e-6));
    return c;
}
vec3 setLum(vec3 c, float l) { return clipColor(c + (l - lum(c))); }
float satOf(vec3 c) { return max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b); }
vec3 setSat(vec3 c, float s) {
    float mx = max(max(c.r, c.g), c.b);
    float mn = min(min(c.r, c.g), c.b);
    return mx - mn > ${BlendModes.SAT_EPS} ? (c - mn) * s / (mx - mn) : vec3(0.0);
}
vec3 screenB(vec3 b, vec3 s) { return b + s - b * s; }
vec3 colorBurn(vec3 b, vec3 s) {
    vec3 r = 1.0 - min(vec3(1.0), (1.0 - b) / max(s, vec3(1e-6)));
    r = mix(r, vec3(0.0), step(s, vec3(0.0)));
    return mix(r, vec3(1.0), step(vec3(1.0), b));
}
vec3 colorDodge(vec3 b, vec3 s) {
    vec3 r = min(vec3(1.0), b / max(1.0 - s, vec3(1e-6)));
    r = mix(r, vec3(1.0), step(vec3(1.0), s));
    return mix(r, vec3(0.0), step(b, vec3(0.0)));
}
vec3 hardLight(vec3 b, vec3 s) {
    return mix(screenB(b, 2.0 * s - 1.0), b * 2.0 * s, step(s, vec3(0.5)));
}
vec3 softLight(vec3 b, vec3 s) {
    vec3 d = mix(sqrt(b), ((16.0 * b - 12.0) * b + 4.0) * b, step(b, vec3(0.25)));
    vec3 lo = b - (1.0 - 2.0 * s) * b * (1.0 - b);
    vec3 hi = b + (2.0 * s - 1.0) * (d - b);
    return mix(hi, lo, step(s, vec3(0.5)));
}
vec3 blendMode(int m, vec3 b, vec3 s) {
    if (m == 1) return b * s;
    if (m == 2) return min(b, s);
    if (m == 3) return lum(s) < lum(b) ? s : b;
    if (m == 4) return colorBurn(b, s);
    if (m == 5) return max(b + s - 1.0, vec3(0.0));
    if (m == 6) return screenB(b, s);
    if (m == 7) return max(b, s);
    if (m == 8) return lum(s) > lum(b) ? s : b;
    if (m == 9) return colorDodge(b, s);
    if (m == 10) return min(b + s, vec3(1.0));
    if (m == 11) return hardLight(s, b);
    if (m == 12) return softLight(b, s);
    if (m == 13) return hardLight(b, s);
    if (m == 14) return (1.0 - 2.0 * s) * b * b + 2.0 * s * b;
    if (m == 15) return mix(colorDodge(b, 2.0 * s - 1.0), colorBurn(b, 2.0 * s), step(s, vec3(0.5)));
    if (m == 16) return clamp(b + 2.0 * s - 1.0, 0.0, 1.0);
    if (m == 17) return mix(max(b, 2.0 * s - 1.0), min(b, 2.0 * s), step(s, vec3(0.5)));
    if (m == 18) return step(vec3(${BlendModes.HARD_MIX_EDGE}), b + s);
    if (m == 19) return abs(b - s);
    if (m == 20) return b + s - 2.0 * b * s;
    if (m == 21) return max(b - s, vec3(0.0));
    if (m == 22) return mix(min(vec3(1.0), b / max(s, vec3(1e-6))), step(vec3(1e-6), b), step(s, vec3(0.0)));
    if (m == 23) return setLum(setSat(s, satOf(b)), lum(b));
    if (m == 24) return setLum(setSat(b, satOf(s)), lum(b));
    if (m == 25) return setLum(s, lum(b));
    if (m == 26) return setLum(b, lum(s));
    return s;
}

void main() {
    // Mismas coordenadas que el shader principal (encuadre, giro del vídeo vertical, PNG).
    vec2 sc = vec2(vScreen.x, mix(1.0 - vScreen.y, vScreen.y, uFlipY));
    if (uRotate > 0.5) sc = vec2(1.0 - sc.y, sc.x);
    vec2 uv = uViewOffset + sc * uViewScale;
    vec2 p = (uv - uCenter) * uBasePx;
    vec2 local = vec2(uRot.x * p.x + uRot.y * p.y, -uRot.y * p.x + uRot.x * p.y);
    vec2 luv = local / max(2.0 * uHalf, vec2(1e-3)) + 0.5;
    if (uFlipX > 0.5) luv.x = 1.0 - luv.x;
    // Borde suavizado de 1 px (fwidth) y nada fuera de la capa.
    vec2 e = min(luv, 1.0 - luv) / max(fwidth(luv), vec2(1e-6));
    float edge = clamp(min(e.x, e.y) + 0.5, 0.0, 1.0);
    vec4 tc = texture(uLayer, clamp(luv, 0.0, 1.0));
    float a = tc.a * edge;
    vec3 cs = tc.a > 1e-5 ? clamp(tc.rgb / tc.a, 0.0, 1.0) : vec3(0.0);
    if (uBehind > 0.5) a *= 1.0 - texture(uMasks, vec3(clamp(uv, 0.0, 1.0), 0.0)).r;
    float o = clamp(uOpacity, 0.0, 1.0);
    float cover = a * o;
    if (uPath == 1) {
        // Mezcla por hardware (glBlendFunc) con la fórmula exacta de cada modo.
        if (uMode == 0) { fragColor = vec4(cs * cover, cover); return; }
        if (uMode == 1) { fragColor = vec4(1.0 - cover + cover * cs, 1.0 - cover * (1.0 - lum(cs))); return; }
        if (uMode == 6) { fragColor = vec4(cs * cover, 0.0); return; }
        float f = uMode == 27 ? 1.0 - o + o * a : 1.0 - o * a;
        fragColor = vec4(vec3(f), f);
        return;
    }
    vec4 back = texelFetch(uBack, ivec2(gl_FragCoord.xy - uBackRect.xy), 0);
    vec3 cb = back.rgb;
    vec3 res = cover > 0.0 ? clamp(cb + (blendMode(uMode, cb, cs) - cb) * cover, 0.0, 1.0) : cb;
    float outA = 1.0;
    if (uEmit > 0.5) {
        // Alfa = luz de los efectos (la usa el brillo de «Calidad cine»): lo que oscurece la
        // reduce y lo que aclara la aumenta.
        float lb = lum(cb), lr = lum(res);
        outA = clamp(back.a * min(1.0, lr / max(lb, 0.02)) + max(lr - lb, 0.0) * 1.5, 0.0, 1.0);
    }
    fragColor = vec4(res, outA);
}
""".trimIndent()
    }
}

/**
 * v44.23 · Cuentas de pantalla de las capas (código puro, se prueba en JVM): dónde cae una capa en
 * el área de dibujo, con las MISMAS fórmulas que el shader (encuadre, giro del vídeo, PNG).
 */
object LayerMath {
    /** Coordenada de imagen (0..1) → píxel de ventana dentro del área (x0, y0, vw, vh). */
    fun uvToWindow(u: Float, v: Float, x0: Int, y0: Int, vw: Int, vh: Int, view: FloatArray,
                   flipY: Boolean, rotate: Boolean): Pair<Float, Float> {
        var sx = (u - view[0]) / view[2].takeIf { it != 0f }!!
        var sy = (v - view[1]) / view[3].takeIf { it != 0f }!!
        if (rotate) { val ox = sy; val oy = 1f - sx; sx = ox; sy = oy }
        val vy = if (flipY) sy else 1f - sy
        return (x0 + sx * vw) to (y0 + vy * vh)
    }

    /** Rectángulo (x, y, ancho, alto) en píxeles de ventana que cubre la capa, o null si no se ve. */
    fun screenBox(f: LayerFrame, baseW: Int, baseH: Int, x0: Int, y0: Int, vw: Int, vh: Int,
                  view: FloatArray, flipY: Boolean, rotate: Boolean): IntArray? {
        if (view[2] == 0f || view[3] == 0f) return null
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for ((u, v) in ImageLayers.corners(f, baseW, baseH)) {
            val (wx, wy) = uvToWindow(u, v, x0, y0, vw, vh, view, flipY, rotate)
            minX = min(minX, wx); minY = min(minY, wy); maxX = max(maxX, wx); maxY = max(maxY, wy)
        }
        val ax = max(x0, floor(minX - 2f).toInt())
        val ay = max(y0, floor(minY - 2f).toInt())
        val bx = min(x0 + vw, ceil(maxX + 2f).toInt())
        val by = min(y0 + vh, ceil(maxY + 2f).toInt())
        if (bx <= ax || by <= ay) return null
        return intArrayOf(ax, ay, bx - ax, by - ay)
    }
}
