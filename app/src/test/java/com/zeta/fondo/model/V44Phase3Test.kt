package com.zeta.fondo.model

import com.zeta.fondo.engine.FrameUniforms
import com.zeta.fondo.engine.RenderParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v44 · Fase 3: fotogramas clave por bloque y parámetro, curvas y cámara libre. */
class V44Phase3Test {
    private val lin = listOf(Keyframe(0f, 0f), Keyframe(2f, 10f))

    @Test fun interpolationAndCurves() {
        assertEquals(0f, Keyframes.valueAt(lin, -1f)!!, 0f)
        assertEquals(5f, Keyframes.valueAt(lin, 1f)!!, 1e-5f)
        assertEquals(10f, Keyframes.valueAt(lin, 9f)!!, 0f)
        val easeIn = listOf(Keyframe(0f, 0f, Keyframes.EASE_IN), Keyframe(2f, 10f))
        val easeOut = listOf(Keyframe(0f, 0f, Keyframes.EASE_OUT), Keyframe(2f, 10f))
        val both = listOf(Keyframe(0f, 0f, Keyframes.EASE_IN_OUT), Keyframe(2f, 10f))
        assertEquals(2.5f, Keyframes.valueAt(easeIn, 1f)!!, 1e-5f)
        assertEquals(7.5f, Keyframes.valueAt(easeOut, 1f)!!, 1e-5f)
        assertEquals(5f, Keyframes.valueAt(both, 1f)!!, 1e-5f)
        assertTrue(Keyframes.valueAt(both, .2f)!! < Keyframes.valueAt(lin, .2f)!!)
        assertNull(Keyframes.valueAt(emptyList(), 1f))
        assertEquals(3f, Keyframes.valueAt(listOf(Keyframe(1f, 3f)), 0f)!!, 0f)
    }

    @Test fun putReplacesNearbyKeyAndKeepsOrder() {
        var ks = Keyframes.put(emptyList(), 2f, 1f)
        ks = Keyframes.put(ks, .5f, 4f)
        ks = Keyframes.put(ks, 2.02f, 7f)          // a menos de 1 cuadro: sustituye
        assertEquals(listOf(.5f, 2f), ks.map { it.t })
        assertEquals(7f, ks[1].value, 0f)
        assertEquals(1, Keyframes.remove(ks, 1.98f).size)
        assertTrue(Keyframes.animatable("rain.int"))
        assertFalse(Keyframes.animatable("rain.cycles"))
        assertFalse(Keyframes.animatable("rain.variant"))
    }

    @Test fun splitKeepsTheAnimationContinuous() {
        val keys = mapOf("blast.size" to lin)
        val (l, r) = Keyframes.split(keys, 1.5f)
        assertEquals(7.5f, Keyframes.valueAt(l.getValue("blast.size"), 1.5f)!!, 1e-5f)
        assertEquals(7.5f, Keyframes.valueAt(r.getValue("blast.size"), 0f)!!, 1e-5f)
        assertEquals(10f, Keyframes.valueAt(r.getValue("blast.size"), .5f)!!, 1e-5f)
        // Recortar por la izquierda 0,5 s: la clave de 2 s queda en 1,5 s locales (mismo instante real).
        assertEquals(1.5f, Keyframes.shift(keys, .5f).getValue("blast.size")[1].t, 1e-5f)
    }

    @Test fun saveFormatRoundTripsAndIgnoresGarbage() {
        val ks = listOf(Keyframe(0f, 1f, 2), Keyframe(1.25f, .5f, 3))
        assertEquals(ks, Keyframes.decode(Keyframes.encode(ks)))
        val bad = floatArrayOf(1f, 2f, 9f, Float.NaN, 1f, 0f, 3f)
        assertEquals(listOf(Keyframe(1f, 2f, Keyframes.LINEAR)), Keyframes.decode(bad))
    }

    @Test fun keyframesDriveTheUniformsFrameByFrame() {
        val s = Effects.defaults() + ("rain.on" to 1f)
        val clip = EffectClip(1, "rain", 0f, 6f, keys = mapOf("rain.int" to listOf(Keyframe(0f, .2f), Keyframe(4f, 1f))))
        val p = RenderParams.from(s, 1f, 6f, listOf(clip))
        fun rainAt(sec: Float) = (FrameUniforms.compute(p, sec / 6f)["uRain"] ?: p.floatUniforms["uRain"]!!)[0]
        assertEquals(.2f, rainAt(0f), 1e-4f)
        assertEquals(.6f, rainAt(2f), 1e-4f)
        assertEquals(1f, rainAt(5f), 1e-4f)
        // Sin claves, el valor es constante (como en la v43).
        val plain = RenderParams.from(s, 1f, 6f, listOf(clip.copy(keys = emptyMap())))
        assertTrue(plain.keyTracks.isEmpty())
    }

    @Test fun freeCameraMovesWithKeys() {
        val s = Effects.defaults() + mapOf("cam.on" to 1f, "cam.mode" to 9f)
        val clip = EffectClip(1, "cam", 0f, 6f, keys = mapOf(
            "cam.mzoom" to listOf(Keyframe(0f, 1f), Keyframe(6f, 2f)),
            "cam.mx" to listOf(Keyframe(0f, -1f), Keyframe(6f, 1f))))
        val p = RenderParams.from(s, 1f, 6f, listOf(clip))
        val mid = FrameUniforms.compute(p, .5f)["uCamManual"]!!
        assertEquals(1.5f, mid[3], 1e-3f)
        assertEquals(0f, mid[0], 1e-3f)
        assertEquals(1f, p.floatUniforms["uCam"]!![0], 0f)   // la cámara libre está encendida
    }

    @Test fun replacingAnEffectKeepsPortableKeysOnly() {
        val c = EffectClip(1, "smoke", 0f, 3f, keys = mapOf("smoke.originX" to lin, "smoke.int" to lin))
        val r = ClipEdits.replaceEffect(c, "fog")
        assertEquals(setOf("fog.originX"), r.keys.keys)
    }
}
