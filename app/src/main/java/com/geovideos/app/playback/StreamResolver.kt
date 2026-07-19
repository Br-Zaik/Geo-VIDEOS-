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
import java.util.concurrent.ConcurrentHashMap

internal data class ResolvedMedia(
    val uri: String,
    val mimeType: String? = null
)

/** Resolves public YouTube pages to a stream playable by Media3. */
internal object StreamResolver {
    @Volatile
    private var initialized = false

    private data class CachedStream(
        val media: ResolvedMedia,
        val expiresAtMs: Long
    )

    private val resolvedCache = ConcurrentHashMap<String, CachedStream>()

    suspend fun resolve(video: VideoItem, dataSaver: Boolean): ResolvedMedia {
        if (video.mediaKind != MediaKind.YOUTUBE) return ResolvedMedia(video.source)
        val key = cacheKey(video.id, dataSaver)
        val now = System.currentTimeMillis()
        resolvedCache[key]?.takeIf { it.expiresAtMs > now }?.let { return it.media }

        val media = withContext(Dispatchers.IO) {
            ensureInitialized()
            val watchUrl = "https://www.youtube.com/watch?v=${video.id}"
            val info = StreamInfo.getInfo(watchUrl)

            if (video.isLive && info.hlsUrl.isNotBlank()) {
                return@withContext ResolvedMedia(info.hlsUrl, MimeTypes.APPLICATION_M3U8)
            }

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
        resolvedCache[key] = CachedStream(media, now + CACHE_TTL_MS)
        trimCache(now)
        return media
    }

    suspend fun preload(videos: List<VideoItem>, dataSaver: Boolean) {
        videos.asSequence()
            .filter { it.mediaKind == MediaKind.YOUTUBE && it.id.isNotBlank() }
            .distinctBy { it.id }
            .take(PRELOAD_COUNT)
            .forEach { video -> runCatching { resolve(video, dataSaver) } }
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

    private fun trimCache(now: Long) {
        resolvedCache.entries
            .filter { it.value.expiresAtMs <= now }
            .forEach { resolvedCache.remove(it.key, it.value) }
        if (resolvedCache.size <= MAX_CACHE_ENTRIES) return
        resolvedCache.entries
            .sortedBy { it.value.expiresAtMs }
            .take(resolvedCache.size - MAX_CACHE_ENTRIES)
            .forEach { resolvedCache.remove(it.key) }
    }

    private fun cacheKey(videoId: String, dataSaver: Boolean): String =
        "$videoId:${if (dataSaver) "save" else "auto"}"

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        val localization = Localization("es", "PE")
        NewPipe.init(AndroidDownloader(), localization, ContentCountry("PE"))
        initialized = true
    }

    private const val CACHE_TTL_MS = 20L * 60L * 1000L
    private const val MAX_CACHE_ENTRIES = 24
    private const val PRELOAD_COUNT = 2
}
