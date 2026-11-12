package com.zeta.fondo.model

import kotlin.math.abs
import kotlin.math.roundToInt

/** Utilidades puras de color (sin Android) para el selector HEX de los efectos. */
object ColorHex {
    /** Acepta "#RRGGBB", "RRGGBB", "#RGB" o "RGB". Devuelve 0xRRGGBB o null. */
    fun parse(text: String): Int? {
        val t = text.trim().removePrefix("#")
        if (!t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return when (t.length) {
            6 -> t.toInt(16)
            3 -> {
                val r = t[0].digitToInt(16); val g = t[1].digitToInt(16); val b = t[2].digitToInt(16)
                (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
            }
            else -> null
        }
    }

    fun format(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

    /** HSV (h 0..360, s 0..1, v 0..1) -> 0xRRGGBB. */
    fun fromHsv(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val c = v.coerceIn(0f, 1f) * s.coerceIn(0f, 1f)
        val x = c * (1f - abs(hh % 2f - 1f))
        val m = v.coerceIn(0f, 1f) - c
        val (r, g, b) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
        return (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** 0xRRGGBB -> [h, s, v]. */
    fun toHsv(rgb: Int): FloatArray {
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        val h = when {
            d < 1e-6f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf((h + 360f) % 360f, if (max <= 0f) 0f else d / max, max)
    }
}
