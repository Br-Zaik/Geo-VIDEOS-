package com.zeta.fondo.ui

import kotlin.math.abs

/**
 * v44 · Imán preciso de la línea de tiempo (código puro, se prueba en JVM).
 *
 * - Entra a [enterSec] de un punto (≈ 8 dp a cualquier zoom).
 * - Una vez pegado NO se suelta hasta alejarse [exitSec] (1,5 × la entrada): así el bloque no
 *   salta adelante y atrás cuando el dedo tiembla justo en el borde.
 * - [delta] = cuánto movió el imán respecto al dedo (se muestra «Imán +0,12 s»).
 * - Apagado (enabled = false) devuelve siempre la posición del dedo.
 */
class MagnetState {
    var lockedValue: Float? = null; private set
    var lockedGuide: Float? = null; private set

    fun reset() { lockedValue = null; lockedGuide = null }

    data class Result(val value: Float, val guide: Float?, val delta: Float, val newSnap: Boolean)

    /**
     * [candidates] = pares (valor al que se pegaría, línea guía que se dibuja). Para recortar un
     * borde o recorrer el cabezal valor = guía; para mover un bloque, el inicio puede pegarse
     * por su borde izquierdo (valor = guía) o por el derecho (valor = guía − duración).
     */
    fun apply(raw: Float, candidates: List<Pair<Float, Float>>, enterSec: Float, exitSec: Float, enabled: Boolean): Result {
        if (!enabled) { reset(); return Result(raw, null, 0f, false) }
        val lv = lockedValue
        if (lv != null && abs(raw - lv) <= exitSec && candidates.any { abs(it.first - lv) < 1e-4f }) {
            return Result(lv, lockedGuide, lv - raw, false)
        }
        var best: Pair<Float, Float>? = null
        var bestD = enterSec
        for (c in candidates) {
            val d = abs(c.first - raw)
            if (d <= bestD) { bestD = d; best = c }
        }
        if (best == null) { reset(); return Result(raw, null, 0f, false) }
        val changed = lockedValue == null || abs(best.first - lockedValue!!) > 1e-4f
        lockedValue = best.first; lockedGuide = best.second
        return Result(best.first, best.second, best.first - raw, changed)
    }

    companion object {
        const val DEFAULT_DP = 8f
        const val EXIT_FACTOR = 1.5f

        /** Candidatos de un borde (recortar o cabezal): cada punto vale tal cual. */
        fun edgeCandidates(anchors: List<Float>): List<Pair<Float, Float>> = anchors.map { it to it }

        /** Candidatos para mover un bloque de [duration] dentro de [0, total]. */
        fun moveCandidates(anchors: List<Float>, duration: Float, total: Float): List<Pair<Float, Float>> {
            val maxStart = (total - duration).coerceAtLeast(0f)
            val out = ArrayList<Pair<Float, Float>>(anchors.size * 2)
            for (a in anchors) {
                if (a in 0f..maxStart) out += a to a
                val byEnd = a - duration
                if (byEnd in 0f..maxStart) out += byEnd to a
            }
            return out
        }

        /** «+0,12 s» / «−0,08 s» (coma decimal, como el resto de la app). */
        fun deltaText(delta: Float): String {
            val cs = Math.round(abs(delta) * 100f)
            val sign = if (delta >= 0f) "+" else "−"
            return "$sign${cs / 100},${(cs % 100).toString().padStart(2, '0')} s"
        }
    }
}

