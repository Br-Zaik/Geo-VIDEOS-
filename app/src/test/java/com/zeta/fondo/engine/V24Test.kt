package com.zeta.fondo.engine

import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.Presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V24Test {
    @Test fun seasonalLeavesExposeFourStyles() {
        val leaves = Effects.all.first { it.id == "leaves" }
        val variant = leaves.params.first { it.key == "variant" }
        assertEquals(0f, variant.min, 0f)
        assertEquals(3f, variant.max, 0f)
    }

    @Test fun quickLibraryContainsWindLeavesWaterAndWeather() {
        val names = Presets.all.map { it.name }.toSet()
        assertTrue(names.containsAll(setOf(
            "Brisa suave", "Viento fuerte", "Viento de tormenta",
            "Hojas verdes", "Hojas secas", "Hojas de otoño", "Hojas de Navidad",
            "Olas suaves", "Olas fuertes", "Mar de tormenta",
            "Lluvia suave", "Lluvia fuerte", "Nieve suave", "Nieve fuerte", "Ventisca"
        )))
    }

    @Test fun renderParamsExposeLeafPaletteToPreviewAndExport() {
        val settings = Effects.defaults() + mapOf(
            "leaves.on" to 1f,
            "leaves.variant" to 2f,
            "leaves.copy.variant" to 3f
        )
        val p = RenderParams.from(settings, 1f, 6f)
        val style = p.studio.getValue("uLeavesStyle")
        assertEquals(2f, style[0], 0f)
        assertEquals(3f, style[1], 0f)
    }
}
