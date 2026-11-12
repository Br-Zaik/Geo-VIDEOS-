package com.zeta.fondo.model

import com.zeta.fondo.engine.LayerCompositor
import com.zeta.fondo.engine.LayerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** v44.23 · Capas de imagen: modos de mezcla, transformaciones, claves, guardado y pantalla. */
class V4423LayersTest {
    private fun c(r: Int, g: Int, b: Int) = floatArrayOf(r / 255f, g / 255f, b / 255f)
    private fun near(a: FloatArray, b: FloatArray, eps: Float = 1e-4f) =
        (0..2).all { abs(a[it] - b[it]) <= eps }

    @Test fun losModosSonLosDeAlightEnSuOrden() {
        assertEquals(29, BlendModes.COUNT)
        assertEquals(BlendModes.COUNT, BlendModes.all.size)
        BlendModes.all.forEachIndexed { i, m -> assertEquals(i, m.id); assertTrue(m.help.length > 20) }
        assertEquals(listOf("Multiplicar", "Oscurecer", "Color más oscuro", "Subexponer color", "Subexposición lineal"),
            BlendModes.inGroup(1).map { it.name })
        assertEquals(listOf("Pantalla", "Aclarar", "Color más claro", "Sobreexponer color", "Sobreexposición lineal"),
            BlendModes.inGroup(2).map { it.name })
        assertEquals(listOf("Diferencia", "Exclusión", "Sustraer", "Dividir"), BlendModes.inGroup(4).map { it.name })
        assertEquals(listOf("Tono", "Saturación", "Color", "Luminancia"), BlendModes.inGroup(5).map { it.name })
        assertEquals(listOf("Máscara", "Excluir"), BlendModes.inGroup(6).map { it.name })
        assertEquals(8, BlendModes.inGroup(3).size)
        assertEquals(BlendModes.groups.size, BlendModes.groupHelp.size)
    }

    @Test fun lasReglasDeCadaGrupoSeCumplen() {
        val cb = c(120, 80, 200)
        // Aclarar: lo negro desaparece.
        for (m in listOf(BlendModes.SCREEN, BlendModes.LIGHTEN, BlendModes.COLOR_DODGE, BlendModes.LINEAR_DODGE))
            assertTrue(BlendModes.byId(m).name, near(BlendModes.blend(m, cb, c(0, 0, 0)), cb))
        // Oscurecer: lo blanco desaparece.
        for (m in listOf(BlendModes.MULTIPLY, BlendModes.DARKEN, BlendModes.COLOR_BURN, BlendModes.LINEAR_BURN))
            assertTrue(BlendModes.byId(m).name, near(BlendModes.blend(m, cb, c(255, 255, 255)), cb))
        // Contraste: el gris medio casi no cambia nada.
        val gray = floatArrayOf(.5f, .5f, .5f)
        for (m in listOf(BlendModes.SOFT_LIGHT, BlendModes.HARD_LIGHT, BlendModes.LINEAR_LIGHT, BlendModes.SOFT_OVERLAY))
            assertTrue(BlendModes.byId(m).name, near(BlendModes.blend(m, cb, gray), cb, .01f))
        // Diferencia con negro no cambia; con blanco invierte.
        assertTrue(near(BlendModes.blend(BlendModes.DIFFERENCE, cb, c(0, 0, 0)), cb))
        assertTrue(near(BlendModes.blend(BlendModes.DIFFERENCE, cb, c(255, 255, 255)), floatArrayOf(1 - cb[0], 1 - cb[1], 1 - cb[2])))
        // Dividir entre blanco no cambia.
        assertTrue(near(BlendModes.blend(BlendModes.DIVIDE, cb, c(255, 255, 255)), cb))
        // Luminancia/Color conservan lo que dicen.
        val lum = { x: FloatArray -> BlendModes.lum(x[0], x[1], x[2]) }
        assertEquals(lum(cb), lum(BlendModes.blend(BlendModes.COLOR, cb, c(255, 0, 0))), .003f)
        assertEquals(lum(c(30, 220, 90)), lum(BlendModes.blend(BlendModes.LUMINOSITY, cb, c(30, 220, 90))), .003f)
        // Saturación con una imagen gris quita el color.
        val g = BlendModes.blend(BlendModes.SATURATION, cb, gray)
        assertTrue(abs(g[0] - g[1]) < .002f && abs(g[1] - g[2]) < .002f)
        // Mezcla dura: solo 0 o 1.
        for (v in BlendModes.blend(BlendModes.HARD_MIX, cb, c(200, 10, 90))) assertTrue(v == 0f || v == 1f)
    }

    @Test fun opacidadYMascaras() {
        val cb = c(100, 150, 200); val cs = c(255, 0, 0)
        for (m in 0 until BlendModes.COUNT - 2) assertTrue(near(BlendModes.composite(m, cb, cs, 1f, 0f), cb))
        assertTrue(near(BlendModes.composite(BlendModes.NORMAL, cb, cs, 1f, 1f), cs))
        assertTrue(near(BlendModes.composite(BlendModes.NORMAL, cb, cs, 0f, 1f), cb))
        // Máscara: fuera de la imagen (alfa 0) todo negro; dentro se ve lo de abajo.
        assertTrue(near(BlendModes.composite(BlendModes.MASK, cb, cs, 0f, 1f), floatArrayOf(0f, 0f, 0f)))
        assertTrue(near(BlendModes.composite(BlendModes.MASK, cb, cs, 1f, 1f), cb))
        assertTrue(near(BlendModes.composite(BlendModes.EXCLUDE, cb, cs, 1f, 1f), floatArrayOf(0f, 0f, 0f)))
        assertTrue(near(BlendModes.composite(BlendModes.EXCLUDE, cb, cs, 0f, 1f), cb))
        // Opacidad intermedia = mezcla lineal.
        val half = BlendModes.composite(BlendModes.MULTIPLY, cb, cs, 1f, .5f)
        val full = BlendModes.blend(BlendModes.MULTIPLY, cb, cs)
        assertTrue(near(half, FloatArray(3) { (cb[it] + full[it]) / 2f }))
    }

    @Test fun elShaderUsaLasMismasConstantesYCaminos() {
        assertTrue(LayerCompositor.FRAG.contains(BlendModes.HARD_MIX_EDGE.toString()))
        assertTrue(LayerCompositor.FRAG.contains(BlendModes.SAT_EPS.toString()))
        assertTrue(LayerCompositor.FRAG.contains("texelFetch(uBack"))
        // Pantalla por hardware solo sin «Calidad cine» (el alfa de la luz necesita la fórmula).
        assertTrue(LayerCompositor.hardwarePath(BlendModes.SCREEN, false))
        assertFalse(LayerCompositor.hardwarePath(BlendModes.SCREEN, true))
        for (m in listOf(0, 1, 27, 28)) { assertTrue(LayerCompositor.hardwarePath(m, true)); assertTrue(LayerCompositor.hardwarePath(m, false)) }
        for (m in (2..26) - 6) assertFalse(LayerCompositor.hardwarePath(m, false))
        // Cada modo de la lista tiene su rama en el shader.
        for (m in 1..26) assertTrue("modo $m", LayerCompositor.FRAG.contains("if (m == $m)"))
    }

    private fun layer(vararg keys: Pair<String, List<Keyframe>>) = ImageLayer(7, "Luz", "layers/a.png", 2f,
        x = .4f, y = .3f, sx = .5f, sy = .5f, rot = 30f, keys = keys.toMap())

    @Test fun tamanoYGiroEnPixelesDeLaBase() {
        val f = ImageLayers.frame(layer(), 0f, 6f, 1000, 2000)
        assertEquals(.4f, f.x, 1e-6f); assertEquals(.3f, f.y, 1e-6f)
        assertEquals(250f, f.halfW, 1e-3f)           // .5 · 1000 / 2
        assertEquals(125f, f.halfH, 1e-3f)           // .5 · 1000 / aspecto 2 / 2
        assertEquals(Math.toRadians(30.0).toFloat(), f.rot, 1e-5f)
        // Las esquinas vuelven a ser (±1, ±1) en coordenadas locales.
        for ((i, p) in ImageLayers.corners(f, 1000, 2000).withIndex()) {
            val l = ImageLayers.localOf(f, p.first * .999f + f.x * .001f, p.second * .999f + f.y * .001f, 1000, 2000)
            assertNotNull("esquina $i", l)
        }
        assertNull(ImageLayers.localOf(f, .99f, .99f, 1000, 2000))
        assertNotNull(ImageLayers.localOf(f, .4f, .3f, 1000, 2000))
    }

    @Test fun fotogramasClaveYEdicion() {
        val l = layer("x" to listOf(Keyframe(0f, .2f), Keyframe(2f, .8f)))
        assertEquals(.5f, ImageLayers.frame(l, 1f / 6f, 6f, 100, 100).x, 1e-4f)   // 1 s de 2 s
        assertEquals(.8f, ImageLayers.frame(l, .9f, 6f, 100, 100).x, 1e-4f)
        // Editar una propiedad animada crea una clave en el cabezal; una fija cambia su valor.
        val e = ImageLayers.edit(l, 4f, mapOf("x" to .1f, "y" to .6f))
        assertEquals(3, e.keys["x"]!!.size)
        assertEquals(.6f, e.y, 0f)
        assertTrue(e.keys["y"].isNullOrEmpty())
        // Poner y quitar claves (el rombo).
        val k = ImageLayers.putKeys(e, 1f, mapOf("rot" to 45f))
        assertTrue(ImageLayers.hasKeyAt(k, 1.02f, listOf("rot")))
        assertFalse(ImageLayers.hasKeyAt(ImageLayers.removeKeys(k, 1f, listOf("rot")), 1f, listOf("rot")))
        assertEquals(listOf(0f, 1f, 2f, 4f), ImageLayers.keyTimes(k))
        val curved = ImageLayers.setCurve(k, 0f, listOf("x"), Keyframes.EASE_IN_OUT)
        assertEquals(Keyframes.EASE_IN_OUT, curved.keys["x"]!!.first().curve)
    }

    @Test fun losMovimientosCierranElBucle() {
        for (m in ImageLayers.motionNames.indices) for (cyc in listOf(1, 3)) {
            val l = layer().copy(motion = m, motionAmt = .8f, motionCycles = cyc)
            val a = ImageLayers.frame(l, 0f, 6f, 1000, 1000)
            val b = ImageLayers.frame(l, 1f, 6f, 1000, 1000)
            val rotDiff = ((b.rot - a.rot) / (2 * Math.PI)).let { it - Math.round(it) }
            assertEquals("${ImageLayers.motionNames[m]} x", a.x, b.x, 1e-4f)
            assertEquals("${ImageLayers.motionNames[m]} y", a.y, b.y, 1e-4f)
            assertEquals("${ImageLayers.motionNames[m]} tamaño", a.halfW, b.halfW, 1e-2f)
            assertEquals("${ImageLayers.motionNames[m]} opacidad", a.opacity, b.opacity, 1e-3f)
            assertEquals("${ImageLayers.motionNames[m]} giro", 0.0, rotDiff, 1e-4)
        }
        // Y de verdad se mueven a mitad del vídeo.
        assertTrue(abs(ImageLayers.frame(layer().copy(motion = ImageLayers.MOTION_FLOAT), .25f, 6f, 100, 100).y - .3f) > .01f)
        assertTrue(ImageLayers.frame(layer().copy(motion = ImageLayers.MOTION_FLICKER, motionAmt = 1f), .37f, 6f, 100, 100).opacity < 1f)
    }

    @Test fun tramoDeTiempo() {
        val l = layer().copy(startSec = 2f, durSec = 2f)
        assertEquals(0f, ImageLayers.frame(l, 1f / 6f, 6f, 100, 100).opacity, 0f)
        assertEquals(1f, ImageLayers.frame(l, 3f / 6f, 6f, 100, 100).opacity, 1e-4f)
        assertEquals(0f, ImageLayers.frame(l, 5f / 6f, 6f, 100, 100).opacity, 0f)
        assertEquals(1f, ImageLayers.spanGate(layer(), 5f, 6f), 0f)
    }

    @Test fun guardarYLeer() {
        val a = layer("opacity" to listOf(Keyframe(0f, 1f), Keyframe(3f, .2f, Keyframes.EASE_OUT))).copy(
            blend = BlendModes.SCREEN, hidden = true, behind = true, flipX = true, motion = 5, motionAmt = .3f,
            motionCycles = 2, startSec = 1f, durSec = 3f, name = "Destello \"naranja\"")
        val back = LayerJson.fromJson(org.json.JSONArray(LayerJson.toJson(listOf(a, a.copy(id = 8, blend = 0))).toString()))
        assertEquals(2, back.size)
        val r = back[0]
        assertEquals(a.copy(x = r.x, y = r.y, sx = r.sx, sy = r.sy, rot = r.rot, opacity = r.opacity, aspect = r.aspect), r)
        assertEquals(a.x, r.x, 1e-6f); assertEquals(a.rot, r.rot, 1e-5f)
        // Rutas peligrosas o raras se ignoran; ids repetidos se corrigen.
        assertFalse(LayerJson.safeFile("../x.png"))
        assertFalse(LayerJson.safeFile("layers/../../x.png"))
        assertFalse(LayerJson.safeFile("other/x.png"))
        assertTrue(LayerJson.safeFile("layers/l_1_2.png"))
        val bad = org.json.JSONArray("""[{"file":"../evil.png"},{"id":3,"file":"layers/a.png","blend":99,"op":7},{"id":3,"file":"layers/b.png"}]""")
        val ok = LayerJson.fromJson(bad)
        assertEquals(2, ok.size)
        assertEquals(BlendModes.COUNT - 1, ok[0].blend)
        assertEquals(1f, ok[0].opacity, 0f)
        assertTrue(ok[0].id != ok[1].id)
        assertTrue(LayerJson.fromJson(null).isEmpty())
    }

    @Test fun cajaEnPantallaCubreLaCapaConEncuadreGiroYPng() {
        val f = ImageLayers.frame(layer(), 0f, 6f, 1080, 1920)
        val views = listOf(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(.2f, .1f, .5f, .5f))
        for (view in views) for (flip in listOf(false, true)) for (rot in listOf(false, true)) {
            val (vw, vh) = if (rot) 1920 to 1080 else 1080 to 1920
            val box = LayerMath.screenBox(f, 1080, 1920, 10, 20, vw, vh, view, flip, rot) ?: continue
            for ((u, v) in ImageLayers.corners(f, 1080, 1920)) {
                // Fórmula del shader: ventana → uv. Debe volver a la misma esquina.
                val (wx, wy) = LayerMath.uvToWindow(u, v, 10, 20, vw, vh, view, flip, rot)
                val vsx = (wx - 10) / vw; val vsy = (wy - 20) / vh
                var sx = vsx; var sy = if (flip) vsy else 1f - vsy
                if (rot) { val nx = 1f - sy; val ny = sx; sx = nx; sy = ny }
                assertEquals(u, view[0] + sx * view[2], 1e-3f)
                assertEquals(v, view[1] + sy * view[3], 1e-3f)
                if (wx in 10f..(10f + vw) && wy in 20f..(20f + vh)) {
                    assertTrue(wx >= box[0] && wx <= box[0] + box[2])
                    assertTrue(wy >= box[1] && wy <= box[1] + box[3])
                }
            }
        }
        // Fuera de la vista → null (no se dibuja ni se copia nada).
        val far = ImageLayers.frame(layer().copy(x = 5f, y = 5f, sx = .05f, sy = .05f), 0f, 6f, 100, 100)
        assertNull(LayerMath.screenBox(far, 100, 100, 0, 0, 100, 100, floatArrayOf(0f, 0f, 1f, 1f), false, false))
    }

    @Test fun hayMiniaturaDeEjemploParaCadaModo() {
        val dir = listOf(java.io.File("src/main/assets/thumbs"), java.io.File("app/src/main/assets/thumbs")).first { it.isDirectory }
        for (m in 0 until BlendModes.COUNT) {
            val f = java.io.File(dir, "blend_%02d.webp".format(m))
            assertTrue(f.name, f.isFile && f.length() in 500L..20_000L)
        }
    }

    @Test fun escalasIniciales() {
        // Una capa cuadrada en una base vertical 9:16: cabe holgada (60 % del ancho).
        assertEquals(.6f, ImageLayers.initialScale(1f, 1080, 1920), 1e-4f)
        // Una capa muy ancha en una base cuadrada también.
        assertEquals(.6f, ImageLayers.initialScale(3f, 1000, 1000), 1e-4f)
        // Una capa alta: limitada por la altura.
        assertTrue(ImageLayers.initialScale(.25f, 1000, 1000) < .2f)
        // Llenar cubre ancho y alto.
        for (a in listOf(.3f, 1f, 3f)) {
            val s = ImageLayers.coverScale(a, 1080, 1920)
            assertTrue(s >= 1f - 1e-5f)
            assertTrue(s * 1080 / a >= 1920 - .5f)
        }
        assertEquals("Destello", ImageLayers.displayName("content://x/Destello.png", 1))
        assertEquals("Imagen 3", ImageLayers.displayName(null, 3))
    }
}
