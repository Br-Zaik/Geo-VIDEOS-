package com.zeta.fondo.engine

import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.Presets
import org.junit.Assert.*
import org.junit.Test

class V21Test {
    @Test fun depthAndMeshStartDisabled() {
        val p = RenderParams.from(Effects.defaults(), 1f, 6f)
        assertEquals(0f, p.studio.getValue("uParallax")[0], 0f)
        assertEquals(0f, p.studio.getValue("uMesh")[0], 0f)
    }

    @Test fun depthAndMeshReachShaderWithPeriodicCycles() {
        val s = Effects.defaults() + mapOf(
            "parallax.on" to 1f, "parallax.amount" to .03f, "parallax.direction" to 90f,
            "parallax.cycles" to 2.4f, "mesh.on" to 1f, "mesh.int" to .006f,
            "mesh.cycles" to 3.6f, "mesh.direction" to 180f, "mesh.mask" to 4f
        )
        val p = RenderParams.from(s, 1f, 8f)
        assertEquals(.03f, p.studio.getValue("uParallax")[0], 1e-6f)
        assertEquals(2f, p.studio.getValue("uParallax")[2], 0f)
        assertEquals(.006f, p.studio.getValue("uMesh")[0], 1e-6f)
        assertEquals(4f, p.studio.getValue("uMesh")[2], 0f)
        assertEquals(4, p.effectMasks["mesh"])
    }

    @Test fun cameraPanDirectionIsStoredInRadians() {
        val p = RenderParams.from(Effects.defaults() + mapOf(
            "cam.on" to 1f, "cam.mode" to 2f, "cam.direction" to 90f, "cam.pan" to .5f
        ), 1f)
        assertEquals((Math.PI / 2.0).toFloat(), p.studio.getValue("uCameraMode")[1], 1e-4f)
    }

    @Test fun quickAnimePresetsExist() {
        val names = Presets.all.map { it.name }.toSet()
        for (name in listOf("Fondo con profundidad", "Cabello al viento", "Ropa suave", "Cielo vivo", "Lluvia nocturna anime", "Manga respirando"))
            assertTrue(name, name in names)
    }
}
