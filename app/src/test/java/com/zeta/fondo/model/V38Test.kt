package com.zeta.fondo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v38: diseño del editor, reglas de gestos y catálogo de efectos. */
class V38Test {

    @Test fun imageRectMatchesOpenGlFitting() {
        // Imagen vertical 9:16 en un lienzo apaisado: franjas a los lados.
        val r = ImageRect.fit(1000f, 800f, 9f / 16f)
        assertEquals(800f, r.height, .01f)
        assertEquals(450f, r.width, .01f)
        assertEquals(275f, r.left, .01f)
        assertEquals(0f, r.top, .01f)
        assertEquals(0.5f, r.normX(500f), 1e-4f)
        assertFalse(r.contains(100f, 400f))   // franja negra
        assertTrue(r.contains(500f, 400f))
        // Imagen apaisada en lienzo vertical: franjas arriba y abajo.
        val w = ImageRect.fit(400f, 800f, 2f)
        assertEquals(200f, w.height, .01f)
        assertEquals(300f, w.top, .01f)
    }

    // ---------------------------------------------------------------- Gestos

    @Test fun twoFingersAlwaysZoomAndNeverPaint() {
        var m = GestureRules.Mode.WAITING
        m = GestureRules.next(m, 1, 2f, 10f, toolDraws = true)
        assertEquals(GestureRules.Mode.WAITING, m)          // aún no pinta (no se movió)
        m = GestureRules.next(m, 2, 3f, 10f, toolDraws = true)
        assertEquals(GestureRules.Mode.TRANSFORM, m)        // llega el 2.º dedo: zoom
        m = GestureRules.next(m, 1, 80f, 10f, toolDraws = true)
        assertEquals("al soltar un dedo no empieza a pintar", GestureRules.Mode.TRANSFORM, m)
    }

    @Test fun brushStartsOnlyAfterMoving() {
        var m = GestureRules.Mode.WAITING
        m = GestureRules.next(m, 1, 4f, 10f, toolDraws = true)
        assertEquals(GestureRules.Mode.WAITING, m)
        m = GestureRules.next(m, 1, 12f, 10f, toolDraws = true)
        assertEquals(GestureRules.Mode.DRAW, m)
        // Si después llega otro dedo, se cancela el trazo y pasa a zoom.
        m = GestureRules.next(m, 2, 12f, 10f, toolDraws = true)
        assertEquals(GestureRules.Mode.TRANSFORM, m)
    }

    @Test fun panToolMovesWithOneFinger() {
        assertEquals(GestureRules.Mode.TRANSFORM,
            GestureRules.next(GestureRules.Mode.WAITING, 1, 0f, 10f, toolDraws = false))
    }

    // ---------------------------------------------------------------- Catálogo de efectos

    private val ids = Effects.all.map { it.id }

    @Test fun everyEffectIsReachableFromAClearCategory() {
        val categorized = EffectCatalog.categories.filter { it !in setOf("Populares", "Activos", "Todos", "Escenas") }
            .flatMap { EffectCatalog.idsFor(it, ids) { false } }.toSet()
        val missing = ids.filter { it !in EffectCatalog.animateIds && it !in Effects.legacyDuplicates && it !in categorized }
        assertTrue("efectos sin categoría: $missing", missing.isEmpty())
    }

    @Test fun animateToolsAreNotInTheEffectsCatalog() {
        for (c in EffectCatalog.categories) {
            val list = EffectCatalog.idsFor(c, ids) { true }
            assertTrue("$c contiene herramientas de Animar", list.none { it in EffectCatalog.animateIds })
        }
    }

    @Test fun activeCategoryShowsOnlyActiveEffects() {
        val on = setOf("smoke", "rain")
        assertEquals(on, EffectCatalog.idsFor("Activos", ids) { it in on }.toSet())
        // Un efecto activo nunca desaparece de Populares.
        assertTrue("tone" in EffectCatalog.idsFor("Populares", ids) { it == "tone" })
    }

    @Test fun legacyDuplicatesAreHiddenUnlessUsed() {
        val all = EffectCatalog.idsFor("Todos", ids) { false }
        assertTrue(Effects.legacyDuplicates.none { it in all })
        assertTrue("sway" in EffectCatalog.idsFor("Todos", ids) { it == "sway" })
    }

    @Test fun oldCategoryNamesStillWork() {
        assertEquals("Populares", EffectCatalog.normalize("Útiles"))
        assertEquals("Agua", EffectCatalog.normalize("Agua y cielo"))
        assertEquals("Fuego", EffectCatalog.normalize("Fuego"))
        assertEquals("Populares", EffectCatalog.normalize("¿?"))
    }

    @Test fun editorSectionsAreSimpleAndOrdered() {
        val smoke = EffectCatalog.sections("smoke", colorable = true, hasShapes = true)
        assertEquals(listOf("Ajustes", "Estilo", "Color", "Tiempo", "Zona"), smoke)
        val rain = EffectCatalog.sections("rain", colorable = false, hasShapes = false)
        // v44.11: la lluvia ya tiene «Color» (blanco y negro, sepia, saturación…).
        assertEquals(listOf("Ajustes", "Estilo", "Color", "Tiempo", "Zona"), rain)
        assertEquals(listOf("Ajustes", "Estilo", "Tiempo"), EffectCatalog.sections("parallax", false, false))
        assertTrue(EffectCatalog.sections("style", false, false).none { it == "Tiempo" })
        // Todas las secciones existen en el editor (iconos y contenido).
        val known = setOf("Ajustes", "Estilo", "Color", "Tiempo", "Zona")
        for (id in ids) assertTrue(EffectCatalog.sections(id, id in Effects.colorable, Effects.shapeLabels.containsKey(id)).all { it in known })
    }
}
