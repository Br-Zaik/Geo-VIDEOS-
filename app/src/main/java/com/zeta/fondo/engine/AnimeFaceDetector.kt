package com.zeta.fondo.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v47 · Detector de caras anime/manga sin dependencias (ni OpenCV ni modelos pesados).
 *
 * Evalúa la cascada LBP «lbpcascade_animeface» (nagadomi, licencia MIT; assets/detect/)
 * con el mismo algoritmo que cv2.CascadeClassifier.detectMultiScale:
 * - histograma ecualizado;
 * - pirámide de escalas ×1.1;
 * - ventana de 24×24 con paso 2;
 * - características LBP de 3×3 bloques sobre la imagen integral;
 * - nodos categóricos de 256 valores;
 * - agrupación de rectángulos (eps = 0.2, mínimo de vecinos).
 *
 * La IA solo PROPONE regiones: la app las pinta como zona editable, nunca deforma la cara.
 * Sin Android: se prueba en la JVM.
 */
class AnimeFaceDetector private constructor(
    val winW: Int, val winH: Int,
    private val stages: List<Stage>,
    private val rects: List<IntArray>
) {
    private class Weak(val feature: Int, val subset: IntArray, val left: Float, val right: Float)
    private class Stage(val threshold: Float, val weak: List<Weak>)

    /** Rectángulo en píxeles de la imagen analizada y número de detecciones que lo apoyan. */
    data class Face(val x: Int, val y: Int, val w: Int, val h: Int, val votes: Int)

    val stageCount: Int get() = stages.size
    val featureCount: Int get() = rects.size

    /**
     * Caras en una imagen en grises (fila 0 = arriba). [minSize] en píxeles; [minNeighbors]
     * como OpenCV (el README de la cascada usa 1.1 / 5 / 24).
     */
    fun detect(gray: ByteArray, w: Int, h: Int, scaleFactor: Float = 1.1f, minNeighbors: Int = 5, minSize: Int = 24): List<Face> {
        val eq = equalizeHist(gray)
        val found = ArrayList<IntArray>()
        var factor = 1.0
        while (true) {
            val ww = (winW * factor).roundToInt(); val wh = (winH * factor).roundToInt()
            val sw = (w / factor).roundToInt(); val sh = (h / factor).roundToInt()
            if (sw < winW || sh < winH || ww > w || wh > h) break
            if (ww >= minSize && wh >= minSize) {
                val scaled = if (factor == 1.0) eq else resize(eq, w, h, sw, sh)
                val ii = integral(scaled, sw, sh)
                val step = if (factor > 2.0) 1 else 2
                var y = 0
                while (y + winH <= sh) {
                    var x = 0
                    while (x + winW <= sw) {
                        if (passes(ii, sw + 1, x, y)) found.add(intArrayOf((x * factor).roundToInt(), (y * factor).roundToInt(), ww, wh))
                        x += step
                    }
                    y += step
                }
            }
            factor *= scaleFactor
        }
        return group(found, minNeighbors, 0.2f)
    }

    private fun passes(ii: IntArray, stride: Int, x0: Int, y0: Int): Boolean {
        for (st in stages) {
            var sum = 0f
            for (wk in st.weak) {
                val c = lbp(ii, stride, x0, y0, rects[wk.feature])
                sum += if (wk.subset[c shr 5] and (1 shl (c and 31)) != 0) wk.left else wk.right
            }
            if (sum < st.threshold) return false
        }
        return true
    }

    /** Código LBP de 8 bits con el mismo orden de bits que OpenCV (LBPEvaluator::Feature::calc). */
    private fun lbp(ii: IntArray, stride: Int, x0: Int, y0: Int, r: IntArray): Int {
        val bx = x0 + r[0]; val by = y0 + r[1]; val bw = r[2]; val bh = r[3]
        fun s(i: Int, j: Int): Int { // bloque (columna i, fila j) de la rejilla 3×3
            val xa = bx + i * bw; val ya = by + j * bh
            return ii[(ya + bh) * stride + xa + bw] - ii[ya * stride + xa + bw] - ii[(ya + bh) * stride + xa] + ii[ya * stride + xa]
        }
        val c = s(1, 1)
        return (if (s(0, 0) >= c) 128 else 0) or (if (s(1, 0) >= c) 64 else 0) or (if (s(2, 0) >= c) 32 else 0) or
            (if (s(2, 1) >= c) 16 else 0) or (if (s(2, 2) >= c) 8 else 0) or (if (s(1, 2) >= c) 4 else 0) or
            (if (s(0, 2) >= c) 2 else 0) or (if (s(0, 1) >= c) 1 else 0)
    }

    companion object {
        /** Lee la cascada en formato XML de OpenCV (nuevo formato, LBP, árboles de profundidad 1). */
        fun parse(xml: String): AnimeFaceDetector {
            require(xml.contains("<featureType>LBP</featureType>")) { "La cascada no es LBP" }
            fun tag(t: String) = Regex("<$t>\\s*([^<]+?)\\s*</$t>").find(xml)?.groupValues?.get(1)?.trim()
                ?: throw IllegalArgumentException("Falta <$t>")
            val width = tag("width").toInt(); val height = tag("height").toInt()
            val stagesPart = xml.substringAfter("<stages>").substringBefore("</stages>")
            val headers = Regex("<maxWeakCount>\\s*(\\d+)\\s*</maxWeakCount>\\s*<stageThreshold>\\s*([^<]+?)\\s*</stageThreshold>")
                .findAll(stagesPart).map { it.groupValues[1].toInt() to it.groupValues[2].toFloat() }.toList()
            val nodes = Regex("<internalNodes>\\s*([^<]+?)\\s*</internalNodes>").findAll(stagesPart)
                .map { it.groupValues[1].trim().split(Regex("\\s+")).map { v -> v.toLong().toInt() } }.toList()
            val leaves = Regex("<leafValues>\\s*([^<]+?)\\s*</leafValues>").findAll(stagesPart)
                .map { it.groupValues[1].trim().split(Regex("\\s+")).map { v -> v.toFloat() } }.toList()
            require(nodes.size == leaves.size && nodes.size == headers.sumOf { it.first }) { "Cascada incompleta" }
            var k = 0
            val stages = headers.map { (n, thr) ->
                Stage(thr - 1e-5f, List(n) {
                    val nd = nodes[k]; val lv = leaves[k]; k++
                    require(nd.size == 11) { "Nodo LBP inesperado" }
                    Weak(nd[2], nd.subList(3, 11).toIntArray(), lv[0], lv[1])
                })
            }
            val featPart = xml.substringAfter("<features>").substringBefore("</features>")
            val rects = Regex("<rect>\\s*([^<]+?)\\s*</rect>").findAll(featPart)
                .map { it.groupValues[1].trim().split(Regex("\\s+")).map { v -> v.toInt() }.toIntArray() }.toList()
            return AnimeFaceDetector(width, height, stages, rects)
        }

        fun equalizeHist(g: ByteArray): ByteArray {
            val hist = IntArray(256)
            for (b in g) hist[b.toInt() and 0xFF]++
            // Igual que cv::equalizeHist: lut = round((cdf - cdf_min) * 255 / (total - cdf_min))
            var i = 0
            while (i < 256 && hist[i] == 0) i++
            val total = g.size
            if (i == 256 || hist[i] == total) return g.copyOf()
            val scale = 255f / (total - hist[i])
            val lut = IntArray(256)
            var sum = 0
            lut[i] = 0
            for (j in i + 1 until 256) { sum += hist[j]; lut[j] = (sum * scale).roundToInt().coerceIn(0, 255) }
            return ByteArray(g.size) { lut[g[it].toInt() and 0xFF].toByte() }
        }

        fun integral(g: ByteArray, w: Int, h: Int): IntArray {
            val s = w + 1
            val ii = IntArray(s * (h + 1))
            for (y in 0 until h) {
                var row = 0
                for (x in 0 until w) {
                    row += g[y * w + x].toInt() and 0xFF
                    ii[(y + 1) * s + x + 1] = ii[y * s + x + 1] + row
                }
            }
            return ii
        }

        /** Reducción bilineal (como cv::resize INTER_LINEAR). */
        fun resize(src: ByteArray, sw: Int, sh: Int, dw: Int, dh: Int): ByteArray {
            val out = ByteArray(dw * dh)
            val fx = sw.toFloat() / dw; val fy = sh.toFloat() / dh
            for (y in 0 until dh) {
                val syf = ((y + .5f) * fy - .5f).coerceIn(0f, (sh - 1).toFloat())
                val y0 = syf.toInt(); val y1 = min(y0 + 1, sh - 1); val ty = syf - y0
                for (x in 0 until dw) {
                    val sxf = ((x + .5f) * fx - .5f).coerceIn(0f, (sw - 1).toFloat())
                    val x0 = sxf.toInt(); val x1 = min(x0 + 1, sw - 1); val tx = sxf - x0
                    fun p(xx: Int, yy: Int) = (src[yy * sw + xx].toInt() and 0xFF).toFloat()
                    val a = p(x0, y0) + (p(x1, y0) - p(x0, y0)) * tx
                    val b = p(x0, y1) + (p(x1, y1) - p(x0, y1)) * tx
                    out[y * dw + x] = (a + (b - a) * ty + .5f).toInt().toByte()
                }
            }
            return out
        }

        /** cv::groupRectangles: agrupa los rectángulos parecidos y quita los incluidos en otros mayores. */
        fun group(rs: List<IntArray>, minNeighbors: Int, eps: Float): List<Face> {
            if (rs.isEmpty()) return emptyList()
            val n = rs.size
            val parent = IntArray(n) { it }
            fun find(a: Int): Int { var x = a; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
            fun similar(a: IntArray, b: IntArray): Boolean {
                val delta = eps * (min(a[2], b[2]) + min(a[3], b[3])) * 0.5f
                return abs(a[0] - b[0]) <= delta && abs(a[1] - b[1]) <= delta &&
                    abs(a[0] + a[2] - b[0] - b[2]) <= delta && abs(a[1] + a[3] - b[1] - b[3]) <= delta
            }
            for (i in 0 until n) for (j in i + 1 until n) if (similar(rs[i], rs[j])) {
                val a = find(i); val b = find(j); if (a != b) parent[a] = b
            }
            val sums = HashMap<Int, LongArray>()
            for (i in 0 until n) {
                val s = sums.getOrPut(find(i)) { LongArray(5) }
                for (k in 0..3) s[k] += rs[i][k].toLong(); s[4]++
            }
            val cand = sums.values.filter { it[4] > minNeighbors }.map { s ->
                val c = s[4].toFloat()
                Face((s[0] / c).roundToInt(), (s[1] / c).roundToInt(), (s[2] / c).roundToInt(), (s[3] / c).roundToInt(), s[4].toInt())
            }
            return cand.filter { r1 ->
                cand.none { r2 ->
                    r2 !== r1 && run {
                        val dx = (r2.w * eps).roundToInt(); val dy = (r2.h * eps).roundToInt()
                        r1.x >= r2.x - dx && r1.y >= r2.y - dy && r1.x + r1.w <= r2.x + r2.w + dx && r1.y + r1.h <= r2.y + r2.h + dy &&
                            (r2.votes > max(3, r1.votes) || r1.votes < 3)
                    }
                }
            }.sortedByDescending { it.w * it.h }
        }
    }
}
