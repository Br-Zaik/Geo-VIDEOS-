package com.zeta.fondo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v40: lienzo fijo y edición real de bloques (dividir, sustituir, recortar, ajustes por bloque). */
class V40Test {
    private val W = 392f
    private val H = 820f

    @Test fun canvasNeverDependsOnToolsOrOptions() {
        // El cálculo solo recibe pantalla, proporción, pantalla completa y el tamaño que
        // elige el usuario: no existe ningún parámetro de herramienta, categoría o panel.
        for (a in listOf(9f / 16f, 1f, 16f / 9f)) {
            val first = EditorLayout.compute(W, H, a, false)
            repeat(5) { assertEquals(first, EditorLayout.compute(W, H, a, false)) }
        }
    }

    @Test fun userChoosesPreviewSizeExplicitly() {
        val a = 9f / 16f
        val c = EditorLayout.compute(W, H, a, false, PreviewSize.COMPACT).canvasH
        val n = EditorLayout.compute(W, H, a, false, PreviewSize.NORMAL).canvasH
        val g = EditorLayout.compute(W, H, a, false, PreviewSize.LARGE).canvasH
        assertTrue(c < n && n < g)
        assertEquals(PreviewSize.NORMAL, PreviewSize.of(null))
        assertEquals(PreviewSize.LARGE, PreviewSize.of("LARGE"))
    }

    @Test fun workspaceAlwaysHasRoomOnSmallPhones() {
        for (h in listOf(560f, 640f, 720f, 820f)) {
            val l = EditorLayout.compute(360f, h, 9f / 16f, false, PreviewSize.LARGE)
            assertTrue("$h: ${l.workspace}", l.workspace >= EditorLayout.MIN_WORKSPACE - .01f || l.canvasH <= EditorLayout.MIN_CANVAS + .01f)
        }
    }

    private fun clip(uid: Long, id: String, s: Float, d: Float, ov: Map<String, Float> = emptyMap()) =
        EffectClip(uid, id, s, d, track = 0, overrides = ov)

    @Test fun clipKeysExcludeEffectWideSwitches() {
        assertTrue(ClipEdits.isClipKey("blast", "blast.size"))
        assertTrue(ClipEdits.isClipKey("blast", "blast.originX"))
        assertTrue(ClipEdits.isClipKey("smoke", "smoke.tintR"))
        assertFalse(ClipEdits.isClipKey("blast", "blast.on"))
        assertFalse(ClipEdits.isClipKey("blast", "blast.mode"))
        assertFalse(ClipEdits.isClipKey("blast", "smoke.size"))
        assertFalse(ClipEdits.isClipKey("leaves", "leaves.copy.on"))
    }

    @Test fun singleBlockEditsTheEffectAndSeveralBlocksEditOnlyTheSelectedOne() {
        val a = clip(1, "blast", 0f, 2f)
        assertNull(ClipEdits.target(listOf(a), 1))
        val b = clip(2, "blast", 3f, 2f)
        assertEquals(2L, ClipEdits.target(listOf(a, b), 2)?.uid)
        assertEquals(1L, ClipEdits.target(listOf(a, b), 99)?.uid)
        // Un bloque único que ya tiene ajustes propios se sigue editando a sí mismo.
        val own = clip(3, "smoke", 0f, 6f, mapOf("smoke.size" to 5f))
        assertEquals(3L, ClipEdits.target(listOf(own), null)?.uid)
    }

    @Test fun effectiveValuesPreferTheBlock() {
        val s = mapOf("blast.size" to .65f, "blast.int" to .9f)
        val b = clip(2, "blast", 3f, 2f, mapOf("blast.size" to 1.2f))
        assertEquals(1.2f, ClipEdits.value(s, b, "blast.size")!!, 0f)
        assertEquals(.9f, ClipEdits.value(s, b, "blast.int")!!, 0f)
        assertEquals(.65f, ClipEdits.value(s, null, "blast.size")!!, 0f)
        val eff = ClipEdits.effectiveSettings(s, b)
        assertEquals(1.2f, eff["blast.size"]!!, 0f)
    }

    @Test fun splitKeepsTimeTrackAndOwnSettings() {
        val c = EffectClip(5, "smoke", 1f, 3f, track = 2, strength = 1.3f, overrides = mapOf("smoke.size" to 4f))
        val (l, r) = ClipEdits.split(c, 2.5f, 6)!!
        assertEquals(1f, l.startSec, 1e-5f); assertEquals(1.5f, l.durationSec, 1e-5f)
        assertEquals(2.5f, r.startSec, 1e-5f); assertEquals(1.5f, r.durationSec, 1e-5f)
        assertEquals(6L, r.uid); assertEquals(2, r.track); assertEquals(1.3f, r.strength, 0f)
        assertEquals(c.overrides, r.overrides)
        assertNull("corte pegado al extremo", ClipEdits.split(c, 1.02f, 7))
    }

    @Test fun replaceKeepsPlacementAndPortableSettingsOnly() {
        val c = clip(4, "blast", 1f, 2f, mapOf("blast.originX" to .2f, "blast.size" to 1.4f, "blast.tintOn" to 1f))
        val r = ClipEdits.replaceEffect(c, "smoke")
        assertEquals("smoke", r.effectId)
        assertEquals(1f, r.startSec, 0f); assertEquals(2f, r.durationSec, 0f); assertEquals(c.track, r.track)
        assertEquals(.2f, r.overrides["smoke.originX"]!!, 0f)
        assertEquals(1f, r.overrides["smoke.tintOn"]!!, 0f)
        assertFalse("el tamaño de otro efecto no tiene el mismo significado", r.overrides.containsKey("smoke.size"))
    }

    @Test fun trimmingFromBothEndsAndMovingRespectLimits() {
        // Izquierda: el final queda fijo.
        val (s1, d1) = ClipEdits.trimStart(1f, 3f, .5f)
        assertEquals(1.5f, s1, 1e-5f); assertEquals(1.5f, d1, 1e-5f)
        val (s2, d2) = ClipEdits.trimStart(1f, 3f, 10f)
        assertEquals(3f - ClipEdits.MIN_DURATION, s2, 1e-5f); assertEquals(ClipEdits.MIN_DURATION, d2, 1e-5f)
        val (s3, _) = ClipEdits.trimStart(1f, 3f, -5f)
        assertEquals(0f, s3, 0f)
        // Derecha: el inicio queda fijo, sin pasar del final del vídeo.
        val (s4, d4) = ClipEdits.trimEnd(1f, 3f, 10f, 6f)
        assertEquals(1f, s4, 0f); assertEquals(5f, d4, 1e-5f)
        val (_, d5) = ClipEdits.trimEnd(1f, 3f, -10f, 6f)
        assertEquals(ClipEdits.MIN_DURATION, d5, 1e-5f)
        // Mover conserva la duración y no sale del vídeo.
        assertEquals(4f, ClipEdits.move(1f, 2f, 10f, 6f), 1e-5f)
        assertEquals(0f, ClipEdits.move(1f, 2f, -10f, 6f), 0f)
    }
}
