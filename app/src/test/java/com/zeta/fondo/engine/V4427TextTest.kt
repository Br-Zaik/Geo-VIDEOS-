package com.zeta.fondo.engine

import com.zeta.fondo.model.CropRect
import com.zeta.fondo.model.ImageLayer
import com.zeta.fondo.model.ImageLayers
import com.zeta.fondo.model.Keyframe
import com.zeta.fondo.model.LayerCrop
import com.zeta.fondo.model.LayerJson
import com.zeta.fondo.model.TextLayers
import com.zeta.fondo.model.TextSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.27 · Parte 2: texto como capa (los efectos pasan por detrás de las letras). */
class V4427TextTest {
    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }

    @Test fun specIsAlwaysValid() {
        val s = TextLayers.sanitize(TextSpec("a\r\nb" + "x".repeat(400), font = 99, outline = 9))
        assertEquals(TextLayers.MAX_CHARS, s.text.length)
        assertFalse(s.text.contains('\r'))
        assertEquals(TextLayers.fonts.size - 1, s.font)
        assertEquals(3, s.outline)
        assertEquals("T · Hola mundo", TextLayers.displayName("\n  Hola mundo  \nsegunda"))
        assertEquals("Texto", TextLayers.displayName("   "))
        assertEquals(0f, TextLayers.outlineFraction(0), 0f)
        assertTrue(TextLayers.outlineFraction(3) > TextLayers.outlineFraction(1))
    }

    @Test fun editingKeepsLetterHeight() {
        val l0 = ImageLayer(id = 3, name = "T · Hola", file = "layers/t_1.png", aspect = 3f, x = .4f, y = .6f,
            sx = .5f, sy = .5f, rot = 12f, keys = mapOf("sx" to listOf(Keyframe(0f, .5f), Keyframe(6f, .9f))),
            text = TextSpec("Hola"),
            src = "layers/t_0.png", cropL = .1f, cropR = .9f)
        val l1 = TextLayers.keepLetterSize(l0, 6f, "layers/t_2.png", TextSpec("Hola amigos", color = TextLayers.BLACK))
        val f0 = ImageLayers.frame(l0, 0f, 6f, 1920, 1080)
        val f1 = ImageLayers.frame(l1, 0f, 6f, 1920, 1080)
        assertEquals(f0.halfH, f1.halfH, 1e-3f)          // misma altura de letra
        assertEquals(f0.halfW * 2f, f1.halfW, 1e-3f)     // el doble de ancho
        assertEquals(l0.x, l1.x, 0f); assertEquals(l0.rot, l1.rot, 0f)
        assertEquals(.9f * 2f, l1.keys["sx"]!!.last().value, 1e-4f)
        assertEquals("layers/t_2.png", l1.file); assertNull(l1.src); assertEquals(0f, l1.cropL, 0f)
        assertEquals("T · Hola amigos", l1.name)
        assertEquals(TextLayers.BLACK, l1.text!!.color)
    }

    @Test fun savedAndRestored() {
        val t = TextSpec("Línea 1\nLínea 2", font = 4, color = 0xFFFF3B30.toInt(), outline = 3,
            outlineColor = TextLayers.WHITE, shadow = false)
        val l = ImageLayer(id = 1, name = "T", file = "layers/t.png", aspect = 2f, text = t)
        val back = LayerJson.fromJson(LayerJson.toJson(listOf(l))).single()
        assertEquals(t, back.text)
        val img = LayerJson.fromJson(LayerJson.toJson(listOf(l.copy(text = null)))).single()
        assertNull(img.text)
        // Un texto se puede girar, mover, etc.; el recorte no se ofrece, pero si llegara, es válido.
        val c = LayerCrop.place(l, 800, 400, CropRect(.1f, .1f, .9f, .9f), 0, "layers/x.png", 1f)
        assertEquals(t, c.text)
    }

    @Test fun visibleAndWired() {
        val tools = file("src/main/java/com/zeta/fondo/ui/EditorTools.kt").readText()
        val bar = tools.substringAfter("val items = mainItems.take(3)")
        assertTrue(bar.indexOf("MainItem(\"Imagen\"") < bar.indexOf("MainItem(\"Texto\", Icons.Filled.TextFields) { it.openNewText() }"))
        assertTrue(bar.indexOf("MainItem(\"Texto\"") < bar.indexOf("mainItems.drop(3)"))
        val panels = file("src/main/java/com/zeta/fondo/ui/LayerPanels.kt").readText()
        assertTrue(panels.contains("if (l.text != null) ToolChip(\"Editar\""))
        val quick = file("src/main/java/com/zeta/fondo/ui/LayerCropUi.kt").readText()
        assertTrue(quick.contains("QuickButton(\"Editar\", Icons.Filled.Edit, StudioPink) { vm.editTextLayer(layer.id) }"))
        // La cajita no tapa el punto de girar.
        assertTrue(quick.contains("val knob = top + up *"))
        val screen = file("src/main/java/com/zeta/fondo/ui/EditorScreen.kt").readText()
        assertTrue(screen.contains("if (vm.textEditor != null) TextEditorOverlay(vm, Modifier.fillMaxSize())"))
        assertTrue(screen.contains("vm.textEditor != null -> vm.closeTextEditor()"))
        val editor = file("src/main/java/com/zeta/fondo/ui/TextEditorUi.kt").readText()
        assertTrue(editor.contains(".imePadding()"))
        assertTrue(editor.contains("delay(90)"))   // la vista previa no se rehace en cada letra
        // Fuentes del sistema: ningún archivo de fuente dentro de la app (sin licencias aparte).
        val render = file("src/main/java/com/zeta/fondo/engine/TextRender.kt").readText()
        assertTrue(render.contains("Typeface.create(f.family") && render.contains("typeface = typefaceFor(s.font)"))
        assertTrue(render.contains("StaticLayout.Builder.obtain"))
        val assets = file("src/main/assets")
        assertTrue(assets.walkTopDown().none { it.extension.lowercase() in setOf("ttf", "otf", "woff", "woff2") })
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        assertTrue(vm.contains("TextLayers.keepLetterSize(fresh, aspect, file, spec)"))
        assertTrue(vm.contains("// El editor sigue abierto: no se pierde lo escrito."))
    }
}
