package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectCatalog
import com.zeta.fondo.model.Effects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v44.20 · Estela de cámara, luz sobre la escena, looks, franjas, partículas y parpadeo. */
class V4420Test {
    private fun frag(): String {
        val c = listOf("src/main/assets/shaders/fondo.frag", "app/src/main/assets/shaders/fondo.frag")
        return java.io.File(c.first { java.io.File(it).isFile }).readText()
    }

    @Test fun nuevosAjustesDeCine() {
        val keys = Effects.all.first { it.id == "cine" }.params.map { it.key }
        assertTrue(keys.containsAll(listOf("mblur", "spill", "variant", "lookamt", "bars")))
        assertEquals(CinePlan.LOOKS, EffectCatalog.variants("cine").size)
        assertEquals(listOf("Ajustes", "Estilo"), EffectCatalog.sections("cine", false, false))
        assertEquals(0f, Effects.default("cine.bars"))
        assertEquals(0f, Effects.default("cine.variant"))
    }

    @Test fun todoSeApagaConCalidadCine() {
        val on = RenderParams.from(Effects.defaults() + mapOf("cine.variant" to 3f, "cine.bars" to 1f), 1f).post
        assertEquals(.5f, CinePlan.motionBlur(on))
        assertEquals(3, CinePlan.look(on))
        assertEquals(1f, CinePlan.bars(on))
        val off = RenderParams.from(Effects.defaults() + mapOf("cine.active" to 0f, "cine.variant" to 3f, "cine.bars" to 1f), 1f).post
        assertEquals(0f, CinePlan.motionBlur(off))
        assertEquals(0, CinePlan.look(off))
        assertEquals(0f, CinePlan.bars(off))
        assertEquals(0f, CinePlan.spill(off))
    }

    @Test fun lookNaturalNoCambiaNada() {
        val p = RenderParams.from(Effects.defaults(), 1f).post
        assertEquals(0, CinePlan.look(p))
        assertEquals(0f, CinePlan.lookAmount(p))
        // un look fuera de rango se acota
        assertEquals(CinePlan.LOOKS - 1, CinePlan.look(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 99f, 1f, 0f)))
    }

    @Test fun franjasDeCine() {
        // 16:9 → franjas hasta 2,39:1
        val f = CinePlan.barFraction(16f / 9f, 1f)
        assertEquals(1f - 2f * f, (16f / 9f) / 2.39f, 0.01f)
        assertEquals(0.10f, CinePlan.barFraction(9f / 16f, 1f), 1e-5f)
        assertEquals(0f, CinePlan.barFraction(3f, 1f))      // ya es más ancho que cine
        assertEquals(0f, CinePlan.barFraction(1f, 0f))
    }

    @Test fun estelaDeCamaraEnElShader() {
        val s = frag()
        assertTrue(s.contains("vec2 zetaCamera(vec2 s, float tt)"))
        assertTrue(s.contains("sc = zetaCamera(s, uT);"))
        assertTrue(s.contains("uniform float uMotionBlur;"))
        val cam = s.substring(s.indexOf("vec2 zetaCamera("), s.indexOf("vec3 cameraBlur("))
        assertFalse("la cámara solo depende de tt", Regex("\\buT\\b").containsMatchIn(cam))
    }

    @Test fun particulasCruzanSuCelda() {
        val s = frag()
        assertTrue(s.contains("vec4 particleFieldX("))
        assertTrue(s.contains("vec4 sakuraLayerX("))
        assertTrue(s.contains("vec3 sceneTint(vec2 uv)"))
        val pf = s.substring(s.indexOf("vec4 particleFieldX("), s.indexOf("vec2 particleField(vec2 p"))
        assertTrue("4 celdas vecinas", pf.contains("for (int k = 0; k < 4; k++)"))
        assertFalse(Regex("\\b(float|vec[234])\\s+half\\b").containsMatchIn(s))
    }

    @Test fun parpadeoConParpado() {
        val s = frag()
        val b = s.substring(s.indexOf("#ifdef FX_BLINK\n    // v44.20"), s.indexOf("#endif", s.indexOf("#ifdef FX_BLINK\n    // v44.20")))
        assertTrue(b.contains("maskLayer(uv - vec2(0.0, fi * STEP), eyeLayer)"))
        assertTrue(b.contains("lash"))
        assertEquals("Parpadeo (Zona 1)", Effects.all.first { it.id == "blink" }.title)
    }
}
