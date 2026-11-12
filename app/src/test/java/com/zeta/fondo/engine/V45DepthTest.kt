package com.zeta.fondo.engine

import com.zeta.fondo.model.Effects
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v45 · Profundidad IA del 2.5D: preprocesado, normalización, capas, caché y contrato con el shader. */
class V45DepthTest {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }

    @Test fun inputSizeKeepsAspectAndPatchMultiples() {
        val a = DepthMath.inputSize(435, 619)
        assertEquals(518, a[1])
        assertEquals(0, a[0] % 14); assertEquals(0, a[1] % 14)
        assertEquals(435f / 619f, a[0].toFloat() / a[1], 0.03f)
        val b = DepthMath.inputSize(4000, 3000)
        assertEquals(518, b[0]); assertEquals(392, b[1])
        val tiny = DepthMath.inputSize(2000, 10)
        assertTrue(tiny[1] >= 14)
    }

    @Test fun imagenetNormalizationIsChannelPlanar() {
        val px = intArrayOf(0xFFFF0000.toInt(), 0xFF000000.toInt())
        val chw = DepthMath.toChw(px, 2, 1)
        assertEquals(6, chw.size)
        assertEquals((1f - .485f) / .229f, chw[0], 1e-5f) // R del píxel 0
        assertEquals((0f - .485f) / .229f, chw[1], 1e-5f) // R del píxel 1
        assertEquals((0f - .456f) / .224f, chw[2], 1e-5f) // G del píxel 0
    }

    @Test fun normalizationIsRobustToOutliersAndNearIsBright() {
        val raw = FloatArray(1000) { it.toFloat() }
        raw[999] = 1e9f // un píxel absurdo no debe aplanar el mapa
        val m = DepthMath.normalize(raw)
        assertEquals(0, m[0].toInt() and 0xFF)
        assertEquals(255, m[999].toInt() and 0xFF)
        assertEquals(128f, (m[500].toInt() and 0xFF).toFloat(), 6f)
        val flat = DepthMath.normalize(FloatArray(50) { 3f })
        assertTrue(flat.all { (it.toInt() and 0xFF) == 128 })
        val nan = DepthMath.normalize(floatArrayOf(Float.NaN, 0f, 1f, 2f))
        assertEquals(0, nan[0].toInt() and 0xFF)
    }

    @Test fun bilinearResizeKeepsCornersAndRange() {
        val src = byteArrayOf(0, 255.toByte(), 255.toByte(), 0)
        val r = DepthMath.resize(src, 2, 2, 8, 8)
        assertEquals(64, r.size)
        assertEquals(0, r[0].toInt() and 0xFF)
        assertEquals(255, r[7].toInt() and 0xFF)
        assertArrayEquals(src, DepthMath.resize(src, 2, 2, 2, 2))
    }

    @Test fun layersMatchTheShaderQuantizationThresholds() {
        // depthQuant del shader: frontera entre capas en x = k + 0.5 con x = d * (L-1)
        assertEquals(-1, DepthMath.layerOf(200, 1))
        assertEquals(0, DepthMath.layerOf(60, 3)); assertEquals(1, DepthMath.layerOf(70, 3))
        assertEquals(1, DepthMath.layerOf(190, 3)); assertEquals(2, DepthMath.layerOf(192, 3))
        assertEquals(3, DepthMath.layerOf(255, 4)); assertEquals(0, DepthMath.layerOf(0, 2))
        val shares = DepthMath.layerShares(ByteArray(100) { if (it < 30) 0 else 255.toByte() }, 2)
        assertEquals(.3f, shares[0], 1e-6f); assertEquals(.7f, shares[1], 1e-6f)
    }

    @Test fun lowContrastWarnsForFlatMaps() {
        assertTrue(DepthMath.contrast(ByteArray(100) { 128.toByte() }) < DepthMath.LOW_CONTRAST)
        assertTrue(DepthMath.contrast(ByteArray(100) { if (it % 2 == 0) 0 else 255.toByte() }) > DepthMath.LOW_CONTRAST)
    }

    @Test fun cacheMetaRoundTripAndImageStamp() {
        val m = DepthMath.Meta("depth_anything_v2_small", 364, 518, "abc-364x518", 1234, .31f)
        assertEquals(m, DepthMath.Meta.decode(m.encode()))
        assertNull(DepthMath.Meta.decode("version=99\nmodel=x"))
        val a = IntArray(16) { it }
        val b = a.copyOf().also { it[5] = 99 }
        assertEquals(DepthMath.stamp(a, 4, 4), DepthMath.stamp(a.copyOf(), 4, 4))
        assertNotEquals(DepthMath.stamp(a, 4, 4), DepthMath.stamp(b, 4, 4))
        assertNotEquals(DepthMath.stamp(a, 4, 4), DepthMath.stamp(a, 2, 8))
    }

    @Test fun parallaxSettingsReachTheDepthUniform() {
        val keys = Effects.all.first { it.id == "parallax" }.params.map { it.key }
        assertTrue(keys.containsAll(listOf("ai", "layers", "fix")))
        val p = RenderParams.from(Effects.defaults() + mapOf("parallax.on" to 1f, "parallax.layers" to 4f, "parallax.fix" to .8f), 1f)
        // x = IA (FondoGl lo apaga sin mapa), y = capas, z = fuerza de Acercar/Alejar, w = ver mapa (solo vista previa)
        assertArrayEquals(floatArrayOf(1f, 4f, .8f, 0f), p.studio.getValue("uDepthInfo"), 0f)
        val manual = RenderParams.from(Effects.defaults() + mapOf("parallax.on" to 1f, "parallax.ai" to 0f), 1f)
        assertEquals(0f, manual.studio.getValue("uDepthInfo")[0], 0f)
    }

    @Test fun shaderDeclaresDepthSamplerAndBlockMember() {
        val frag = file("src/main/assets/shaders/fondo.frag").readText()
        val block = frag.substringAfter("uniform ZetaParams {").substringBefore("};")
        assertTrue(block.contains("vec4 uDepthInfo;"))
        assertTrue(frag.contains("uniform sampler2D uDepth;"))
        assertTrue("el visor del mapa vive dentro de FX_PARALLAX",
            frag.substringAfter("#ifdef FX_PARALLAX\n    // v45: «Ver mapa").isNotEmpty())
    }

}
