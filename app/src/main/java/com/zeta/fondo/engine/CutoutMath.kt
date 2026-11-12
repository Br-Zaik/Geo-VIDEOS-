package com.zeta.fondo.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v46 · Recorte de personaje y relleno del fondo, sin Android (se prueba en la JVM).
 *
 * - [letterbox]/[toChw]/[cropMask]: el mismo preprocesado que inference.py de SkyTNT
 *   (isnetis): imagen /255 encajada en un cuadrado de 1024 con relleno negro centrado,
 *   sin media ni desviación; la salida (0..1, ya con sigmoide) se recorta y se escala.
 * - [fill]: relleno «pull-push» (pirámide de medias ponderadas) del hueco que deja el
 *   personaje. Es suave, no inventa detalle; basta para la franja que destapa el 2.5D.
 *   Si se importa MI-GAN, la app usa ese modelo en su lugar.
 */
object CutoutMath {

    const val SEG_SIZE = 1024

    /** [anchoEscalado, altoEscalado, x0, y0] dentro del cuadrado [s]x[s]. */
    fun letterbox(w: Int, h: Int, s: Int = SEG_SIZE): IntArray {
        require(w > 0 && h > 0)
        val nw: Int; val nh: Int
        if (h > w) { nh = s; nw = max(1, (s.toLong() * w / h).toInt()) } else { nw = s; nh = max(1, (s.toLong() * h / w).toInt()) }
        return intArrayOf(nw, nh, (s - nw) / 2, (s - nh) / 2)
    }

    /** Píxeles ARGB ya escalados a (nw, nh) → tensor CHW 1x3xSxS (0..1, fuera de la imagen = 0). */
    fun toChw(argb: IntArray, box: IntArray, s: Int = SEG_SIZE): FloatArray {
        val (nw, nh, x0, y0) = box.toList()
        val n = s * s
        val out = FloatArray(3 * n)
        for (y in 0 until nh) for (x in 0 until nw) {
            val c = argb[y * nw + x]
            val i = (y + y0) * s + (x + x0)
            out[i] = ((c shr 16) and 0xFF) / 255f
            out[n + i] = ((c shr 8) and 0xFF) / 255f
            out[2 * n + i] = (c and 0xFF) / 255f
        }
        return out
    }

    /**
     * Salida del modelo (SxS, 0..1) → máscara del tamaño de la capa (dw x dh, 0..255).
     * [lo]/[hi]: transición suave del borde (smoothstep); 0/1 = la probabilidad tal cual.
     */
    fun cropMask(prob: FloatArray, box: IntArray, dw: Int, dh: Int, lo: Float = .3f, hi: Float = .7f, s: Int = SEG_SIZE): ByteArray {
        val (nw, nh, x0, y0) = box.toList()
        val out = ByteArray(dw * dh)
        for (y in 0 until dh) {
            val fy = ((y + .5f) * nh / dh - .5f).coerceIn(0f, (nh - 1).toFloat())
            val ya = fy.toInt(); val yb = min(ya + 1, nh - 1); val ty = fy - ya
            for (x in 0 until dw) {
                val fx = ((x + .5f) * nw / dw - .5f).coerceIn(0f, (nw - 1).toFloat())
                val xa = fx.toInt(); val xb = min(xa + 1, nw - 1); val tx = fx - xa
                fun p(xx: Int, yy: Int) = prob[(yy + y0) * s + xx + x0]
                val top = p(xa, ya) + (p(xb, ya) - p(xa, ya)) * tx
                val bot = p(xa, yb) + (p(xb, yb) - p(xa, yb)) * tx
                var v = (top + (bot - top) * ty).coerceIn(0f, 1f)
                if (hi > lo) { val t = ((v - lo) / (hi - lo)).coerceIn(0f, 1f); v = t * t * (3f - 2f * t) }
                out[y * dw + x] = (v * 255f + .5f).toInt().toByte()
            }
        }
        return out
    }

    /** Fracción de la imagen que ocupa el personaje (valor ≥ 128). */
    fun coverage(mask: ByteArray): Float = if (mask.isEmpty()) 0f else mask.count { (it.toInt() and 0xFF) >= 128 }.toFloat() / mask.size

    /**
     * Fracción de píxeles «dudosos» (ni fondo ni personaje claro, 40..215). Alta en manga
     * B/N con tramas: entonces se avisa de que conviene repasar Proteger con el pincel.
     */
    fun uncertainty(mask: ByteArray): Float = if (mask.isEmpty()) 0f else mask.count { (it.toInt() and 0xFF) in 40..215 }.toFloat() / mask.size

    /** Dilata un hueco (>=128) [r] píxeles: así el relleno no arrastra el contorno del personaje. */
    fun dilate(mask: ByteArray, w: Int, h: Int, r: Int): ByteArray {
        if (r <= 0) return mask.copyOf()
        val tmp = ByteArray(w * h)
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            var run = -1
            // distancia al último píxel de hueco por la izquierda y por la derecha
            val row = y * w
            val left = IntArray(w); val right = IntArray(w)
            for (x in 0 until w) { if ((mask[row + x].toInt() and 0xFF) >= 128) run = x; left[x] = if (run < 0) Int.MAX_VALUE else x - run }
            run = -1
            for (x in w - 1 downTo 0) { if ((mask[row + x].toInt() and 0xFF) >= 128) run = x; right[x] = if (run < 0) Int.MAX_VALUE else run - x }
            for (x in 0 until w) if (min(left[x], right[x]) <= r) tmp[row + x] = 255.toByte()
        }
        for (x in 0 until w) {
            var run = -1
            val up = IntArray(h); val down = IntArray(h)
            for (y in 0 until h) { if (tmp[y * w + x].toInt() != 0) run = y; up[y] = if (run < 0) Int.MAX_VALUE else y - run }
            run = -1
            for (y in h - 1 downTo 0) { if (tmp[y * w + x].toInt() != 0) run = y; down[y] = if (run < 0) Int.MAX_VALUE else run - y }
            for (y in 0 until h) if (min(up[y], down[y]) <= r) out[y * w + x] = 255.toByte()
        }
        return out
    }

    /**
     * Rellena el hueco (máscara >=128) de una imagen ARGB con la media ponderada de su
     * entorno a varias escalas (pull-push). Fuera del hueco devuelve la imagen tal cual.
     */
    fun fill(argb: IntArray, w: Int, h: Int, hole: ByteArray): IntArray {
        require(argb.size >= w * h && hole.size >= w * h)
        val levels = ArrayList<FloatArray>() // r,g,b (normalizados) y peso, intercalados
        val dims = ArrayList<IntArray>()
        val l0 = FloatArray(w * h * 4)
        for (i in 0 until w * h) {
            val known = if ((hole[i].toInt() and 0xFF) >= 128) 0f else 1f
            val c = argb[i]
            l0[i * 4] = ((c shr 16) and 0xFF).toFloat(); l0[i * 4 + 1] = ((c shr 8) and 0xFF).toFloat()
            l0[i * 4 + 2] = (c and 0xFF).toFloat(); l0[i * 4 + 3] = known
        }
        levels.add(l0); dims.add(intArrayOf(w, h))
        // Pull: reducir a la mitad promediando solo lo conocido.
        while (dims.last()[0] > 1 || dims.last()[1] > 1) {
            val (pw, ph) = dims.last().let { it[0] to it[1] }
            val src = levels.last()
            val cw = (pw + 1) / 2; val ch = (ph + 1) / 2
            val dst = FloatArray(cw * ch * 4)
            for (y in 0 until ch) for (x in 0 until cw) {
                var r = 0f; var g = 0f; var b = 0f; var wt = 0f
                for (dy in 0..1) for (dx in 0..1) {
                    val sx = min(pw - 1, x * 2 + dx); val sy = min(ph - 1, y * 2 + dy)
                    val k = (sy * pw + sx) * 4
                    val ww = src[k + 3]
                    r += src[k] * ww; g += src[k + 1] * ww; b += src[k + 2] * ww; wt += ww
                }
                val o = (y * cw + x) * 4
                if (wt > 0f) { dst[o] = r / wt; dst[o + 1] = g / wt; dst[o + 2] = b / wt }
                dst[o + 3] = min(1f, wt)
            }
            levels.add(dst); dims.add(intArrayOf(cw, ch))
        }
        // Push: completar cada nivel con el más grueso (bilineal), según lo que le falte.
        for (li in levels.size - 2 downTo 0) {
            val (fw, fh) = dims[li].let { it[0] to it[1] }
            val (cw, ch) = dims[li + 1].let { it[0] to it[1] }
            val fine = levels[li]; val coarse = levels[li + 1]
            for (y in 0 until fh) {
                val cy = ((y + .5f) / 2f - .5f).coerceIn(0f, (ch - 1).toFloat())
                val y0 = cy.toInt(); val y1 = min(y0 + 1, ch - 1); val ty = cy - y0
                for (x in 0 until fw) {
                    val o = (y * fw + x) * 4
                    val wf = fine[o + 3]
                    if (wf >= 1f) continue
                    val cx = ((x + .5f) / 2f - .5f).coerceIn(0f, (cw - 1).toFloat())
                    val x0 = cx.toInt(); val x1 = min(x0 + 1, cw - 1); val tx = cx - x0
                    for (c in 0..2) {
                        fun q(xx: Int, yy: Int) = coarse[(yy * cw + xx) * 4 + c]
                        val top = q(x0, y0) + (q(x1, y0) - q(x0, y0)) * tx
                        val bot = q(x0, y1) + (q(x1, y1) - q(x0, y1)) * tx
                        val cc = top + (bot - top) * ty
                        fine[o + c] = fine[o + c] * wf + cc * (1f - wf)
                    }
                    fine[o + 3] = 1f
                }
            }
        }
        val out = IntArray(w * h)
        for (i in 0 until w * h) {
            out[i] = if ((hole[i].toInt() and 0xFF) >= 128) {
                val r = l0[i * 4].roundToInt().coerceIn(0, 255); val g = l0[i * 4 + 1].roundToInt().coerceIn(0, 255)
                val b = l0[i * 4 + 2].roundToInt().coerceIn(0, 255)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            } else argb[i] or (0xFF shl 24)
        }
        return out
    }
}
