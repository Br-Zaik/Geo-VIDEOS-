package com.zeta.fondo.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.18 · «Mejorar imagen» simple: Original / x2 / x4 y antes / después en el lienzo. */
class V4418Test {
    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }

    @Test fun simplePanelWithThreeSizes() {
        val p = file("src/main/java/com/zeta/fondo/ui/SuperResPanel.kt").readText()
        assertTrue(p.contains("listOf(\"Original\" to 1, \"x2\" to 2, \"x4\" to 4)"))
        assertFalse("sin imágenes de comparación abajo", p.contains("Línea fina"))
        assertFalse("se aplica sola, sin interruptor", p.contains("SwitchRow("))
        val sr = file("src/main/java/com/zeta/fondo/engine/SuperRes.kt").readText()
        assertTrue(sr.contains("TileUpscale.targetScale(w, h, scale)"))
        assertTrue("al reducir se promedia el bloque", sr.contains("if (box >= 2) {"))
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        assertTrue(vm.contains("superRes.enhance(src, out, scale)"))
        assertTrue("se activa al terminar", vm.substringAfter("fun enhanceImage(").substringBefore("fun cancelEnhance").contains("setSetting(\"image.sr\", 1f)"))
    }

    @Test fun beforeAfterInTheCanvas() {
        val ed = file("src/main/java/com/zeta/fondo/ui/EditorScreen.kt").readText()
        assertTrue(ed.contains("if (vm.srPanelOpen) vm.srCompare?.let { (before, after) -> BeforeAfterSplit(before, after, Modifier.matchParentSize()) }"))
        val p = file("src/main/java/com/zeta/fondo/ui/SuperResPanel.kt").readText()
        assertTrue(p.contains("clipRect(left = x)"))
    }
}
