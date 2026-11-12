package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectCatalog
import com.zeta.fondo.model.Effects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v44.19 · «Calidad cine»: ajuste global, parámetros y plan de la pirámide de brillo. */
class V4419CineTest {

    @Test fun cineEsAjusteGlobalSinBloqueNiZona() {
        val def = Effects.all.first { it.id == "cine" }
        assertFalse("no se enciende como efecto (sin bloque en la línea de tiempo)", def.toggleable)
        assertTrue("cine" in Effects.globalSettings)
        assertEquals(listOf("Ajustes", "Estilo"), EffectCatalog.sections("cine", false, false))   // v44.20: looks
        assertEquals(listOf("Acabado"), EffectCatalog.groupOf("cine"))
        assertTrue(EffectCatalog.idsFor("Acabado", Effects.all.map { it.id }) { false }.first() == "cine")
    }

    @Test fun activoPorDefectoConValoresSuaves() {
        assertEquals(1f, Effects.default("cine.active"))
        assertTrue(Effects.default("cine.bloom") in .3f..1f)
        assertTrue("las luces blancas de la imagen apenas brillan", Effects.default("cine.image") <= .3f)
        assertEquals(0f, Effects.default("cine.grain"))
    }

    @Test fun renderParamsLlevaElPostSinTocarElBloqueStd140() {
        val p = RenderParams.from(Effects.defaults(), 9f / 16f)
        assertEquals(10, p.post.size)
        assertTrue(CinePlan.active(p.post))
        assertTrue(p.floatUniforms.keys.none { it.contains("Cine") || it.contains("Post") })
        // «cine» no es una variante del shader (no cambia la compilación)
        assertFalse("cine" in p.features)
        val off = RenderParams.from(Effects.defaults() + ("cine.active" to 0f), 1f)
        assertFalse(CinePlan.active(off.post))
        assertEquals(0f, CinePlan.grainAmount(off.post))
    }

    @Test fun postPorDefectoApagado() {
        assertFalse(CinePlan.active(CinePlan.OFF))
        assertFalse(CinePlan.bloomVisible(CinePlan.OFF))
    }

    @Test fun nivelesDeLaPiramide() {
        val lv = CinePlan.levels(1080, 1920)
        assertEquals(540, lv[0]); assertEquals(960, lv[1])
        val n = lv.size / 2
        assertTrue(n in 2..CinePlan.MAX_LEVELS)
        for (i in 1 until n) {
            assertEquals((lv[(i - 1) * 2] + 1) / 2, lv[i * 2])
            assertTrue(minOf(lv[i * 2], lv[i * 2 + 1]) >= CinePlan.MIN_SIDE)
        }
        // vista muy pequeña: al menos un nivel
        assertEquals(2, CinePlan.levels(4, 4).size)
    }

    @Test fun halfFloatSoloConExtension() {
        assertFalse(CinePlan.halfFloatCandidate(emptySet()))
        assertTrue(CinePlan.halfFloatCandidate(setOf("GL_EXT_color_buffer_half_float")))
        assertTrue(CinePlan.halfFloatCandidate(setOf("GL_EXT_color_buffer_float")))
    }

    @Test fun memoriaRazonableEn1080p() {
        val mb = CinePlan.memoryBytes(1080, 1920, true) / (1024 * 1024)
        assertTrue("memoria cine 1080p = $mb MB", mb in 8..40)
    }

    @Test fun shadersCine() {
        for (src in listOf(CineShaders.PREFILTER, CineShaders.DOWN, CineShaders.UP, CineShaders.COMPOSITE)) {
            assertTrue(src.startsWith("#version 300 es"))
            assertTrue(src.contains("uFrac") || src.contains("uSceneScale"))
            assertFalse("palabra reservada de GLSL ES", Regex("\\bhalf\\b").containsMatchIn(src))
        }
    }

    @Test fun shaderPrincipalMarcaLaLuzDeLosEfectos() {
        val frag = java.io.File(shaderDir(), "fondo.frag").readText()
        assertTrue(frag.contains("uniform float uEmitAlpha;"))
        assertTrue(frag.contains("vec3 cineBase = col;"))
        assertTrue(frag.contains("fragColor = vec4(clamp(col, 0.0, 1.0), cineEmit);"))
        // GLSL ES 3.0: «half» es palabra reservada (rompió la compilación en Chrome)
        assertFalse(Regex("\\b(float|vec[234])\\s+half\\b").containsMatchIn(frag))
    }

    @Test fun vientoConHojasDeVerdad() {
        val frag = java.io.File(shaderDir(), "fondo.frag").readText()
        assertTrue(frag.contains("float gustLeafDist(vec2 d)"))
        val gust = frag.substring(frag.indexOf("vec3 gustLayer("), frag.indexOf("#endif", frag.indexOf("vec3 gustLayer(")))
        assertTrue("dos capas de profundidad", gust.contains("for (int L = 1; L >= 0; L--)"))
        assertTrue("líneas legibles sobre fondo claro", gust.contains("airDark"))
    }

    private fun shaderDir(): java.io.File {
        val candidates = listOf("src/main/assets/shaders", "app/src/main/assets/shaders")
        return candidates.map { java.io.File(it) }.firstOrNull { it.isDirectory } ?: java.io.File(candidates[0])
    }
}
