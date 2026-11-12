package com.zeta.fondo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El multiplicador de cada copia debe funcionar tanto en preview como en exportación. */
class EffectTimelineStrengthTest {
    @Test fun legacyClipRetainsDefaultStrength() {
        val old = EffectClip(uid = 1, effectId = "rain", startSec = 0f, durationSec = 6f)
        assertEquals(1f, old.strength, 0.001f)
        assertEquals(1f, EffectTimeline.sample(listOf(old), .5f, 6f).gate, 0.001f)
    }

    @Test fun differentClipsHaveIndependentIntensity() {
        val base = EffectClip(1, "gust", 0f, 6f, strength = .4f)
        val burst = EffectClip(2, "gust", 2f, 2f, strength = 1.3f)
        assertEquals(.4f, EffectTimeline.sample(listOf(base, burst), .1f, 6f).gate, 0.001f)
        assertEquals(1.7f, EffectTimeline.sample(listOf(base, burst), .5f, 6f).gate, 0.001f)
        assertEquals(2L, EffectTimeline.sample(listOf(base, burst), .5f, 6f).clip?.uid)
    }

    @Test fun strengthZeroMakesAClipInvisible() {
        val clip = EffectClip(1, "snow", 0f, 6f, strength = 0f)
        assertEquals(0f, EffectTimeline.sample(listOf(clip), .5f, 6f).gate, 0.001f)
        assertNull(EffectTimeline.sample(listOf(clip), .5f, 6f).clip)
    }

    @Test fun noClipOutsideItsInterval() {
        val clip = EffectClip(1, "rain", 1f, 1f, strength = 2f)
        assertEquals(0f, EffectTimeline.sample(listOf(clip), .6f, 6f).gate, 0.001f)
    }

    @Test fun clampExtremeIntensityAndDuration() {
        val clip = EffectTimeline.normalized(EffectClip(1, "rain", -1f, 99f, strength = 12f), 6f)
        assertEquals(0f, clip.startSec, 0.001f)
        assertEquals(6f, clip.durationSec, 0.001f)
        assertEquals(2f, clip.strength, 0.001f)
        assertTrue(EffectTimeline.sample(listOf(clip), .5f, 6f).gate <= 2f)
    }
}
