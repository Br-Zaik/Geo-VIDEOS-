package com.zeta.fondo.engine

import com.zeta.fondo.model.ExportSettings
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded reads; audio restarts with each animation loop in both playback and export. */
class PcmReader(file: File, private var settings: ExportSettings) : AutoCloseable {
    private val source = RandomAccessFile(file, "r")
    private val frameBytes = settings.audioChannels * 2
    private val frames = minOf(settings.audioFrames, source.length() / frameBytes)
    fun update(settings: ExportSettings) { this.settings = settings }
    fun read(startFrame: Long, count: Int): ByteArray {
        val result = ByteArray(count * frameBytes)
        val loopFrames = settings.loopSec.toLong() * settings.audioRate
        val clipFrames = minOf(loopFrames, frames)
        // Si el usuario cambia la duración, se mantiene una ventana válida de vídeo completo.
        val start = settings.audioStartFrame.coerceIn(0L, frames - clipFrames)
        var out = 0
        while (out < count && clipFrames > 0) {
            val withinLoop = (startFrame + out) % loopFrames
            val pos = if (settings.audioLoop) withinLoop % clipFrames else withinLoop
            val n = minOf((count - out).toLong(), loopFrames - withinLoop,
                if (pos < clipFrames) clipFrames - pos else loopFrames - withinLoop).toInt()
            if (pos < clipFrames) {
                source.seek((start + pos) * frameBytes)
                source.readFully(result, out * frameBytes, n * frameBytes)
            }
            out += n
        }
        val volume = settings.soundVolume.coerceIn(0f, 1f)
        if (volume != 1f) {
            val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until result.size / 2) buffer.putShort(i * 2, (buffer.getShort(i * 2) * volume).toInt().toShort())
        }
        return result
    }
    override fun close() = source.close()
}
