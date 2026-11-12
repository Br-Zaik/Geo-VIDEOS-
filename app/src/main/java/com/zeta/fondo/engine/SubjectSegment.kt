package com.zeta.fondo.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * v44.24 · Silueta del personaje SIN IA y SIN caras (p. ej. un personaje de espaldas sobre un
 * fondo con detalle). Código propio, puro Kotlin (se prueba en la JVM):
 *
 *  1. Primera idea de quién es el personaje: lo nítido (líneas de tinta) y lo que tiene un color
 *     distinto del borde de la imagen, con más peso en el centro y abajo.
 *  2. Modelo de color iterativo (como un GrabCut simplificado): histograma de colores del
 *     personaje y del fondo; cada píxel va con el que más se le parece.
 *  3. Crece hacia lo que no se parece al fondo de su propia fila (ropa oscura, piernas) y hacia
 *     lo oscuro del centro (pelo).
 *  4. Se queda con la pieza más grande, rellena huecos y suaviza el borde.
 *
 * Devuelve 0..1 (1 = personaje) del tamaño de entrada, o null si no parece haber un personaje
 * (paisajes: lo «detectado» cubre todo el ancho de abajo, demasiado o casi nada).
 */
object SubjectSegment {
    private const val WORK = 160
    private const val BINS = 8
    /** Parecido máximo de colores personaje/fondo (Bhattacharyya, 0..1) para aceptar la silueta. */
    const val MAX_OVERLAP = 0.80f
    /** Fracción máxima de filas cubiertas casi de lado a lado (más = paisaje, no personaje). */
    const val WIDE_ROWS = 0.20f
    /**
     * v44.25 · El contorno de un personaje dibujado cae sobre líneas de tinta o cambios fuertes;
     * el de una silueta falsa en un paisaje, sobre degradados (cielo, brillo del sol). Fuerza del
     * borde de la silueta / fuerza media de la imagen: personajes ≥ 3,2; paisajes ≤ 2,3.
     */
    const val MIN_EDGE_SUPPORT = 2.6f
    /** Filas (del 85 % de arriba) en que una silueta puede tocar los lados de la imagen. */
    const val SIDE_ROWS = 0.20f
    /** Apoyo en bordes de la última detección (Diagnóstico y pruebas). */
    @Volatile var lastEdgeSupport = 0f
        private set
    /** Parecido de la última detección (Diagnóstico y pruebas). */
    @Volatile var lastOverlap = 0f
        private set

    fun detect(argb: IntArray, w: Int, h: Int): FloatArray? {
        if (w < 16 || h < 16 || argb.size < w * h) return null
        val s = WORK.toFloat() / max(w, h)
        val sw = if (s < 1f) max(16, (w * s).roundToInt()) else w
        val sh = if (s < 1f) max(16, (h * s).roundToInt()) else h
        val small = downscale(argb, w, h, sw, sh)
        val mask = detectSmall(small, sw, sh) ?: return null
        val up = if (sw == w && sh == h) mask else upscale(mask, sw, sh, w, h)
        return DepthMath.boxBlur(up, w, h, max(1, max(w, h) / 96))
    }

    /** Detección al tamaño de trabajo. 1 = personaje, 0 = fondo. */
    fun detectSmall(argb: IntArray, w: Int, h: Int): FloatArray? {
        val n = w * h
        val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n); val lum = FloatArray(n)
        for (i in 0 until n) {
            val c = argb[i]
            r[i] = ((c shr 16) and 0xFF) / 255f; g[i] = ((c shr 8) and 0xFF) / 255f; b[i] = (c and 0xFF) / 255f
            lum[i] = .299f * r[i] + .587f * g[i] + .114f * b[i]
        }
        val r1 = DepthMath.boxBlur(r, w, h, 1); val g1 = DepthMath.boxBlur(g, w, h, 1); val b1 = DepthMath.boxBlur(b, w, h, 1)
        val lum1 = DepthMath.boxBlur(lum, w, h, 1)

        // 1 · nitidez local y distancia al color del borde (arriba, izquierda y derecha)
        val sharp = DepthMath.boxBlur(FloatArray(n) { abs(lum[it] - lum1[it]) }, w, h, 3)
        val border = ArrayList<Int>()
        for (x in 0 until w) border.add(x)
        for (y in 0 until h) { border.add(y * w); border.add(y * w + w - 1) }
        val step = max(1, border.size / 96)
        val ref = border.filterIndexed { k, _ -> k % step == 0 }
        val dist = DepthMath.boxBlur(FloatArray(n) { i ->
            var best = Float.MAX_VALUE
            for (j in ref) {
                val dr = r[i] - r[j]; val dg = g[i] - g[j]; val db = b[i] - b[j]
                val d = dr * dr + dg * dg + db * db
                if (d < best) best = d
            }
            sqrt(best)
        }, w, h, 2)
        val sRef = DepthMath.percentiles(sharp, .05f, .95f); val dRef = DepthMath.percentiles(dist, .05f, .95f)
        fun nrm(v: Float, ref: FloatArray) = if (ref[1] - ref[0] > 1e-5f) ((v - ref[0]) / (ref[1] - ref[0])).coerceIn(0f, 1f) else 0f
        val center = FloatArray(n); val prior = FloatArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val fx = x.toFloat() / (w - 1) - .5f; val fy = y.toFloat() / (h - 1)
            center[y * w + x] = exp(-(fx * fx) / (2f * .28f * .28f)) * (.55f + .45f * fy)
            prior[y * w + x] = .15f + .85f * exp(-(fx * fx) / (2f * .24f * .24f)) * (.75f + .25f * fy)
        }
        val score = DepthMath.boxBlur(FloatArray(n) { i ->
            (.45f * nrm(sharp[i], sRef) + .55f * nrm(dist[i], dRef)) * (.35f + .65f * center[i])
        }, w, h, 2)
        val t = otsu(score)
        var p = FloatArray(n) { if (score[it] > t) 1f else 0f }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (abs(x.toFloat() / (w - 1) - .5f) < .16f && y.toFloat() / (h - 1) > .82f && score[i] > t * .6f) p[i] = 1f
        }

        // 2 · modelo de color iterativo
        val band = max(2, (min(w, h) * .05f).toInt())
        val isBorder = BooleanArray(n) { i -> val x = i % w; val y = i / w; y < band || x < band || x >= w - band }
        val q = BINS - 1e-3f
        val idx = IntArray(n) { i ->
            ((r1[i] * q).toInt() * BINS + (g1[i] * q).toInt()) * BINS + (b1[i] * q).toInt()
        }
        val bins = BINS * BINS * BINS
        repeat(5) {
            val hf = DoubleArray(bins) { 1.0 }; val hb = DoubleArray(bins) { 1.0 }
            for (i in 0 until n) if (p[i] > .5f && !isBorder[i]) hf[idx[i]] += 1.0 else hb[idx[i]] += 1.0
            val sf = hf.sum(); val sb = hb.sum()
            val np = FloatArray(n) { i ->
                val pf = hf[idx[i]] / sf * prior[i]
                val pb = hb[idx[i]] / sb * (1f - .85f * prior[i])
                (pf / (pf + pb)).toFloat()
            }
            p = DepthMath.boxBlur(np, w, h, 2)
            for (i in 0 until n) if (isBorder[i]) p[i] = min(p[i], .3f)
        }
        val rad = max(2, max(w, h) / 40)
        var fg = close(BooleanArray(n) { p[it] > .5f }, w, h, rad)
        fg = largest(fg, w, h) ?: return null

        // 3 · crecer hacia lo distinto del fondo de su fila y lo oscuro del centro (pelo)
        val bw = max(2, (w * .08f).toInt())
        val cand = BooleanArray(n)
        for (y in 0 until h) {
            val y0 = max(0, y - 3); val y1 = min(h, y + 4)
            for (x in 0 until w) {
                val i = y * w + x
                var best = Float.MAX_VALUE
                for (yy in y0 until y1) {
                    for (xx in 0 until bw) best = min(best, d2(r1, g1, b1, i, yy * w + xx))
                    for (xx in w - bw until w) best = min(best, d2(r1, g1, b1, i, yy * w + xx))
                }
                cand[i] = (sqrt(best) > .12f && prior[i] > .35f) || (lum1[i] < .28f && prior[i] > .5f)
            }
        }
        val queue = IntArray(n); var head = 0; var tail = 0
        for (i in 0 until n) if (fg[i]) queue[tail++] = i
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            if (x > 0 && cand[i - 1] && !fg[i - 1]) { fg[i - 1] = true; queue[tail++] = i - 1 }
            if (x < w - 1 && cand[i + 1] && !fg[i + 1]) { fg[i + 1] = true; queue[tail++] = i + 1 }
            if (y > 0 && cand[i - w] && !fg[i - w]) { fg[i - w] = true; queue[tail++] = i - w }
            if (y < h - 1 && cand[i + w] && !fg[i + w]) { fg[i + w] = true; queue[tail++] = i + w }
        }
        fg = close(fg, w, h, rad)
        fg = largest(fg, w, h) ?: return null
        fg = fillHoles(fg, w, h)

        // 4 · ¿parece un personaje?
        var cover = 0
        for (v in fg) if (v) cover++
        val frac = cover.toFloat() / n
        if (frac < .06f || frac > .85f) return null
        if (wideRows(w, h) { y -> var c = 0; for (x in 0 until w) if (fg[y * w + x]) c++; c } > WIDE_ROWS * h) return null
        // Personaje y fondo deben tener colores distintos (en ruido o texturas uniformes no hay nadie).
        val hf = DoubleArray(bins); val hb = DoubleArray(bins)
        for (i in 0 until n) if (fg[i]) hf[idx[i]] += 1.0 else hb[idx[i]] += 1.0
        val sf = hf.sum(); val sb = hb.sum()
        var bc = 0.0
        if (sf > 0 && sb > 0) for (k in 0 until bins) bc += sqrt(hf[k] / sf * hb[k] / sb)
        lastOverlap = bc.toFloat()
        if (bc > MAX_OVERLAP) return null
        val support = edgeSupport(lum, fg, w, h)
        lastEdgeSupport = support
        if (support < MIN_EDGE_SUPPORT) return null
        return FloatArray(n) { if (fg[it]) 1f else 0f }
    }

    /**
     * ¿Es creíble una silueta (0..1) hecha por otro método? Mismas reglas que [detect]: tamaño
     * razonable, no ocupa todo el ancho de abajo (suelo, mar) y colores distintos del fondo.
     */
    fun plausible(m: FloatArray, argb: IntArray, w: Int, h: Int): Boolean {
        val n = w * h
        if (m.size < n || argb.size < n || w < 4 || h < 4) return false
        var cover = 0
        for (i in 0 until n) if (m[i] > .5f) cover++
        val frac = cover.toFloat() / n
        if (frac < .04f || frac > .75f) return false
        if (wideRows(w, h) { y -> var c = 0; for (x in 0 until w) if (m[y * w + x] > .5f) c++; c } > WIDE_ROWS * h) return false
        // v44.25: un personaje toca los lados como mucho abajo (hombros cortados); una silueta que
        // toca el borde izquierdo o derecho en muchas filas de arriba es suelo, árboles o cielo.
        val band = max(1, w / 50)
        val upto = max(1, (h * .85f).toInt())
        var side = 0
        for (y in 0 until upto) {
            var touch = false
            for (x in 0 until band) if (m[y * w + x] > .5f || m[y * w + w - 1 - x] > .5f) { touch = true; break }
            if (touch) side++
        }
        if (side > SIDE_ROWS * upto) return false
        val bins = BINS * BINS * BINS
        val hf = DoubleArray(bins); val hb = DoubleArray(bins)
        val q = BINS - 1e-3f
        for (i in 0 until n) {
            val c = argb[i]
            val k = ((((c shr 16) and 0xFF) / 255f * q).toInt() * BINS + (((c shr 8) and 0xFF) / 255f * q).toInt()) * BINS +
                ((c and 0xFF) / 255f * q).toInt()
            if (m[i] > .5f) hf[k] += 1.0 else hb[k] += 1.0
        }
        val sf = hf.sum(); val sb = hb.sum()
        var bc = 0.0
        for (k in 0 until bins) bc += sqrt(hf[k] / sf * hb[k] / sb)
        if (bc > MAX_OVERLAP) return false
        // v44.25: el contorno debe seguir bordes reales (al tamaño de trabajo, como detect).
        val s = WORK.toFloat() / max(w, h)
        val sw = if (s < 1f) max(16, (w * s).roundToInt()) else w
        val sh = if (s < 1f) max(16, (h * s).roundToInt()) else h
        val small = if (sw == w && sh == h) argb else downscale(argb, w, h, sw, sh)
        val lum = FloatArray(sw * sh) { i ->
            val c = small[i]
            (.299f * ((c shr 16) and 0xFF) + .587f * ((c shr 8) and 0xFF) + .114f * (c and 0xFF)) / 255f
        }
        val fg = BooleanArray(sw * sh) { i ->
            val x = i % sw; val y = i / sw
            m[min(h - 1, y * h / sh) * w + min(w - 1, x * w / sw)] > .5f
        }
        return edgeSupport(lum, fg, sw, sh) >= MIN_EDGE_SUPPORT
    }

    /**
     * Fuerza media del borde de la silueta (máximo del gradiente a ≤ 2 px) dividida por la
     * fuerza media del gradiente en toda la imagen. Alto = la silueta sigue contornos reales.
     */
    fun edgeSupport(lum: FloatArray, fg: BooleanArray, w: Int, h: Int): Float {
        val n = w * h
        val g = FloatArray(n)
        var total = 0.0
        for (y in 0 until h) for (x in 0 until w) {
            val gx = if (x in 1 until w - 1) lum[y * w + x + 1] - lum[y * w + x - 1] else 0f
            val gy = if (y in 1 until h - 1) lum[(y + 1) * w + x] - lum[(y - 1) * w + x] else 0f
            val v = sqrt(gx * gx + gy * gy)
            g[y * w + x] = v; total += v
        }
        val mean = total / n
        if (mean < 1e-6) return 0f
        var sum = 0.0; var count = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (!fg[i] || (fg[i - 1] && fg[i + 1] && fg[i - w] && fg[i + w])) continue
            var best = 0f
            for (yy in max(0, y - 2)..min(h - 1, y + 2)) for (xx in max(0, x - 2)..min(w - 1, x + 2)) {
                val v = g[yy * w + xx]; if (v > best) best = v
            }
            sum += best; count++
        }
        if (count == 0) return 0f
        return (sum / count / mean).toFloat()
    }

    /**
     * Filas que la silueta cubre casi de lado a lado. En un paisaje el mar o el suelo lo hacen en
     * muchas filas; un personaje, como mucho en las últimas (hombros cortados por el borde).
     */
    private fun wideRows(w: Int, h: Int, countRow: (Int) -> Int): Int {
        var n = 0
        for (y in 0 until h) if (countRow(y) > .85f * w) n++
        return n
    }

    private fun d2(r: FloatArray, g: FloatArray, b: FloatArray, i: Int, j: Int): Float {
        val dr = r[i] - r[j]; val dg = g[i] - g[j]; val db = b[i] - b[j]
        return dr * dr + dg * dg + db * db
    }

    /** Umbral de Otsu (64 niveles) de [v] (valores ≥ 0). */
    fun otsu(v: FloatArray): Float {
        var mx = 0f
        for (x in v) if (x > mx) mx = x
        if (mx <= 0f) return 0f
        val hist = DoubleArray(64)
        for (x in v) hist[((x / mx) * 63.999f).toInt().coerceIn(0, 63)] += 1.0
        val total = v.size.toDouble()
        var sumAll = 0.0
        for (k in 0 until 64) sumAll += k * hist[k]
        var wB = 0.0; var sumB = 0.0; var best = -1.0; var at = 0
        for (k in 0 until 64) {
            wB += hist[k]; if (wB == 0.0) continue
            val wF = total - wB; if (wF == 0.0) break
            sumB += k * hist[k]
            val mB = sumB / wB; val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; at = k }
        }
        return (at + 1) / 64f * mx
    }

    /** Cierre morfológico con cajas: dilatar y luego erosionar. */
    private fun close(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val dil = DepthMath.boxBlur(FloatArray(m.size) { if (m[it]) 1f else 0f }, w, h, r)
        val d = FloatArray(m.size) { if (dil[it] > .3f) 1f else 0f }
        val ero = DepthMath.boxBlur(d, w, h, r)
        return BooleanArray(m.size) { ero[it] > .7f }
    }

    /** La pieza conectada más grande (null si no hay ninguna). */
    private fun largest(m: BooleanArray, w: Int, h: Int): BooleanArray? {
        val n = w * h
        val label = IntArray(n)
        val queue = IntArray(n)
        var bestLabel = 0; var bestSize = 0; var next = 0
        for (s in 0 until n) {
            if (!m[s] || label[s] != 0) continue
            next++
            var head = 0; var tail = 0
            queue[tail++] = s; label[s] = next
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                fun push(j: Int) { if (m[j] && label[j] == 0) { label[j] = next; queue[tail++] = j } }
                if (x > 0) push(i - 1); if (x < w - 1) push(i + 1)
                if (y > 0) push(i - w); if (y < h - 1) push(i + w)
            }
            if (tail > bestSize) { bestSize = tail; bestLabel = next }
        }
        if (bestLabel == 0) return null
        return BooleanArray(n) { label[it] == bestLabel }
    }

    /** Rellena los huecos: es fondo solo lo que se alcanza desde el borde sin cruzar al personaje. */
    private fun fillHoles(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val n = w * h
        val bg = BooleanArray(n)
        val queue = IntArray(n); var head = 0; var tail = 0
        fun seed(i: Int) { if (!m[i] && !bg[i]) { bg[i] = true; queue[tail++] = i } }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]; val x = i % w; val y = i / w
            if (x > 0) seed(i - 1); if (x < w - 1) seed(i + 1)
            if (y > 0) seed(i - w); if (y < h - 1) seed(i + w)
        }
        return BooleanArray(n) { !bg[it] }
    }

    /** Reducción por promedio de áreas (sin Android). */
    fun downscale(argb: IntArray, w: Int, h: Int, dw: Int, dh: Int): IntArray {
        val out = IntArray(dw * dh)
        for (y in 0 until dh) {
            val sy0 = y * h / dh; val sy1 = max(sy0 + 1, (y + 1) * h / dh)
            for (x in 0 until dw) {
                val sx0 = x * w / dw; val sx1 = max(sx0 + 1, (x + 1) * w / dw)
                var rr = 0; var gg = 0; var bb = 0; var c = 0
                for (yy in sy0 until sy1) for (xx in sx0 until sx1) {
                    val p = argb[yy * w + xx]
                    rr += (p shr 16) and 0xFF; gg += (p shr 8) and 0xFF; bb += p and 0xFF; c++
                }
                out[y * dw + x] = (0xFF shl 24) or ((rr / c) shl 16) or ((gg / c) shl 8) or (bb / c)
            }
        }
        return out
    }

    /** Ampliación bilineal de una máscara 0..1. */
    fun upscale(m: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        val out = FloatArray(dw * dh)
        for (y in 0 until dh) {
            val fy = ((y + .5f) * sh / dh - .5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(sh - 1, y0 + 1); val ty = fy - y0
            for (x in 0 until dw) {
                val fx = ((x + .5f) * sw / dw - .5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(sw - 1, x0 + 1); val tx = fx - x0
                val a = m[y0 * sw + x0] * (1 - tx) + m[y0 * sw + x1] * tx
                val b = m[y1 * sw + x0] * (1 - tx) + m[y1 * sw + x1] * tx
                out[y * dw + x] = a * (1 - ty) + b * ty
            }
        }
        return out
    }
}
