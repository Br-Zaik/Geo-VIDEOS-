package com.zeta.fondo.engine

import com.zeta.fondo.model.CropHandle
import com.zeta.fondo.model.CropRect
import com.zeta.fondo.model.ImageLayer
import com.zeta.fondo.model.ImageLayers
import com.zeta.fondo.model.Keyframe
import com.zeta.fondo.model.LayerCrop
import com.zeta.fondo.model.LayerJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * v44.27 · Recortar una capa: el marco sobre el original girado, la copia recortada en su
 * mismo lugar (con giro, espejo y claves), «Original» que la devuelve entera y el guardado.
 */
class V4427Test {
    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private val eps = 1e-3f

    /** Punto del original → imagen girada [rot] (sentido del reloj). */
    private fun rotPoint(x: Float, y: Float, rot: Int): Pair<Float, Float> = when (rot) {
        90 -> (1f - y) to x
        180 -> (1f - x) to (1f - y)
        270 -> y to (1f - x)
        else -> x to y
    }

    private fun near(a: CropRect, b: CropRect, e: Float = eps) =
        abs(a.l - b.l) < e && abs(a.t - b.t) < e && abs(a.r - b.r) < e && abs(a.b - b.b) < e

    @Test fun rotationMapsTheSameRegion() {
        val r = CropRect(.1f, .2f, .45f, .9f)
        var q = r
        repeat(4) { q = LayerCrop.rotate90(q) }
        assertTrue(near(q, r))
        for (rot in listOf(0, 90, 180, 270)) {
            // toSource: las esquinas del original llevadas a la imagen girada caen en el marco.
            val s = LayerCrop.toSource(r, rot)
            val pts = listOf(s.l to s.t, s.r to s.t, s.l to s.b, s.r to s.b).map { rotPoint(it.first, it.second, rot) }
            assertEquals(r.l, pts.minOf { it.first }, eps); assertEquals(r.r, pts.maxOf { it.first }, eps)
            assertEquals(r.t, pts.minOf { it.second }, eps); assertEquals(r.b, pts.maxOf { it.second }, eps)
            // Girar 90° más sigue marcando el MISMO trozo del original.
            assertTrue(near(LayerCrop.toSource(LayerCrop.rotate90(r), rot + 90), s))
        }
    }

    @Test fun sizesAndPixels() {
        assertEquals(1200 to 800, LayerCrop.rotatedSize(800, 1200, 90))
        assertEquals(800 to 1200, LayerCrop.rotatedSize(800, 1200, 180))
        assertEquals(600 to 600, LayerCrop.outputSize(1200, 800, CropRect(0f, 0f, .5f, .75f), 0))
        val px = LayerCrop.sourcePixels(1000, 500, CropRect(.1f, .2f, .6f, .8f), 0)
        assertEquals(listOf(100, 100, 500, 300), px.toList())
        // Girada 90°: el ancho del resultado sale del alto del original.
        val px90 = LayerCrop.sourcePixels(1000, 500, CropRect(0f, 0f, .5f, 1f), 90)
        assertEquals(1000, px90[2]); assertEquals(250, px90[3])
        assertTrue(LayerCrop.isFull(CropRect.FULL, 0))
        assertFalse(LayerCrop.isFull(CropRect.FULL, 90))
    }

    @Test fun ratiosAndDragging() {
        val w = 1600; val h = 900
        for ((_, ratio) in LayerCrop.ratios.drop(1)) {
            val f = LayerCrop.fitRatio(CropRect(.3f, .3f, .5f, .6f), ratio, w, h)
            assertEquals(ratio, f.w * w / (f.h * h), 1e-3f)
            assertTrue(f.l >= -1e-5f && f.t >= -1e-5f && f.r <= 1f + 1e-5f && f.b <= 1f + 1e-5f)
            // Esquina con proporción fija: la mantiene y no se sale.
            val d = LayerCrop.drag(f, CropHandle.BR, .5f, .5f, ratio, w, h)
            assertEquals(ratio, d.w * w / (d.h * h), 1e-3f)
            assertTrue(d.r <= 1f + 1e-5f && d.b <= 1f + 1e-5f)
            val small = LayerCrop.drag(f, CropHandle.TL, 2f, 2f, ratio, w, h)
            assertTrue(small.w >= LayerCrop.MIN - 1e-5f || small.h >= LayerCrop.MIN - 1e-5f)
        }
        val r0 = CropRect(.2f, .2f, .6f, .6f)
        val moved = LayerCrop.drag(r0, CropHandle.MOVE, .9f, -.9f, 0f, w, h)
        assertTrue(near(moved, CropRect(.6f, 0f, 1f, .4f)))
        val left = LayerCrop.drag(r0, CropHandle.L, .5f, 0f, 0f, w, h)
        assertEquals(.6f - LayerCrop.MIN, left.l, eps); assertEquals(.6f, left.r, eps)
        val c = LayerCrop.clamp(CropRect(.9f, .5f, .1f, .5f))
        assertTrue(c.w > 0f && c.h >= LayerCrop.MIN - 1e-5f)
    }

    // ---- En el mismo lugar

    private val bw = 1920; private val bh = 1080

    /** Dónde cae en el fondo el punto (u, v) (0..1) de la imagen que dibuja la capa. */
    private fun at(l: ImageLayer, u: Float, v: Float, t: Float = 0f): Pair<Float, Float> {
        val f = ImageLayers.frame(l, t, 6f, bw, bh)
        val lu = (if (f.flipX) 1f - u else u) * 2f - 1f
        val lv = v * 2f - 1f
        val lx = lu * f.halfW; val ly = lv * f.halfH
        val c = cos(f.rot); val s = sin(f.rot)
        return (f.x + (c * lx - s * ly) / bw) to (f.y + (s * lx + c * ly) / bh)
    }

    private fun layer(flip: Boolean) = ImageLayer(id = 1, name = "a", file = "layers/a.png", aspect = 1200f / 800f,
        x = .42f, y = .55f, sx = .5f, sy = .55f, rot = 30f, flipX = flip)

    @Test fun croppedPartStaysInPlace() {
        for (flip in listOf(false, true)) {
            val l0 = layer(flip)
            val r = CropRect(.2f, .1f, .7f, .65f)
            val l1 = LayerCrop.place(l0, 1200, 800, r, 0, "layers/a_c.png", bw.toFloat() / bh)
            assertEquals("layers/a_c.png", l1.file); assertEquals("layers/a.png", l1.src)
            for ((u, v) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f, .5f to .5f)) {
                val want = at(l0, r.l + u * r.w, r.t + v * r.h)
                val got = at(l1, u, v)
                assertEquals("flip=$flip u=$u v=$v x", want.first, got.first, 2e-4f)
                assertEquals("flip=$flip u=$u v=$v y", want.second, got.second, 2e-4f)
            }
            // Otro recorte sobre el ya recortado (marco en el original) y luego «Original».
            val r2 = CropRect(.3f, .3f, .9f, .9f)
            val l2 = LayerCrop.place(l1, 1200, 800, r2, 0, "layers/a_c2.png", bw.toFloat() / bh)
            val w2 = at(l0, r2.l, r2.t); val g2 = at(l2, 0f, 0f)
            assertEquals(w2.first, g2.first, 2e-4f); assertEquals(w2.second, g2.second, 2e-4f)
            val back = LayerCrop.place(l2, 1200, 800, CropRect.FULL, 0, "x", bw.toFloat() / bh)
            assertEquals("layers/a.png", back.file); assertNull(back.src)
            assertEquals(l0.x, back.x, 2e-4f); assertEquals(l0.y, back.y, 2e-4f)
            assertEquals(l0.sx, back.sx, 2e-4f); assertEquals(l0.sy, back.sy, 2e-4f)
            assertEquals(l0.aspect, back.aspect, 2e-3f)
        }
    }

    @Test fun rotatingKeepsCenterAndPixelSize() {
        val l0 = layer(false)
        val l1 = LayerCrop.place(l0, 1200, 800, CropRect.FULL, 90, "layers/a_r.png", bw.toFloat() / bh)
        assertEquals(90, l1.cropRot)
        assertEquals(800f / 1200f, l1.aspect, 1e-3f)
        assertEquals(l0.x, l1.x, 1e-5f); assertEquals(l0.y, l1.y, 1e-5f)
        // Mismos píxeles de pantalla por píxel: el ancho girado = alto original.
        assertEquals(l0.sx * 800f / 1200f, l1.sx, 1e-4f)
    }

    @Test fun keyframesFollowTheCrop() {
        val l0 = layer(false).copy(keys = mapOf(
            "x" to listOf(Keyframe(0f, .3f), Keyframe(6f, .7f)),
            "sx" to listOf(Keyframe(0f, .4f), Keyframe(6f, .8f)),
            "sy" to listOf(Keyframe(0f, .4f), Keyframe(6f, .8f))))
        val r = CropRect(.5f, .2f, 1f, .8f)
        // Se recorta viendo el segundo 0: ahí todo queda exacto; la «x» (con claves) en cada clave.
        val l1 = LayerCrop.place(l0, 1200, 800, r, 0, "layers/k.png", bw.toFloat() / bh, 0f)
        for (t in listOf(0f, 1f)) {
            for ((u, v) in listOf(0f to 0f, 1f to 1f)) {
                val want = at(l0, r.l + u * r.w, r.t + v * r.h, t)
                val got = at(l1, u, v, t)
                assertEquals(want.first, got.first, 3e-4f)
                if (t == 0f) assertEquals(want.second, got.second, 3e-4f)
            }
        }
    }

    @Test fun savedAndSanitized() {
        val l = LayerCrop.place(layer(true), 1200, 800, CropRect(.1f, .1f, .6f, .9f), 270, "layers/c.png", 1f)
        val back = LayerJson.fromJson(LayerJson.toJson(listOf(l))).single()
        assertEquals(l.src, back.src); assertEquals(l.file, back.file); assertEquals(270, back.cropRot)
        assertEquals(l.cropL, back.cropL, 1e-4f); assertEquals(l.cropB, back.cropB, 1e-4f)
        // Sin original no hay recorte; un original fuera de la carpeta de capas no se acepta.
        val plain = ImageLayers.sanitize(l.copy(src = null))
        assertEquals(0, plain.cropRot); assertEquals(1f, plain.cropR, 0f)
        val bad = LayerJson.toJson(listOf(l)).also { it.getJSONObject(0).put("src", "../x.png") }
        assertNull(LayerJson.fromJson(bad).single().src)
    }

    @Test fun visibleWithoutSearching() {
        val ui = file("src/main/java/com/zeta/fondo/ui/LayerPanels.kt").readText()
        val row = ui.substringAfter("fun LayerActionRow(").substringBefore("ToolChip(\"Mezcla\"")
        assertTrue(row.contains("ToolChip(\"Recortar\"") && row.contains("ToolChip(\"Girar 90°\""))
        assertTrue(ui.contains("if (layer != null && !dragging) LayerQuickBar(vm, layer, rect, areaW, areaH)"))
        val tools = file("src/main/java/com/zeta/fondo/ui/EditorTools.kt").readText()
        val bar = tools.substringAfter("fun EditorToolRow(")
        assertTrue(bar.indexOf("vm.layerCrop != null -> LayerCropBar(vm, modifier)") in 0 until bar.indexOf("LayerActionRow(vm, modifier)"))
        val screen = file("src/main/java/com/zeta/fondo/ui/EditorScreen.kt").readText()
        assertTrue(screen.contains("vm.layerCrop != null -> LayerCropOverlay(vm, Modifier.fillMaxSize())"))
        assertTrue(screen.contains("vm.layerCrop != null -> vm.cancelLayerCrop()"))
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        assertTrue(vm.contains("prefs.getBoolean(\"cropTip\", false)"))
        // El original nunca se borra mientras alguna capa lo use.
        val store = file("src/main/java/com/zeta/fondo/model/ProjectStore.kt").readText()
        assertTrue(store.contains("layers.flatMap { listOfNotNull(it.file, it.src) }"))
    }
}
