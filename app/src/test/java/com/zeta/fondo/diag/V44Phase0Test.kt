package com.zeta.fondo.diag

import com.zeta.fondo.engine.FrameUniforms
import com.zeta.fondo.engine.RenderParams
import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.EffectTimeline
import com.zeta.fondo.model.Effects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v44 · Fase 0 (diagnóstico sin glFinish) y base de Ocultar/Solo. */
class V44Phase0Test {

    @Test fun frameStatsMeasureRealIntervalsAndNeverInventGpu() {
        val s = FrameStats()
        var t = 1_000_000_000L
        repeat(61) { s.frame(t, 2f, 1f, 33.3f); t += 33_333_333L }
        val snap = s.snapshot()
        assertEquals(30f, snap.fps!!, .2f)
        assertEquals(60L, snap.frames)
        assertNull("sin temporizador de GPU no hay valor", snap.gpuMs)
        assertNull(snap.totalGpuMs)
        s.gpu(7.5f); s.gpu(8.5f)
        assertEquals(8f, s.snapshot().gpuMs!!, .001f)
        // Una pausa larga no cuenta como cuadro lento.
        s.frame(t + 5_000_000_000L, 2f, 1f, 33.3f)
        assertEquals(60L, s.snapshot().frames)
        s.reset()
        assertNull(s.snapshot().fps)
    }

    @Test fun frameStatsP95AndSlowFrames() {
        val s = FrameStats()
        var t = 1L
        s.frame(t, 1f, 1f, 16.7f)
        repeat(95) { t += 16_700_000L; s.frame(t, 1f, 1f, 16.7f) }
        repeat(5) { t += 60_000_000L; s.frame(t, 1f, .5f, 16.7f) }
        val snap = s.snapshot()
        assertEquals(5L, snap.slowFrames)
        assertTrue(snap.p95Ms!! >= 16.6f)
        assertTrue(snap.scale!! < 1f)
    }

    @Test fun adaptiveScaleDropsFastAndRisesSlowlyWithoutGlFinish() {
        val a = AdaptiveScale(.45f, 1f)
        // Cuadros al doble del objetivo: baja.
        repeat(10) { a.onInterval(66f, 33f, a.value) }
        val low = a.value
        assertTrue("debe bajar: $low", low < .8f)
        assertTrue(low >= .45f)
        // Cuadros a tiempo: sube, pero poco a poco.
        repeat(19) { a.onInterval(33f, 33f, a.value) }
        assertEquals(low, a.value, 1e-6f)
        a.onInterval(33f, 33f, a.value)
        assertEquals(low + .05f, a.value, 1e-4f)
        // Pausas (> 500 ms) se ignoran.
        val before = a.value
        a.onInterval(900f, 33f, a.value)
        assertEquals(before, a.value, 0f)
        a.onGpuTime(48f, 24f, before)
        assertTrue(a.value < before)
    }

    @Test fun reportSaysNotAvailableInsteadOfInventing() {
        val s = FrameStats()
        var t = 1L
        repeat(31) { s.frame(t, 3f, .8f, 33.3f); t += 40_000_000L }
        val r = Benchmark.StageResult(3, 60, 1200, s.snapshot(), "Normal", "Leve", 900, true)
        val text = Benchmark.report(listOf("Teléfono" to "Prueba"), listOf(r))
        assertTrue(text.contains("3 efectos (rain, snow, fog)"))
        assertTrue(text.contains("GPU por cuadro: No disponible"))
        assertTrue(text.contains("FPS medios: 25.0"))
        assertTrue(text.contains("Normal → Leve"))
        assertEquals("No disponible", Benchmark.thermalName(null))
        assertEquals(listOf("rain"), Benchmark.effectsFor(1))
        assertEquals(6, Benchmark.effectsFor(6).size)
        assertTrue(Benchmark.effects.all { id -> Effects.all.any { it.id == id } })
    }

    @Test fun emptyClipListMeansEffectOff_nullMeansLegacyOn() {
        assertEquals(1f, EffectTimeline.sample(null, .5f, 6f).gate, 0f)
        assertEquals(0f, EffectTimeline.sample(emptyList(), .5f, 6f).gate, 0f)
    }

    /** Ocultar un bloque debe apagar el efecto de verdad en TODOS los efectos con bloques. */
    @Test fun hiddenClipTurnsEveryEffectOff() {
        val ungated = ArrayList<String>()
        for (e in Effects.all) {
            if (!e.toggleable || e.id == "style") continue
            val s = Effects.defaults() + ("${e.id}.on" to 1f)
            val visible = RenderParams.from(s, 1f, 6f, listOf(EffectClip(1, e.id, 0f, 6f)))
            val hidden = RenderParams.from(s, 1f, 6f, listOf(EffectClip(1, e.id, 0f, 6f, hidden = true)))
            assertTrue(hidden.timeline[e.id]!!.isEmpty())
            val a = FrameUniforms.compute(visible, .5f)
            val b = FrameUniforms.compute(hidden, .5f)
            val changed = b.any { (k, v) -> val base = a[k] ?: visible.floatUniforms[k]; base != null && !base.contentEquals(v) }
            if (!changed) ungated += e.id
        }
        assertTrue("Efectos que no se apagan al ocultar: $ungated", ungated.isEmpty())
    }

    @Test fun soloMutesEffectsWithoutBlocks() {
        val s = Effects.defaults() + ("rain.on" to 1f) + ("snow.on" to 1f)
        val clips = listOf(EffectClip(1, "rain", 0f, 6f))
        val normal = RenderParams.from(s, 1f, 6f, clips)
        assertNull("sin Solo, la nieve sin bloque sigue como siempre", normal.timeline["snow"])
        val solo = RenderParams.from(s, 1f, 6f, clips, muteUnclipped = true)
        assertNotNull(solo.timeline["snow"])
        assertTrue(solo.timeline["snow"]!!.isEmpty())
        assertEquals(1, solo.timeline["rain"]!!.size)
        assertEquals(0f, FrameUniforms.compute(solo, .5f)["uSnow"]!![0], 0f)
        assertFalse(FrameUniforms.compute(solo, .5f)["uRain"]!![0] == 0f)
    }
}
