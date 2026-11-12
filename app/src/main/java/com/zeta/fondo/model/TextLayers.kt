package com.zeta.fondo.model

/**
 * v44.27 · Texto como capa. El texto se dibuja UNA vez a una imagen transparente y la capa se
 * comporta como cualquier imagen: mover, girar, tamaño, 2.5D, Movimiento, Tiempo, deshacer y
 * exportar igual que en la vista previa. Las capas se dibujan encima de la escena ya animada, así
 * que los efectos (fuego, luz, partículas…) pasan SIEMPRE por detrás de las letras.
 *
 * Fuentes: solo las familias del propio teléfono (sin archivos de fuentes ni licencias aparte).
 */
data class TextSpec(
    val text: String,
    val font: Int = 0,
    /** Color ARGB del relleno. */
    val color: Int = TextLayers.WHITE,
    /** 0 = sin borde, 1 fino, 2 medio, 3 grueso. */
    val outline: Int = 2,
    val outlineColor: Int = TextLayers.BLACK,
    val shadow: Boolean = true
)

object TextLayers {
    const val MAX_CHARS = 200
    const val WHITE = -1                 // 0xFFFFFFFF
    const val BLACK = -16777216          // 0xFF000000

    /** Fuente: nombre en la app, familia del sistema y si va en negrita. */
    data class Font(val name: String, val family: String, val bold: Boolean)

    val fonts = listOf(
        Font("Clásica", "sans-serif", true),
        Font("Negra", "sans-serif-black", false),
        Font("Estrecha", "sans-serif-condensed", true),
        Font("Elegante", "serif", true),
        Font("A mano", "cursive", true),
        Font("Cómic", "casual", false),
        Font("Máquina", "monospace", true)
    )

    /** Colores de relleno y de borde (ARGB). */
    val colors: List<Int> = listOf(
        WHITE, BLACK, 0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFD60A.toInt(), 0xFF34C759.toInt(),
        0xFF32D2FF.toInt(), 0xFF0A84FF.toInt(), 0xFFAF52DE.toInt(), 0xFFFF2D92.toInt()
    )

    val outlineNames = listOf("Sin borde", "Fino", "Medio", "Grueso")

    /** Grosor del borde como fracción del tamaño de la letra. */
    fun outlineFraction(level: Int): Float = when (level.coerceIn(0, 3)) {
        1 -> .045f
        2 -> .085f
        3 -> .13f
        else -> 0f
    }

    fun sanitize(s: TextSpec): TextSpec = s.copy(
        text = s.text.replace("\r", "").take(MAX_CHARS),
        font = s.font.coerceIn(0, fonts.size - 1),
        outline = s.outline.coerceIn(0, 3)
    )

    /** Nombre en la línea de tiempo: «T · » + la primera línea con algo escrito. */
    fun displayName(text: String): String {
        val first = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        return if (first == null) "Texto" else "T · " + first.take(24)
    }

    /**
     * Texto editado: la imagen nueva tiene otro ancho/alto ([newAspect]). Se mantiene el ALTO de
     * las letras en pantalla (un texto más largo crece a lo ancho, no se achica), con su lugar,
     * giro, claves y demás ajustes. Un recorte anterior no tiene sentido sobre el texto nuevo.
     */
    fun keepLetterSize(layer: ImageLayer, newAspect: Float, newFile: String, spec: TextSpec): ImageLayer {
        val a0 = layer.aspect.coerceIn(.02f, 50f)
        val a1 = newAspect.coerceIn(.02f, 50f)
        val f = a1 / a0
        val keys = layer.keys.mapValues { (prop, list) ->
            if (prop == "sx" || prop == "sy") list.map { it.copy(value = it.value * f) } else list
        }
        return ImageLayers.sanitize(layer.copy(
            file = newFile, aspect = a1, sx = layer.sx * f, sy = layer.sy * f, keys = keys,
            src = null, cropL = 0f, cropT = 0f, cropR = 1f, cropB = 1f, cropRot = 0,
            text = sanitize(spec), name = displayName(spec.text)
        ))
    }
}
