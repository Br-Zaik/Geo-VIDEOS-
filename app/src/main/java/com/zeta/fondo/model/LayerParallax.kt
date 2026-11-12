package com.zeta.fondo.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * v44.24 · Las capas de imagen dentro de la «Profundidad 2.5D».
 *
 * Antes las capas quedaban fijas mientras el fondo se movía y se ampliaba, y se despegaban de
 * la escena. Ahora cada capa se mueve como el plano en el que está: la misma cámara del shader
 * (fondo.frag, bloque FX_PARALLAX) resuelta en forma cerrada para un único valor de profundidad.
 *
 * Profundidad de la capa ([ImageLayer.depth]):
 *  - [AUTO]: pegada a lo que tiene debajo (lee el mapa de profundidad en su centro): un sol en
 *    el cielo se mueve como el cielo; algo sobre el personaje, como el personaje;
 *  - 0..1: elegida a mano (0 = lo más lejano, 1 = delante de todo).
 * Código puro (lo usan la vista previa, la exportación y las pruebas JVM).
 */
object LayerParallax {
    const val AUTO = -1f

    /** Opciones de la interfaz: nombre y valor. */
    val choices = listOf("Pegada" to AUTO, "Lejos" to 0f, "Medio" to .5f, "Cerca" to .8f, "Delante" to 1f)

    /** Cámara del 2.5D en un instante. */
    class Cam(val x: Float, val y: Float, val amt: Float, val dolly: Float, val over: Float,
              val focus: Float, val diff: Float)

    /**
     * Cámara a partir de los uniformes ya escritos para este cuadro: [parallax] = uParallax
     * (cantidad con la línea de tiempo aplicada, dirección en radianes, ciclos, diferencia entre
     * planos) y [style] = uParallaxStyle (estilo, foco). null = el 2.5D no actúa en este cuadro.
     */
    fun cam(parallax: FloatArray?, style: FloatArray?, t: Float): Cam? {
        if (parallax == null || parallax.size < 4 || style == null || style.size < 2) return null
        val amt = parallax[0]
        if (!(amt > 0f)) return null
        val ph = (2.0 * PI * t * parallax[2]).toFloat()
        val st = floor(style[0] + .5f)
        val dx = cos(parallax[1]); val dy = -sin(parallax[1])
        var cx = dx * sin(ph); var cy = dy * sin(ph)
        var dolly = 0f
        if (st > .5f && st < 1.5f) { cx = cos(ph) - 1f; cy = sin(ph) * .5f }
        else if (st > 1.5f) { dolly = .5f - .5f * cos(ph); cx = dx * dolly * .35f; cy = dy * dolly * .35f }
        val focus = style[1].coerceIn(-1f, 1f)
        val w = parallax[3]
        val over = 1f + amt * (1f + w + abs(focus)) * (if (st > .5f && st < 1.5f) 2.2f else 1.15f) + dolly * amt * 1.2f
        return Cam(cx, cy, amt, dolly, over, focus, w)
    }

    /**
     * Profundidad para la cámara (misma escala que parallaxDepth del shader, ya sin el foco).
     * [raw] = 0..1 (0 lejos, 1 cerca); [manual] = el 2.5D usa solo las zonas pintadas: lo que no
     * está pintado no se mueve, así que una capa «Pegada» tampoco.
     */
    fun depthFor(raw: Float?, cam: Cam, manual: Boolean): Float {
        if (raw == null || (manual && raw < 0f)) return -cam.focus
        val d = raw.coerceIn(0f, 1f)
        return d * (1f + cam.diff) - cam.diff - cam.focus
    }

    /** 0..1 que usa una capa: la elegida, o la del fondo bajo su centro ([sample]) si es AUTO. */
    fun rawDepth(layer: ImageLayer, f: LayerFrame, sample: (Float, Float) -> Float?): Float? =
        if (layer.depth >= 0f) layer.depth.coerceIn(0f, 1f) else sample(f.x, f.y)

    /**
     * Mueve [f] con la cámara. [view] = encuadre (desplazamiento x, y; escala x, y), igual que el
     * shader: el 2.5D trabaja en coordenadas de pantalla y luego se aplica el encuadre.
     */
    fun apply(f: LayerFrame, cam: Cam, d: Float, view: FloatArray): LayerFrame {
        val zoom = max(1f + cam.dolly * cam.amt * 4f * d, .2f)
        val k = zoom * cam.over
        val vsx = if (abs(view[2]) > 1e-6f) view[2] else 1f
        val vsy = if (abs(view[3]) > 1e-6f) view[3] else 1f
        val qx = (f.x - view[0]) / vsx; val qy = (f.y - view[1]) / vsy
        val sx = (qx - .5f + cam.x * cam.amt * d) * k + .5f
        val sy = (qy - .5f + cam.y * cam.amt * d) * k + .5f
        return f.copy(x = view[0] + sx * vsx, y = view[1] + sy * vsy, halfW = f.halfW * k, halfH = f.halfH * k)
    }
}
