package com.zeta.fondo.engine

import com.zeta.fondo.model.EffectClip
import com.zeta.fondo.model.ExportSettings
import com.zeta.fondo.model.Keyframe
import com.zeta.fondo.model.SoundCatalog
import com.zeta.fondo.model.SoundClips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** v44 · Fase 4: bloques de sonido, mezcla, fundidos, panorama, Solo/silencio y Beat Sync. */
class V44Phase4Test {
    private fun clip(uid: Long, sound: String, start: Float, dur: Float, ov: Map<String, Float> = emptyMap(), hidden: Boolean = false) =
        EffectClip(uid, SoundClips.clipId(sound), start, dur, overrides = ov.mapKeys { "${SoundClips.clipId(sound)}.${it.key}" }, hidden = hidden)

    @Test fun fadesVolumeAndKeys() {
        val t = SoundClips.tracks(listOf(clip(1, "rain_soft", 1f, 4f, mapOf("vol" to 1f, "fadein" to 1f, "fadeout" to .5f))), emptyMap()).single()
        assertEquals(0f, SoundClips.gain(t, .5f), 0f)          // antes del bloque
        assertEquals(.5f, SoundClips.gain(t, 1.5f), 1e-5f)     // a mitad del fundido de entrada
        assertEquals(1f, SoundClips.gain(t, 3f), 1e-5f)
        assertEquals(.5f, SoundClips.gain(t, 4.75f), 1e-4f)    // a mitad del fundido de salida
        val keyed = t.copy(volKeys = listOf(Keyframe(1f, .2f), Keyframe(3f, 1f)), fadeIn = 0f)
        assertEquals(.6f, SoundClips.gain(keyed, 3f), 1e-5f)
        val (l, r) = SoundClips.panGains(0f)
        assertEquals(l, r, 1e-6f); assertEquals(1f, l * l + r * r, 1e-5f)
        assertEquals(0f, SoundClips.panGains(1f).first, 1e-6f)
    }

    @Test fun muteAndSoloAndLegacyMigration() {
        val clips = listOf(clip(1, "rain_soft", 0f, 6f), clip(2, "birds", 0f, 6f, hidden = true), EffectClip(3, "rain", 0f, 6f))
        assertEquals(listOf("rain_soft"), SoundClips.tracks(clips, emptyMap()).map { it.sound })
        val c4 = clip(4, "owl", 0f, 6f)
        val all = clips + c4
        assertEquals(listOf("owl"), SoundClips.tracks(all, emptyMap(), soloUid = 4).map { it.sound })
        assertTrue(SoundClips.musicMuted(all, 4))
        assertFalse(SoundClips.musicMuted(all, 3))            // Solo en un efecto visual no calla la música
        val legacy = SoundClips.fromLegacy(mapOf("waves" to .6f, "birds" to 0f), 8f, 10)
        assertEquals(1, legacy.size)
        assertEquals("snd_waves", legacy[0].effectId); assertEquals(8f, legacy[0].durationSec, 0f)
        assertEquals(.6f, legacy[0].overrides["snd_waves.vol"]!!, 0f)
    }

    private fun rms(b: ByteArray, from: Int, to: Int): Float {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var s = 0.0
        for (i in from until to) { val v = bb.getShort(i * 2) / 32768.0; s += v * v }
        return kotlin.math.sqrt(s / (to - from)).toFloat()
    }

    @Test fun mixerPlaysEachBlockOnlyInsideItsTime() {
        val clips = listOf(clip(1, "impact", 2f, 1f, mapOf("fadein" to 0f, "fadeout" to 0f)))
        val s = ExportSettings(loopSec = 4, sounds = SoundClips.tracks(clips, emptyMap()))
        val m = SoundMixer(null, s, 0)
        val rate = m.rate
        val pcm = m.read(0L, rate * 4)
        val ch = m.channels
        assertEquals(0f, rms(pcm, 0, (rate * 1.9f).toInt() * ch), 0f)                 // silencio antes
        assertTrue(rms(pcm, rate * 2 * ch, (rate * 2.3f).toInt() * ch) > .01f)       // suena en su bloque
        assertTrue(SoundMixer.hasSound(s, null))
        assertFalse(SoundMixer.hasSound(ExportSettings(), null))
    }

    @Test fun everyCatalogSoundIsSynthesizedAndLoopsWithoutClick() {
        for (snd in SoundCatalog.byCategory.values.flatten().distinctBy { it.id }) {
            val data = if (snd.event) Ambient.shot(snd.id, 22050) else Ambient.loop(snd.id, 22050, 22050 * 6, 1)
            assertTrue(snd.id, data != null && data.isNotEmpty())
            if (!snd.event) {
                // salto en el empalme ≈ como un paso normal entre muestras
                val d = data!!
                val n = d.size / 2
                val seam = abs(d[0] - d[(n - 1) * 2]).toFloat()
                var step = 0.0
                for (i in 1 until n) step += abs(d[i * 2] - d[(i - 1) * 2]).toDouble()
                step /= (n - 1)
                assertTrue("${snd.id} empalme $seam paso $step", seam <= step * 12 + 600)
            }
        }
    }

    @Test fun userSoundsResampleAndLoop() {
        val x = FloatArray(1000) { kotlin.math.sin(it * .05f) }
        assertEquals(2000, UserSounds.resample(x, 22050, 44100).size)
        val loop = UserSounds.loopable(x, x, 4000)
        assertEquals((1000 - 250) * 2, loop.size)
    }

    @Test fun beatDetectFindsClicks() {
        val rate = 22050
        val mono = FloatArray(rate * 4)
        val beats = listOf(.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f)
        for (b in beats) { val at = (b * rate).toInt(); for (k in 0 until 800) mono[at + k] = (if (k % 2 == 0) .8f else -.8f) * (1f - k / 800f) }
        for (i in mono.indices) mono[i] += ((i * 7919) % 13 - 6) / 6000f   // ruido de fondo leve
        val found = BeatDetect.onsets(mono, rate, .5f)
        assertEquals(beats.size, found.size)
        for ((a, b) in beats.zip(found)) assertEquals(a, b, .04f)
    }

    @Test fun licenseManifestListsOnlyWhatShips() {
        val f = listOf(File("src/main/assets/sounds/LICENSES.json"), File("app/src/main/assets/sounds/LICENSES.json")).first { it.isFile }
        val text = f.readText()
        assertTrue(text.contains("\"grabaciones\": []"))
        for (snd in SoundCatalog.byCategory.values.flatten()) assertTrue(snd.id, text.contains("\"id\": \"${snd.id}\""))
        assertFalse(text.contains("Pixabay\","))
    }
}
