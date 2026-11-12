package com.zeta.fondo.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v44.23 · Modos de mezcla de las capas de imagen (como Alight Motion, Photoshop o ibisPaint).
 *
 * Las fórmulas son las estándar (W3C Compositing Level 1 + los modos «Photoshop» que no están
 * en la norma: subexposición/sobreexposición lineal, luz vívida/lineal/focal, mezcla dura,
 * sustraer, dividir y color más oscuro/claro). Se mezclan en el espacio de color de la imagen
 * (sRGB), igual que esas apps. El shader de capas usa EXACTAMENTE estas mismas fórmulas: las
 * pruebas comparan el resultado de la GPU con esta versión de referencia.
 *
 * Código puro (sin Android): se prueba en JVM y genera las miniaturas del panel.
 */
object BlendModes {
    data class Mode(val id: Int, val name: String, val group: Int, val help: String)

    const val NORMAL = 0
    const val MULTIPLY = 1
    const val DARKEN = 2
    const val DARKER_COLOR = 3
    const val COLOR_BURN = 4
    const val LINEAR_BURN = 5
    const val SCREEN = 6
    const val LIGHTEN = 7
    const val LIGHTER_COLOR = 8
    const val COLOR_DODGE = 9
    const val LINEAR_DODGE = 10
    const val OVERLAY = 11
    const val SOFT_LIGHT = 12
    const val HARD_LIGHT = 13
    const val SOFT_OVERLAY = 14
    const val VIVID_LIGHT = 15
    const val LINEAR_LIGHT = 16
    const val PIN_LIGHT = 17
    const val HARD_MIX = 18
    const val DIFFERENCE = 19
    const val EXCLUSION = 20
    const val SUBTRACT = 21
    const val DIVIDE = 22
    const val HUE = 23
    const val SATURATION = 24
    const val COLOR = 25
    const val LUMINOSITY = 26
    const val MASK = 27
    const val EXCLUDE = 28

    const val COUNT = 29

    /** Grupos del panel, en orden (el 0 es «Normal», sin desplegable). */
    val groups = listOf("Normal", "Oscurecer", "Aclarar", "Contraste", "Diferencia", "Color", "Enmascarar")
    val groupHelp = listOf(
        "La imagen tal cual, encima",
        "Lo blanco se vuelve invisible",
        "Lo negro se vuelve invisible",
        "Lo gris medio se vuelve invisible",
        "Resta o invierte colores",
        "Mezcla tono, saturación o brillo",
        "Recorta con la forma de la imagen"
    )

    val all: List<Mode> = listOf(
        Mode(NORMAL, "Normal", 0, "La imagen va encima tal cual. Con la opacidad la haces más transparente."),
        Mode(MULTIPLY, "Multiplicar", 1, "Oscurece: lo blanco desaparece y lo oscuro tiñe la imagen de abajo. Ideal para sombras y texturas."),
        Mode(DARKEN, "Oscurecer", 1, "En cada punto se queda con lo más oscuro de las dos imágenes."),
        Mode(DARKER_COLOR, "Color más oscuro", 1, "Compara el color completo y deja el más oscuro, sin mezclarlos."),
        Mode(COLOR_BURN, "Subexponer color", 1, "Oscurece fuerte y sube el contraste: colores intensos, como quemados."),
        Mode(LINEAR_BURN, "Subexposición lineal", 1, "Oscurece más que Multiplicar: las sombras se vuelven negras."),
        Mode(SCREEN, "Pantalla", 2, "Aclara: lo negro desaparece y queda solo la luz. Ideal para destellos, rayos y brillos."),
        Mode(LIGHTEN, "Aclarar", 2, "En cada punto se queda con lo más claro de las dos imágenes."),
        Mode(LIGHTER_COLOR, "Color más claro", 2, "Compara el color completo y deja el más claro, sin mezclarlos."),
        Mode(COLOR_DODGE, "Sobreexponer color", 2, "Aclara fuerte y satura: luces muy intensas que queman el color."),
        Mode(LINEAR_DODGE, "Sobreexposición lineal", 2, "Suma la luz de las dos imágenes. El brillo más fuerte; lo negro desaparece."),
        Mode(OVERLAY, "Superponer", 3, "Más contraste: lo claro aclara y lo oscuro oscurece según la imagen de abajo."),
        Mode(SOFT_LIGHT, "Luz suave", 3, "Como Superponer, pero suave, como una luz difusa."),
        Mode(HARD_LIGHT, "Luz intensa", 3, "Como Superponer, pero fuerte, como un foco directo."),
        Mode(SOFT_OVERLAY, "Superposición suave", 3, "Contraste muy suave y sin cortes. Bueno para dar color sin quemar."),
        Mode(VIVID_LIGHT, "Luz vívida", 3, "Mucho contraste: aclara u oscurece muy fuerte. Colores intensos."),
        Mode(LINEAR_LIGHT, "Luz lineal", 3, "Aclara u oscurece sumando o restando luz. Fuerte y contrastado."),
        Mode(PIN_LIGHT, "Luz focal", 3, "Lo claro aclara, lo oscuro oscurece y los tonos medios desaparecen."),
        Mode(HARD_MIX, "Mezcla dura", 3, "Deja solo colores puros, blanco y negro. Efecto póster muy fuerte."),
        Mode(DIFFERENCE, "Diferencia", 4, "Resta un color del otro: lo blanco invierte los colores y lo negro no cambia nada."),
        Mode(EXCLUSION, "Exclusión", 4, "Como Diferencia, pero más suave y con menos contraste."),
        Mode(SUBTRACT, "Sustraer", 4, "Resta la imagen de arriba a la de abajo. Oscurece mucho."),
        Mode(DIVIDE, "Dividir", 4, "Divide los colores: aclara mucho; lo blanco no cambia nada."),
        Mode(HUE, "Tono", 5, "Usa el tono (el color) de arriba con el brillo y la saturación de abajo."),
        Mode(SATURATION, "Saturación", 5, "Usa lo vivo del color de arriba. Una imagen gris quita el color de abajo."),
        Mode(COLOR, "Color", 5, "Tiñe la imagen de abajo con el color de arriba, sin cambiar sus luces y sombras."),
        Mode(LUMINOSITY, "Luminancia", 5, "Usa las luces y sombras de arriba con los colores de abajo."),
        Mode(MASK, "Máscara", 6, "La imagen recorta a lo de abajo: solo se ve donde la imagen tiene forma."),
        Mode(EXCLUDE, "Excluir", 6, "Al revés de Máscara: hace un hueco con la forma de la imagen.")
    )

    fun byId(id: Int): Mode = all.getOrElse(id.coerceIn(0, COUNT - 1)) { all[0] }
    fun inGroup(group: Int): List<Mode> = all.filter { it.group == group }

    /** Modos que no cambian lo de abajo fuera de la imagen (todos menos Máscara). */
    fun coversWholeCanvas(mode: Int): Boolean = mode == MASK

    // ------------------------------------------------------------------ fórmulas

    fun lum(r: Float, g: Float, b: Float): Float = .3f * r + .59f * g + .11f * b

    private fun screen(b: Float, s: Float) = b + s - b * s
    private fun colorBurn(b: Float, s: Float): Float = when {
        b >= 1f -> 1f
        s <= 0f -> 0f
        else -> 1f - min(1f, (1f - b) / s)
    }
    private fun colorDodge(b: Float, s: Float): Float = when {
        b <= 0f -> 0f
        s >= 1f -> 1f
        else -> min(1f, b / (1f - s))
    }
    private fun hardLight(b: Float, s: Float) = if (s <= .5f) b * 2f * s else screen(b, 2f * s - 1f)
    private fun softLight(b: Float, s: Float): Float = if (s <= .5f) b - (1f - 2f * s) * b * (1f - b) else {
        val d = if (b <= .25f) ((16f * b - 12f) * b + 4f) * b else sqrt(b)
        b + (2f * s - 1f) * (d - b)
    }

    /** Un canal de los modos separables. [b] = abajo, [s] = arriba (0..1). */
    fun channel(mode: Int, b: Float, s: Float): Float = when (mode) {
        MULTIPLY -> b * s
        DARKEN -> min(b, s)
        COLOR_BURN -> colorBurn(b, s)
        LINEAR_BURN -> max(b + s - 1f, 0f)
        SCREEN -> screen(b, s)
        LIGHTEN -> max(b, s)
        COLOR_DODGE -> colorDodge(b, s)
        LINEAR_DODGE -> min(b + s, 1f)
        OVERLAY -> hardLight(s, b)
        SOFT_LIGHT -> softLight(b, s)
        HARD_LIGHT -> hardLight(b, s)
        SOFT_OVERLAY -> (1f - 2f * s) * b * b + 2f * s * b
        VIVID_LIGHT -> if (s <= .5f) colorBurn(b, 2f * s) else colorDodge(b, 2f * s - 1f)
        LINEAR_LIGHT -> (b + 2f * s - 1f).coerceIn(0f, 1f)
        PIN_LIGHT -> if (s <= .5f) min(b, 2f * s) else max(b, 2f * s - 1f)
        HARD_MIX -> if (b + s >= HARD_MIX_EDGE) 1f else 0f
        DIFFERENCE -> abs(b - s)
        EXCLUSION -> b + s - 2f * b * s
        SUBTRACT -> max(b - s, 0f)
        DIVIDE -> if (s <= 0f) (if (b > 0f) 1f else 0f) else min(1f, b / s)
        else -> s
    }

    /**
     * Por debajo de medio paso de 8 bits un color se trata como gris en Tono/Saturación: el tono
     * de un gris no existe y el ruido del filtrado de la textura lo haría saltar de color.
     */
    const val SAT_EPS = .5f / 255f

    /** Umbral de Mezcla dura (medio paso de 8 bits por debajo de 1: evita errores de redondeo). */
    const val HARD_MIX_EDGE = 1f - .5f / 255f

    private fun clipColor(c: FloatArray) {
        val l = lum(c[0], c[1], c[2])
        val n = min(c[0], min(c[1], c[2]))
        val x = max(c[0], max(c[1], c[2]))
        if (n < 0f) { val k = l / max(l - n, 1e-6f); for (i in 0..2) c[i] = l + (c[i] - l) * k }
        if (x > 1f) { val k = (1f - l) / max(x - l, 1e-6f); for (i in 0..2) c[i] = l + (c[i] - l) * k }
    }
    private fun setLum(c: FloatArray, l: Float): FloatArray {
        val d = l - lum(c[0], c[1], c[2])
        val o = floatArrayOf(c[0] + d, c[1] + d, c[2] + d)
        clipColor(o)
        return o
    }
    private fun sat(c: FloatArray) = max(c[0], max(c[1], c[2])) - min(c[0], min(c[1], c[2]))
    private fun setSat(c: FloatArray, s: Float): FloatArray {
        val mx = max(c[0], max(c[1], c[2]))
        val mn = min(c[0], min(c[1], c[2]))
        return if (mx - mn > SAT_EPS) FloatArray(3) { (c[it] - mn) * s / (mx - mn) } else FloatArray(3)
    }

    /** Color mezclado B(Cb, Cs) sin opacidad. Para Máscara/Excluir devuelve Cb (se aplican aparte). */
    fun blend(mode: Int, cb: FloatArray, cs: FloatArray): FloatArray = when (mode) {
        DARKER_COLOR -> if (lum(cs[0], cs[1], cs[2]) < lum(cb[0], cb[1], cb[2])) cs.copyOf() else cb.copyOf()
        LIGHTER_COLOR -> if (lum(cs[0], cs[1], cs[2]) > lum(cb[0], cb[1], cb[2])) cs.copyOf() else cb.copyOf()
        HUE -> setLum(setSat(cs, sat(cb)), lum(cb[0], cb[1], cb[2]))
        SATURATION -> setLum(setSat(cb, sat(cs)), lum(cb[0], cb[1], cb[2]))
        COLOR -> setLum(cs, lum(cb[0], cb[1], cb[2]))
        LUMINOSITY -> setLum(cb, lum(cs[0], cs[1], cs[2]))
        MASK, EXCLUDE -> cb.copyOf()
        NORMAL -> cs.copyOf()
        else -> FloatArray(3) { channel(mode, cb[it], cs[it]) }
    }

    /**
     * Resultado final sobre un fondo opaco: Co = (1 − a·o)·Cb + a·o·B(Cb, Cs).
     * [alpha] = transparencia propia de la capa en ese punto (0 fuera de la imagen),
     * [opacity] = opacidad de la capa. Máscara y Excluir multiplican lo de abajo.
     */
    fun composite(mode: Int, cb: FloatArray, cs: FloatArray, alpha: Float, opacity: Float): FloatArray {
        val a = alpha.coerceIn(0f, 1f)
        val o = opacity.coerceIn(0f, 1f)
        return when (mode) {
            MASK -> { val f = 1f - o + o * a; FloatArray(3) { cb[it] * f } }
            EXCLUDE -> { val f = 1f - o * a; FloatArray(3) { cb[it] * f } }
            else -> {
                val c = a * o
                if (c <= 0f) cb.copyOf() else {
                    val b = blend(mode, cb, cs)
                    FloatArray(3) { (cb[it] + (b[it] - cb[it]) * c).coerceIn(0f, 1f) }
                }
            }
        }
    }

    /** Mezcla dos colores ARGB empaquetados (miniaturas del panel). */
    fun compositeArgb(mode: Int, below: Int, above: Int, opacity: Float): Int {
        val a = ((above ushr 24) and 255) / 255f
        val cb = floatArrayOf(((below shr 16) and 255) / 255f, ((below shr 8) and 255) / 255f, (below and 255) / 255f)
        val cs = floatArrayOf(((above shr 16) and 255) / 255f, ((above shr 8) and 255) / 255f, (above and 255) / 255f)
        val o = composite(mode, cb, cs, a, opacity)
        fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255f + .5f).toInt()
        return (0xFF shl 24) or (ch(o[0]) shl 16) or (ch(o[1]) shl 8) or ch(o[2])
    }
}
