package com.zeta.fondo.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * v44.13 · Lectura rápida de la imagen (sin IA) para colocar solos los efectos de cielo:
 * dónde está el horizonte y dónde está el sol (o la luna). Código puro, pruebas JVM.
 * Entrada: luminancia 0..1 de una imagen pequeña (≈ 160 px), fila a fila.
 */
object SceneAnalysis {

    /**
     * Horizonte = la fila con el borde HORIZONTAL más fuerte y continuo entre el 25 % y el 88 %
     * de la altura (mar, tierra, barandilla). Devuelve y (0..1) o null si no hay ninguno claro.
     */
    fun horizon(lum: FloatArray, w: Int, h: Int): Float? {
        if (w < 8 || h < 8) return null
        val score = FloatArray(h)
        for (y in 2 until h - 2) {
            var s = 0f
            var same = 0
            for (x in 0 until w) {
                val g = lum[(y + 1) * w + x] + lum[(y + 2) * w + x] - lum[(y - 1) * w + x] - lum[(y - 2) * w + x]
                s += abs(g)
                if (abs(g) > .04f) same++
            }
            // Un horizonte cruza casi toda la imagen: premia las filas con borde en todo el ancho.
            score[y] = s / w * (0.4f + 0.6f * same.toFloat() / w)
        }
        val lo = (h * .25f).toInt(); val hi = (h * .88f).toInt()
        var best = -1; var bestS = 0f
        for (y in lo..hi) if (score[y] > bestS) { bestS = score[y]; best = y }
        val mean = (lo..hi).map { score[it] }.average().toFloat()
        if (best < 0 || bestS < max(.02f, mean * 2.2f)) return null
        // El horizonte es el borde fuerte MÁS ALTO (la barandilla o el mar empiezan ahí; lo de
        // debajo, como el reflejo del sol o la ciudad, ya no es cielo).
        for (y in lo..hi) if (score[y] >= bestS * .6f && score[y] >= score[(y - 1).coerceAtLeast(0)] && score[y] >= score[(y + 1).coerceAtMost(h - 1)])
            return (y + .5f) / h
        return (best + .5f) / h
    }

    /** Punto más luminoso (centro de la zona clara más grande): el sol o la luna. */
    fun brightest(lum: FloatArray, w: Int, h: Int): Pair<Float, Float> {
        val r = max(1, min(w, h) / 14)
        // Desenfoque de caja separable para buscar la ZONA clara, no un píxel suelto.
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        for (y in 0 until h) {
            var acc = 0f
            for (x in -r..r) acc += lum[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[y * w + x] = acc
                acc += lum[y * w + (x + r + 1).coerceIn(0, w - 1)] - lum[y * w + (x - r).coerceIn(0, w - 1)]
            }
        }
        for (x in 0 until w) {
            var acc = 0f
            for (y in -r..r) acc += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = acc
                acc += tmp[(y + r + 1).coerceIn(0, h - 1) * w + x] - tmp[(y - r).coerceIn(0, h - 1) * w + x]
            }
        }
        var bi = 0
        for (i in out.indices) if (out[i] > out[bi]) bi = i
        return ((bi % w) + .5f) / w to ((bi / w) + .5f) / h
    }

    /**
     * v44.17 · ¿La zona más clara es una fuente de luz (sol, luna, ventana) de donde pueden salir
     * rayos? Muy clara y no pegada al borde de abajo (ahí suele ser reflejo o suelo).
     */
    fun isLightSource(lum: Float, y: Float): Boolean = lum >= .80f && y <= .85f

    /** Luminancia 0..1 desde ARGB. */
    fun luminance(argb: IntArray): FloatArray = FloatArray(argb.size) { i ->
        val c = argb[i]
        (((c shr 16) and 0xFF) * .299f + ((c shr 8) and 0xFF) * .587f + (c and 0xFF) * .114f) / 255f
    }
}
