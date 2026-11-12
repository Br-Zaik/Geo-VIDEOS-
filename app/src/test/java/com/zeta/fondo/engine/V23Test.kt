package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.EffectTimeline
import org.junit.Assert.assertEquals
import org.junit.Test

class V23Test {
    @Test fun normalizedKeepsTimelineTrack() {
        val clip = EffectTimeline.normalized(EffectClip(7L, "gust", 1f, 2f, track = 3), 6f)
        assertEquals(3, clip.track)
        assertEquals(1f, clip.startSec, .0001f)
        assertEquals(2f, clip.durationSec, .0001f)
    }

    @Test fun oldClipDefaultsToAutomaticTrack() {
        val clip = EffectClip(8L, "impact", 0f, .8f)
        assertEquals(-1, clip.track)
    }
}
