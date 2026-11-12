package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectCatalog
import com.zeta.fondo.model.EffectGrade
import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.Presets
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.14 · Luz y color «de edit»: tono en degradado, bruma adaptada, luz de color, contorno. */
class V4414Test {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }

    @Test fun editEffectsAreComplete() {
        val f = file("src/main/assets/shaders/fondo.frag").readText()
        for (id in listOf("gradmap", "atmos", "lightdir", "rimlight")) {
            assertTrue(id, Effects.all.any { it.id == id })
            assertTrue("#ifdef FX_${id.uppercase()}", f.contains("#ifdef FX_${id.uppercase()}"))
            assertTrue("miniatura $id", file("src/main/assets/thumbs/$id.webp").exists())
            assertTrue("variantes $id", EffectCatalog.variants(id).isNotEmpty())
            val p = RenderParams.from(Effects.defaults() + ("$id.on" to 1f), 1f)
            assertTrue(p.floatUniforms["uFx${RenderParams.uniformName(id)}"]!![0] > 0f)
            assertTrue(EffectCatalog.idsFor("Acabado", Effects.all.map { it.id }) { false }.contains(id))
        }
        for (id in listOf("atmos", "lightdir", "rimlight")) assertTrue(EffectGrade.has(id))
        assertTrue("lightdir" in com.zeta.fondo.model.Wind.directional && "rimlight" in com.zeta.fondo.model.Wind.directional)
    }

    @Test fun hazeAdaptsToImageAndSparesCharacter() {
        val f = file("src/main/assets/shaders/fondo.frag").readText()
        val fn = f.substringAfter("vec3 atmosLayer(").substringBefore("\n}\n")
        assertTrue("toma el color medio de la imagen", fn.contains("texture(uImage, vec2(0.5), 12.0)"))
        assertTrue("no cubre Proteger", fn.contains("maskLayer(clamp(uv, vec2(0.0), vec2(1.0)), 0)"))
    }

    @Test fun rimLightAsksForProtect() {
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        assertTrue(vm.contains("if (id == \"rimlight\" && !maskHasPaint(0))"))
    }

    @Test fun editScenesExist() {
        for (n in listOf("Edit manga (gris azulado)", "Edit manhwa (rosa y azul)", "Luz roja desde abajo", "Bruma mágica")) {
            val p = Presets.scenes.firstOrNull { it.name == n }
            assertTrue(n, p != null)
            for (k in p!!.values.keys) assertTrue("$n: ${k.substringBefore('.')}", Effects.all.any { it.id == k.substringBefore('.') })
        }
    }
}
