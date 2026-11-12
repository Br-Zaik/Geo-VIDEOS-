package com.zeta.fondo.engine

import com.zeta.fondo.model.Effects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v41: variantes del shader (solo se compilan los efectos activos) y bloque std140 común.
 * Las posiciones esperadas son las que informa un driver OpenGL ES 3 real (Mesa) para el
 * shader completo; todas las variantes usan el mismo búfer con esa distribución.
 */
class V41ShaderVariantTest {
    private fun read(name: String): String = listOf(File("src/main/assets/shaders/$name"), File("app/src/main/assets/shaders/$name"))
        .first { it.isFile }.readText()

    @Test fun std140LayoutMatchesTheDriver() {
        val l = Std140Layout.parse(read("fondo.frag"))
        assertEquals(6432, l.size) // v44.15: + 7 efectos y uGrade[61], comprobado con ANGLE (WebGL2)
        val expected = mapOf("uNaturalBox" to 0, "uAspect" to 384, "uFlow3" to 400, "uDrift" to 416,
            "uElementPose" to 832, "uElementStyle" to 896, "uFxSmoke" to 2224, "uMaskSmoke" to 2256,
            "uCharAspect" to 3248, "uImpactMore" to 3424)
        for ((name, off) in expected) assertEquals(name, off, l.members[name]!![0])
        assertEquals(16, l.members["uElementPose"]!![1])
        assertEquals(1, l.members["uMaskSmoke"]!![2])
        assertEquals(0, l.members["uFxSmoke"]!![2])
    }

    @Test fun everyEffectTheAppCanTurnOnHasItsOwnShaderBlock() {
        val declared = ShaderVariants.declaredFeatures(read("fondo.frag"))
        for (e in Effects.all.filter { it.toggleable }) assertTrue("falta #ifdef FX_${e.id.uppercase()}", e.id in declared)
        assertTrue("elements" in declared)
    }

    @Test fun variantSourceAddsDefinesRightAfterVersion() {
        val src = ShaderVariants.source(read("fondo.frag"), setOf("smoke", "blast"))
        val lines = src.lines()
        assertEquals("#version 300 es", lines[0].trim())
        assertEquals("#define FX_BLAST 1", lines[1])
        assertEquals("#define FX_SMOKE 1", lines[2])
        assertEquals(ShaderVariants.key(listOf("smoke", "blast")), ShaderVariants.key(setOf("blast", "smoke")))
    }

    @Test fun paramsAskOnlyForTheEffectsThatAreOn() {
        val none = RenderParams.from(Effects.allOff(Effects.defaults()) + ("flow.on" to 0f), 1f)
        assertTrue(none.features.isEmpty())
        val two = RenderParams.from(Effects.allOff(Effects.defaults()) + mapOf("smoke.on" to 1f, "blast.on" to 1f, "flow.on" to 0f), 1f)
        // v44.15: el humo añade el sitio donde se dibuja (delante por defecto).
        assertEquals(setOf("smoke", "blast", "smokefront"), two.features)
        assertEquals("blast,smoke,smokefront", two.variantKey)
        val element = RenderParams.from(Effects.allOff(Effects.defaults()) + mapOf("element0.on" to 1f, "flow.on" to 0f), 1f)
        assertTrue("elements" in element.features)
        assertFalse("smoke" in element.features)
    }
}
