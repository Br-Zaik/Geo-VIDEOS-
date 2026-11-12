package com.zeta.fondo.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v47 · Selección de regiones sin modelos pesados (sin Android: se prueba en la JVM).
 *
 * - [grow]: «tocar para seleccionar». Crece desde el punto tocado mientras el color cambie
 *   poco entre vecinos y no se aleje del color del punto; si hay mapa de profundidad, tampoco
 *   salta a otra profundidad. Nunca entra en Proteger (el personaje).
 * - [autoSky] / [autoWater]: cielo pegado al borde superior (lejano si hay profundidad) y agua
 *   pegada al borde inferior. Son PROPUESTAS que el usuario repasa con el pincel.
 *
 * Todas las máscaras: 0..255, mismo tamaño que la imagen de entrada.
 * MobileSAM no se usa: sus pesos se publican en Google Drive / Hugging Face (no verificables aquí).
 */
object RegionTools {

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    /** Distancia de color aproximadamente perceptual («redmean»), 0..~765. */
    fun colorDist(c1: Int, c2: Int): Float {
        val rm = (r(c1) + r(c2)) / 2f
        val dr = (r(c1) - r(c2)).toFloat(); val dg = (g(c1) - g(c2)).toFloat(); val db = (b(c1) - b(c2)).toFloat()
        return sqrt((2f + rm / 256f) * dr * dr + 4f * dg * dg + (2f + (255f - rm) / 256f) * db * db)
    }

    /**
     * Crecimiento de región desde varias semillas. [local] = salto máximo entre vecinos,
     * [global] = distancia máxima al color medio de las semillas, [depthTol] = salto máximo de
     * profundidad respecto de la semilla (0..255; ignorado si [depth] es null).
     */
    fun growFrom(argb: IntArray, w: Int, h: Int, seeds: List<Int>, local: Float, global: Float,
                 depth: ByteArray? = null, depthTol: Int = 40, exclude: ByteArray? = null): ByteArray {
        val out = ByteArray(w * h)
        if (seeds.isEmpty()) return out
        var mr = 0f; var mg = 0f; var mb = 0f
        for (s in seeds) { mr += r(argb[s]); mg += g(argb[s]); mb += b(argb[s]) }
        val mean = (0xFF shl 24) or ((mr / seeds.size).toInt() shl 16) or ((mg / seeds.size).toInt() shl 8) or (mb / seeds.size).toInt()
        val seedDepth = depth?.let { d -> seeds.map { d[it].toInt() and 0xFF }.sorted()[seeds.size / 2] }
        val queue = IntArray(w * h)
        var head = 0; var tail = 0
        fun ok(i: Int) = (exclude == null || (exclude[i].toInt() and 0xFF) < 128) &&
            (depth == null || abs((depth[i].toInt() and 0xFF) - seedDepth!!) <= depthTol)
        for (s in seeds) if (out[s].toInt() == 0 && ok(s)) { out[s] = -1; queue[tail++] = s }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w; val y = i / w
            val c = argb[i]
            for (k in 0..3) {
                val nx = x + (if (k == 0) 1 else if (k == 1) -1 else 0)
                val ny = y + (if (k == 2) 1 else if (k == 3) -1 else 0)
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (out[j].toInt() != 0 || !ok(j)) continue
                val cj = argb[j]
                if (colorDist(c, cj) <= local && colorDist(mean, cj) <= global) { out[j] = -1; queue[tail++] = j }
            }
        }
        return close(out, w, h, 2)
    }

    /** Tocar para seleccionar: región parecida al punto (x, y) en píxeles. [tolerance] 0..1. */
    fun grow(argb: IntArray, w: Int, h: Int, x: Int, y: Int, tolerance: Float = .5f,
             depth: ByteArray? = null, exclude: ByteArray? = null): ByteArray {
        val t = tolerance.coerceIn(0f, 1f)
        val seeds = ArrayList<Int>()
        for (dy in -2..2) for (dx in -2..2) {
            val xx = (x + dx).coerceIn(0, w - 1); val yy = (y + dy).coerceIn(0, h - 1); seeds.add(yy * w + xx)
        }
        return growFrom(argb, w, h, seeds.distinct(), 18f + 40f * t, 70f + 230f * t, depth, (25 + 60 * t).toInt(), exclude)
    }

    private fun skyLike(c: Int): Boolean {
        val lum = (r(c) * 77 + g(c) * 150 + b(c) * 29) shr 8
        return (b(c) >= r(c) + 8 && b(c) >= g(c) - 12) || lum > 205
    }

    private fun waterLike(c: Int): Boolean = b(c) >= r(c) + 15 && b(c) >= g(c) - 15 && ((r(c) * 77 + g(c) * 150 + b(c) * 29) shr 8) > 35

    /** Cielo: lo «celeste o muy claro» unido al borde superior (y lejano si hay profundidad). null si no hay. */
    fun autoSky(argb: IntArray, w: Int, h: Int, depth: ByteArray? = null, exclude: ByteArray? = null): ByteArray? {
        val seeds = (0 until w).filter { x ->
            val i = x
            skyLike(argb[i]) && (depth == null || (depth[i].toInt() and 0xFF) < 110) &&
                (exclude == null || (exclude[i].toInt() and 0xFF) < 128)
        }
        if (seeds.size < w / 8) return null
        val m = growFrom(argb, w, h, seeds, 26f, 230f, depth, 70, exclude)
        val cov = m.count { it.toInt() != 0 }.toFloat() / m.size
        return if (cov in .02f..0.85f) m else null
    }

    /**
     * Agua: tonos azules/cian en la mitad inferior que forman una franja ANCHA (≥ 45 % del
     * ancho): así no confunde ropa azul ni césped. null si no hay.
     */
    fun autoWater(argb: IntArray, w: Int, h: Int, depth: ByteArray? = null, exclude: ByteArray? = null): ByteArray? {
        // El cielo nunca es agua (un cielo nocturno azul oscuro se parece mucho).
        val sky = autoSky(argb, w, h, depth, exclude)
        val block = ByteArray(w * h) { i ->
            if ((exclude != null && (exclude[i].toInt() and 0xFF) >= 128) || (sky != null && sky[i].toInt() != 0)) -1 else 0
        }
        val seeds = ArrayList<Int>()
        val stepX = max(1, w / 64); val stepY = max(1, h / 96)
        for (y in h * 2 / 3 until h step stepY) for (x in 0 until w step stepX) {
            val i = y * w + x
            if (waterLike(argb[i]) && block[i].toInt() == 0) seeds.add(i)
        }
        if (seeds.size < 12) return null
        val m = growFrom(argb, w, h, seeds, 22f, 150f, depth, 90, block)
        for (y in 0 until h / 4) for (x in 0 until w) m[y * w + x] = 0
        var cols = 0
        for (x in 0 until w) { var any = false; for (y in h / 4 until h) if (m[y * w + x].toInt() != 0) { any = true; break }; if (any) cols++ }
        val cov = coverage(m)
        // Ropa oscura azulada (pantalones, abrigos) no es agua: el agua refleja el cielo.
        var lum = 0L; var n = 0
        for (i in m.indices) if (m[i].toInt() != 0) { val c = argb[i]; lum += (r(c) * 77 + g(c) * 150 + b(c) * 29) shr 8; n++ }
        if (n == 0 || lum / n < 70) return null
        // El agua forma bandas horizontales: la mitad de sus filas cubren ≥ 55 % del ancho.
        var rows = 0; var wide = 0
        for (y in 0 until h) {
            var c = 0
            for (x in 0 until w) if (m[y * w + x].toInt() != 0) c++
            if (c > 0) { rows++; if (c >= w * .55f) wide++ }
        }
        if (rows == 0 || wide < rows * .5f) return null
        return if (cols >= w * .45f && cov in .02f..0.8f) m else null
    }

    /** Cierre morfológico (dilatar y erosionar [rad] px): tapa poros de la trama y del ruido. */
    fun close(m: ByteArray, w: Int, h: Int, rad: Int): ByteArray {
        val d = CutoutMath.dilate(m, w, h, rad)
        val inv = ByteArray(d.size) { if (d[it].toInt() == 0) -1 else 0 }
        val e = CutoutMath.dilate(inv, w, h, rad)
        return ByteArray(d.size) { if (e[it].toInt() == 0) -1 else 0 }
    }

    fun coverage(m: ByteArray): Float = if (m.isEmpty()) 0f else m.count { it.toInt() != 0 }.toFloat() / m.size

    // ------------------------------------------------------------------ Sugerencias de escena

    /** Rasgos sencillos de la imagen (miniatura) para sugerir escenas por reglas, sin IA pesada. */
    data class Traits(val luminance: Float, val saturation: Float, val skyTop: Float, val warmTop: Float,
                      val green: Float, val waterBottom: Float, val brightSpots: Float, val white: Float, val faces: Int,
                      /** Hay cielo / agua de verdad (autoSky / autoWater encuentran región). */
                      val sky: Boolean = false, val water: Boolean = false)

    /** [exclude] = Proteger (personaje) a ese tamaño, si lo hay: la ropa no cuenta como agua o cielo. */
    fun traits(argb: IntArray, w: Int, h: Int, faces: Int = 0, exclude: ByteArray? = null): Traits {
        var lum = 0f; var sat = 0f; var sky = 0; var warm = 0; var green = 0; var water = 0; var spots = 0; var white = 0
        var top = 0; var bottom = 0
        for (y in 0 until h) for (x in 0 until w) {
            val c = argb[y * w + x]
            val rr = r(c); val gg = g(c); val bb = b(c)
            val mx = max(rr, max(gg, bb)); val mn = min(rr, min(gg, bb))
            val l = (rr * 77 + gg * 150 + bb * 29) shr 8
            lum += l; sat += if (mx == 0) 0f else (mx - mn).toFloat() / mx
            if (y < h / 3) { top++; if (skyLike(c) && l > 90) sky++; if (rr > bb + 40 && rr > 150 && gg > 70) warm++ }
            if (y >= h * 2 / 3) { bottom++; if (waterLike(c) && l > 50) water++ }
            if (gg > rr + 12 && gg > bb + 5) green++
            if (l > 230 && mx - mn < 30) white++
            if (l > 200) spots++
        }
        val n = (w * h).toFloat()
        return Traits(lum / n / 255f, sat / n, sky / max(1f, top.toFloat()), warm / max(1f, top.toFloat()),
            green / n, water / max(1f, bottom.toFloat()), spots / n, white / n, faces,
            autoSky(argb, w, h, null, exclude) != null, autoWater(argb, w, h, null, exclude) != null)
    }

    /** Nombres de escenas de un toque (Presets.scenes), de la más a la menos indicada. */
    fun suggest(t: Traits): List<String> {
        val out = LinkedHashMap<String, Float>()
        fun add(name: String, score: Float) { if (score > 0f) out[name] = max(out[name] ?: 0f, score) }
        val night = t.luminance < .28f
        // Manga B/N: solo escenas que no tiñen la imagen de color.
        if (t.saturation < .08f) {
            add("Impacto manga", .9f); add("Terror", if (night) .8f else .4f)
            return out.entries.sortedByDescending { it.value }.map { it.key }
        }
        if (night && t.brightSpots > .01f) { add("Noche lluviosa", .85f); add("Ciudad", .7f); add("Ciudad de noche", .75f) }
        if (night && t.brightSpots <= .01f) add("Terror", .6f)
        if (t.warmTop > .2f) { add("Atardecer", .9f); add("Mañana tranquila", .4f) }
        if (t.sky && t.skyTop > .35f && !night) { add("Cielo animado", .75f); add("Mañana tranquila", .55f) }
        if (t.water && t.waterBottom > .3f) add("Mar", .8f)
        if (t.green > .25f) add("Bosque", .8f)
        if (t.white > .25f) add("Nieve intensa", .7f)
        if (t.faces > 0 && t.saturation >= .08f) { add("Ojo brillante", .5f); add("Batalla anime", .45f) }
        if (out.isEmpty()) { add("Mañana tranquila", .3f); add("Cielo animado", .2f) }
        return out.entries.sortedByDescending { it.value }.map { it.key }.take(4)
    }
}
