package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.Effects
import com.zeta.fondo.model.EventMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v40: lo que ve el motor en cada instante (vista previa y exportación usan el mismo
 * FrameUniforms). Duplicados independientes, tiempos de cada bloque y restauración.
 */
class V40RenderTest {
    private val loop = 6f
    private val base = Effects.defaults() + mapOf("blast.on" to 1f, "blast.mask" to -2f,
        "blast.mode" to EventMode.ONCE.toFloat(), "blast.originX" to .3f, "blast.originY" to .4f, "blast.origin" to 0f)

    private class Rec : FrameUniforms.Sink {
        val floats = HashMap<String, FloatArray>()
        val ints = HashMap<String, Int>()
        override fun putFloats(name: String, values: FloatArray) { floats[name] = values.copyOf() }
        override fun putInt(name: String, value: Int) { ints[name] = value }
    }

    private fun t(sec: Float) = sec / loop

    @Test fun duplicateWithOwnPositionAndSizeIsIndependent() {
        val a = EffectClip(1, "blast", .5f, 2f, 0)
        val b = EffectClip(2, "blast", 3.5f, 2f, 1, overrides = mapOf("blast.originX" to .8f, "blast.originY" to .7f, "blast.size" to 1.2f))
        val p = RenderParams.from(base, 1f, loop, listOf(a, b))
        assertTrue("el bloque con ajustes propios tiene diferencias", p.clipDiffs.containsKey(2L))
        val rec = Rec()
        val f = FrameUniforms()
        f.writeBase(p, rec)
        // Durante el bloque A: posición y tamaño del efecto.
        f.apply(p, t(1.5f), rec)
        assertEquals(.3f, rec.floats["uMotionBlast"]!![2], 1e-6f)
        assertEquals(.4f, rec.floats["uMotionBlast"]!![3], 1e-6f)
        assertEquals(.65f, rec.floats["uFxBlast"]!![1], 1e-6f)
        // Durante el bloque B: SUS valores.
        f.apply(p, t(4.5f), rec)
        assertEquals(.8f, rec.floats["uMotionBlast"]!![2], 1e-6f)
        assertEquals(.7f, rec.floats["uMotionBlast"]!![3], 1e-6f)
        assertEquals(1.2f, rec.floats["uFxBlast"]!![1], 1e-6f)
        // Vuelve a A: se restauran los valores del efecto.
        f.apply(p, t(1.0f), rec)
        assertEquals(.3f, rec.floats["uMotionBlast"]!![2], 1e-6f)
        assertEquals(.65f, rec.floats["uFxBlast"]!![1], 1e-6f)
    }

    @Test fun effectIsInvisibleOutsideItsBlocksAndPhaseFollowsEachBlock() {
        val a = EffectClip(1, "blast", 1f, 2f, 0)
        val p = RenderParams.from(base, 1f, loop, listOf(a))
        val before = FrameUniforms.compute(p, t(.5f))
        assertEquals(0f, before["uFxBlast"]!![0], 0f)
        val inside = FrameUniforms.compute(p, t(2f))
        assertTrue(inside["uFxBlast"]!![0] > 0f)
        // Fase de la explosión (modo Una vez) = posición dentro del bloque.
        assertEquals(.5f, inside["uEventPhase"]!![0], .02f)
        val moved = RenderParams.from(base, 1f, loop, listOf(a.copy(startSec = 3f)))
        assertEquals(0f, FrameUniforms.compute(moved, t(2f))["uFxBlast"]!![0], 0f)
        assertEquals(.25f, FrameUniforms.compute(moved, t(3.5f))["uEventPhase"]!![0], .02f)
    }

    @Test fun ownMaskSlotOrBehindIsPerBlock() {
        val a = EffectClip(1, "smoke", 0f, 3f, 0)
        val b = EffectClip(2, "smoke", 3f, 3f, 0, overrides = mapOf("smoke.behind" to 1f))
        val s = Effects.defaults() + mapOf("smoke.on" to 1f, "smoke.mask" to -2f)
        val p = RenderParams.from(s, 1f, loop, listOf(a, b))
        val rec = Rec(); val f = FrameUniforms(); f.writeBase(p, rec)
        f.apply(p, t(4f), rec)
        assertEquals(-2 + RenderParams.BEHIND_OFFSET, rec.ints["uMaskSmoke"])
        f.apply(p, t(1f), rec)
        assertEquals(-2, rec.ints["uMaskSmoke"])
    }

    @Test fun blocksWithoutOwnSettingsHaveNoDiffs() {
        val p = RenderParams.from(base, 1f, loop, listOf(EffectClip(1, "blast", 0f, 2f), EffectClip(2, "blast", 2f, 2f)))
        assertTrue(p.clipDiffs.isEmpty())
        // La tabla de uniforms contiene todo lo que antes escribía FondoGl.writeParams.
        assertNotNull(p.floatUniforms["uFxBlast"]); assertNotNull(p.floatUniforms["uMotionBlast"])
        assertNotNull(p.floatUniforms["uTintSmoke"]); assertNotNull(p.intUniforms["uMaskBlast"])
        assertArrayEquals(floatArrayOf(p.aspect), p.floatUniforms["uAspect"], 0f)
    }

    @Test fun everyUniformNameExistsInTheShaderBlock() {
        val frag = listOf(File("src/main/assets/shaders/fondo.frag"), File("app/src/main/assets/shaders/fondo.frag")).first { it.exists() }.readText()
        val p = RenderParams.from(Effects.defaults(), 1f)
        val names = p.floatUniforms.keys.map { it.substringBefore('[') } + p.intUniforms.keys
        val missing = names.filter { !Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(frag) }
        // v44.15: los efectos sin «Zona» (acabados de toda la imagen) no leen máscara: su uMask
        // se ignora en FondoGl (member() == null). Solo cuentan los demás.
        val zoneless = Effects.all.map { it.id }.filter { "Zona" !in com.zeta.fondo.model.EffectCatalog.sections(it, false, false) }
            .map { "uMask" + RenderParams.uniformName(it) }.toSet()
        val real = missing.filter { it !in zoneless }
        assertTrue("faltan en el shader: $real", real.size <= 2)
    }
}
