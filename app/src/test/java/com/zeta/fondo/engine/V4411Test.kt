package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectCatalog
import com.zeta.fondo.model.EffectGrade
import com.zeta.fondo.model.Effects
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v44.11 · Audio sin cortes, color propio de cada efecto y efectos nuevos de manga / manhwa. */
class V4411Test {

    private fun file(rel: String) = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private fun frag() = file("src/main/assets/shaders/fondo.frag").readText()

    // ---------- Audio

    @Test fun soundClipCacheSurvivesTheMixerCall() {
        // El fallo: Ambient.mix(vacío) borraba la caché de los bloques de sonido en cada lectura
        // (~23 ms) y el sonido se volvía a fabricar una y otra vez → entrecortado.
        val a = Ambient.loop("rain_soft", 22050, 22050 * 2, 0)!!
        Ambient.mix(emptyMap(), 22050, 22050 * 2, 0)
        assertTrue(Ambient.isLoopReady("rain_soft", 22050, 22050 * 2, 0))
        val b = Ambient.loop("rain_soft", 22050, 22050 * 2, 0)!!
        assertSame("misma copia: no se vuelve a sintetizar", a, b)
    }

    @Test fun soundEdgesHaveNoClicks() {
        val ramp = 100L
        assertEquals(0f, SoundEdges.gain(0, 5000, ramp, false, false, false), 0f)
        assertEquals(.5f, SoundEdges.gain(50, 5000, ramp, false, false, false), 1e-4f)
        assertEquals(1f, SoundEdges.gain(500, 5000, ramp, false, false, false), 0f)
        assertEquals(.2f, SoundEdges.gain(5000, 20, ramp, false, false, false), 1e-4f)
        // Ambiente que cubre el inicio/fin del vídeo: al repetirse no se apaga.
        assertEquals(1f, SoundEdges.gain(0, 0, ramp, true, true, false), 0f)
        // Evento: su golpe inicial no se suaviza.
        assertEquals(1f, SoundEdges.gain(0, 5000, ramp, false, false, true), 0f)
    }

    @Test fun smallDriftDoesNotCutTheAudio() {
        val g = DriftGuard()
        val rate = 44100
        // Un cuadro lento (0,3 s de desfase) una vez: no se corta.
        assertFalse(g.shouldResync(rate * 3L / 10, rate))
        assertFalse(g.shouldResync(rate / 20L, rate))
        // Desfase mantenido: se corrige.
        repeat(3) { assertFalse(g.shouldResync(rate * 3L / 10, rate)) }
        assertTrue(g.shouldResync(rate * 3L / 10, rate))
        // Salto grande (tocar la línea de tiempo): al instante.
        assertTrue(g.shouldResync(rate * 2L, rate))
    }

    @Test fun previewUsesBigBufferAndPrewarm() {
        val src = file("src/main/java/com/zeta/fondo/engine/ImportedAudioPreview.kt").readText()
        assertTrue(src.contains("m.rate * m.channels * 2 / 4"))
        assertTrue(src.contains("m.prewarm()"))
        assertTrue(src.contains("drift.shouldResync("))
        assertFalse(src.contains("> m.rate / 5"))
    }

    @Test fun newAmbientSoundsStartAtZero() {
        val vm = file("src/main/java/com/zeta/fondo/AppViewModel.kt").readText()
        val add = vm.substringAfter("fun addSound(sound: String)").substringBefore("\n    }")
        assertTrue(add.contains("ClipEdits.defaultSpan(total)"))
    }

    // ---------- Color propio de cada efecto

    @Test fun gradeArrayMatchesShader() {
        val f = frag()
        assertTrue(f.contains("vec4 uGrade[${EffectGrade.ids.size}];"))
        val l = Std140Layout.parse(f)
        assertTrue(l.members.containsKey("uGrade"))
        assertEquals(16, l.members["uGrade"]!![1])
        // Cada bloque envuelto usa el índice de SU efecto.
        val wraps = Regex("#ifdef FX_([A-Z0-9]+)\\n    vec3 gb(\\d+) = col;[\\s\\S]*?col = fxGrade\\(gb\\2, col, uGrade\\[(\\d+)]\\);\\n#endif").findAll(f).toList()
        assertTrue("bloques envueltos: ${wraps.size}", wraps.size >= EffectGrade.ids.size)
        // v44.15: el humo tiene dos sitios (detrás / delante), cada uno con su propio #ifdef.
        fun fx(w: MatchResult) = w.groupValues[1].let { if (it.startsWith("SMOKE")) it.removeSuffix("BACK").removeSuffix("FRONT") else it }
        for (w in wraps) assertEquals(fx(w), EffectGrade.ids[w.groupValues[3].toInt()].uppercase())
        for (id in EffectGrade.ids) assertTrue("sin color: $id", wraps.any { fx(it) == id.uppercase() })
    }

    @Test fun gradeUniformsAreWritten() {
        val s = Effects.defaults() + mapOf("sakura.on" to 1f, "sakura.gmode" to 1f, "sakura.gsat" to -.5f, "sakura.gbri" to 2f)
        val p = RenderParams.from(s, 1f)
        val i = EffectGrade.ids.indexOf("sakura")
        assertArrayEquals(floatArrayOf(1f, -.5f, 1f, 0f), p.floatUniforms["uGrade[$i]"], 0f)   // brillo limitado a 1
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f), p.floatUniforms["uGrade[${EffectGrade.ids.indexOf("rain")}]"], 0f)
        assertTrue(EffectGrade.isDefault(Effects.defaults(), "rain"))
    }

    @Test fun gradedEffectsShowColorTabAndKeysAreEffectWide() {
        assertTrue("Color" in EffectCatalog.sections("sakura", colorable = false, hasShapes = false))
        assertFalse("la cámara no añade color", EffectGrade.has("cam"))
        assertFalse(com.zeta.fondo.model.ClipEdits.isClipKey("sakura", "sakura.gmode"))
        assertTrue(com.zeta.fondo.model.ClipEdits.isClipKey("sakura", "sakura.size"))
    }

    @Test fun mangaModeIsGlobalInShader() {
        val f = frag()
        val fn = f.substringAfter("vec3 fxGrade(").substringBefore("\n}\n")
        assertTrue("el modo manga (uMono) pasa todos los efectos a B/N", fn.contains("uMono"))
    }

    // ---------- Efectos nuevos

    @Test fun newEffectsAreComplete() {
        val f = frag()
        for (id in listOf("symbols", "glowup", "inkweather")) {
            val def = Effects.all.firstOrNull { it.id == id }
            assertTrue("falta EffectDef $id", def != null)
            assertTrue("#ifdef FX_${id.uppercase()}", f.contains("#ifdef FX_${id.uppercase()}"))
            assertTrue("variantes $id", EffectCatalog.variants(id).isNotEmpty())
            assertTrue("miniatura $id", file("src/main/assets/thumbs/$id.webp").exists())
            assertTrue("con color $id", EffectGrade.has(id))
            val on = RenderParams.from(Effects.defaults() + ("$id.on" to 1f), 1f)
            val n = RenderParams.uniformName(id)
            assertTrue("uFx$n encendido", on.floatUniforms["uFx$n"]!![0] > 0f)
            assertTrue("uMask$n en el shader", f.contains("int uMask$n;"))
        }
        assertTrue(EffectCatalog.idsFor("Manga", Effects.all.map { it.id }) { false }.containsAll(listOf("symbols", "inkweather")))
    }

    @Test fun symbolsAreMovableAndInkRainFollowsWind() {
        assertTrue("symbols" in com.zeta.fondo.model.Wind.directional)
        assertTrue("inkweather" in com.zeta.fondo.model.Wind.windDriven)
        val p = RenderParams.from(Effects.defaults() + ("symbols.on" to 1f), 1f)
        val m = p.floatUniforms["uMotionSymbols"]!!
        assertEquals(.68f, m[2], 1e-4f); assertEquals(.28f, m[3], 1e-4f)
    }
}
