package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectCatalog
import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.EffectGrade
import com.zeta.fondo.model.Effects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.15 · Categorías sin repetir, 7 efectos de acabado, gotas de lluvia y shader más ligero. */
class V4415Test {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private fun frag() = file("src/main/assets/shaders/fondo.frag").readText()
    private val newIds = listOf("glitch", "radialblur", "focusblur", "deepglow", "vhs", "filmburn", "raindrops")

    @Test fun everyEffectIsInExactlyOneCategory() {
        val seen = HashMap<String, String>()
        for ((cat, ids) in EffectCatalog.groupIds) for (id in ids) {
            assertFalse("$id está en $cat y en ${seen[id]}", id in seen)
            seen[id] = cat
        }
        // «Todos» sigue mostrando todo.
        val all = EffectCatalog.idsFor("Todos", Effects.all.map { it.id }) { false }
        for (id in seen.keys) if (id in Effects.all.map { it.id } && id !in Effects.legacyDuplicates) assertTrue(id, id in all)
    }

    @Test fun newEffectsAreComplete() {
        val f = frag()
        for (id in newIds) {
            assertTrue("falta EffectDef $id", Effects.all.any { it.id == id })
            assertTrue("#ifdef FX_${id.uppercase()}", f.contains("#ifdef FX_${id.uppercase()}"))
            assertTrue("miniatura $id", file("src/main/assets/thumbs/$id.webp").exists())
            assertTrue("variantes $id", id == "vhs" || EffectCatalog.variants(id).isNotEmpty())
            val on = RenderParams.from(Effects.defaults() + ("$id.on" to 1f), 1f)
            assertTrue("uFx$id encendido", on.floatUniforms["uFx${RenderParams.uniformName(id)}"]!![0] > 0f)
            val off = RenderParams.from(Effects.defaults(), 1f)
            assertEquals("uFx$id apagado", 0f, off.floatUniforms["uFx${RenderParams.uniformName(id)}"]!![0], 0f)
            assertTrue("$id con categoría", EffectCatalog.groupIds.values.any { id in it })
        }
        for (id in listOf("deepglow", "filmburn")) assertTrue(EffectGrade.has(id))
        for (id in listOf("radialblur", "focusblur")) assertTrue(id in com.zeta.fondo.model.Wind.directional)
    }

    @Test fun rainHasDropTypes() {
        assertTrue(EffectCatalog.variants("raindrops").size >= 3)
        assertTrue(EffectCatalog.idsFor("Agua", Effects.all.map { it.id }) { false }.contains("raindrops"))
    }

    @Test fun secondInstanceIsOnlyCompiledWhenUsed() {
        val f = frag()
        for (id in RenderParams.copyFeatures) {
            val n = id.replaceFirstChar { it.uppercaseChar() }
            assertTrue("zeta${n}2 aparte", f.contains("#ifdef FX_${id.uppercase()}2\n// v44.15"))
        }
        val one = RenderParams.from(Effects.defaults() + ("smoke.on" to 1f), 1f)
        assertTrue("smoke" in one.features)
        assertFalse("con un solo humo no se compila el segundo", "smoke2" in one.features)
        assertTrue("el 2.º humo tiene su bloque declarado", "smoke2" in ShaderVariants.declaredFeatures(f))
        // El humo solo compila el sitio que usa (delante por defecto).
        assertTrue("smokefront" in one.features); assertFalse("smokeback" in one.features)
        val back = RenderParams.from(Effects.defaults() + mapOf("smoke.on" to 1f, "smoke.layer" to 0f), 1f)
        assertTrue("smokeback" in back.features); assertFalse("smokefront" in back.features)
        val mixed = RenderParams.from(Effects.defaults() + mapOf("smoke.on" to 1f, "smoke.layer" to 0f), 1f, 6f,
            listOf(EffectClip(1, "smoke", 0f, 6f), EffectClip(2, "smoke", 0f, 6f, overrides = mapOf("smoke.layer" to 1f))))
        assertTrue(setOf("smoke", "smoke2", "smokeback", "smokefront").all { it in mixed.features })
        assertFalse("sin humo no hay sitios", "smokefront" in RenderParams.from(Effects.defaults(), 1f).features)
        val s = Effects.defaults() + ("blast.on" to 1f)
        val two = RenderParams.from(s, 1f, 6f, listOf(EffectClip(1, "blast", 0f, 6f), EffectClip(2, "blast", 1f, 3f)))
        assertTrue("dos explosiones → se compila la 2.ª", "blast2" in two.features)
        assertFalse("la línea de tiempo no recibe ids de shader", "blast2" in two.timeline.keys)
        // Las uniforms de la 2.ª instancia siguen llegando igual.
        assertTrue(FrameUniforms.compute(two, .5f)["uFxBlast2"]!![0] > 0f)
    }

    @Test fun timelineUiFixes() {
        val tl = file("src/main/java/com/zeta/fondo/ui/Timeline.kt").readText()
        assertTrue("flecha dibujada", tl.contains("cap = StrokeCap.Round"))
        assertFalse("sin el texto ← que quedaba bajo", tl.contains("Text(\"←\""))
        val ui = file("src/main/java/com/zeta/fondo/ui/SimpleEditorPanels.kt").readText()
        assertTrue("escenas más pequeñas", ui.contains("GridCells.Adaptive(78.dp)"))
    }
}
