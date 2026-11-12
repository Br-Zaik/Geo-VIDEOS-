package com.zeta.fondo.engine

import kotlin.math.max
import kotlin.math.sqrt

/**
 * v44 · Beat Sync: golpes de la música por análisis de señal (sin modelos de IA).
 * Energía por ventanas de ~23 ms, flujo positivo de energía y umbral adaptativo
 * (media + k·desviación en ~1 s), con separación mínima entre golpes. Código puro (JVM).
 */
object BeatDetect {
    /** [mono] en −1..1 a [rate] Hz → segundos de cada golpe (ordenados). */
    fun onsets(mono: FloatArray, rate: Int, sensitivity: Float = .5f, minGapSec: Float = .22f): List<Float> {
        val hop = (rate * .0116f).toInt().coerceAtLeast(64)
        val win = hop * 2
        val n = (mono.size - win) / hop
        if (n < 8) return emptyList()
        val energy = FloatArray(n)
        for (i in 0 until n) {
            var e = 0.0
            val o = i * hop
            for (k in 0 until win) { val x = mono[o + k]; e += x * x }
            energy[i] = sqrt(e / win).toFloat()
        }
        // flujo: aumento respecto a la media de las ventanas anteriores (~70 ms)
        val flux = FloatArray(n)
        for (i in 1 until n) {
            var m = 0f; var c = 0
            for (k in max(0, i - 6) until i) { m += energy[k]; c++ }
            flux[i] = max(0f, energy[i] - m / c.coerceAtLeast(1))
        }
        val span = (rate / hop).coerceAtLeast(8)          // ~1 s
        val k = 2.2f - 1.6f * sensitivity.coerceIn(0f, 1f)
        val out = ArrayList<Float>()
        var last = -1e9f
        for (i in 1 until n - 1) {
            val a = max(0, i - span / 2); val b = minOf(n, i + span / 2)
            var mean = 0f
            for (j in a until b) mean += flux[j]
            mean /= (b - a)
            var v = 0f
            for (j in a until b) { val d = flux[j] - mean; v += d * d }
            val sd = sqrt(v / (b - a))
            val t = (i * hop + win / 2).toFloat() / rate
            if (flux[i] > mean + k * sd && flux[i] >= flux[i - 1] && flux[i] >= flux[i + 1] && flux[i] > 1e-4f && t - last >= minGapSec) {
                out += t; last = t
            }
        }
        return out
    }

    /** PCM 16 bits intercalado → mono −1..1. */
    fun toMono(pcm: ByteArray, channels: Int): FloatArray {
        val frames = pcm.size / (2 * channels)
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var s = 0f
            for (c in 0 until channels) {
                val idx = (i * channels + c) * 2
                s += ((pcm[idx].toInt() and 0xff) or (pcm[idx + 1].toInt() shl 8)).toShort() / 32768f
            }
            out[i] = s / channels
        }
        return out
    }
}
