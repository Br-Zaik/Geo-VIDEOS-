package com.zeta.fondo.ui

import com.zeta.fondo.engine.RenderParams
import com.zeta.fondo.model.AnimCatalog
import com.zeta.fondo.model.EffectCatalog
import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.PreciseTime
import com.zeta.fondo.model.Wind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44 · Fase 1: imán con histéresis, tiempo exacto, joystick/viento, catálogo de Animar. */
class V44Phase1Test {
    // Pantalla de la captura: 214 px por segundo; 8 dp ≈ 21 px en un teléfono de densidad 2,625.
    private val pps = 214f
    private val enter = 21f / pps
    private val exit = enter * MagnetState.EXIT_FACTOR

    @Test fun magnetSnapsWithinEightDpAndHoldsUntilOnePointFive() {
        val m = MagnetState()
        val anchors = MagnetState.edgeCandidates(listOf(2.3f))
        // A 15 px del cabezal: se pega.
        val a = m.apply(2.3f - 15f / pps, anchors, enter, exit, true)
        assertEquals(2.3f, a.value, 1e-5f)
        assertTrue(a.newSnap)
        // A 28 px (más que la entrada, menos que 1,5×): sigue pegado y no vuelve a vibrar.
        val b = m.apply(2.3f - 28f / pps, anchors, enter, exit, true)
        assertEquals(2.3f, b.value, 1e-5f)
        assertFalse(b.newSnap)
        // A 34 px (> 1,5 × 21 = 31,5): se suelta y el bloque queda donde está el dedo.
        val c = m.apply(2.3f - 34f / pps, anchors, enter, exit, true)
        assertEquals(2.3f - 34f / pps, c.value, 1e-5f)
        assertNull(c.guide)
        // Sin pegar, a 28 px no entra (la histéresis solo actúa ya enganchado).
        val fresh = MagnetState()
        assertNull(fresh.apply(2.3f - 28f / pps, anchors, enter, exit, true).guide)
    }

    @Test fun magnetOffNeverMovesTheBlock() {
        val m = MagnetState()
        val r = m.apply(2.29f, MagnetState.edgeCandidates(listOf(2.3f)), enter, exit, false)
        assertEquals(2.29f, r.value, 0f)
        assertNull(r.guide)
        assertEquals(0f, r.delta, 0f)
    }

    @Test fun movingSnapsByEitherEdgeAndReportsDelta() {
        val m = MagnetState()
        // Bloque de 1 s; su FINAL se pega al cabezal en 3,0 s.
        val cands = MagnetState.moveCandidates(listOf(3f), 1f, 6f)
        val r = m.apply(2.0f - 10f / pps, cands, enter, exit, true)
        assertEquals(2.0f, r.value, 1e-5f)
        assertEquals(3f, r.guide!!, 1e-5f)
        assertEquals("+0,05 s", MagnetState.deltaText(r.delta))
        assertEquals("−0,08 s", MagnetState.deltaText(-.08f))
        // Nunca propone un inicio fuera del vídeo.
        assertTrue(MagnetState.moveCandidates(listOf(0f, 5.8f, 6f), 1f, 6f).all { it.first in 0f..5f })
    }

    @Test fun preciseTimeHundredthsAndLimits() {
        assertEquals(1.25f to 2f, PreciseTime.setStart(1.2501f, 2f, 6f))
        // El inicio no puede dejar el bloque fuera del vídeo.
        assertEquals(4f to 2f, PreciseTime.setStart(5.5f, 2f, 6f))
        // Duración mínima y máxima.
        assertEquals(.1f, PreciseTime.setDuration(1f, .01f, 6f).second, 1e-6f)
        assertEquals(5f, PreciseTime.setDuration(1f, 9f, 6f).second, 1e-6f)
        assertEquals(1f to 2.37f, PreciseTime.setEnd(1f, 3.37f, 6f))
        assertEquals(1.25f, PreciseTime.parse("1,25")!!, 0f)
        assertEquals(.5f, PreciseTime.parse(",5")!!, 0f)
        assertNull(PreciseTime.parse("abc"))
        assertNull(PreciseTime.parse("-1"))
        assertEquals("0,10", PreciseTime.format(.1f))
    }

    @Test fun windDefaultsKeepV43AndForceIsTheVectorLength() {
        val base = Effects.defaults() + ("rain.on" to 1f)
        val p = RenderParams.from(base, 1f, 6f)
        val v = p.motion["rain"]!!
        assertEquals(1f, kotlin.math.hypot(v[0], v[1]), 1e-5f)
        assertEquals(0f, v[0], 1e-5f) // 90° = hacia abajo, como siempre
        val strong = RenderParams.from(base + ("rain.force" to 2f), 1f, 6f).motion["rain"]!!
        assertEquals(2f, kotlin.math.hypot(strong[0], strong[1]), 1e-5f)
        // Seguir el viento global (dirección 0° = derecha, fuerza 1,5).
        val s = base + mapOf("wind.on" to 1f, "wind.direction" to 0f, "wind.force" to 1.5f, "rain.followWind" to 1f)
        val w = RenderParams.from(s, 1f, 6f).motion["rain"]!!
        assertEquals(1.5f, w[0], 1e-5f)
        assertEquals(0f, w[1], 1e-5f)
        // Con el viento global apagado, el efecto vuelve a su dirección propia.
        assertEquals(0f, RenderParams.from(s + ("wind.on" to 0f), 1f, 6f).motion["rain"]!![0], 1e-5f)
        // Un bloque puede tener su propia fuerza.
        val clipP = RenderParams.from(base, 1f, 6f, listOf(EffectClip(1, "rain", 0f, 6f, overrides = mapOf("rain.force" to 2f))))
        assertNotNull(clipP.clipDiffs[1L])
    }

    @Test fun joystickMapsPositionToAngleAndForce() {
        val (a, f) = Wind.fromStick(0f, 1f)
        assertEquals(90f, a, 1e-4f)
        assertEquals(Wind.MAX_FORCE, f, 1e-4f)
        val (a2, f2) = Wind.fromStick(.5f, .02f) // casi a la derecha: imán de 15°
        assertEquals(0f, a2, 1e-4f)
        assertEquals(1f, f2, .05f)
        assertEquals(Wind.MIN_FORCE, Wind.fromStick(0f, 0f).second, 1e-6f)
        val (x, y) = Wind.toStick(180f, 1f)
        assertEquals(-.5f, x, 1e-5f); assertEquals(0f, y, 1e-5f)
    }

    @Test fun animCatalogUsesRealEffectsAndRealThumbnails() {
        val ids = Effects.all.map { it.id }.toSet()
        for (e in AnimCatalog.all()) {
            if (e.kind == AnimCatalog.Kind.STYLE) {
                assertTrue("${e.key}: ${e.effectId}", e.effectId in ids)
                val variants = EffectCatalog.variants(e.effectId)
                if (variants.isNotEmpty()) assertTrue(e.key, e.variant < variants.size)
            }
            assertTrue("miniatura ${e.thumb}", File("src/main/assets/thumbs/${e.thumb}.webp").exists() || e.thumb == "flow")
        }
        assertEquals(6, AnimCatalog.categories.size)
        assertTrue(AnimCatalog.isActive(AnimCatalog.find("cam6")!!, mapOf("cam.on" to 1f, "cam.mode" to 6f)))
        assertFalse(AnimCatalog.isActive(AnimCatalog.find("cam6")!!, mapOf("cam.on" to 0f, "cam.mode" to 6f)))
    }

    @Test fun oldClipsAreVisibleByDefault() {
        assertFalse(EffectClip(1, "rain", 0f, 6f).hidden)
    }
}
