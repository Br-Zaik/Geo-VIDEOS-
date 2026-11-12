package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.EffectDefaults
import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.EffectTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44 · Fase 2: efectos nuevos, dos instancias a la vez, viento global y valores iniciales. */
class V44Phase2Test {
    private fun frag(): String = listOf(File("src/main/assets/shaders/fondo.frag"), File("app/src/main/assets/shaders/fondo.frag"))
        .first { it.isFile }.readText()

    @Test fun newEffectsExistWithShaderBlocksAndUniforms() {
        val ids = listOf("sand", "cloudshadow", "glassrain", "birds", "aurora", "citylights", "lensflare")
        val declared = ShaderVariants.declaredFeatures(frag())
        for (id in ids) {
            assertTrue(id, Effects.all.any { it.id == id })
            assertTrue("FX_$id", id in declared)
            val p = RenderParams.from(Effects.defaults() + ("$id.on" to 1f), 1f, 6f)
            val n = RenderParams.uniformName(id)
            assertTrue("uFx$n encendido", p.floatUniforms["uFx$n"]!![0] > 0f)
            assertTrue(id in p.features)
            val off = RenderParams.from(Effects.defaults(), 1f, 6f)
            assertEquals("uFx$n apagado", 0f, off.floatUniforms["uFx$n"]!![0], 0f)
        }
    }

    @Test fun layoutOffsetsMatchMesa() {
        // Valores leídos del driver OpenGL ES (Mesa) para el shader completo de esta versión.
        val l = Std140Layout.parse(frag())
        val expected = mapOf("uImpactMore" to 3424, "uFxSand" to 3440, "uMaskSand" to 3488, "uFxLensflare" to 3792,
            "uMaskLensflare" to 3840, "uRainMore" to 3856, "uStarsMore" to 3872, "uFxBlast2" to 3888,
            "uMaskSmoke2" to 4256, "uEventPhaseB" to 4272, "uSnowMore" to 4288, "uWind" to 4336, "uCamManual" to 4352, "uDepthInfo" to 4368,
            // v44.11 · leídos de un driver real (ANGLE/WebGL2) para el shader completo
            "uFxSymbols" to 4384, "uMaskSymbols" to 4432, "uFxGlowup" to 4448, "uMaskInkweather" to 4544,
            "uFxDapple" to 4560, "uMaskFlicker" to 4752,
            "uFxSkydrift" to 4768, "uMaskSkydrift" to 4816, "uFxSunlight" to 4880, "uFinishMore" to 4960,
            "uFxGradmap" to 4976, "uMaskAtmos" to 5056, "uFxLightdir" to 5072, "uMotionRimlight" to 5168,
            // v44.15 · 7 efectos nuevos (ANGLE/WebGL2)
            "uFxGlitch" to 5184, "uFxDeepglow" to 5264, "uMaskDeepglow" to 5296, "uFxVhs" to 5312,
            "uMaskRaindrops" to 5392, "uMotionFocusblur" to 5440, "uGrade" to 5456)
        for ((name, off) in expected) assertEquals(name, off, l.members[name]!![0])
        assertEquals(6432, l.size)
    }

    @Test fun twoOverlappingExplosionsAreTwoInstances() {
        val s = Effects.defaults() + mapOf("blast.on" to 1f, "blast.originX" to .25f, "blast.originY" to .75f)
        val clips = listOf(EffectClip(1, "blast", 0f, 6f),
            EffectClip(2, "blast", 0f, 6f, overrides = mapOf("blast.originX" to .8f, "blast.originY" to .3f)))
        val p = RenderParams.from(s, 1f, 6f, clips)
        val f = FrameUniforms.compute(p, .5f)
        val main = f["uFxBlast"] ?: p.floatUniforms["uFxBlast"]!!
        val second = f["uFxBlast2"]!!
        assertTrue(second[0] > 0f)
        // Cada una con su visibilidad propia (no la suma de las dos).
        assertEquals(p.floatUniforms["uFxBlast"]!![0], main[0], 1e-5f)
        val m1 = f["uMotionBlast"] ?: p.floatUniforms["uMotionBlast"]!!
        val m2 = f["uMotionBlast2"]!!
        assertTrue("posiciones distintas", m1[2] != m2[2] || m1[3] != m2[3])
        assertEquals(.8f, m2[2], 1e-5f); assertEquals(.3f, m2[3], 1e-5f)
        // Sin solape: la segunda instancia no se toca (queda apagada).
        val single = RenderParams.from(s, 1f, 6f, listOf(clips[0]))
        assertNull(FrameUniforms.compute(single, .5f)["uFxBlast2"])
        assertEquals(0f, single.floatUniforms["uFxBlast2"]!![0], 0f)
    }

    @Test fun particlesUseTheFreeCopySlotForTheSecondBlock() {
        val s = Effects.defaults() + ("leaves.on" to 1f)
        val clips = listOf(EffectClip(1, "leaves", 0f, 6f),
            EffectClip(2, "leaves", 1f, 4f, overrides = mapOf("leaves.direction" to 200f)))
        val p = RenderParams.from(s, 1f, 6f, clips)
        val f = FrameUniforms.compute(p, .5f)
        assertTrue(f["uFxLeavesCopy"]!![0] > 0f)
        // Si el usuario ya usa la Copia, se respeta (no se sobrescribe).
        val withCopy = RenderParams.from(s + ("leaves.copy.on" to 1f), 1f, 6f, clips)
        assertNull(FrameUniforms.compute(withCopy, .5f)["uFxLeavesCopy"])
        assertEquals(2, EffectTimeline.active(clips, .5f, 6f).size)
        assertEquals(1, EffectTimeline.active(clips, .05f, 6f).size)
    }

    @Test fun firstActivationDefaultsNeverTouchExistingProjects() {
        val fresh = EffectDefaults.firstActivation("blast", Effects.defaults())
        assertEquals(.74f, fresh["blast.originX"]!!, 0f)
        // v44.25: sin personaje pintado en Proteger nace «Delante»; con personaje, «Detrás».
        assertFalse(fresh.containsKey("blast.behind"))
        assertEquals(1f, EffectDefaults.firstActivation("blast", Effects.defaults(), hasCharacter = true)["blast.behind"]!!, 0f)
        val old = EffectDefaults.firstActivation("blast", Effects.defaults() + mapOf("blast.originX" to .5f, "blast.behind" to 0f))
        assertFalse(old.containsKey("blast.originX"))
        assertFalse(old.containsKey("blast.behind"))
        assertTrue(EffectDefaults.firstActivation("rain", Effects.defaults()).isEmpty())
        assertEquals(.2f, EffectDefaults.firstActivation("birds", Effects.defaults())["birds.originY"]!!, 0f)
    }

    @Test fun globalWindOnlyActsWhenEnabled() {
        val s = Effects.defaults() + mapOf("wind.gust" to .8f, "wind.turb" to .5f, "wind.direction" to 90f)
        val off = RenderParams.from(s, 1f, 6f).floatUniforms["uWind"]!!
        assertEquals(0f, off[0], 0f); assertEquals(0f, off[1], 0f)
        val on = RenderParams.from(s + ("wind.on" to 1f), 1f, 6f).floatUniforms["uWind"]!!
        assertEquals(.8f, on[0], 0f); assertEquals(.5f, on[1], 0f); assertEquals(1f, on[3], 1e-5f)
    }
}
