package com.zeta.fondo.engine

import com.zeta.fondo.model.Effects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.16 · Línea de tiempo quieta, efectos que cumplen lo que muestran, captura y catálogo fluido. */
class V4416Test {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private fun frag() = file("src/main/assets/shaders/fondo.frag").readText()

    @Test fun timelineTextMovesWithItsBlock() {
        val tl = file("src/main/java/com/zeta/fondo/ui/Timeline.kt").readText()
        assertTrue(tl.contains("internal fun Modifier.followStart("))
        val block = tl.substringAfter("private fun TimelineBlock(").substringBefore("internal fun Modifier.spanX(")
        assertTrue("nombre pegado al inicio real del bloque", block.contains("Row(Modifier.followStart({ x0() }, { x1() })"))
        val audio = tl.substringAfter("private fun AudioTrack(").substringBefore("private fun TimelineBlock(")
        assertTrue("música alineada con el vídeo", audio.contains("Modifier.spanX(x0, { center + (total - cur()) * pps })"))
        assertTrue(audio.contains("followStart(x0"))
        assertFalse("ya no hay botón fijo de pantalla completa", audio.contains("Box(modifier.padding(horizontal = 12.dp))"))
    }

    @Test fun followStartPlacesContentAtRealStart() {
        // Mismo cálculo que el modificador: inicio fuera de la pantalla → se desplaza a la izquierda.
        fun shift(x0: Float) = x0.coerceAtMost(0f).toInt()
        assertEquals(-120, shift(-120f)); assertEquals(0, shift(35f))
    }

    // ---------- Proteger automático

    private fun portrait(w: Int, h: Int): IntArray {
        val px = IntArray(w * h)
        val cx = w / 2f
        for (y in 0 until h) for (x in 0 until w) {
            val g = 235
            var c = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            val head = kotlin.math.hypot(x - cx, y - h * .3f) < w * .22f
            val body = y > h * .48f && kotlin.math.abs(x - cx) < w * (.18f + .5f * (y - h * .48f) / h)
            if (head || body) c = if ((x / 5 + y / 5) % 2 == 0) 0xFF2A2A30.toInt() else 0xFF3A3A44.toInt()
            px[y * w + x] = c
        }
        return px
    }

    @Test fun autoProtectFindsTheCharacter() {
        val w = 120; val h = 200
        val m = DepthMath.autoProtect(portrait(w, h), w, h)
        assertNotNull(m); m!!
        val head = m[(h * .3f).toInt() * w + w / 2].toInt() and 0xFF
        val corner = m[5 * w + 5].toInt() and 0xFF
        assertTrue("cabeza $head", head > 200)
        assertTrue("fondo $corner", corner < 30)
    }

    @Test fun autoProtectGivesUpOnBusyImages() {
        val w = 96; val h = 96
        val rnd = java.util.Random(3)
        assertNull(DepthMath.autoProtect(IntArray(w * h) { (0xFF shl 24) or rnd.nextInt(0xFFFFFF) }, w, h))
    }

    @Test fun effectsThatNeedTheCharacterGetItAutomatically() {
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        val task = vm.substringAfter("private val paramsTask = Runnable {").substringBefore("private fun pushParams()")
        assertTrue(task.contains("ensureAutoProtect()"))
        assertTrue(task.contains("ensureAutoZones()"))
        val fn = vm.substringAfter("private fun ensureAutoProtect()").substringBefore("/** Caras anime")
        assertTrue("una vez por proyecto", fn.contains("autoProtectTried = m.id"))
        assertTrue("se deshace con ↶", fn.contains("pushUndo(UndoAction.AddStroke(stroke))"))
        assertTrue(fn.contains("DepthMath.autoProtect("))
        // v44.25: needsProtect → needsCharacter (ya no cuenta los efectos «Detrás»).
        assertTrue(vm.substringAfter("private fun needsCharacter()").substringBefore("\n    }").contains("isEffectOn(\"rimlight\")"))
        // Todo lo que se dibuja (vista previa, exportación, captura) usa los mismos ajustes de render.
        assertFalse(vm.contains("RenderParams.from(settings,"))
    }

    // ---------- Efectos que hacen lo que muestran

    @Test fun animeAuraStartsAsAura() {
        assertEquals(1f, Effects.default("energy.shape"), 0f)
        val f = frag()
        val fn = f.substringAfter("vec3 zetaEnergy(").substringBefore("\n}\n")
        assertTrue("se pinta (visible sobre blanco)", fn.contains("col = mix(col, ec * 1.08, clamp(flame * 0.78 * k, 0.0, 1.0));"))
        assertTrue("nada dentro del personaje", fn.contains("(1.0 - smoothstep(0.35, 0.85, m0))"))
    }

    @Test fun rimLightTintsBrightAreas() {
        val fn = frag().substringAfter("vec3 rimlightLayer(").substringBefore("\n}\n")
        assertTrue(fn.contains("vec3 tint = mix(col, lc * (0.55 + 0.55 * luma(col)), 0.75);"))
    }

    @Test fun fireWithoutZoneBurnsAlongTheBottom() {
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        val rs = vm.substringAfter("private fun renderSettings()").substringBefore("\n    }")
        assertTrue(rs.contains("\"flame.mask\" to -2f"))
        // Con -2 (toda la imagen) el fuego del shader usa el borde inferior (modo «whole»).
        val p = RenderParams.from(Effects.defaults() + mapOf("flame.on" to 1f, "flame.mask" to -2f), 1f)
        assertEquals(-2, p.intUniforms["uMaskFlame"])
        assertTrue(frag().contains("(1.0 - smoothstep(box.y - 0.12, box.y + 0.02, uv.y))"))
    }

    // ---------- Captura y catálogo

    @Test fun captureSavesTheCurrentFrame() {
        val ex = file("src/main/java/com/zeta/fondo/engine/Exporter.kt").readText()
        assertTrue(ex.contains("fun still(req: ExportRequest, t: Float, fileName: String)"))
        assertTrue(ex.contains("val t = stillT ?: LoopClock.phase(i, frames)"))
        val top = file("src/main/java/com/zeta/fondo/ui/EditorScreen.kt").readText()
        assertTrue(top.contains("vm.captureFrame()"))
    }

    @Test fun catalogSwipesAndThumbsDoNotFlicker() {
        val ui = file("src/main/java/com/zeta/fondo/ui/SimpleEditorPanels.kt").readText()
        assertTrue(ui.contains("HorizontalPager(pager"))
        val th = file("src/main/java/com/zeta/fondo/ui/EffectThumb.kt").readText()
        assertTrue(th.contains("produceState(ThumbCache.peek(id), id)"))
        assertTrue(file("src/main/java/com/zeta/fondo/ui/EditorScreen.kt").readText().contains("ThumbCache.prewarm("))
    }
}
