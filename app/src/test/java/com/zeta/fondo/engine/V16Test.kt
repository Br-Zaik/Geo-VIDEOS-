package com.zeta.fondo.engine

import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.ExportSettings
import org.junit.Assert.*
import org.junit.Test

class V16Test {
    @Test fun legacyCropPositionIsPreserved() {
        val s = ExportSettings(framing = 1, cropPos = 0.25f)
        val c = ExportPlanner.cropForSettings(1920, 1080, s)
        val sx = (9f / 16f) / (1920f / 1080f)
        assertEquals(sx, c[2], 1e-4f)
        assertEquals((1f - sx) * 0.25f, c[0], 1e-4f)
    }

    @Test fun freeCropStaysInsideImage() {
        val s = ExportSettings(framing = 1, cropCx = 0.99f, cropCy = 0.01f, cropZoom = 2f)
        val c = ExportPlanner.cropForSettings(1920, 1080, s)
        assertTrue(c[0] >= 0f && c[0] + c[2] <= 1.0001f)
        assertTrue(c[1] >= 0f && c[1] + c[3] <= 1.0001f)
        assertEquals(0.5f, c[3], 1e-4f)
    }

    @Test fun exactSizeForVertical1080() {
        val (w, h) = ExportPlanner.targetSize(1920, 1080, ExportSettings(framing = 1, res = 3))
        assertEquals(1080, w)
        assertEquals(1920, h)
    }

    @Test fun ambientLoopHasExactLengthAndSound() {
        val frames = 8000 * 2
        val mix = Ambient.mix(mapOf("wind" to 0.7f, "thunder" to 0.5f), 8000, frames, 2)
        assertEquals(2, mix.size)
        mix.forEach { (data, _) ->
            assertEquals(frames * 2, data.size)
            assertTrue(data.any { it.toInt() != 0 })
        }
    }

    @Test fun newEffectsStartOffAndReachTheShader() {
        val p = RenderParams.from(Effects.defaults(), 1f, 6f)
        for (u in listOf("uBolt", "uGust", "uTornado", "uAppear", "uClouds3", "uShore", "uSunpath", "uImpact"))
            assertEquals(0f, p.studio.getValue(u)[0], 0f)
        val on = RenderParams.from(Effects.defaults() + mapOf("gust.on" to 1f, "bolt.on" to 1f), 1f, 8f)
        assertTrue(on.studio.getValue("uGust")[0] > 0f)
        assertEquals(8f, on.studio.getValue("uLoopInfo")[0], 0f)
        assertNotNull(on.motion["gust"])
    }
}
