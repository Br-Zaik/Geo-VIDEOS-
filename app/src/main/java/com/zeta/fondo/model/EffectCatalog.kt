package com.zeta.fondo.model

/**
 * v38 · Catálogo de efectos por categorías claras (fila de herramientas de Efectos) y
 * secciones del editor de cada efecto. Sin dependencias de Android: se prueba en JVM.
 */
object EffectCatalog {

    /** Herramientas que viven en Animar (no aparecen en el catálogo de Efectos). */
    val animateIds = setOf("flow", "zonemotion", "natural", "parallax", "mesh", "cam")

    val categories = listOf("Populares", "Activos", "Cielo y mar", "Fuego", "Agua", "Clima", "Luz", "Energía",
        "Anime", "Manga", "Acabado", "Escenas", "Todos")

    /** Lo más usado: aparece primero y es lo que ve un principiante. */
    val popular = listOf("skydrift", "sunlight", "finish", "smoke", "flame", "rain", "snow", "leaves", "sakura", "fog", "clouds3", "water",
        "blast", "energy", "bolt", "rays", "sparkle", "embers", "slash", "eyes", "gust", "shock", "reveal")

    // v44.15: cada efecto está en UNA sola categoría (Populares, Activos y Todos aparte).
    private val groups: Map<String, List<String>> = mapOf(
        "Cielo y mar" to listOf("skydrift", "seawaves", "sunlight", "sunpath", "stars", "clouds3", "cloudshadow",
            "sky", "aurora", "birds", "shore", "drift"),
        "Fuego" to listOf("flame", "steam", "smoke", "embers", "ash", "blast", "heat"),
        "Agua" to listOf("water", "raindrops", "caustics", "waterfall", "reflect", "glassrain", "bubbles", "ripple", "glint"),
        "Clima" to listOf("rain", "snow", "sakura", "leaves", "fog", "sand", "gust", "tornado",
            "dust", "sway"),
        "Luz" to listOf("rays", "lensflare", "deepglow", "dapple", "flicker", "glowup", "citylights", "sparkle", "bokeh",
            "lightleak", "flies", "comet"),
        "Energía" to listOf("energy", "electric", "shock", "blackaura", "runes", "pulse", "shards", "dispersion", "bolt"),
        "Anime" to listOf("slash", "blood", "reveal", "impact", "flash", "speed", "focus", "elements",
            "appear", "eyes", "blink", "cloth"),
        "Manga" to listOf("tone", "grain", "ink", "hatch", "inkdrip", "inkweather", "symbols", "dramatic", "style"),
        "Acabado" to listOf("cine", "finish", "gradmap", "atmos", "lightdir", "rimlight", "focusblur", "radialblur", "glitch", "vhs", "filmburn")
    )

    /** Todas las categorías con sus efectos (para comprobar que no se repiten). */
    val groupIds: Map<String, List<String>> get() = groups

    /** Ids duplicados de versiones antiguas: solo se muestran si el proyecto ya los usa. */
    private val legacy = Effects.legacyDuplicates

    fun idsFor(category: String, allIds: List<String>, isOn: (String) -> Boolean): List<String> {
        val usable = allIds.filter { it !in animateIds }
        fun visible(id: String) = id !in legacy || isOn(id)
        return when (category) {
            "Populares" -> popular.filter { it in usable || it == "elements" } +
                usable.filter { it !in popular && isOn(it) }
            "Activos" -> usable.filter { isOn(it) }
            "Todos" -> usable.filter { visible(it) } + "elements"
            else -> (groups[category] ?: emptyList()).filter { (it in usable || it == "elements") && visible(it) }
        }.distinct()
    }

    /** Categoría a la que se vuelve si el nombre guardado ya no existe (p. ej. "Útiles"). */
    fun normalize(category: String): String = when (category) {
        in categories -> category
        "Útiles" -> "Populares"
        "Agua y cielo" -> "Agua"
        "Manhwa" -> "Anime"
        else -> "Populares"
    }

    fun groupOf(id: String): List<String> = groups.filterValues { id in it }.keys.toList()

    /** Estilos (variantes) que ofrece cada efecto. */
    fun variants(id: String): List<String> = when (id) {
        "zonemotion" -> listOf("Balanceo", "Ondulación", "Respiración")
        "sky" -> listOf("Día", "Atardecer", "Noche", "Tormenta")
        "water" -> listOf("Ondas", "Corriente", "Reflejos")
        "cam" -> listOf("Acercar", "Alejar", "Desplazar", "Girar", "Temblar", "Órbita 3D", "Latido", "Flotar", "Balanceo", "Libre (fotogramas)")
        "mesh" -> listOf("Ondulación", "Respirar", "Tela")
        "natural" -> listOf("Árbol", "Bandera", "Pasto", "Cortina", "Cabello", "Ropa")
        "gust" -> listOf("Brisa", "Viento", "Fuerte", "Tormenta")
        "leaves" -> listOf("Verdes", "Secas", "Otoño", "Navidad")
        "shore" -> listOf("Suaves", "Medias", "Fuertes", "Tormenta")
        "rain" -> listOf("Suave", "Normal", "Fuerte", "Tormenta")
        "snow" -> listOf("Suave", "Normal", "Fuerte", "Ventisca")
        "dispersion" -> listOf("Fragmentos", "Pétalos", "Polvo")
        "tornado" -> listOf("Remolino", "Tornado")
        "appear" -> listOf("Humo", "Ráfaga", "Destello", "Fundido")
        "clouds3" -> listOf("Día", "Atardecer", "Noche")
        "bokeh" -> listOf("Dorado", "Romántico", "Azul", "Mágico")
        "lightleak" -> listOf("Cálida", "Rosa", "Azul", "Violeta")
        "rays" -> listOf("Suaves", "Contraluz", "Atardecer", "Ventana", "Volumen", "Luz de luna")
        "sunpath" -> listOf("Dorado", "Atardecer", "Luna", "Destellos")
        "energy" -> listOf("Azul", "Violeta", "Dorado", "Rojo")
        "flame" -> listOf("Fuego", "Azul", "Verde", "Violeta")
        "parallax" -> listOf("Planos", "Órbita 2.5D", "Acercamiento")
        // v44: efectos nuevos
        "sand" -> listOf("Arena", "Polvo", "Desierto rojo")
        "birds" -> listOf("Oscuros", "Gaviotas")
        "aurora" -> listOf("Verde", "Violeta", "Mixta")
        "citylights" -> listOf("Ventanas", "Neón", "Tráfico")
        "lensflare" -> listOf("Cálido", "Frío", "Anamórfico")
        "sunlight" -> listOf("Atardecer", "Día", "Luna llena")
        "gradmap" -> listOf("Gris azulado (manga)", "Noche azul", "Rosa y azul", "Atardecer", "Rojo intenso", "Morado")
        "atmos" -> listOf("De tu imagen", "Blanca", "Azul noche", "Cálida")
        "lightdir", "rimlight" -> listOf("Rojo neón", "Cálida", "Fría", "Morada", "Blanca", "Verde")
        "glitch" -> listOf("RGB", "Bloques", "RGB y bloques")
        "radialblur" -> listOf("Pulsos", "Constante")
        "focusblur" -> listOf("Franja", "Círculo")
        "deepglow" -> listOf("Natural", "Cálido", "Frío", "Rosa")
        "filmburn" -> listOf("Naranja", "Rojo", "Azul")
        "raindrops" -> listOf("Gotas cercanas", "Goteo desde arriba", "Salpicaduras")
        "cine" -> listOf("Natural", "Kodak cálido", "Fuji suave", "Teal & Orange", "Noche azul", "B/N cine")
        "finish" -> listOf("Natural+", "Atardecer cálido", "Noche azul", "Anime pastel", "Cine")
        "smoke" -> listOf("Gris", "Negro", "Blanco", "Violeta", "Cálido")
        "blast" -> listOf("Fuego", "Azul", "Violeta", "Oscura")
        "blood" -> listOf("Rojo", "Tinta negra", "Azul manga")
        "slash" -> listOf("Azul", "Blanco", "Dorado", "Rojo")
        "reveal" -> listOf("Sombra", "Niebla", "Oscuro")
        // v44.11: efectos nuevos para manga / manhwa
        "symbols" -> listOf("Gota de sudor", "Vena de enojo", "¡Exclamación!", "Mareo")
        "glowup" -> listOf("Dorado", "Rosa", "Azul")
        "inkweather" -> listOf("Lluvia de tinta", "Nieve", "Lluvia realista")
        "dapple" -> listOf("Sol cálido", "Luz blanca", "Luna")
        "caustics" -> listOf("Mar", "Piscina", "Dorado")
        "flicker" -> listOf("Lámpara que falla", "Hoguera", "Neón")
        else -> emptyList()
    }

    /** Efectos cuyo estilo aplica un preajuste completo (no solo cambia la variante). */
    val styledPresets = setOf("gust", "leaves", "shore", "rain", "snow")

    /** Efectos que no usan zona pintada propia. */
    private val noZone = setOf("style", "cine", "elements", "appear", "impact", "parallax", "finish", "rimlight",
        "glitch", "radialblur", "focusblur", "vhs", "filmburn")

    /**
     * Secciones del editor de un efecto, en orden: siempre «Ajustes»; «Estilo» si tiene
     * variantes o formas; «Color» si se puede teñir; «Tiempo» y «Zona» si aplican.
     */
    fun sections(id: String, colorable: Boolean, hasShapes: Boolean): List<String> = buildList {
        add("Ajustes")
        if (variants(id).isNotEmpty() || hasShapes) add("Estilo")
        if (colorable || id == "shore" || EffectGrade.has(id)) add("Color")
        if (id !in Effects.globalSettings && id != "elements") add("Tiempo")
        if (id !in noZone) add("Zona")
    }
}
