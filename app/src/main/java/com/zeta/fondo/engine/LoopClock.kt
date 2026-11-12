package com.zeta.fondo.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Tiempo del bucle compartido por la vista previa y la exportación.
 * Un bucle de S segundos a F fps tiene N = S×F cuadros; el cuadro i usa t = i/N.
 * El cuadro N sería idéntico al 0, por eso nunca se genera: el MP4 empalma sin salto.
 */
object LoopClock {
    fun frameCount(loopSec: Float, fps: Int): Int = max(2, (loopSec * fps).roundToInt())

    fun phase(frame: Int, frames: Int): Float {
        val n = max(1, frames)
        val i = ((frame % n) + n) % n
        return i.toFloat() / n
    }

    /** Ajusta una fase continua 0..1 al cuadro exportable más cercano por debajo. */
    fun quantize(t: Double, loopSec: Float, fps: Int): Float {
        val n = frameCount(loopSec, fps)
        val wrapped = t - floor(t)
        val i = floor(wrapped * n + 1e-6).toInt().coerceIn(0, n - 1)
        return i.toFloat() / n
    }
}
