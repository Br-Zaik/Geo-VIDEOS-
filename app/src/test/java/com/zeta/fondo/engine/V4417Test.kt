package com.zeta.fondo.engine

import com.zeta.fondo.model.SceneAnalysis
import com.zeta.fondo.model.SoundClips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.17 · Sin IA local (salvo «Mejorar imagen»), música como bloque, sin destellos al cambiar de pestaña y luz más real. */
class V4417Test {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private fun exists(rel: String) = File(rel).exists() || File("app/$rel").exists()
    private fun frag() = file("src/main/assets/shaders/fondo.frag").readText()

    @Test fun localAiIsGoneExceptEnhance() {
        // Fuera: profundidad con IA, MI-GAN, descargas de modelos y el proceso aparte.
        for (f in listOf("DepthService", "DepthJob", "ModelStore", "OnnxAssets", "CharacterSegmenter"))
            assertFalse(f, exists("src/main/java/com/zeta/fondo/engine/$f.kt"))
        assertFalse(exists("src/main/java/com/zeta/fondo/ui/ModelsPanel.kt"))
        assertFalse("sin descarga de modelos al compilar", exists("zeta-models.gradle.kts"))
        val gradle = file("build.gradle.kts").readText()
        assertFalse(gradle.contains("zeta-models")); assertFalse(gradle.contains("generated/zetaModels"))
        val manifest = file("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("DepthService")); assertFalse(manifest.contains("android.permission.INTERNET"))
        val tools = file("src/main/java/com/zeta/fondo/ui/EditorTools.kt").readText()
        assertFalse(tools.contains("\"IA\""))
        // «Mejorar imagen» se queda (Brian, 7-nov): su modelo va dentro del APK, sin descargas.
        assertTrue(tools.contains("Triple(9, \"Mejorar\""))
        assertTrue(exists("src/main/java/com/zeta/fondo/engine/SuperRes.kt"))
        assertTrue(gradle.contains("onnxruntime-android"))
        val models = File(file("src/main/assets/models/LICENSES.json").parentFile.path).list()!!.toSet()
        assertEquals(setOf("LICENSES.json", "realesr_animevideov3_x4.onnx"), models)
        val sr = file("src/main/java/com/zeta/fondo/engine/SuperRes.kt").readText()
        assertFalse(sr.contains("OnnxAssets")); assertFalse(sr.contains("ModelStore"))
        // Se borran los modelos descargados antes, pero no la copia del de «Mejorar».
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        assertTrue(vm.contains("\"models_dl\""))
        assertTrue(vm.contains("if (it.name != com.zeta.fondo.engine.SuperResolver.ASSET) it.delete()"))
        assertFalse("la imagen mejorada del proyecto se conserva", vm.contains("\"image_sr.png\").delete()"))
    }

    @Test fun musicIsAClipLikeAnEffect() {
        assertEquals(6f, SoundClips.musicSpan(20f, 6f), 0f)
        assertEquals(3.5f, SoundClips.musicSpan(3.5f, 6f), 0f)
        assertEquals(6f, SoundClips.musicSpan(0f, 6f), 0f)
        val tl = file("src/main/java/com/zeta/fondo/ui/Timeline.kt").readText()
        val audio = tl.substringAfter("private fun AudioTrack(").substringBefore("private fun TimelineBlock(")
        assertTrue("botón como «+ Agregar efecto»", audio.contains("AddPill("))
        assertTrue(audio.contains("vm.importUserSound(it, asMusic = true)"))
        assertTrue(file("src/main/java/com/zeta/fondo/ui/Panels.kt").readText().contains("vm.importUserSound(it, asMusic = true)"))
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        val add = vm.substringAfter("private fun addMusicClip(").substringBefore("\n    }")
        assertTrue("empieza en 0 s", add.contains("EffectClip(uid, id, 0f, dur"))
        assertTrue(add.contains("SoundClips.musicSpan(seconds, total)"))
    }

    @Test fun tabsDoNotFlash() {
        val th = file("src/main/java/com/zeta/fondo/ui/EffectThumb.kt").readText()
        assertTrue("sin icono provisional si la miniatura existe", th.contains("if (ThumbCache.has(ctx, id)) Box(modifier.background(ThumbPlaceholder))"))
        val ed = file("src/main/java/com/zeta/fondo/ui/EditorScreen.kt").readText()
        assertTrue("se preparan también las de Animar", ed.contains("AnimCatalog.entries.values.flatten().map { it.thumb }"))
        val ui = file("src/main/java/com/zeta/fondo/ui/SimpleEditorPanels.kt").readText()
        assertTrue(ui.contains("vm.effectsCategory = cats[i]; scope.launch { pager.scrollToPage(i) }"))
    }

    @Test fun lightRaysComeFromTheRealLight() {
        val f = frag()
        assertTrue(f.contains("float godRaysField(vec2 uv, vec2 sun, float cyc)"))
        val rays = f.substringAfter("#ifdef FX_RAYS").substringBefore("#endif")
        assertTrue(rays.contains("godRaysField(uv, uMotionRays.zw"))
        assertTrue("mezcla pantalla", rays.contains("col = 1.0 - (1.0 - clamp(col, 0.0, 1.0)) * (1.0 - add)"))
        assertTrue(SceneAnalysis.isLightSource(.92f, .5f))
        assertFalse("zona apagada", SceneAnalysis.isLightSource(.6f, .5f))
        assertFalse("reflejo en el suelo", SceneAnalysis.isLightSource(.95f, .95f))
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        assertTrue(vm.substringAfter("private fun autoScene(").substringBefore("private fun ensureTimelineActivation(")
            .contains("listOf(\"rays\", \"lensflare\")"))
    }

    @Test fun flowAnimationHasNoGlobalPulse() {
        assertTrue(frag().contains("phase += vnoise(uv * vec2(uAspect, 1.0) * 2.3 + 7.1);"))
    }
}
