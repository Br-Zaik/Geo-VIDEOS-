package com.zeta.fondo.diag

/**
 * v44 · Escala de la vista previa en movimiento, SIN glFinish.
 *
 * Antes (v42) se medía la GPU con glFinish cada 4 cuadros: eso detiene el hilo de dibujo
 * hasta que la GPU acaba y rompe el paralelismo CPU/GPU. Ahora se usan dos señales que no
 * bloquean nada:
 *  - el tiempo de GPU de un temporizador asíncrono (si el teléfono lo admite), o
 *  - el intervalo real entre cuadros: si la GPU no llega, eglSwapBuffers retiene el hilo
 *    y el intervalo crece por encima del objetivo.
 * Baja enseguida si no llega y sube despacio para no oscilar. Solo afecta a la VISTA PREVIA:
 * la exportación siempre se dibuja a la resolución elegida.
 */
class AdaptiveScale(private val min: Float = .45f, private val max: Float = 1f) {
    var value = max; private set
    private var goodFrames = 0

    fun reset(v: Float = max) { value = v.coerceIn(min, max); goodFrames = 0 }
    fun clampTo(v: Float) { if (value > v) value = v.coerceIn(min, max) }

    /** Tiempo de GPU medido para un cuadro dibujado a escala [used]. */
    fun onGpuTime(ms: Float, budgetMs: Float, used: Float) {
        if (ms <= 0f) return
        val wanted = (used * kotlin.math.sqrt(budgetMs / ms.coerceAtLeast(.5f))).coerceIn(min, max)
        value = if (wanted < value) wanted else value + (wanted - value) * .25f
    }

    /** Intervalo real entre dos cuadros cuando se pidió uno cada [targetMs]. */
    fun onInterval(ms: Float, targetMs: Float, used: Float) {
        if (ms <= 0f || targetMs <= 0f || ms > FrameStats.PAUSE_MS) return
        if (ms > targetMs * 1.30f) {
            goodFrames = 0
            val wanted = (used * kotlin.math.sqrt(targetMs / ms)).coerceIn(min, max)
            if (wanted < value) value = value + (wanted - value) * .5f
        } else if (ms < targetMs * 1.10f) {
            // Va sobrado: tras unos cuadros buenos se sube un poco la calidad.
            if (++goodFrames >= 20) { goodFrames = 0; value = (value + .05f).coerceAtMost(max) }
        }
    }
}
