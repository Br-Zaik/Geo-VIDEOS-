package com.geovideos.app.playback

import androidx.media3.common.MimeTypes
import com.geovideos.app.data.MediaKind
import com.geovideos.app.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

internal data class ResolvedMedia(
    val uri: String,
    val mimeType: String? = null
)

/** Resolves public YouTube pages to a stream playable by Media3. */
internal object StreamResolver {
    @Volatile
    private var initialized = false

    suspend fun resolve(video: VideoItem, dataSaver: Boolean): ResolvedMedia =
        withContext(Dispatchers.IO) {
            if (video.mediaKind != MediaKind.YOUTUBE) {
                return@withContext ResolvedMedia(video.source)
            }

            ensureInitialized()
            val watchUrl = "https://www.youtube.com/watch?v=${video.id}"
            val info = StreamInfo.getInfo(watchUrl)

            if (video.isLive && info.hlsUrl.isNotBlank()) {
                return@withContext ResolvedMedia(info.hlsUrl, MimeTypes.APPLICATION_M3U8)
            }

            // DASH carries separate adaptive audio/video tracks in one manifest and lets
            // Media3 select a suitable quality without creating two independent players.
            if (!dataSaver && info.dashMpdUrl.isNotBlank()) {
                return@withContext ResolvedMedia(info.dashMpdUrl, MimeTypes.APPLICATION_MPD)
            }

            val progressive = selectProgressive(info.videoStreams, dataSaver)
            if (progressive != null) {
                return@withContext ResolvedMedia(
                    progressive.content,
                    progressive.format?.mimeType
                )
            }

            if (info.hlsUrl.isNotBlank()) {
                return@withContext ResolvedMedia(info.hlsUrl, MimeTypes.APPLICATION_M3U8)
            }
            if (info.dashMpdUrl.isNotBlank()) {
                return@withContext ResolvedMedia(info.dashMpdUrl, MimeTypes.APPLICATION_MPD)
            }

            error("No se encontró una transmisión compatible para este video.")
        }

    private fun selectProgressive(
        streams: List<VideoStream>,
        dataSaver: Boolean
    ): VideoStream? {
        val playable = streams.filter { it.isUrl && !it.isVideoOnly && it.content.isNotBlank() }
        if (playable.isEmpty()) return null
        val targetHeight = if (dataSaver) 360 else 720
        return playable
            .filter { it.height in 1..targetHeight }
            .maxByOrNull { it.height }
            ?: playable.minByOrNull { kotlin.math.abs(it.height - targetHeight) }
    }

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        val localization = Localization("es", "PE")
        NewPipe.init(AndroidDownloader(), localization, ContentCountry("PE"))
        initialized = true
    }
}
