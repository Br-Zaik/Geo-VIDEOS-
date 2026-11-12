package com.zeta.fondo.engine

import com.zeta.fondo.model.Effects
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v46 · Recorte de personaje (isnetis) y relleno del fondo para el 2.5D. */
class V46CutoutTest {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }

    @Test fun letterboxMatchesSkyTntInference() {
        // inference.py: h > w → (s, int(s*w/h)); relleno centrado con división entera
        assertArrayEquals(intArrayOf(719, 1024, 152, 0), CutoutMath.letterbox(435, 619))
        assertArrayEquals(intArrayOf(1024, 576, 0, 224), CutoutMath.letterbox(1920, 1080))
        assertArrayEquals(intArrayOf(1024, 1024, 0, 0), CutoutMath.letterbox(500, 500))
    }

    @Test fun chwPutsTheImageInsideThePaddedSquare() {
        val box = intArrayOf(2, 1, 1, 2) // imagen 2x1 en (1,2) de un cuadrado 4x4
        val chw = CutoutMath.toChw(intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()), box, 4)
        assertEquals(48, chw.size)
        assertEquals(1f, chw[2 * 4 + 1], 0f)          // R del píxel (1,2)
        assertEquals(1f, chw[32 + 2 * 4 + 2], 0f)     // B del píxel (2,2)
        assertEquals(0f, chw[0], 0f)                  // relleno negro
    }

    @Test fun cropMaskReadsOnlyTheImageRegionAndSoftensTheEdge() {
        val s = 4
        val prob = FloatArray(s * s)
        // región de imagen: columnas 1..2, filas 0..3; personaje a la izquierda
        for (y in 0 until 4) { prob[y * s + 1] = 1f; prob[y * s + 2] = 0f; prob[y * s + 0] = 1f; prob[y * s + 3] = 1f }
        val m = CutoutMath.cropMask(prob, intArrayOf(2, 4, 1, 0), 2, 4, s = s)
        assertEquals(255, m[0].toInt() and 0xFF)
        assertEquals(0, m[1].toInt() and 0xFF)        // el relleno (columna 3 = 1) no se cuela
        val soft = CutoutMath.cropMask(FloatArray(16) { .5f }, intArrayOf(4, 4, 0, 0), 4, 4, s = 4)
        assertEquals(128f, (soft[5].toInt() and 0xFF).toFloat(), 1f)
    }

    @Test fun coverageAndUncertainty() {
        val clean = ByteArray(100) { if (it < 30) 255.toByte() else 0 }
        assertEquals(.3f, CutoutMath.coverage(clean), 1e-6f)
        assertEquals(0f, CutoutMath.uncertainty(clean), 0f)
        val grey = ByteArray(100) { 128.toByte() }
        assertEquals(1f, CutoutMath.uncertainty(grey), 0f)
    }

    @Test fun dilateGrowsTheHoleByRadius() {
        val w = 9; val h = 9
        val m = ByteArray(w * h); m[4 * w + 4] = 255.toByte()
        val d = CutoutMath.dilate(m, w, h, 2)
        assertEquals(255, d[4 * w + 6].toInt() and 0xFF)
        assertEquals(0, d[4 * w + 7].toInt() and 0xFF)
        assertEquals(255, d[2 * w + 2].toInt() and 0xFF) // cuadrado (Chebyshev)
        assertEquals(0, d[1 * w + 4].toInt() and 0xFF)
    }

    @Test fun fillUsesTheSurroundingsAndKeepsKnownPixels() {
        val w = 32; val h = 16
        val px = IntArray(w * h) { i -> if (i % w < 16) 0xFFC80000.toInt() else 0xFF0000C8.toInt() }
        val hole = ByteArray(w * h)
        // personaje blanco en el centro: no debe aparecer en el relleno
        for (y in 4 until 12) for (x in 12 until 20) { hole[y * w + x] = 255.toByte(); px[y * w + x] = -1 }
        val out = CutoutMath.fill(px, w, h, hole)
        assertEquals(px[0], out[0]); assertEquals(px[w - 1], out[w - 1])
        for (y in 4 until 12) for (x in 12 until 20) {
            val c = out[y * w + x]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            assertTrue("sin blanco del personaje en ($x,$y): ${Integer.toHexString(c)}", g < 20 && r + b in 150..260)
        }
        // lado izquierdo del hueco más rojo, derecho más azul
        val l = out[8 * w + 12]; val r = out[8 * w + 19]
        assertTrue(((l shr 16) and 0xFF) > (l and 0xFF)); assertTrue((r and 0xFF) > ((r shr 16) and 0xFF))
        // uniforme alrededor → exactamente ese color
        val flat = CutoutMath.fill(IntArray(64) { 0xFF336699.toInt() }.also { it[27] = -1 }, 8, 8, ByteArray(64).also { it[27] = -1 })
        assertEquals(0xFF336699.toInt(), flat[27])
    }

    @Test fun plateSwitchReachesTheShader() {
        val on = RenderParams.from(Effects.defaults() + mapOf("parallax.on" to 1f), 1f)
        assertEquals(1f, on.studio.getValue("uParallaxStyle")[3], 0f)
        val off = RenderParams.from(Effects.defaults() + mapOf("parallax.on" to 1f, "parallax.plate" to 0f), 1f)
        assertEquals(0f, off.studio.getValue("uParallaxStyle")[3], 0f)
        val frag = file("src/main/assets/shaders/fondo.frag").readText()
        assertTrue(frag.contains("uniform sampler2D uPlate;"))
        assertTrue(frag.contains("gPlate = 1.0 - maskLayer("))
    }

}
