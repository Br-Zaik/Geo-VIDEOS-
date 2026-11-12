package com.zeta.fondo.ui

import com.zeta.fondo.model.ClipEdits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.10 · Línea de tiempo: tiradores fuera, deslizar para quitar, duración inicial, anclas, Reemplazar. */
class V4410TimelineTest {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }

    @Test fun handlesSitOutsideTheBlock() {
        val src = file("src/main/java/com/zeta/fondo/ui/Timeline.kt").readText()
        // El color del bloque llega hasta el final real: el tirador derecho empieza en x1.
        assertTrue(src.contains("TrimHandle(Modifier.atX { x1() }"))
        assertTrue(src.contains("TrimHandle(Modifier.atX { x0() - handlePx }"))
        assertFalse("sin relleno interior para los tiradores", src.contains(".padding(horizontal = if (selected) 16.dp else 0.dp)"))
    }

    @Test fun removeButtonAndLongPressMove() {
        // v44.12: ya no se borra deslizando; botón ← al inicio del bloque. Mover = mantener pulsado.
        val src = file("src/main/java/com/zeta/fondo/ui/Timeline.kt").readText()
        val block = src.substringAfter("private fun TimelineBlock(").substringBefore("internal fun Modifier.spanX(")
        assertTrue("flecha ← dibujada y centrada", block.contains("cap = StrokeCap.Round"))
        assertTrue(block.contains(".clickable { vm.removeEffectClip(clip.uid) }"))
        assertFalse("un toque ya no arrastra", block.contains("if (selected) detectDragGestures("))
        assertTrue(block.contains("detectDragGesturesAfterLongPress(onDragStart = onStart"))
        assertFalse(src.contains("SwipeDelete"))
    }

    @Test fun newEffectsCoverFromStartUpToSixSeconds() {
        assertEquals(0f to 6f, ClipEdits.defaultSpan(6f))
        assertEquals(0f to 6f, ClipEdits.defaultSpan(10f))
        assertEquals(0f to 4f, ClipEdits.defaultSpan(4f))
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        assertTrue(vm.substringAfter("private fun defaultExtraClip(").substringBefore("eventEffects) {").isNotEmpty())
        assertTrue(vm.substringAfter("private fun defaultExtraClip(").substringBefore("if (!settings.containsKey").contains("ClipEdits.defaultSpan(total)"))
    }

    @Test fun eraserRemovesAnchorsInAnimateTab() {
        val src = file("src/main/java/com/zeta/fondo/ui/EditorScreen.kt").readText()
        val fn = src.substringAfter("private fun eraseNearest(").substringBefore("private fun segDist(")
        assertFalse(fn.contains("vm.tab == EditorTab.MASKS"))
        assertTrue(fn.contains("vm.removeAnchorAt("))
    }

    @Test fun replaceIsSecondAndWorksForAnimations() {
        val src = file("src/main/java/com/zeta/fondo/ui/Workspace.kt").readText()
        val bar = src.substringAfter("ToolChip(\"Ajustar\", Icons.Filled.Tune, false) { vm.editEffect(id) }")
        assertTrue("Reemplazar justo después de Ajustar", bar.indexOf("\"Reemplazar\"") in 0 until bar.indexOf("\"Solo\""))
        val tools = file("src/main/java/com/zeta/fondo/ui/EditorTools.kt").readText()
        assertTrue(tools.contains("vm.replaceInAnim -> AnimCatalogPanel(vm)"))
    }
}
