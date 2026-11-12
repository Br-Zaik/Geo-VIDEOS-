package com.zeta.fondo.model

/** Comportamiento temporal de un efecto de evento. */
object EventMode {
    /** Repetido un número entero de veces por bucle (comportamiento clásico). */
    const val LOOP = 0
    /** Una sola vez dentro de su bloque: inicio, desarrollo y final natural. */
    const val ONCE = 1
    /** Se repite cada X segundos dentro de su bloque. */
    const val REPEAT = 2

    /**
     * Fase 0..1 del evento en el instante [localSec] de su bloque, o -1 para usar el
     * reloj del bucle (modo LOOP). Función pura: la usan la vista previa y la exportación.
     */
    fun phase(mode: Int, localSec: Float, clipSec: Float, everySec: Float): Float = when (mode) {
        ONCE -> (localSec / clipSec.coerceAtLeast(.05f)).coerceIn(0f, .9999f)
        REPEAT -> {
            val e = everySec.coerceAtLeast(.2f)
            val x = localSec / e
            (x - kotlin.math.floor(x)).coerceIn(0f, .9999f)
        }
        else -> -1f
    }
}
