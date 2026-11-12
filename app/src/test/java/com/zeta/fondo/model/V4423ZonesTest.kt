package com.zeta.fondo.model

import com.zeta.fondo.engine.RenderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.23 · Zonas con forma (círculo / rectángulo) que se arrastran. */
class V4423ZonesTest {
    private val circle = mapOf("rain.on" to 1f, "rain.ownedMask" to 4f, "rain.mask" to 4f,
        "rain.zshape" to 1f, "rain.zx" to .4f, "rain.zy" to .3f, "rain.zw" to .2f, "rain.zh" to .2f,
        "rain.zrot" to 0f, "rain.zsoft" to 0f)

    @Test fun formaDentroYFuera() {
        val sh = ZoneShapes.of(circle, "rain")!!
        assertEquals(1f, ZoneShapes.value(sh, .4f, .3f, .5625f), 1e-4f)
        assertEquals(0f, ZoneShapes.value(sh, .9f, .9f, .5625f), 1e-4f)
        // Círculo REDONDO en una imagen vertical: el radio en y (fracción de alto) = .2 · aspecto.
        assertTrue(ZoneShapes.inside(sh, .4f, .3f + .2f * .5625f * .95f, .5625f))
        assertFalse(ZoneShapes.inside(sh, .4f, .3f + .2f * .5625f * 1.05f, .5625f))
        assertTrue(ZoneShapes.inside(sh, .4f + .19f, .3f, .5625f))
        // Borde suave: a mitad del borde vale entre 0 y 1.
        val soft = sh.copy(soft = 1f)
        val mid = ZoneShapes.value(soft, .4f + .1f, .3f, .5625f)
        assertTrue(mid > .05f && mid < .95f)
        // Rectángulo girado 90°: ancho y alto se intercambian.
        val rect = sh.copy(type = ZoneShapes.RECT, hw = .3f, hh = .05f, rotDeg = 90f)
        assertTrue(ZoneShapes.inside(rect, .4f, .3f + .25f * 1f, 1f))
        assertFalse(ZoneShapes.inside(rect, .4f + .25f, .3f, 1f))
    }

    @Test fun esquinasYCaja() {
        val sh = ZoneShapes.of(circle, "rain")!!.copy(rotDeg = 30f)
        val cs = ZoneShapes.corners(sh, 1f)
        assertEquals(4, cs.size)
        val cx = cs.map { it.first }.average().toFloat(); val cy = cs.map { it.second }.average().toFloat()
        assertEquals(.4f, cx, 1e-5f); assertEquals(.3f, cy, 1e-5f)
        val b = ZoneShapes.bounds(sh, 1f)
        for ((u, v) in cs) { assertTrue(u >= b[0] - 1e-6f || u < 0f); assertTrue(v <= b[3] + 1e-6f || v > 1f) }
    }

    @Test fun soloCuentaConElEfectoEncendidoYSuZonaPropia() {
        val u = ZoneShapes.uniforms(circle)
        assertTrue(ZoneShapes.hasShape(u, 4))
        assertEquals(.4f, u[16], 0f); assertEquals(.2f, u[18], 0f)
        assertEquals(1f, u[48 + 16 + 2], 0f)
        for (s in (0..11) - 4) assertFalse(ZoneShapes.hasShape(u, s))
        assertFalse(ZoneShapes.hasShape(ZoneShapes.uniforms(circle + ("rain.on" to 0f)), 4))
        assertFalse(ZoneShapes.hasShape(ZoneShapes.uniforms(circle + ("rain.mask" to -2f)), 4))     // «Toda la imagen»
        assertFalse(ZoneShapes.hasShape(ZoneShapes.uniforms(circle + ("rain.zshape" to 0f)), 4))
        // Nunca en Proteger ni en las zonas azul / verde (las comparten otros efectos).
        assertFalse(ZoneShapes.hasShape(ZoneShapes.uniforms(circle + ("rain.ownedMask" to 1f) + ("rain.mask" to 1f)), 1))
        assertNull(ZoneShapes.of(mapOf("x.zshape" to 7f), "x"))
    }

    @Test fun renderParamsLasLleva() {
        val p = RenderParams.from(Effects.defaults() + circle, .5625f, 6f)
        assertTrue(ZoneShapes.hasShape(p.zones, 4))
        val q = RenderParams.from(Effects.defaults(), .5625f, 6f)
        assertTrue(q.zones.all { it == 0f })
    }

    @Test fun laFormaEsDelEfectoEnteroYNoSeAnima() {
        for (k in ZoneShapes.suffixes) {
            assertFalse(k, ClipEdits.isClipKey("rain", "rain.$k"))
            assertFalse(k, Keyframes.animatable("rain.$k"))
        }
        val init = ZoneShapes.initial("smoke", ZoneShapes.RECT, .5625f)
        assertNotNull(ZoneShapes.of(init, "smoke"))
        assertEquals(.3f, init["smoke.zw"]!!, 1e-5f)
        assertTrue(ZoneShapes.initial("smoke", 1, 1.7778f)["smoke.zw"]!! < .2f)   // horizontal: cabe en el alto
    }

    @Test fun elShaderLeeLasZonasEnTodasLasMascaras() {
        val frag = File("src/main/assets/shaders/fondo.frag").takeIf { it.isFile }
            ?: File("app/src/main/assets/shaders/fondo.frag")
        val s = frag.readText()
        assertTrue(s.contains("uniform vec4 uZoneA[12];"))
        assertTrue(s.contains("if (layer >= 3 && layer < 12) m = max(m, zoneShape(uv, layer));"))
        assertTrue(s.contains("maskLayer(uv, slot)"))
        // Toda lectura de máscaras pasa por maskLayer (una sola lectura directa del atlas).
        assertEquals(1, Regex("texture\\(uMasks").findAll(s).count())
        val lite = File(frag.parentFile, "lite.frag").readText()
        assertTrue(lite.contains("zoneShape(uv, uActiveMask)"))
    }
}
