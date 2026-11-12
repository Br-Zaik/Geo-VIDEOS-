package com.zeta.fondo.engine

import com.zeta.fondo.model.ExportSettings
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.tanh

/**
 * Mezcla la música elegida (fragmento del bucle, con fundido opcional) y los sonidos de ambiente.
 * Devuelve PCM 16 bits intercalado. La vista previa y el MP4 usan exactamente esta mezcla.
 */
class SoundMixer(musicFile: File?, private var s: ExportSettings, private var boltCount: Int) : AutoCloseable {

    private val music: PcmReader? = if (musicOn(s, musicFile)) PcmReader(musicFile!!, s) else null
    val rate: Int = if (music != null) s.audioRate else 44100
    val channels: Int = if (music != null) s.audioChannels else 2
    val loopFrames: Long get() = s.loopSec.toLong() * rate

    fun update(settings: ExportSettings, bolt: Int) {
        s = settings
        boltCount = bolt
        music?.update(settings)
    }

    /**
     * v44.11 · Fabrica (o carga) TODOS los sonidos del proyecto antes de que suenen. Así la
     * primera vez que llega un sonido no hay una pausa mientras se sintetiza.
     */
    fun prewarm() {
        val loop = loopFrames.coerceAtLeast(1L).toInt()
        Ambient.mix(s.ambient, rate, loop, boltCount)
        for (t in s.sounds) {
            val id = t.sound
            when {
                Ambient.isOneShot(id) -> Ambient.shot(id, rate)
                id.startsWith(com.zeta.fondo.model.SoundClips.USER) -> UserSounds.load(s.soundDir, id, rate)
                else -> Ambient.loop(id, rate, loop, boltCount)
            }
        }
    }

    fun read(startFrame: Long, count: Int): ByteArray {
        val out = ByteArray(count * channels * 2)
        val loop = loopFrames.coerceAtLeast(1L)
        val musicBytes = if (music != null && s.audioEnabled && s.soundVolume > 0f && !s.musicMuted) music.read(startFrame, count) else null
        val mb = musicBytes?.let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN) }
        val amb = Ambient.mix(s.ambient, rate, loop.toInt(), boltCount)
        val ob = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        val fade = (rate * 0.25f).coerceAtLeast(1f)
        // v44 · bloques de sonido con su inicio, fin, fundidos, volumen y panorama.
        val sounds = mixSounds(startFrame, count, loop)
        for (i in 0 until count) {
            val pos = ((startFrame + i) % loop + loop) % loop
            val gain = if (s.audioFade && mb != null) min(1f, min(pos / fade, (loop - pos) / fade)) else 1f
            var ambL = 0f
            var ambR = 0f
            val p = (pos * 2).toInt()
            for ((data, vol) in amb) {
                if (p + 1 < data.size) {
                    ambL += data[p] / 32768f * vol
                    ambR += data[p + 1] / 32768f * vol
                }
            }
            if (sounds != null) { ambL += sounds[i * 2]; ambR += sounds[i * 2 + 1] }
            for (c in 0 until channels) {
                var v = if (channels == 2) (if (c == 0) ambL else ambR) else (ambL + ambR) * 0.5f
                if (mb != null) v += mb.getShort((i * channels + c) * 2) / 32768f * gain
                ob.putShort((i * channels + c) * 2, (softClip(v) * 32767f).toInt().toShort())
            }
        }
        return out
    }

    /** Mezcla estéreo (float) de los bloques de sonido para [count] cuadros desde [startFrame]. */
    private fun mixSounds(startFrame: Long, count: Int, loop: Long): FloatArray? {
        if (s.sounds.isEmpty()) return null
        val out = FloatArray(count * 2)
        val loopSec = loop.toFloat() / rate
        val ramp = (rate * SoundEdges.RAMP_SEC).toLong().coerceAtLeast(1L)
        for (t in s.sounds) {
            val id = t.sound
            val oneShot = Ambient.isOneShot(id)
            val user = id.startsWith(com.zeta.fondo.model.SoundClips.USER)
            val data: ShortArray = when {
                oneShot -> Ambient.shot(id, rate)
                user -> UserSounds.load(s.soundDir, id, rate)
                else -> Ambient.loop(id, rate, loop.toInt(), boltCount)
            } ?: continue
            val len = data.size / 2
            if (len <= 0) continue
            val startF = (t.startSec * rate).toLong()
            val endF = (t.endSec * rate).toLong()
            var i = 0
            while (i < count) {
                val pos = ((startFrame + i) % loop + loop) % loop
                // ganancia y panorama por tramos de 64 cuadros (barato y sin escalones audibles)
                val sec = pos.toFloat() / rate
                val n = minOf(64, count - i)
                if (pos + n < startF || pos > endF) { i += n; continue }
                val g = com.zeta.fondo.model.SoundClips.gain(t, sec.coerceIn(0f, loopSec))
                val (gl, gr) = com.zeta.fondo.model.SoundClips.panGains(com.zeta.fondo.model.SoundClips.pan(t, sec))
                for (k in 0 until n) {
                    val p = pos + k
                    if (p < startF || p > endF || g <= 0f) continue
                    val e = SoundEdges.gain(p - startF, endF - p, ramp, startF <= 0L && !oneShot, endF >= loop - 1 && !oneShot, oneShot)
                    val idx = when {
                        oneShot -> (p - startF).toInt().takeIf { it < len } ?: continue
                        user -> ((p - startF) % len).toInt()
                        else -> (p % len).toInt()            // ambiente del bucle: continuo
                    }
                    // Los ambientes ya están panoramizados en estéreo; pan 0 deja el estéreo intacto.
                    val sl = data[idx * 2] / 32768f; val sr = data[idx * 2 + 1] / 32768f
                    out[(i + k) * 2] += sl * g * e * gl * 1.4142f
                    out[(i + k) * 2 + 1] += sr * g * e * gr * 1.4142f
                }
                i += n
            }
        }
        return out
    }

    private fun softClip(v: Float): Float {
        val a = abs(v)
        if (a < 0.8f) return v
        val shaped = 0.8f + 0.2f * tanh((a - 0.8f) / 0.2f)
        return if (v < 0f) -shaped else shaped
    }

    override fun close() {
        music?.close()
    }

    companion object {
        fun musicOn(s: ExportSettings, file: File?): Boolean =
            s.audioName.isNotBlank() && s.audioEnabled && s.soundVolume > 0f && s.audioFrames > 0L && file?.isFile == true

        fun hasSound(s: ExportSettings, file: File?): Boolean = musicOn(s, file) || s.hasAmbient || s.hasSounds
    }
}

