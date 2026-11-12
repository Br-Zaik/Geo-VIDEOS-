package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectCatalog
import com.zeta.fondo.model.EffectGrade
import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.Presets
import com.zeta.fondo.model.SceneAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.13 · Animar el cielo, el mar y la luz pintados + acabado de cine + escenas de un toque. */
class V4413Test {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }

    /** Atardecer sintético: cielo degradado, sol arriba del horizonte, mar oscuro con reflejo. */
    private fun sunset(w: Int, h: Int, hz: Float, sx: Float, sy: Float): FloatArray = FloatArray(w * h) { i ->
        val x = (i % w + .5f) / w; val y = (i / w + .5f) / h
        val sun = Math.exp(-(((x - sx) * (x - sx) + (y - sy) * (y - sy)) * 900.0)).toFloat()
        val base = if (y < hz) .45f + .35f * y / hz else .12f + .05f * (y - hz)
        (base + sun).coerceIn(0f, 1f)
    }

    @Test fun findsHorizonAndSun() {
        val w = 160; val h = 90
        val lum = sunset(w, h, .62f, .3f, .5f)
        val hz = SceneAnalysis.horizon(lum, w, h)
        assertNotNull(hz); assertEquals(.62f, hz!!, .03f)
        val (x, y) = SceneAnalysis.brightest(lum, w, h)
        assertEquals(.3f, x, .03f); assertEquals(.5f, y, .04f)
    }

    @Test fun noHorizonInAFlatImage() {
        val w = 80; val h = 60
        val rnd = java.util.Random(3)
        assertNull(SceneAnalysis.horizon(FloatArray(w * h) { .5f + rnd.nextFloat() * .01f }, w, h))
    }

    @Test fun topmostStrongEdgeWins() {
        // Barandilla: dos bordes fuertes (arriba y abajo de la barra); el horizonte es el de arriba.
        val w = 120; val h = 100
        val lum = FloatArray(w * h) { i -> val y = (i / w + .5f) / h; if (y in .70f..0.78f) .05f else if (y > .78f) .4f else .7f }
        assertEquals(.70f, SceneAnalysis.horizon(lum, w, h)!!, .03f)
    }

    @Test fun newLandscapeEffectsAreComplete() {
        val f = file("src/main/assets/shaders/fondo.frag").readText()
        for (id in listOf("skydrift", "seawaves", "sunlight", "finish")) {
            assertTrue(id, Effects.all.any { it.id == id })
            assertTrue("#ifdef FX_${id.uppercase()}", f.contains("#ifdef FX_${id.uppercase()}"))
            assertTrue("miniatura $id", file("src/main/assets/thumbs/$id.webp").exists())
            val p = RenderParams.from(Effects.defaults() + ("$id.on" to 1f), 1f)
            assertTrue(p.floatUniforms["uFx${RenderParams.uniformName(id)}"]!![0] > 0f)
            // v44.15: cada efecto en UNA categoría; el acabado vive en «Acabado».
            val cat = if (id == "finish") "Acabado" else "Cielo y mar"
            assertTrue(EffectCatalog.idsFor(cat, Effects.all.map { it.id }) { false }.contains(id))
        }
        assertTrue(EffectGrade.has("sunlight"))
        assertTrue("el acabado no tiene zona", "Zona" !in EffectCatalog.sections("finish", false, false))
        // Apagado, el acabado no toca la imagen.
        val off = RenderParams.from(Effects.defaults(), 1f)
        assertTrue(off.floatUniforms["uFxFinish"]!!.all { it == 0f })
    }

    @Test fun skyDriftKeepsSunStillAndNeverTakesSeaPixels() {
        val f = file("src/main/assets/shaders/fondo.frag").readText()
        val fn = f.substringAfter("vec3 skydriftLayer(").substringBefore("\n}\n")
        assertTrue(fn.contains("smoothstep(0.84, 0.97, luma(col))"))
        assertTrue(fn.contains("min(ua.y, hz - 0.004)"))
    }

    @Test fun landscapeScenesExist() {
        for (n in listOf("Atardecer en el mar", "Cielo de anime", "Noche de luna llena", "Lluvia de noche", "Mañana en el bosque")) {
            val p = Presets.scenes.firstOrNull { it.name == n }
            assertTrue(n, p != null)
            for (k in p!!.values.keys) {
                val id = k.substringBefore('.')
                assertTrue("$n usa un efecto que no existe: $id", Effects.all.any { it.id == id })
            }
        }
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        assertTrue("las escenas colocan horizonte y sol solos", vm.substringAfter("fun applyPreset(").substringBefore("\n    }").contains("autoScene("))
    }
}
