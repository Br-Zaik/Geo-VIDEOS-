package com.zeta.fondo.engine

/**
 * v44.11 · Bordes de un bloque de sonido sin «clic»: 12 ms de entrada y salida suaves.
 * No se suaviza el inicio de un evento (su golpe es el ataque) ni un ambiente que cubre el
 * principio o el final del vídeo (al repetirse el bucle sigue sin cortes).
 */
object SoundEdges {
    const val RAMP_SEC = .012f
    fun gain(sinceStart: Long, untilEnd: Long, ramp: Long, openStart: Boolean, openEnd: Boolean, oneShot: Boolean): Float {
        var g = 1f
        if (!openStart && !oneShot && sinceStart < ramp) g = minOf(g, sinceStart.toFloat() / ramp)
        if (!openEnd && untilEnd < ramp) g = minOf(g, untilEnd.toFloat() / ramp)
        return g.coerceIn(0f, 1f)
    }
}

/**
 * v44.11 · Cuándo volver a alinear el audio de la vista previa con la imagen.
 * Antes bastaba UN desfase de 0,2 s (por ejemplo un cuadro lento con muchos efectos) para
 * parar el audio, vaciarlo y reanudarlo: se oía un corte. Ahora:
 * - un salto grande (tocar la línea de tiempo, el bucle) se corrige al instante;
 * - un desfase pequeño solo si se mantiene varias lecturas seguidas.
 */
class DriftGuard(private val strikesNeeded: Int = 4) {
    private var strikes = 0
    fun reset() { strikes = 0 }
    /** [drift] en cuadros de audio (ya con el bucle tenido en cuenta). */
    fun shouldResync(drift: Long, rate: Int): Boolean {
        if (drift > rate * 3L / 4L) { strikes = 0; return true }     // > 0,75 s: el usuario movió el cabezal
        if (drift > rate / 4) { strikes++; if (strikes >= strikesNeeded) { strikes = 0; return true } }
        else strikes = 0
        return false
    }
}
