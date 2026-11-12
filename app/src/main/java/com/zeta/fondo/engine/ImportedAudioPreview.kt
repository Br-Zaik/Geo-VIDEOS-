package com.zeta.fondo.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.zeta.fondo.model.ExportSettings
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class ImportedAudioPreview(context: android.content.Context, private val hub: RenderHub, private val onError: (String) -> Unit) {
    private val manager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    @Volatile private var hasFocus = false
    private val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener({ change -> hasFocus = change == android.media.AudioManager.AUDIOFOCUS_GAIN },
            android.os.Handler(android.os.Looper.getMainLooper())).build()
    private data class State(val file: File, val settings: ExportSettings, val playing: Boolean, val bolt: Int)
    @Volatile private var state: State? = null
    @Volatile private var foreground = true
    private val running = AtomicBoolean(true)
    fun update(file: File, settings: ExportSettings, playing: Boolean, bolt: Int = 0) { state = State(file, settings, playing, bolt) }
    fun setForeground(value: Boolean) { foreground = value }
    private val thread = Thread {
        var track: AudioTrack? = null
        var mixer: SoundMixer? = null
        var loadedKey: String? = null
        var loaded: ExportSettings? = null
        var active = false
        var cursor = 0L
        var base = 0L
        var failedStamp: String? = null
        var focusRequested = false
        val drift = DriftGuard()
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
        try {
            while (running.get()) {
                val current = state
                val s = current?.settings
                if (s == null || !foreground || !current.playing || !SoundMixer.hasSound(s, current.file)) {
                    if (active) { track?.pause(); track?.flush(); active = false }
                    if (focusRequested) { manager.abandonAudioFocusRequest(focusRequest); focusRequested = false; hasFocus = false }
                    Thread.sleep(40)
                    continue
                }
                if (!focusRequested) {
                    hasFocus = manager.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                    focusRequested = true
                }
                if (!hasFocus) {
                    if (active) { track?.pause(); track?.flush(); active = false }
                    Thread.sleep(40); continue
                }
                val key = "${current.file}:${current.file.lastModified()}:$s:${current.bolt}"
                if (failedStamp == key) { Thread.sleep(100); continue }
                try {
                    val musicOn = SoundMixer.musicOn(s, current.file)
                    // Se recrea solo si cambia el archivo, el formato PCM o se enciende/apaga la música.
                    val formatKey = "${current.file.lastModified()}:${s.audioRate}:${s.audioChannels}:${s.audioFrames}:$musicOn"
                    if (formatKey != loadedKey || mixer == null || track == null) {
                        track?.release(); track = null; mixer?.close(); mixer = null; active = false
                        val m = SoundMixer(current.file, s, current.bolt)
                        mixer = m
                        val mask = if (m.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                        val size = AudioTrack.getMinBufferSize(m.rate, mask, AudioFormat.ENCODING_PCM_16BIT)
                        require(size > 0) { "Formato de reproducción no compatible" }
                        // v44.11: búfer de ~0,25 s (antes ~0,02 s: se cortaba con muchos efectos).
                        track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            .setAudioFormat(AudioFormat.Builder().setSampleRate(m.rate).setChannelMask(mask)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                            .setBufferSizeInBytes(maxOf(size * 4, m.rate * m.channels * 2 / 4))
                            .setTransferMode(AudioTrack.MODE_STREAM).build()
                        loadedKey = formatKey
                        loaded = s
                    }
                    val m = mixer!!
                    // Al cambiar fragmento, sonidos o duración se vacía el búfer para escucharlo al instante.
                    val clipChanged = loaded != s
                    m.update(s, current.bolt)
                    val audio = track!!
                    if (clipChanged && active) { audio.pause(); audio.flush(); active = false }
                    loaded = s
                    val loop = m.loopFrames.coerceAtLeast(1L)
                    val target = (hub.currentT() * loop).toLong()
                    val played = (base + (audio.playbackHeadPosition.toLong() and 0xffffffffL)) % loop
                    val diff = abs(target - played)
                    if (!active || drift.shouldResync(minOf(diff, loop - diff), m.rate)) {
                        audio.pause(); audio.flush()
                        if (!active) m.prewarm()      // v44.11: los sonidos ya fabricados antes de sonar
                        cursor = target; base = target
                        audio.play(); active = true
                        drift.reset()
                    }
                    val bytes = m.read(cursor, CHUNK)
                    var offset = 0
                    while (offset < bytes.size && running.get()) {
                        val n = audio.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_BLOCKING)
                        check(n > 0) { "No se pudo reproducir el audio" }
                        offset += n
                    }
                    cursor += CHUNK
                } catch (e: Exception) {
                    track?.release(); track = null; mixer?.close(); mixer = null
                    loadedKey = null; active = false; failedStamp = key
                    onError("Audio: ${e.message ?: "no se pudo reproducir"}")
                }
            }
        } catch (e: InterruptedException) {
        } finally {
            manager.abandonAudioFocusRequest(focusRequest)
            track?.release(); mixer?.close()
        }
    }.apply { name = "ZetaImportedAudio"; isDaemon = true }
    fun start() = thread.start()
    fun close() { running.set(false); thread.interrupt() }

    private companion object { const val CHUNK = 2048 }
}
