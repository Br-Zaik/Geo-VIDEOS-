package com.zeta.fondo.model

import java.util.Locale

/**
 * v44 · Edición numérica del tiempo de un bloque, con precisión de 0,01 s.
 * Código puro (pruebas JVM). Respeta la duración mínima y el final del vídeo.
 */
object PreciseTime {
    /** Redondea a centésimas (evita 1.2999999). */
    fun round(v: Float): Float = Math.round(v * 100f) / 100f

    fun setStart(start: Float, duration: Float, total: Float): Pair<Float, Float> {
        val d = round(duration.coerceIn(ClipEdits.MIN_DURATION, total))
        val s = round(start.coerceIn(0f, (total - d).coerceAtLeast(0f)))
        return s to d
    }

    fun setDuration(start: Float, duration: Float, total: Float): Pair<Float, Float> {
        val s = round(start.coerceIn(0f, (total - ClipEdits.MIN_DURATION).coerceAtLeast(0f)))
        val d = round(duration.coerceIn(ClipEdits.MIN_DURATION, (total - s).coerceAtLeast(ClipEdits.MIN_DURATION)))
        return s to d
    }

    /** Cambia el final dejando el inicio fijo. */
    fun setEnd(start: Float, end: Float, total: Float): Pair<Float, Float> = setDuration(start, end - start, total)

    /** «1,25» (coma, como se muestra en la app). */
    fun format(v: Float): String = String.format(Locale.US, "%.2f", round(v)).replace('.', ',')

    /** Acepta «1,25», «1.25», «1» y «,5»; null si no es un número. */
    fun parse(text: String): Float? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty() || t == ".") return null
        return t.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
    }
}
