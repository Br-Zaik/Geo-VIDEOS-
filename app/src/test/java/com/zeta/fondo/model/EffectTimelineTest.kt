package com.zeta.fondo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresión de la línea de tiempo: los duplicados no deben alterar los originales. */
class EffectTimelineTest {
    @Test fun clipCompletoMantieneSuIntensidad() {
        val clip = EffectClip(1, "natural", 0f, 6f, strength = .7f)
        val sample = EffectTimeline.sample(listOf(clip), .5f, 6f)
        assertEquals(.7f, sample.gate, 0.0001f)
        assertEquals(clip.uid, sample.clip?.uid)
    }

    @Test fun dosClipsSuperpuestosSumanSusPropiasIntensidades() {
        val clips = listOf(
            EffectClip(1, "gust", 0f, 6f, strength = .4f),
            EffectClip(2, "gust", 2f, 2f, strength = .9f)
        )
        assertEquals(.4f, EffectTimeline.sample(clips, .1f, 6f).gate, .0001f)
        assertEquals(1.3f, EffectTimeline.sample(clips, .5f, 6f).gate, .0001f)
    }

    @Test fun clipsParcialesEntranYSeDesvanecenSinCortes() {
        val clip = EffectClip(1, "bolt", 1f, 3f)
        assertEquals(0f, EffectTimeline.sample(listOf(clip), 0f, 6f).gate, .0001f)
        assertEquals(0f, EffectTimeline.sample(listOf(clip), 1f / 6f, 6f).gate, .0001f)
        assertTrue(EffectTimeline.sample(listOf(clip), .35f, 6f).gate > .98f)
        assertEquals(0f, EffectTimeline.sample(listOf(clip), .7f, 6f).gate, .0001f)
    }

    @Test fun normalizacionProtegeDuracionesEIntensidades() {
        val clip = EffectTimeline.normalized(EffectClip(3,"natural", -4f, 99f, strength = 9f), 6f)
        assertEquals(0f, clip.startSec, .0001f)
        assertEquals(6f, clip.durationSec, .0001f)
        assertEquals(2f, clip.strength, .0001f)
    }
}
