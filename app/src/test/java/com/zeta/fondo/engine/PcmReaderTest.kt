package com.zeta.fondo.engine

import com.zeta.fondo.model.ExportSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmReaderTest {
    private fun check(samples: ShortArray, settings: ExportSettings, start: Long, count: Int, expected: ShortArray) {
        val file = File.createTempFile("zeta-pcm", ".pcm")
        try {
            val source = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { source.putShort(it) }; file.writeBytes(source.array())
            PcmReader(file, settings).use { reader ->
                val bytes = ByteBuffer.wrap(reader.read(start, count)).order(ByteOrder.LITTLE_ENDIAN)
                val actual = ShortArray(bytes.remaining() / 2) { bytes.short }
                assertArrayEquals(expected, actual)
            }
        } finally { file.delete() }
    }
    @Test fun loopsStereoWithoutMixingChannels() = check(shortArrayOf(100, -100, 200, -200),
        ExportSettings(audioRate=8000, audioChannels=2, audioFrames=2), 1, 3,
        shortArrayOf(200, -200, 100, -100, 200, -200))
    @Test fun nonRepeatingAudioPadsSilence() = check(shortArrayOf(100, 200),
        ExportSettings(audioRate=8000, audioChannels=1, audioFrames=2, audioLoop=false), 1, 3,
        shortArrayOf(200, 0, 0))
    @Test fun volumeAppliesToBothPolarities() = check(shortArrayOf(1000, -1000),
        ExportSettings(audioRate=8000, audioChannels=1, audioFrames=2, soundVolume=.5f), 0, 2,
        shortArrayOf(500, -500))
    @Test fun newAnimationLoopRestartsAudio() = check(shortArrayOf(100, 200, 300),
        ExportSettings(loopSec=2, audioRate=8000, audioChannels=1, audioFrames=3, audioLoop=false), 15999, 3,
        shortArrayOf(0, 100, 200))
    @Test fun selectsFragmentFromMiddleAndRestartsWithAnimation() = check(
        ShortArray(16006) { (it + 1).toShort() },
        ExportSettings(loopSec=2, audioRate=8000, audioChannels=1, audioFrames=16006,
            audioStartFrame=2, audioLoop=false), 15998, 5,
        shortArrayOf(16001, 16002, 3, 4, 5))
    @Test fun shortAudioRepeatsWhenEnabled() = check(
        shortArrayOf(10, 20, 30, 40),
        ExportSettings(audioRate=8000, audioChannels=1, audioFrames=4,
            audioLoop=true), 0, 5,
        shortArrayOf(10, 20, 30, 40, 10))
    @Test fun shortAudioPadsSilenceWhenNotRepeating() = check(
        shortArrayOf(10, 20, 30, 40),
        ExportSettings(audioRate=8000, audioChannels=1, audioFrames=4,
            audioLoop=false), 0, 5,
        shortArrayOf(10, 20, 30, 40, 0))
    @Test fun clampsSegmentWhenVideoDurationChanges() = check(
        shortArrayOf(10, 20, 30, 40),
        ExportSettings(audioRate=8000, audioChannels=1, audioFrames=4,
            audioStartFrame=3, audioLoop=false), 0, 4,
        shortArrayOf(10, 20, 30, 40))
    @Test fun staleMetadataCannotReadPastFile() = check(shortArrayOf(100),
        ExportSettings(audioRate=8000, audioChannels=1, audioFrames=50), 0, 2,
        shortArrayOf(100, 100))
}
