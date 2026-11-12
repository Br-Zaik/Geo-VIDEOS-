package com.zeta.fondo.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** v47 · Caras/ojos, cielo/agua, tocar para seleccionar, sugerencias y superresolución por baldosas. */
class V47AssistTest {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private fun cascade() = AnimeFaceDetector.parse(file("src/main/assets/detect/lbpcascade_animeface.xml").readText())

    @Test fun cascadeLoadsCompletely() {
        val d = cascade()
        assertEquals(20, d.stageCount); assertEquals(642, d.featureCount)
        assertEquals(24, d.winW); assertEquals(24, d.winH)
    }

    @Test fun noFacesOnFlatOrNoiseImages() {
        val d = cascade()
        assertTrue(d.detect(ByteArray(160 * 120) { 128.toByte() }, 160, 120).isEmpty())
        val rnd = java.util.Random(3)
        assertTrue(d.detect(ByteArray(160 * 120) { rnd.nextInt(256).toByte() }, 160, 120).isEmpty())
    }

    @Test fun equalizeAndIntegralMatchDefinitions() {
        val eq = AnimeFaceDetector.equalizeHist(byteArrayOf(10, 10, 20, 30))
        assertEquals(0, eq[0].toInt() and 0xFF); assertEquals(128, eq[2].toInt() and 0xFF); assertEquals(255, eq[3].toInt() and 0xFF)
        val ii = AnimeFaceDetector.integral(byteArrayOf(1, 2, 3, 4), 2, 2)
        assertEquals(10, ii[2 * 3 + 2]); assertEquals(3, ii[1 * 3 + 2]); assertEquals(4, ii[2 * 3 + 1])
    }

    @Test fun groupingNeedsNeighboursAndMergesSimilarBoxes() {
        val near = List(6) { intArrayOf(100 + it, 50, 40, 40) }
        val g = AnimeFaceDetector.group(near + listOf(intArrayOf(5, 5, 30, 30)), 5, .2f)
        assertEquals(1, g.size); assertEquals(6, g[0].votes); assertEquals(103, g[0].x)
        assertTrue(AnimeFaceDetector.group(near.take(5), 5, .2f).isEmpty())
    }

    @Test fun eyesAreFoundOnTheDarkestContrastyBlobs() {
        val w = 200; val h = 200
        val g = ByteArray(w * h) { 230.toByte() }
        fun blob(cx: Int, cy: Int) { for (y in cy - 6..cy + 6) for (x in cx - 9..cx + 9) g[y * w + x] = if ((x + y) % 3 == 0) 60 else 20 }
        blob(70, 100); blob(130, 100)
        val eyes = FaceRegions.eyes(g, w, h, AnimeFaceDetector.Face(40, 50, 120, 120, 9))
        assertEquals(2, eyes.size)
        assertEquals(70f / w, eyes[0][0], .05f); assertEquals(130f / w, eyes[1][0], .05f)
        assertEquals(100f / h, eyes[0][1], .06f)
        val m = FaceRegions.ellipseMask(eyes, w, h)
        assertEquals(255, m[100 * w + 70].toInt() and 0xFF); assertEquals(0, m[10 * w + 10].toInt() and 0xFF)
        val chest = FaceRegions.breathing(AnimeFaceDetector.Face(40, 50, 120, 120, 9), w, h)
        assertTrue(chest[1] > (50 + 120) / 200f && chest[3] <= 1f)
    }

    /** Paisaje sintético: cielo degradado, montañas, bosque, lago azul y césped verde abajo. */
    private fun landscape(w: Int = 120, h: Int = 160): IntArray = IntArray(w * h) { i ->
        val y = i / w; val x = i % w
        when {
            y < 60 -> rgb(100 + y / 3, 150 + y / 4, 239)
            y < 80 -> if ((x / 7 + y) % 5 == 0) rgb(30, 80, 40) else rgb(114, 132, 173)
            y < 95 -> rgb(35, 90, 40)
            y < 140 -> rgb(79 - (y - 95) / 4, 122 - (y - 95) / 4, 169 - (y - 95) / 5)
            else -> rgb(40, 180, 70)
        }
    }
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun skyAndWaterAreTheRightBands() {
        val w = 120; val h = 160; val px = landscape(w, h)
        val sky = RegionTools.autoSky(px, w, h)
        assertNotNull(sky)
        assertTrue(sky!![10 * w + 50].toInt() != 0); assertTrue(sky[120 * w + 50].toInt() == 0)
        val water = RegionTools.autoWater(px, w, h)
        assertNotNull(water)
        assertTrue("el lago", water!![120 * w + 60].toInt() != 0)
        assertTrue("no el césped", water[150 * w + 60].toInt() == 0)
        assertTrue("no el cielo", water[20 * w + 60].toInt() == 0)
    }

    @Test fun protectedCharacterIsNeverSelected() {
        val w = 120; val h = 160; val px = landscape(w, h)
        val prot = ByteArray(w * h) { i -> if (i % w in 40..80 && i / w in 0..150) -1 else 0 }
        val sky = RegionTools.autoSky(px, w, h, exclude = prot)!!
        for (y in 0 until 60) assertEquals(0, sky[y * w + 60].toInt())
        val tap = RegionTools.grow(px, w, h, 10, 10, .5f, exclude = prot)
        assertTrue(tap[10 * w + 100].toInt() != 0 || tap[10 * w + 20].toInt() != 0)
        assertEquals(0, tap[10 * w + 60].toInt())
    }

    @Test fun darkBluishClothesAreNotWater() {
        val w = 80; val h = 80
        val px = IntArray(w * h) { i -> if (i / w > 40 && i % w in 10..30) rgb(40, 47, 74) else rgb(230, 220, 210) }
        assertNull(RegionTools.autoWater(px, w, h))
    }

    @Test fun sceneSuggestionsFollowSimpleRules() {
        val bw = IntArray(64 * 64) { i -> if ((i / 3) % 2 == 0) rgb(20, 20, 20) else rgb(240, 240, 240) }
        val s1 = RegionTools.suggest(RegionTools.traits(bw, 64, 64))
        assertTrue("B/N: solo escenas sin color $s1", s1.all { it in setOf("Impacto manga", "Terror") })
        val night = IntArray(64 * 64) { i -> if (i % 97 == 0) rgb(255, 230, 150) else rgb(12, 16, 40) }
        assertTrue(RegionTools.suggest(RegionTools.traits(night, 64, 64)).first() in setOf("Noche lluviosa", "Ciudad de noche"))
        val land = RegionTools.suggest(RegionTools.traits(landscape(64, 96), 64, 96))
        assertTrue(land.contains("Mar") && land.contains("Cielo animado"))
    }


    @Test fun tiledUpscaleHasNoSeams() = runBlocking {
        val w = 300; val h = 170
        val rnd = java.util.Random(7)
        val px = IntArray(w * h) { (0xFF shl 24) or rnd.nextInt(0xFFFFFF) }
        // «Modelo» de prueba: vecino más cercano x4 (lo que haría una red perfecta sin detalle).
        val fake: suspend (FloatArray, Int, Int) -> FloatArray = { chw, tw, th ->
            val n = tw * th; val rw = tw * 4; val rn = rw * th * 4
            FloatArray(3 * rn) { k -> val c = k / rn; val o = k % rn; chw[c * n + (o / rw / 4) * tw + (o % rw) / 4] }
        }
        val (out, ow, oh) = TileUpscale.upscale(px, w, h, 4, 4f, fake, tile = 64, pad = 6)
        assertEquals(1200, ow); assertEquals(680, oh)
        for (y in 0 until oh step 7) for (x in 0 until ow step 5) assertEquals("($x,$y)", px[(y / 4) * w + x / 4] or (0xFF shl 24), out[y * ow + x])
        val (_, w2, h2) = TileUpscale.upscale(px, w, h, 4, 2f, fake, tile = 64, pad = 6)
        assertEquals(600, w2); assertEquals(340, h2)
        assertEquals(4096f / 2000f, TileUpscale.outputScale(2000, 1500), 1e-6f)
        assertEquals(15, TileUpscale.tiles(w, h, 64, 6).size)
    }

    @Test fun bundledSuperResModelMatchesTheManifest() {
        val f = file("src/main/assets/models/${SuperResolver.ASSET}")
        val sha = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        val manifest = file("src/main/assets/models/LICENSES.json").readText()
        assertTrue(manifest.contains(sha))
        assertTrue(file("src/main/assets/licenses/Real-ESRGAN_BSD-3.txt").readText().contains("BSD 3-Clause"))
    }
}

/** v49 · «2.5D rápido» sin IA. */
class V49QuickDepthTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun bottomIsNearTopIsFar() {
        val w = 60; val h = 90
        val px = IntArray(w * h) { rgb(120, 140, 160) }
        val d = DepthMath.quick(px, w, h)
        assertTrue((d[(h - 2) * w + 30].toInt() and 0xFF) > (d[2 * w + 30].toInt() and 0xFF) + 100)
    }

    @Test fun protectedCharacterGoesToTheFront() {
        val w = 60; val h = 90
        val px = IntArray(w * h) { rgb(120, 140, 160) }
        val prot = ByteArray(w * h) { i -> if (i % w in 20..40 && i / w in 10..40) -1 else 0 }
        val d = DepthMath.quick(px, w, h, prot)
        // La cara del personaje (arriba) queda más cerca que el suelo de abajo sin pintar.
        assertTrue((d[25 * w + 30].toInt() and 0xFF) >= (d[(h - 2) * w + 5].toInt() and 0xFF))
        assertTrue((d[25 * w + 30].toInt() and 0xFF) > (d[25 * w + 5].toInt() and 0xFF) + 80)
    }

    @Test fun boxBlurKeepsTheMeanAndFlattens() {
        val src = FloatArray(100) { if (it == 55) 100f else 0f }
        val b = DepthMath.boxBlur(src, 10, 10, 1)
        assertEquals(100f / 9f, b[55], 1e-3f); assertEquals(100f / 9f, b[44], 1e-3f); assertEquals(0f, b[0], 0f)
    }
}
