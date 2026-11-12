package com.zeta.fondo.engine

import com.zeta.fondo.model.ImageLayer
import com.zeta.fondo.model.ImageLayers
import com.zeta.fondo.model.LayerJson
import com.zeta.fondo.model.LayerParallax
import com.zeta.fondo.model.LayerFrame
import com.zeta.fondo.model.OpenSourceLicenses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.24 · Licencias simples, capas visibles al abrir, 2.5D sin caras y capas con profundidad. */
class V4424Test {
    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }

    // ---------------------------------------------------------------- licencias

    @Test fun everyLicenseHasItsFullText() {
        for (it in OpenSourceLicenses.items) {
            val f = file("src/main/assets/${it.file}")
            assertTrue(it.file, f.readText().length > 400)
        }
        val names = OpenSourceLicenses.items.joinToString { it.name }
        assertTrue(names.contains("Real-ESRGAN")); assertTrue(names.contains("ONNX Runtime"))
        assertTrue(names.contains("lbpcascade_animeface")); assertTrue(names.contains("Jetpack"))
        assertTrue(file("src/main/assets/licenses/Apache-2.0.txt").readText().contains("Apache License"))
    }

    @Test fun licensesScreenIsSimple() {
        val ui = file("src/main/java/com/zeta/fondo/ui/LicensesPanel.kt").readText()
        assertFalse("sin SHA-256", ui.contains("SHA-256"))
        assertFalse(ui.contains("MI-GAN"))
        assertTrue(ui.contains("OpenSourceLicenses.items"))
        assertFalse(file("src/main/assets/models/LICENSES.json").readText().contains("MI-GAN"))
    }

    @Test fun reflowJoinsWrappedLines() {
        val r = OpenSourceLicenses.reflow("Hola\n  mundo\n\nOtro\r\npárrafo\n")
        assertEquals("Hola mundo\n\nOtro párrafo", r)
    }

    // ---------------------------------------------------------------- capas y efectos al abrir

    @Test fun openingNeverStartsInPaintMode() {
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        val open = vm.substringAfter("fun openProject(").substringBefore("fun saveProject(")
        assertTrue(open.contains("hub.showMask = showMasks && !playing && tool.isBrush"))
        assertTrue(vm.substringAfter("fun toggleMasks()").substringBefore("\n    }").contains("tool.isBrush"))
        assertTrue(file("src/main/java/com/zeta/fondo/engine/RenderHub.kt").readText().contains("var showMask = false"))
    }

    // ---------------------------------------------------------------- 2.5D

    private fun portraitWithBusyBackground(w: Int, h: Int): IntArray {
        val rnd = java.util.Random(7)
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            // fondo con detalle (barras verticales grises y azuladas, como un gimnasio)
            val bar = (x / 7) % 3
            var r = 150 + bar * 18 + rnd.nextInt(12); var g = 160 + bar * 15 + rnd.nextInt(12); var b = 180 + bar * 10 + rnd.nextInt(12)
            // personaje de espaldas: piel y pantalón oscuro, con contorno de tinta
            val cx = w / 2f
            val torso = y > h * .18f && y < h * .62f && kotlin.math.abs(x - cx) < w * (.16f + .10f * (y - h * .18f) / h)
            val legs = y >= h * .62f && kotlin.math.abs(x - cx) < w * .2f
            val head = kotlin.math.hypot(x - cx, y - h * .12f) < w * .09f
            if (torso || head) { r = 225; g = 170; b = 135 }
            if (legs) { r = 35; g = 38; b = 48 }
            px[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return px
    }

    @Test fun findsACharacterWithoutFaceOnABusyBackground() {
        val w = 120; val h = 200
        val m = SubjectSegment.detect(portraitWithBusyBackground(w, h), w, h)
        assertNotNull(m); m!!
        assertTrue("torso ${m[(h * .4f).toInt() * w + w / 2]}", m[(h * .4f).toInt() * w + w / 2] > .7f)
        assertTrue("piernas", m[(h * .85f).toInt() * w + w / 2] > .7f)
        assertTrue("fondo", m[(h * .4f).toInt() * w + 4] < .3f)
    }

    @Test fun landscapesAndNoiseHaveNoCharacter() {
        val w = 120; val h = 200
        // paisaje: cielo degradado, montañas y mar que cubre todo el ancho de abajo
        val land = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val c = when {
                y > h * .6f -> 0x1E508C
                y > h * .42f + 12 * kotlin.math.sin(x / 9f) -> 0x463C6E
                else -> { val k = y.toFloat() / h; ((40 + 200 * k).toInt() shl 16) or ((90 + 70 * k).toInt() shl 8) or 200 }
            }
            (0xFF shl 24) or c
        }
        assertNull(DepthMath.subjectAuto(land, w, h))
        val rnd = java.util.Random(3)
        assertNull(SubjectSegment.detect(IntArray(96 * 96) { (0xFF shl 24) or rnd.nextInt(0xFFFFFF) }, 96, 96))
    }

    @Test fun backgroundSiluetteMustBeBelievable() {
        // El método antiguo (fondo liso) se usa solo si es creíble; si no, el nuevo.
        val dm = file("src/main/java/com/zeta/fondo/engine/DepthMath.kt").readText()
        assertTrue(dm.contains("subjectFromBackground(argb, w, h)?.takeIf { SubjectSegment.plausible(it, argb, w, h) }"))
        assertTrue(file("src/main/java/com/zeta/fondo/engine/DepthEstimator.kt").readText().contains("DepthMath.subjectAuto("))
        assertEquals("los mapas viejos se rehacen", 2, DepthMath.Meta.VERSION)
    }

    @Test fun parallaxNeverStandsStillWhileTheMapIsComputed() {
        val frag = file("src/main/assets/shaders/fondo.frag").readText()
        val fn = frag.substringAfter("float parallaxDepth(vec2 fu) {").substringBefore("\n}")
        assertTrue(fn.contains("if (uDepthInfo.x > 0.1)"))
        assertTrue(fn.contains("clamp(fu.y + uDepthInfo.z * (front - back), 0.0, 1.0)"))
        val gl = file("src/main/java/com/zeta/fondo/engine/FondoGl.kt").readText()
        assertTrue(gl.contains("(if (depthReady) 1f else DEPTH_PENDING)"))
        // más visible por defecto
        val def = com.zeta.fondo.model.Effects.all.first { it.id == "parallax" }.params.first { it.key == "amount" }
        assertEquals(.035f, def.def, 1e-6f)
    }

    // ---------------------------------------------------------------- capas con profundidad

    @Test fun layerDepthIsSavedAndSanitized() {
        val l = ImageLayer(1, "Sol", "layers/a.png", 1f, depth = .8f)
        val back = LayerJson.fromJson(org.json.JSONArray(LayerJson.toJson(listOf(l, l.copy(id = 2))).toString()))
        assertEquals(.8f, back[0].depth, 1e-6f)
        val auto = LayerJson.fromJson(org.json.JSONArray("[{\"file\":\"layers/b.png\"}]"))
        assertEquals("proyectos viejos: pegada", LayerParallax.AUTO, auto[0].depth, 0f)
        assertEquals(1f, ImageLayers.sanitize(l.copy(depth = 7f)).depth, 0f)
        assertEquals(LayerParallax.AUTO, ImageLayers.sanitize(l.copy(depth = Float.NaN)).depth, 0f)
    }

    @Test fun layerFollowsTheSameCameraAsTheShader() {
        // uParallax = cantidad, dirección (rad), ciclos, diferencia; uParallaxStyle = estilo, foco
        val par = floatArrayOf(.04f, 0f, 1f, .65f)
        val style = floatArrayOf(0f, 0f)
        assertNull(LayerParallax.cam(floatArrayOf(0f, 0f, 1f, .65f), style, .25f))
        val cam = LayerParallax.cam(par, style, .25f)!!       // sin(π/2) = 1: máximo a la derecha
        assertEquals(1f, cam.x, 1e-5f)
        val f = LayerFrame(.5f, .5f, 100f, 50f, 0f, 1f, false)
        val view = floatArrayOf(0f, 0f, 1f, 1f)
        val near = LayerParallax.apply(f, cam, LayerParallax.depthFor(1f, cam, false), view)
        val far = LayerParallax.apply(f, cam, LayerParallax.depthFor(0f, cam, false), view)
        assertTrue("cerca se mueve a un lado", near.x > .5f)
        assertTrue("lejos se mueve al otro", far.x < .5f)
        assertTrue("se amplía con el fondo", near.halfW > 100f)
        // misma fórmula que el shader: pantalla = (q - 0.5 + cam·amt·d)·over + 0.5
        val d = LayerParallax.depthFor(1f, cam, false)
        assertEquals((0f + cam.x * cam.amt * d) * cam.over + .5f, near.x, 1e-5f)
        // al inicio del bucle (fase 0) no hay desplazamiento lateral
        val c0 = LayerParallax.cam(par, style, 0f)!!
        assertEquals(.5f, LayerParallax.apply(f, c0, d, view).x, 1e-5f)
    }

    @Test fun pinnedLayerUsesTheSceneDepthAndProtectIsTheFocus() {
        val l = ImageLayer(1, "Sol", "layers/a.png", 1f)
        val f = LayerFrame(.3f, .2f, 10f, 10f, 0f, 1f, false)
        assertEquals(.123f, LayerParallax.rawDepth(l, f) { _, _ -> .123f }!!, 0f)
        assertEquals(.9f, LayerParallax.rawDepth(l.copy(depth = .9f), f) { _, _ -> .123f }!!, 0f)
        val cam = LayerParallax.cam(floatArrayOf(.04f, 0f, 1f, .65f), floatArrayOf(0f, .2f), .25f)!!
        assertEquals("zonas manuales: no se mueve", -.2f, LayerParallax.depthFor(null, cam, true), 1e-6f)
        val gl = file("src/main/java/com/zeta/fondo/engine/FondoGl.kt").readText()
        assertTrue(gl.contains("if (p > .5f) return Float.NaN"))
        assertTrue(file("src/main/java/com/zeta/fondo/engine/LayerCompositor.kt").readText().contains("move?.invoke(layer, f0) ?: f0"))
    }

    // ---------------------------------------------------------------- interfaz

    @Test fun interfaceFitsEveryPhone() {
        val main = file("src/main/java/com/zeta/fondo/MainActivity.kt").readText()
        assertTrue(main.contains("WindowInsets.systemBars.union(WindowInsets.displayCutout)"))
        assertTrue(main.contains("d.fontScale.coerceAtMost(MAX_FONT_SCALE)"))
        val tools = file("src/main/java/com/zeta/fondo/ui/EditorTools.kt").readText()
        assertTrue(tools.contains("widthIn(max = 560.dp)"))
        assertTrue(file("src/main/java/com/zeta/fondo/ui/EditorScreen.kt").readText().contains("Nuevo proyecto con otra imagen"))
    }
}
