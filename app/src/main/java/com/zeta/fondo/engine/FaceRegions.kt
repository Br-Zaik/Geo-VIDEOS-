package com.zeta.fondo.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v47 · Regiones propuestas a partir de una cara detectada (sin Android).
 * - Ojos: dentro de la banda media de la cara, la ventana con más contraste y más oscura
 *   de cada mitad (en anime, iris y pestañas son lo más contrastado de la cara).
 * - Respiración: el pecho, bajo la cara.
 * Son PROPUESTAS: la app las pinta como zona del efecto y el usuario las corrige con el pincel.
 */
object FaceRegions {

    fun gray(argb: IntArray): ByteArray = ByteArray(argb.size) { i ->
        val c = argb[i]
        ((((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8).toByte()
    }

    /** Dos elipses [cx, cy, rx, ry] normalizadas (0..1 de la imagen): ojo izquierdo y derecho en pantalla. */
    fun eyes(gray: ByteArray, w: Int, h: Int, f: AnimeFaceDetector.Face): List<FloatArray> {
        val ww = max(3, (f.w * .26f).toInt()); val wh = max(3, (f.h * .17f).toInt())
        val y0 = f.y + (f.h * .22f).toInt(); val y1 = f.y + (f.h * .74f).toInt() - wh
        val out = ArrayList<FloatArray>()
        for (half in 0..1) {
            val x0 = f.x + (f.w * (if (half == 0) .04f else .50f)).toInt()
            val x1 = f.x + (f.w * (if (half == 0) .50f else .96f)).toInt() - ww
            var best = -1f; var bx = (x0 + x1) / 2; var by = (y0 + y1) / 2
            val step = max(1, ww / 8)
            var y = y0
            while (y <= max(y0, y1)) {
                var x = x0
                while (x <= max(x0, x1)) {
                    var s = 0.0; var s2 = 0.0; var n = 0
                    for (yy in max(0, y) until min(h, y + wh) step 2) for (xx in max(0, x) until min(w, x + ww) step 2) {
                        val v = (gray[yy * w + xx].toInt() and 0xFF).toDouble(); s += v; s2 += v * v; n++
                    }
                    if (n > 0) {
                        val m = s / n
                        val sd = sqrt(max(0.0, s2 / n - m * m))
                        val score = (sd + (255 - m) * .6).toFloat()
                        if (score > best) { best = score; bx = x; by = y }
                    }
                    x += step
                }
                y += step
            }
            out.add(floatArrayOf((bx + ww / 2f) / w, (by + wh / 2f) / h, ww * .6f / w, wh * .75f / h))
        }
        return out
    }

    /** Rectángulo [x0, y0, x1, y1] normalizado del pecho para la respiración. */
    fun breathing(f: AnimeFaceDetector.Face, w: Int, h: Int): FloatArray {
        val x0 = (f.x - f.w * .35f).coerceIn(0f, w.toFloat()); val x1 = (f.x + f.w * 1.35f).coerceIn(0f, w.toFloat())
        val y0 = (f.y + f.h * 1.15f).coerceIn(0f, h.toFloat()); val y1 = (f.y + f.h * 2.5f).coerceIn(0f, h.toFloat())
        return floatArrayOf(x0 / w, y0 / h, x1 / w, y1 / h)
    }

    /** Máscara (0..255) con elipses suaves, del tamaño de la capa (dw x dh). */
    fun ellipseMask(ellipses: List<FloatArray>, dw: Int, dh: Int, feather: Float = .35f): ByteArray {
        val out = ByteArray(dw * dh)
        for (e in ellipses) {
            val cx = e[0] * dw; val cy = e[1] * dh; val rx = max(1f, e[2] * dw); val ry = max(1f, e[3] * dh)
            for (y in max(0, (cy - ry).toInt()) until min(dh, (cy + ry).toInt() + 1))
                for (x in max(0, (cx - rx).toInt()) until min(dw, (cx + rx).toInt() + 1)) {
                    val d = sqrt(((x + .5f - cx) / rx).let { it * it } + ((y + .5f - cy) / ry).let { it * it })
                    val a = ((1f - d) / feather).coerceIn(0f, 1f)
                    val v = (a * 255f + .5f).toInt()
                    if (v > (out[y * dw + x].toInt() and 0xFF)) out[y * dw + x] = v.toByte()
                }
        }
        return out
    }

    /** Rectángulo suave (0..255) [x0,y0,x1,y1] normalizado, del tamaño de la capa. */
    fun rectMask(r: FloatArray, dw: Int, dh: Int): ByteArray =
        ellipseMask(listOf(floatArrayOf((r[0] + r[2]) / 2, (r[1] + r[3]) / 2, (r[2] - r[0]) / 2, (r[3] - r[1]) / 2)), dw, dh, .5f)
}
