package com.zeta.fondo.model

/**
 * v44 · Valores con los que un efecto NACE la primera vez que se activa en un proyecto.
 * Solo se aplican si el proyecto no tiene ya esa clave: los proyectos v43 no cambian.
 * Código puro (pruebas JVM).
 */
object EffectDefaults {
    /** Van detrás del personaje si ya hay uno pintado en Proteger. */
    val behindByDefault = setOf("fog", "clouds3", "aurora", "stars", "birds", "blast", "cloudshadow")

    /**
     * v44.25 · [hasCharacter] = Proteger ya tiene un personaje pintado. Sin él, todos los efectos
     * nacen «Delante» (antes nacían «Detrás» y eso hacía crear una máscara automática que, en un
     * paisaje, tapaba el efecto entero).
     */
    fun firstActivation(id: String, s: Map<String, Float>, hasCharacter: Boolean = false): Map<String, Float> = buildMap {
        if (hasCharacter && id in behindByDefault && !s.containsKey("$id.behind")) put("$id.behind", 1f)
        // La explosión ya no nace en el centro (sobre la cara): abajo a la derecha.
        if (id == "blast" && !s.containsKey("blast.originX")) { put("blast.originX", .74f); put("blast.originY", .70f) }
        // Efectos nuevos que nacen en un punto concreto (pájaros en el cielo, destello arriba…).
        Wind.defaultOrigin[id]?.let { (x, y) ->
            if (!s.containsKey("$id.originX")) { put("$id.originX", x); put("$id.originY", y) }
        }
    }
}
