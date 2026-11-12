package com.zeta.fondo.engine

import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.Presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V22Test {
    @Test fun naturalMotionDefinitionIsComplete() {
        val e = Effects.all.first { it.id == "natural" }
        val keys = e.params.map { it.key }.toSet()
        assertTrue(keys.containsAll(setOf("int", "direction", "cycles", "flex", "variation", "gust", "variant", "anchor")))
        assertEquals(5f, e.params.first { it.key == "variant" }.max, 0f)
        assertEquals(4f, e.params.first { it.key == "anchor" }.max, 0f)
    }

    @Test fun naturalPresetsCoverBackgroundMaterials() {
        val names = Presets.all.map { it.name }.toSet()
        assertTrue(names.containsAll(setOf(
            "Árbol natural", "Bandera realista", "Pasto al viento",
            "Cortina suave", "Cabello natural", "Ropa al viento"
        )))
        Presets.all.filter { it.name in names }.filter { it.values.containsKey("natural.on") }.forEach { p ->
            assertEquals(1f, p.values["natural.on"] ?: 0f, 0f)
            assertEquals(2f, p.values["natural.mask"] ?: -1f, 0f)
        }
    }

    @Test fun renderParamsExposeNaturalUniformsAndMask() {
        val settings = Effects.defaults() + mapOf(
            "natural.on" to 1f,
            "natural.int" to 0.01f,
            "natural.direction" to 90f,
            "natural.cycles" to 3f,
            "natural.variant" to 1f,
            "natural.flex" to 0.8f,
            "natural.variation" to 0.6f,
            "natural.gust" to 0.4f,
            "natural.anchor" to 1f,
            "natural.mask" to 4f
        )
        val p = RenderParams.from(settings, 1f, 6f)
        val n = p.studio.getValue("uNatural")
        val more = p.studio.getValue("uNaturalMore")
        assertEquals(0.01f, n[0], 1e-6f)
        assertEquals(3f, n[2], 0f)
        assertEquals(1f, n[3], 0f)
        assertEquals(0.8f, more[0], 0f)
        assertEquals(1f, more[3], 0f)
        assertEquals(4, p.effectMasks["natural"])
    }
}
