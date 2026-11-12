package com.zeta.fondo.model

/** v40 · Tamaño de la vista previa elegido por el usuario (Ajustes › Vista). */
enum class PreviewSize(val label: String, val bottomMin: Float) {
    // v40.1: Normal deja ~190 dp a la zona de trabajo (catálogo o pistas) en un teléfono típico.
    COMPACT("Compacta", 420f), NORMAL("Normal", 340f), LARGE("Grande", 270f);
    companion object { fun of(name: String?): PreviewSize = values().firstOrNull { it.name == name } ?: NORMAL }
}

/**
 * v40 · Medidas del editor (en dp), sin dependencias de Android para poder probarlas.
 *
 * Estructura (como un editor de vídeo):
 *   barra superior · LIENZO FIJO · controles (tiempo, play, deshacer) ·
 *   zona de trabajo (línea de tiempo u opciones, con scroll propio) · herramientas · categorías.
 *
 * El alto del lienzo depende SOLO de la pantalla, de la proporción de la imagen y del
 * tamaño de vista previa que el usuario elige. Abrir o cerrar opciones, cambiar de
 * categoría, de herramienta o seleccionar un efecto NUNCA lo cambia: lo que se adapta es
 * la zona de trabajo inferior, que se desplaza por dentro. Pantalla completa solo con su botón.
 */
data class EditorLayout(
    val landscape: Boolean,
    val topBar: Float,
    val canvasH: Float,
    val controls: Float,
    val workspace: Float,
    val toolRow: Float,
    val tabBar: Float,
    val sideW: Float
) {
    companion object {
        const val TOP = 48f
        const val CONTROLS = 40f
        /** v41: una sola barra de herramientas (antes fila de herramientas + categorías). */
        const val TOOLS = 64f
        const val TABS = 0f
        const val MIN_CANVAS = 180f
        const val MIN_WORKSPACE = 96f
        const val SIDE_MARGIN = 20f

        fun compute(widthDp: Float, heightDp: Float, imageAspect: Float, fullscreen: Boolean,
                    size: PreviewSize = PreviewSize.NORMAL): EditorLayout {
            val w = widthDp.coerceAtLeast(1f)
            val h = heightDp.coerceAtLeast(1f)
            if (fullscreen) return EditorLayout(false, 0f, h, 0f, 0f, 0f, 0f, 0f)
            if (w > h * 1.15f) {
                // Horizontal / tableta: lienzo a la izquierda y todo lo demás en una columna.
                val side = (w * 0.38f).coerceIn(280f, 420f).coerceAtMost(w * 0.5f)
                val work = (h - CONTROLS - TOOLS - TABS).coerceAtLeast(0f)
                return EditorLayout(true, TOP, (h - TOP - 10f).coerceAtLeast(0f), CONTROLS, work, TOOLS, TABS, side)
            }
            val fixedBottom = CONTROLS + TOOLS + TABS
            val avail = (h - TOP).coerceAtLeast(0f)
            val bottomMin = size.bottomMin.coerceAtLeast(fixedBottom + MIN_WORKSPACE)
            val maxCanvas = (avail - bottomMin).coerceAtLeast(MIN_CANVAS.coerceAtMost(avail - fixedBottom))
            // Altura que necesita la imagen a todo el ancho (+ un pequeño margen).
            val a = if (imageAspect > 0.05f) imageAspect else 1f
            val natural = (w - SIDE_MARGIN) / a + 8f
            val canvas = natural.coerceIn(MIN_CANVAS.coerceAtMost(maxCanvas), maxCanvas.coerceAtLeast(0f))
            val work = (avail - canvas - fixedBottom).coerceAtLeast(0f)
            return EditorLayout(false, TOP, canvas, CONTROLS, work, TOOLS, TABS, 0f)
        }
    }
}

/** Rectángulo (en píxeles) donde se ve la imagen dentro del lienzo, igual que en OpenGL. */
data class ImageRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun normX(x: Float): Float = (x - left) / width.coerceAtLeast(1f)
    fun normY(y: Float): Float = (y - top) / height.coerceAtLeast(1f)
    fun contains(x: Float, y: Float): Boolean = x >= left && y >= top && x <= left + width && y <= top + height

    companion object {
        /** Encaja [aspect] (ancho/alto) centrado en un lienzo de [boxW]×[boxH]. */
        fun fit(boxW: Float, boxH: Float, aspect: Float): ImageRect {
            val bw = boxW.coerceAtLeast(1f)
            val bh = boxH.coerceAtLeast(1f)
            val a = if (aspect > 0.01f) aspect else bw / bh
            return if (bw / bh > a) {
                val w = bh * a
                ImageRect((bw - w) / 2f, 0f, w, bh)
            } else {
                val hh = bw / a
                ImageRect(0f, (bh - hh) / 2f, bw, hh)
            }
        }
    }
}

/**
 * Decide qué hace un toque sobre el lienzo. Dos dedos SIEMPRE son zoom/desplazamiento,
 * aunque esté activo el pincel o cualquier herramienta; un dedo solo pinta o dibuja
 * después de moverse más que [slop] (un toque sin mover = punto).
 */
object GestureRules {
    enum class Mode { WAITING, DRAW, TRANSFORM }

    fun next(mode: Mode, pointers: Int, movedPx: Float, slop: Float, toolDraws: Boolean): Mode = when {
        pointers >= 2 -> Mode.TRANSFORM
        mode == Mode.TRANSFORM -> Mode.TRANSFORM          // al soltar un dedo no empieza a pintar
        !toolDraws -> Mode.TRANSFORM                      // herramienta Mover: un dedo desplaza
        // toolDraws = la herramienta usa un dedo (pincel, flecha, ancla, origen…)
        mode == Mode.WAITING && movedPx > slop -> Mode.DRAW
        else -> mode
    }
}
