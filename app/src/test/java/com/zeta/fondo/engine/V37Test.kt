package com.zeta.fondo.engine

import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.EventMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V37Test {
    @Test fun onceModeRunsStartToEndInsideItsClip() {
        assertEquals(0f, EventMode.phase(EventMode.ONCE, 0f, 2f, 2f), 0f)
        assertEquals(.5f, EventMode.phase(EventMode.ONCE, 1f, 2f, 2f), 1e-5f)
        // Al final del bloque se queda en el último instante: no vuelve a empezar.
        assertTrue(EventMode.phase(EventMode.ONCE, 5f, 2f, 2f) > .99f)
    }

    @Test fun repeatModeCyclesEveryInterval() {
        assertEquals(.25f, EventMode.phase(EventMode.REPEAT, .5f, 6f, 2f), 1e-5f)
        assertEquals(.25f, EventMode.phase(EventMode.REPEAT, 2.5f, 6f, 2f), 1e-5f)
        assertEquals(-1f, EventMode.phase(EventMode.LOOP, 1f, 6f, 2f), 0f)
    }

    @Test fun eventUniformsDefaultToLoopClock() {
        val p = RenderParams.from(Effects.defaults(), 1f, 6f)
        assertArrayEquals(floatArrayOf(-1f, -1f, -1f, -1f), p.studio.getValue("uEventPhase"), 0f)
        assertArrayEquals(floatArrayOf(-1f, -1f, -1f, -1f), p.studio.getValue("uEventPhase2"), 0f)
        val q = RenderParams.from(Effects.defaults() + mapOf("blast.mode" to 1f, "blast.every" to 3f), 1f)
        assertArrayEquals(floatArrayOf(1f, 3f), q.events.getValue("blast"), 0f)
    }

    @Test fun ambientEffectsAreNotEvents() {
        for (id in listOf("smoke", "flame", "water", "clouds3", "fog", "rain", "natural", "gust"))
            assertTrue(id, id !in Effects.eventEffects)
        for (id in Effects.eventEffects) assertTrue(id, Effects.all.any { it.id == id })
        // El viento ya no nace como una ráfaga corta: cubre todo el vídeo.
        assertEquals(1f, Effects.default("gust.dur"), 0f)
    }

    @Test fun newControlsReachTheShader() {
        val p = RenderParams.from(Effects.defaults() + mapOf("smoke.fill" to 1f, "flame.sparks" to .7f,
            "flame.smoke" to .4f, "blast.push" to .6f), 1f)
        assertEquals(1f, p.studio.getValue("uSmokeShape")[3], 0f)
        assertArrayEquals(floatArrayOf(.7f, .4f, 0f, 0f), p.studio.getValue("uFlameExtra"), 0f)
        assertEquals(.6f, p.studio.getValue("uBlastShape")[3], 0f)
    }
}
