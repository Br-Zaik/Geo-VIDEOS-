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
import kotlin.math.abs

internal data class ResolvedMedia(
    val uri: String,
    val mimeType: String? = null
)

internal data class StreamQualityOption(
    val height: Int,
    val label: String
)

internal data class DownloadStreamOption(
    val id: String,
    val label: String,
    val height: Int,
    val uri: String,
    val mimeType: String?,
    val extension: String
)

internal data class StreamOptions(
    val qualities: List<StreamQualityOption> = emptyList(),
    val downloads: List<DownloadStreamOption> = emptyList(),
    val isLive: Boolean = false
)

/** Resolves public YouTube pages to streams playable by Media3. */
internal object StreamResolver {
    @Volatile
    private var initialized = false

    private data class CachedStream(
        val media: ResolvedMedia,
        val expiresAtMs: Long
    )

    private data class CachedInfo(
        val info: StreamInfo,
        val expiresAtMs: Long
    )

    private val resolvedCache = ConcurrentHashMap<String, CachedStream>()
    private val infoCache = ConcurrentHashMap<String, CachedInfo>()

    suspend fun resolve(
        video: VideoItem,
        dataSaver: Boolean,
        preferredHeight: Int? = null
    ): ResolvedMedia {
        if (video.mediaKind != MediaKind.YOUTUBE) return ResolvedMedia(video.source)
        val key = cacheKey(video.id, dataSaver, preferredHeight)
        val now = System.currentTimeMillis()
        resolvedCache[key]?.takeIf { it.expiresAtMs > now }?.let { return it.media }

        val media = withContext(Dispatchers.IO) {
            val info = streamInfo(video.id)

            if (video.isLive && info.hlsUrl.isNotBlank()) {
                return@withContext ResolvedMedia(info.hlsUrl, MimeTypes.APPLICATION_M3U8)
            }

            // DASH allows Media3 to switch between the real qualities contained in the manifest.
            // It is used for Automatic and every explicit quality whenever it is available.
            if ((!dataSaver || preferredHeight != null) && info.dashMpdUrl.isNotBlank()) {
                return@withContext ResolvedMedia(info.dashMpdUrl, MimeTypes.APPLICATION_MPD)
            }

            val progressive = selectProgressive(
                streams = info.videoStreams,
                dataSaver = dataSaver,
                preferredHeight = preferredHeight
            )
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

    suspend fun options(video: VideoItem): StreamOptions {
        if (video.mediaKind != MediaKind.YOUTUBE) {
            val direct = video.source.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            return StreamOptions(
                downloads = direct?.let {
                    listOf(
                        DownloadStreamOption(
                            id = "original",
                            label = "Calidad original",
                            height = 0,
                            uri = it,
                            mimeType = null,
                            extension = extensionFrom(null, it)
                        )
                    )
                }.orEmpty(),
                isLive = video.isLive
            )
        }

        return withContext(Dispatchers.IO) {
            val info = streamInfo(video.id)
            val allVideoStreams = buildList {
                addAll(info.videoStreams)
                addAll(info.videoOnlyStreams)
            }
                .filter { it.isUrl && it.content.isNotBlank() && it.height > 0 }

            val qualities = allVideoStreams
                .map { it.height }
                .distinct()
                .sortedDescending()
                .map { height -> StreamQualityOption(height, "${height}p") }

            // Android DownloadManager can save a single progressive file. Therefore only
            // streams that already contain video + audio are offered here; no fake 1080p item
            // is shown when it would require muxing a separate audio stream.
            val progressive = info.videoStreams
                .filter { it.isUrl && !it.isVideoOnly && it.content.isNotBlank() && it.height > 0 }
                .sortedWith(
                    compareByDescending<VideoStream> { it.height }
                        .thenByDescending { it.format?.mimeType?.contains("mp4", ignoreCase = true) == true }
                )
                .distinctBy { it.height }
                .map { stream ->
                    val mime = stream.format?.mimeType
                    DownloadStreamOption(
                        id = "${stream.height}:${stream.content.hashCode()}",
                        label = "Video ${stream.height}p",
                        height = stream.height,
                        uri = stream.content,
                        mimeType = mime,
                        extension = extensionFrom(mime, stream.content)
                    )
                }

            StreamOptions(
                qualities = qualities,
                downloads = progressive,
                isLive = video.isLive
            )
        }
    }

    suspend fun isVerifiedShort(video: VideoItem): Boolean {
        if (video.mediaKind != MediaKind.YOUTUBE) {
            return video.durationMs in 1L..MAX_SHORT_DURATION_MS
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                // NewPipe distinguishes the real YouTube Shorts surface from ordinary
                // horizontal videos that merely happen to last less than one minute.
                streamInfo(video.id).isShortFormContent
            }.getOrElse {
                val text = "${video.title} ${video.description} ${video.source}".lowercase()
                ("/shorts/" in text || "#shorts" in text || "#short " in text) &&
                    (video.durationMs <= 0L || video.durationMs <= MAX_SHORT_DURATION_MS)
            }
        }
    }

    suspend fun preload(videos: List<VideoItem>, dataSaver: Boolean) {
        videos.asSequence()
            .filter { it.mediaKind == MediaKind.YOUTUBE && it.id.isNotBlank() }
            .distinctBy { it.id }
            .take(PRELOAD_COUNT)
            .forEach { video -> runCatching { resolve(video, dataSaver) } }
    }

    private suspend fun streamInfo(videoId: String): StreamInfo {
        val now = System.currentTimeMillis()
        infoCache[videoId]?.takeIf { it.expiresAtMs > now }?.let { return it.info }
        return withContext(Dispatchers.IO) {
            ensureInitialized()
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            StreamInfo.getInfo(watchUrl).also { info ->
                infoCache[videoId] = CachedInfo(info, now + INFO_CACHE_TTL_MS)
                trimCache(now)
            }
        }
    }

    private fun selectProgressive(
        streams: List<VideoStream>,
        dataSaver: Boolean,
        preferredHeight: Int?
    ): VideoStream? {
        val playable = streams.filter { it.isUrl && !it.isVideoOnly && it.content.isNotBlank() }
        if (playable.isEmpty()) return null
        val targetHeight = preferredHeight?.takeIf { it > 0 } ?: if (dataSaver) 360 else 720
        return playable
            .filter { it.height in 1..targetHeight }
            .maxByOrNull { it.height }
            ?: playable.minByOrNull { abs(it.height - targetHeight) }
    }

    private fun extensionFrom(mimeType: String?, uri: String): String {
        return when {
            mimeType?.contains("webm", ignoreCase = true) == true -> "webm"
            mimeType?.contains("mp4", ignoreCase = true) == true -> "mp4"
            else -> uri.substringBefore('?')
                .substringAfterLast('.', "mp4")
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
                ?: "mp4"
        }
    }

    private fun trimCache(now: Long) {
        resolvedCache.entries
            .filter { it.value.expiresAtMs <= now }
            .forEach { resolvedCache.remove(it.key, it.value) }
        infoCache.entries
            .filter { it.value.expiresAtMs <= now }
            .forEach { infoCache.remove(it.key, it.value) }

        if (resolvedCache.size > MAX_CACHE_ENTRIES) {
            resolvedCache.entries
                .sortedBy { it.value.expiresAtMs }
                .take(resolvedCache.size - MAX_CACHE_ENTRIES)
                .forEach { resolvedCache.remove(it.key) }
        }
        if (infoCache.size > MAX_INFO_CACHE_ENTRIES) {
            infoCache.entries
                .sortedBy { it.value.expiresAtMs }
                .take(infoCache.size - MAX_INFO_CACHE_ENTRIES)
                .forEach { infoCache.remove(it.key) }
        }
    }

    private fun cacheKey(videoId: String, dataSaver: Boolean, preferredHeight: Int?): String =
        "$videoId:${if (dataSaver) "save" else "auto"}:${preferredHeight ?: 0}"

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        val localization = Localization("es", "PE")
        NewPipe.init(AndroidDownloader(), localization, ContentCountry("PE"))
        initialized = true
    }

    private const val CACHE_TTL_MS = 20L * 60L * 1000L
    private const val INFO_CACHE_TTL_MS = 12L * 60L * 1000L
    private const val MAX_CACHE_ENTRIES = 30
    private const val MAX_INFO_CACHE_ENTRIES = 12
    private const val PRELOAD_COUNT = 2
    private const val MAX_SHORT_DURATION_MS = 75_000L
}
