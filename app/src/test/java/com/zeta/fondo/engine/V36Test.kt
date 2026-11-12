package com.zeta.fondo.engine

import com.zeta.fondo.model.ColorHex
import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.Kind
import com.zeta.fondo.model.Presets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V36Test {

    // ---------- Bucle: misma rejilla en vista previa y exportación
    @Test fun loopClockNeverRepeatsFirstFrameAtTheEnd() {
        val n = LoopClock.frameCount(6f, 30)
        assertEquals(180, n)
        assertEquals(0f, LoopClock.phase(0, n), 0f)
        assertEquals(179f / 180f, LoopClock.phase(179, n), 1e-6f)
        assertEquals(0f, LoopClock.phase(180, n), 0f) // repetición del MP4: vuelve al cuadro 0
    }

    @Test fun previewTimeSnapsToExportFrames() {
        val n = LoopClock.frameCount(4f, 60)
        for (i in 0 until n) {
            val t = (i + 0.5) / n
            assertEquals(i.toFloat() / n, LoopClock.quantize(t, 4f, 60), 1e-6f)
        }
        assertEquals(0f, LoopClock.quantize(1.0, 4f, 60), 0f)
    }

    // ---------- Máscaras: rectángulo pintado para la raíz del movimiento natural
    @Test fun maskBoundsFindsPaintedRectangle() {
        val w = 40; val h = 20
        val a = ByteArray(w * h)
        for (y in 4..9) for (x in 10..29) a[y * w + x] = 200.toByte()
        val b = MaskBounds.of(a, w, h, w)
        assertNotNull(b)
        assertEquals(10f / w, b!![0], 0.06f)
        assertEquals(4f / h, b[1], 0.11f)
        assertEquals(30f / w, b[2], 0.06f)
        assertEquals(10f / h, b[3], 0.11f)
        assertNull(MaskBounds.of(ByteArray(w * h), w, h, w))
    }

    // ---------- Color HEX
    @Test fun hexParsingAndHsvRoundTrip() {
        assertEquals(0xFF7A1A, ColorHex.parse("#FF7A1A"))
        assertEquals(0xFFAA00, ColorHex.parse("fa0"))
        assertNull(ColorHex.parse("#12345"))
        assertNull(ColorHex.parse("zzzzzz"))
        assertEquals("#2F6BFF", ColorHex.format(0x2F6BFF))
        for (c in listOf(0xFFFFFF, 0x000000, 0xE53935, 0x22D3EE, 0x8B5CF6)) {
            val hsv = ColorHex.toHsv(c)
            val back = ColorHex.fromHsv(hsv[0], hsv[1], hsv[2])
            for (shift in listOf(16, 8, 0)) {
                val d = kotlin.math.abs(((back shr shift) and 0xFF) - ((c shr shift) and 0xFF))
                assertTrue("canal $shift de ${ColorHex.format(c)}", d <= 1)
            }
        }
    }

    // ---------- Delante / detrás del personaje y color propio
    @Test fun behindFlagAndTintReachTheShader() {
        val s = Effects.defaults() + mapOf("smoke.on" to 1f, "smoke.mask" to -2f, "smoke.behind" to 1f,
            "fog.mask" to 3f, "smoke.tintOn" to 1f, "smoke.tintR" to 1f, "smoke.tintG" to .5f, "smoke.tintB" to 0f)
        val p = RenderParams.from(s, 1f, 6f)
        assertEquals(-2 + RenderParams.BEHIND_OFFSET, p.effectMasks["smoke"])
        assertEquals(3, p.effectMasks["fog"])
        assertArrayEquals(floatArrayOf(1f, .5f, 0f, 1f), p.studio.getValue("uTintSmoke"), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f), p.studio.getValue("uTintFlame"), 0f)
    }

    @Test fun shapesAreRealParametersWithLabels() {
        for ((id, labels) in Effects.shapeLabels) {
            val def = Effects.all.first { it.id == id }
            val shape = def.params.firstOrNull { it.key == "shape" }
            assertNotNull("$id sin parámetro shape", shape)
            assertEquals("$id: etiquetas = formas", labels.size - 1, shape!!.max.toInt())
            assertEquals(Kind.INT, shape.kind)
            // v44.16: «Aura anime» nace como Aura (forma 1), que es lo que muestra su miniatura.
            assertEquals("$id: forma inicial", if (id == "energy") 1f else 0f, shape.def, 0f)
        }
        val p = RenderParams.from(Effects.defaults() + mapOf("blood.direction" to 90f, "blast.shape" to 2f), 1f)
        assertEquals((Math.PI / 2).toFloat(), p.studio.getValue("uBloodShape")[1], 1e-5f)
        assertEquals(2f, p.studio.getValue("uBlastShape")[0], 0f)
    }

    @Test fun oldProjectsKeepTheirSlidersMappedToTheSameUniformSlot() {
        // La .y de cada efecto "extra" debe seguir siendo el mismo parámetro de siempre.
        val p = RenderParams.from(Effects.defaults() + mapOf("smoke.on" to 1f, "smoke.size" to 4f,
            "blast.on" to 1f, "blast.size" to .9f, "flame.on" to 1f, "flame.size" to 1.7f), 1f)
        assertEquals(4f, p.extra.getValue("smoke")[1], 0f)
        assertEquals(.9f, p.extra.getValue("blast")[1], 0f)
        assertEquals(1.7f, p.extra.getValue("flame")[1], 0f)
    }

    @Test fun quickScenesExistAndOpenARealEffect() {
        val names = Presets.scenes.map { it.name }.toSet()
        assertTrue(names.containsAll(setOf("Aparición entre humo", "Explosión anime", "Cielo animado",
            "Aura oscura", "Ojo brillante", "Impacto manga", "Viento en cabello", "Fuego y humo",
            "Nubes detrás del personaje")))
        val ids = Effects.all.map { it.id }.toSet()
        for (scene in Presets.scenes) {
            assertTrue(scene.name, Presets.sceneMainEffect(scene) in ids)
            for (key in scene.values.keys) {
                val id = key.substringBefore('.')
                // v44: «wind.*» es el viento global (no un efecto).
                assertTrue("${scene.name}: $key", id in ids || id == "wind")
            }
        }
    }

    @Test fun legacyDuplicatesStillRenderForOldProjects() {
        val s = Effects.defaults() + Effects.legacyDuplicates.associate { "$it.on" to 1f }
        val p = RenderParams.from(s, 1f)
        assertTrue(p.sway[0] > 0f)
        assertTrue(p.ripple[0] > 0f)
        assertTrue(p.extra.getValue("cloth")[0] > 0f)
        assertTrue(p.extra.getValue("pulse")[0] > 0f)
    }

    // ---------- Contrato Kotlin <-> shader: un nombre mal escrito silenciaría un efecto
    @Test fun everyUniformWrittenByKotlinExistsInTheShader() {
        val shader = listOf(File("src/main/assets/shaders/fondo.frag"), File("app/src/main/assets/shaders/fondo.frag"))
            .first { it.isFile }.readText()
        val block = shader.substringAfter("uniform ZetaParams {").substringBefore("};")
        val declared = Regex("""\b(u[A-Z]\w*)""").findAll(block).map { it.value }.toSet()
        val all = Effects.defaults().keys.filter { it.endsWith(".on") }.associateWith { 1f }
        val p = RenderParams.from(Effects.defaults() + all, 1f, 6f)
        fun up(id: String) = id.replaceFirstChar { it.uppercaseChar() }
        val written = buildSet {
            p.studio.keys.forEach { add(it.substringBefore('[')) }
            p.extra.keys.forEach { add("uFx${up(it)}") }
            p.motion.keys.forEach { add("uMotion${up(it)}") }
            p.copyExtra.keys.forEach { add("uFx${up(it)}Copy") }
            p.copyMotion.keys.forEach { add("uMotion${up(it)}Copy") }
            p.copyMasks.keys.forEach { add("uMask${up(it)}Copy") }
            addAll(listOf("uAspect", "uMono", "uPixelSnap", "uFlow3", "uDrift", "uRipple", "uGlint", "uSway",
                "uHeat", "uRain", "uSnow", "uSakura", "uStars", "uFlies", "uFog", "uRays", "uEnergy", "uSpeed", "uCam"))
        }
        val missing = written - declared
        assertTrue("Uniforms sin declarar en ZetaParams: $missing", missing.isEmpty())
        // Las máscaras de cada efecto: las que el shader usa deben llamarse igual.
        val maskNames = Regex("""\buMask(\w+)""").findAll(shader).map { it.groupValues[1] }.toSet()
        for (name in maskNames.filter { !it.endsWith("Copy") }) {
            assertTrue("uMask$name no corresponde a ningún efecto",
                Effects.all.any { up(it.id) == name } || name == "s" ||
                    // v44: segunda instancia (uMaskBlast2…)
                    (name.endsWith("2") && Effects.all.any { up(it.id) == name.dropLast(1) }))
        }
    }
}
