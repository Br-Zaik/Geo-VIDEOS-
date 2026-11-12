package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectCatalog
import com.zeta.fondo.model.EffectGrade
import com.zeta.fondo.model.Effects
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.12 · Keyframes visibles, audio de prueba, pausa al exportar, lluvia nueva y 4 efectos. */
class V4412Test {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private fun vm() = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()

    @Test fun newSceneEffectsAreComplete() {
        val f = file("src/main/assets/shaders/fondo.frag").readText()
        for (id in listOf("dapple", "caustics", "steam", "flicker")) {
            assertTrue(id, Effects.all.any { it.id == id })
            assertTrue("#ifdef FX_${id.uppercase()}", f.contains("#ifdef FX_${id.uppercase()}"))
            assertTrue("miniatura $id", file("src/main/assets/thumbs/$id.webp").exists())
            assertTrue("color $id", EffectGrade.has(id))
            val p = RenderParams.from(Effects.defaults() + ("$id.on" to 1f), 1f)
            assertTrue(p.floatUniforms["uFx${RenderParams.uniformName(id)}"]!![0] > 0f)
            assertTrue("en algún catálogo: $id", EffectCatalog.categories.any { c ->
                EffectCatalog.idsFor(c, Effects.all.map { it.id }) { false }.contains(id) })
        }
        assertTrue("steam" in com.zeta.fondo.model.Wind.directional)
    }

    @Test fun inkRainIsThinWithTrailNotThickRods() {
        val f = file("src/main/assets/shaders/fondo.frag").readText()
        val fn = f.substringAfter("float inkStreak(").substringBefore("\n}\n")
        assertTrue("antialias por píxel", fn.contains("fwidth("))
        assertTrue("estela: cola tenue, cabeza marcada", fn.contains("mix(0.25, 1.0, along)"))
        val layer = f.substringAfter("vec3 inkweatherLayer(").substringBefore("\n}\n#endif")
        assertFalse("sin el borde blanco grueso de antes", layer.contains("0.0018 * thick"))
    }

    @Test fun exportPausesPreview() {
        val src = vm()
        val start = src.substringAfter("fun startExport()").substringBefore("hub.renderEnabled = false")
        assertTrue(start.contains("if (playing) togglePlay()"))
        assertTrue(src.substringAfter("fun togglePlay()").substringBefore("\n    }").contains("ExportState.Running"))
    }

    @Test fun soundTrialReplacesInsteadOfStacking() {
        val src = vm()
        val t = src.substringAfter("fun trySound(sound: String)").substringBefore("\n    fun addSound(")
        assertTrue(t.contains("effectClips.filterNot { it.uid == trial.uid }"))
        val panel = file("src/main/java/com/zeta/fondo/ui/SoundPanels.kt").readText()
        assertFalse("ya no se suman al tocar", panel.contains("vm.addSound("))
    }

    @Test fun keyframeStripInEditor() {
        val ui = file("src/main/java/com/zeta/fondo/ui/SimpleEditorPanels.kt").readText()
        assertTrue(ui.contains("KeyframeStrip(vm, id)"))
        val k = file("src/main/java/com/zeta/fondo/ui/KeyframeControls.kt").readText()
        assertTrue(k.contains("fun KeyframeStrip("))
        assertTrue("tenue fuera de la clave", k.contains("StudioPink.copy(alpha = .35f)"))
    }
}
