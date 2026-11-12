package com.zeta.fondo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v44.10 · IA de profundidad que ya no da el falso «sin memoria», 2.5D automático que separa
 * al personaje sin IA, y «Mejorar imagen» visible en la vista previa.
 */
class V4410Test {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }

    // ---------- Fin del cálculo IA (el fallo de las capturas)

    // ---------- 2.5D automático: personaje separado del fondo sin IA

    /** Retrato sintético: fondo claro liso en degradado, personaje con contorno de tinta. */
    private fun portrait(w: Int, h: Int): Pair<IntArray, BooleanArray> {
        val px = IntArray(w * h); val inside = BooleanArray(w * h)
        val cx = w / 2f; val hy = h * .3f; val hr = w * .22f
        for (y in 0 until h) for (x in 0 until w) {
            val g = (225 + 20 * y / h)                 // fondo: gris claro que se aclara hacia abajo
            var c = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            val dHead = kotlin.math.hypot(x - cx, (y - hy) * 1.0f)
            val body = y > h * .48f && kotlin.math.abs(x - cx) < w * (.18f + .5f * (y - h * .48f) / h)
            val head = dHead < hr
            if (head || body) {
                inside[y * w + x] = true
                c = if (head) 0xFFF2C9A8.toInt() else if ((x / 6 + y / 6) % 2 == 0) 0xFFB01E4A.toInt() else 0xFF8A1238.toInt()
            }
            // contorno de tinta
            val edgeHead = kotlin.math.abs(dHead - hr) < 1.2f
            val edgeBody = y > h * .48f && kotlin.math.abs(kotlin.math.abs(x - cx) - w * (.18f + .5f * (y - h * .48f) / h)) < 1.2f
            if (edgeHead || edgeBody) c = 0xFF202020.toInt()
            px[y * w + x] = c
        }
        return px to inside
    }

    @Test fun backgroundFloodFindsTheCharacter() {
        val w = 120; val h = 200
        val (px, inside) = portrait(w, h)
        val s = DepthMath.subjectFromBackground(px, w, h)
        assertNotNull(s); s!!
        var inSum = 0f; var inN = 0; var outSum = 0f; var outN = 0
        for (y in 4 until h - 4) for (x in 4 until w - 4) {
            val i = y * w + x
            if (inside[i]) { inSum += s[i]; inN++ } else { outSum += s[i]; outN++ }
        }
        assertTrue("personaje ${inSum / inN}", inSum / inN > .85f)
        assertTrue("fondo ${outSum / outN}", outSum / outN < .25f)
    }

    @Test fun busySceneGivesNoSilhouette() {
        // Paisaje cargado (ruido por todas partes): mejor no inventar un personaje.
        val w = 96; val h = 96
        val rnd = java.util.Random(7)
        val px = IntArray(w * h) { (0xFF shl 24) or rnd.nextInt(0xFFFFFF) }
        assertNull(DepthMath.subjectFromBackground(px, w, h))
    }

    @Test fun quickDepthPutsCharacterInFront() {
        val w = 120; val h = 200
        val (px, inside) = portrait(w, h)
        val subject = DepthMath.subjectFromBackground(px, w, h)!!
        val d = DepthMath.quick(px, w, h, null, subject)
        var inSum = 0; var inN = 0; var outSum = 0; var outN = 0
        // Solo la mitad de arriba: abajo el fondo ya está «cerca» por la regla general.
        for (y in 0 until h / 2) for (x in 0 until w) {
            val i = y * w + x; val v = d[i].toInt() and 0xFF
            if (inside[i]) { inSum += v; inN++ } else { outSum += v; outN++ }
        }
        val near = inSum.toFloat() / inN; val far = outSum.toFloat() / outN
        assertTrue("cerca $near vs lejos $far", near - far > 90f)
        // Sin silueta, la cabeza (arriba) quedaba casi tan lejos como el fondo.
        val old = DepthMath.quick(px, w, h)
        var oIn = 0; var oOut = 0
        for (y in 0 until h / 2) for (x in 0 until w) { val i = y * w + x; val v = old[i].toInt() and 0xFF; if (inside[i]) oIn += v else oOut += v }
        assertTrue("mejora la separación", near - far > (oIn.toFloat() / inN - oOut.toFloat() / outN) + 30f)
    }

    @Test fun facePriorCoversHeadAndBody() {
        val w = 100; val h = 160
        val s = DepthMath.subjectPrior(w, h, listOf(floatArrayOf(.35f, .15f, .3f, .2f)))
        assertTrue(s[(h * .25f).toInt() * w + w / 2] > .9f)   // cara
        assertTrue(s[(h * .8f).toInt() * w + w / 2] > .9f)    // torso
        assertTrue(s[5 * w + 3] < .1f)                       // esquina de arriba = fondo
    }

    @Test fun quickWithProtectIgnoresSilhouette() {
        val w = 60; val h = 80
        val (px, _) = portrait(w, h)
        val prot = ByteArray(w * h)
        val a = DepthMath.quick(px, w, h, prot, null)
        val b = DepthMath.quick(px, w, h, prot, FloatArray(w * h) { 1f })
        assertTrue(a.contentEquals(b))
    }

    // ---------- Automático y vista previa

    @Test fun autoDepthIsQuickNotAi() {
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        val ensure = vm.substringAfter("fun ensureDepth()").substringBefore("\n    }")
        assertTrue(ensure.contains("computeQuickDepth(auto = true)"))
        assertTrue("la IA nunca arranca sola", !ensure.contains("computeDepth()"))
        assertTrue("borrar el mapa pasa a zonas manuales", vm.substringAfter("fun discardDepth()").substringBefore("\n    }").contains("\"parallax.ai\", 0f"))
    }

    @Test fun depthPanelIsSimple() {
        val panel = file("src/main/java/com/zeta/fondo/ui/DepthPanel.kt").readText()
        assertTrue("MI-GAN fuera del panel", !panel.contains("Importar MI-GAN"))
        assertTrue(panel.contains("\"Suave\"") && panel.contains("\"Normal\"") && panel.contains("\"Fuerte\""))
    }

    @Test fun enhancedImageIsShownInPreview() {
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        assertTrue(vm.contains("private fun refreshPreviewImage()"))
        assertTrue(vm.substringAfter("fun setUseEnhanced(").substringBefore("\n").contains("refreshPreviewImage()"))
        val panel = file("src/main/java/com/zeta/fondo/ui/SuperResPanel.kt").readText()
        assertTrue(!panel.contains("La vista previa sigue usando el original"))
    }
}
