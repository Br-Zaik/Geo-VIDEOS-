package com.zeta.fondo.model

/**
 * v44 · Catálogo de Animar, con el mismo flujo que Efectos: categorías, miniaturas que
 * enseñan lo que hace cada animación, tocar = probar sobre tu imagen (sin confirmar),
 * tocar otra vez = Ajustar, ✓ confirma y ✕ descarta. Código puro (pruebas JVM).
 */
object AnimCatalog {
    enum class Kind { STYLE, TOOL }

    /**
     * [effectId] = animación del motor (cam, parallax, natural, mesh, zonemotion, water, sky,
     * blink, eyes, cloth). [variant] = estilo dentro del efecto (modo de cámara, preset…).
     * [thumb] = miniatura (assets/thumbs/<thumb>.webp, renderizada con el shader de la app).
     * [tool] = herramienta de dibujo para las entradas de tipo TOOL (Ruta, Ancla, Congelar).
     */
    data class Entry(val key: String, val title: String, val kind: Kind, val effectId: String = "",
                     val variant: Int = 0, val thumb: String = effectId, val tool: String = "", val needsZone: Boolean = false)

    val categories = listOf("Cámara", "Profundidad 2.5D", "Viento y tela", "Agua y cielo", "Rutas", "Personaje")

    private fun cam(mode: Int, title: String) = Entry("cam$mode", title, Kind.STYLE, "cam", mode, thumb = "anim_cam$mode")

    val entries: Map<String, List<Entry>> = mapOf(
        "Cámara" to listOf(
            cam(0, "Acercar"), cam(1, "Alejar"), cam(2, "Desplazar"), cam(3, "Girar"),
            cam(4, "Temblar"), cam(5, "Órbita 3D"), cam(6, "Latido"), cam(7, "Flotar"), cam(8, "Balanceo"),
            Entry("cam9", "Cámara libre (◆)", Kind.STYLE, "cam", 9, thumb = "anim_cam0")),
        "Profundidad 2.5D" to listOf(
            Entry("parallax0", "Planos", Kind.STYLE, "parallax", 0, needsZone = true),
            Entry("parallax1", "Órbita 2.5D", Kind.STYLE, "parallax", 1, needsZone = true),
            Entry("parallax2", "Acercamiento", Kind.STYLE, "parallax", 2, needsZone = true)),
        "Viento y tela" to listOf(
            Entry("natural4", "Cabello", Kind.STYLE, "natural", 4, needsZone = true),
            Entry("natural5", "Ropa", Kind.STYLE, "natural", 5, needsZone = true),
            Entry("natural1", "Bandera", Kind.STYLE, "natural", 1, needsZone = true),
            Entry("natural3", "Cortina", Kind.STYLE, "natural", 3, needsZone = true),
            Entry("natural0", "Árbol", Kind.STYLE, "natural", 0, needsZone = true),
            Entry("natural2", "Pasto", Kind.STYLE, "natural", 2, needsZone = true),
            Entry("mesh2", "Tela ondulante", Kind.STYLE, "mesh", 2, needsZone = true),
            Entry("mesh0", "Ondulación", Kind.STYLE, "mesh", 0, needsZone = true)),
        "Agua y cielo" to listOf(
            Entry("water0", "Agua: ondas", Kind.STYLE, "water", 0, needsZone = true),
            Entry("water1", "Agua: corriente", Kind.STYLE, "water", 1, needsZone = true),
            Entry("water2", "Agua: reflejos", Kind.STYLE, "water", 2, needsZone = true),
            Entry("sky0", "Cielo de día", Kind.STYLE, "sky", 0, needsZone = true),
            Entry("sky1", "Atardecer", Kind.STYLE, "sky", 1, needsZone = true),
            Entry("sky2", "Cielo de noche", Kind.STYLE, "sky", 2, needsZone = true)),
        "Rutas" to listOf(
            Entry("tool_arrow", "Ruta (flechas)", Kind.TOOL, thumb = "flow", tool = "ARROW"),
            Entry("tool_anchor", "Ancla", Kind.TOOL, thumb = "flow", tool = "ANCHOR"),
            Entry("tool_freeze", "Congelar", Kind.TOOL, thumb = "flow", tool = "FREEZE"),
            Entry("zonemotion0", "Balanceo de zona", Kind.STYLE, "zonemotion", 0, needsZone = true),
            Entry("zonemotion1", "Ondulación de zona", Kind.STYLE, "zonemotion", 1, needsZone = true)),
        "Personaje" to listOf(
            Entry("zonemotion2", "Respiración", Kind.STYLE, "zonemotion", 2, needsZone = true),
            Entry("eyes0", "Ojos brillantes", Kind.STYLE, "eyes", 0, needsZone = true),
            Entry("mesh1", "Respirar (tela)", Kind.STYLE, "mesh", 1, needsZone = true))
    )

    fun all(): List<Entry> = categories.flatMap { entries[it].orEmpty() }
    fun find(key: String): Entry? = all().firstOrNull { it.key == key }

    /** ¿Esta entrada es la que está activa con los ajustes actuales? */
    fun isActive(e: Entry, s: Map<String, Float>): Boolean {
        if (e.kind != Kind.STYLE) return false
        if ((s["${e.effectId}.on"] ?: 0f) < .5f) return false
        val v = when (e.effectId) {
            "cam" -> s["cam.mode"]
            else -> s["${e.effectId}.variant"]
        }?.toInt() ?: 0
        return v == e.variant || e.effectId in setOf("blink", "eyes", "cloth")
    }
}
