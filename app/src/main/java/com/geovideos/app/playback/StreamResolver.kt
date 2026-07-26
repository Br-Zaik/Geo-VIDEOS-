package com.geovideos.app.playback

import androidx.media3.common.MimeTypes
import com.geovideos.app.data.MediaKind
import com.geovideos.app.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
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
    val extension: String,
    val audioUri: String? = null,
    val audioMimeType: String? = null,
    val audioExtension: String? = null
) {
    val requiresMux: Boolean get() = !audioUri.isNullOrBlank()
}

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
        preferredHeight: Int? = null,
        preferProgressive: Boolean = false
    ): ResolvedMedia {
        if (video.mediaKind != MediaKind.YOUTUBE) return ResolvedMedia(video.source)
        val key = cacheKey(video.id, dataSaver, preferredHeight, preferProgressive)
        val now = System.currentTimeMillis()
        resolvedCache[key]?.takeIf { it.expiresAtMs > now }?.let { return it.media }

        val media = withContext(Dispatchers.IO) {
            val info = streamInfo(video.id)

            if (video.isLive && info.hlsUrl.isNotBlank()) {
                return@withContext ResolvedMedia(info.hlsUrl, MimeTypes.APPLICATION_M3U8)
            }

            // Shorts start faster and more reliably with a progressive stream.
            // It avoids recreating adaptive video/audio tracks every time the vertical page changes.
            if (preferProgressive) {
                val fastStart = selectProgressive(
                    streams = info.videoStreams,
                    dataSaver = dataSaver,
                    preferredHeight = preferredHeight ?: if (dataSaver) 360 else 720
                )
                if (fastStart != null) {
                    return@withContext ResolvedMedia(
                        fastStart.content,
                        fastStart.format?.mimeType
                    )
                }
            }

            // The DASH manifest contains the adaptive tracks used by the exact quality selector.
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
            val progressive = info.videoStreams
                .filter { it.isUrl && !it.isVideoOnly && it.content.isNotBlank() && it.height > 0 }
            val adaptiveVideo = buildList {
                addAll(info.videoOnlyStreams)
                addAll(info.videoStreams.filter { it.isVideoOnly })
            }
                .filter { it.isUrl && it.content.isNotBlank() && it.height > 0 }
            val audioStreams = info.audioStreams
                .filter { it.isUrl && it.content.isNotBlank() }

            val heights = (progressive.asSequence() + adaptiveVideo.asSequence())
                .map { it.height }
                .distinct()
                .sortedDescending()
                .toList()

            val qualities = heights.map { height -> StreamQualityOption(height, "${height}p") }

            val downloads = heights.mapNotNull { height ->
                val direct = progressive
                    .filter { it.height == height }
                    .sortedWith(
                        compareByDescending<VideoStream> { if (isMp4(it.format?.mimeType)) 1 else 0 }
                            .thenByDescending { it.bitrate }
                    )
                    .firstOrNull()
                if (direct != null) {
                    val mime = direct.format?.mimeType
                    return@mapNotNull DownloadStreamOption(
                        id = "direct:${height}:${direct.content.hashCode()}",
                        label = "Video ${height}p",
                        height = height,
                        uri = direct.content,
                        mimeType = mime,
                        extension = extensionFrom(mime, direct.content)
                    )
                }

                val preferMp4 = height <= 1080
                val videoOnly = adaptiveVideo
                    .filter { it.height == height }
                    .sortedWith(
                        compareByDescending<VideoStream> {
                            if (isMp4(it.format?.mimeType) == preferMp4) 1 else 0
                        }.thenByDescending { it.bitrate }
                    )
                    .firstOrNull()
                    ?: return@mapNotNull null

                val videoMime = videoOnly.format?.mimeType
                val family = containerFamily(videoMime)
                val audio = chooseAudio(audioStreams, family) ?: return@mapNotNull null
                val audioMime = audio.format?.mimeType
                val extension = if (family == FAMILY_WEBM) "webm" else "mp4"

                DownloadStreamOption(
                    id = "mux:${height}:${videoOnly.content.hashCode()}:${audio.content.hashCode()}",
                    label = "Video ${height}p",
                    height = height,
                    uri = videoOnly.content,
                    mimeType = videoMime,
                    extension = extension,
                    audioUri = audio.content,
                    audioMimeType = audioMime,
                    audioExtension = extensionFrom(audioMime, audio.content)
                )
            }

            StreamOptions(
                qualities = qualities,
                downloads = downloads,
                isLive = video.isLive
            )
        }
    }

    suspend fun isVerifiedShort(video: VideoItem): Boolean {
        if (video.mediaKind != MediaKind.YOUTUBE) {
            return video.durationMs in 1L..MAX_SHORT_DURATION_MS
        }
        return withContext(Dispatchers.IO) {
            val text = "${video.title} ${video.description} ${video.source}".lowercase()
            val tagged = "/shorts/" in text || "#shorts" in text || "#short " in text
            runCatching {
                val info = streamInfo(video.id)
                info.isShortFormContent ||
                    (tagged && info.duration in 1L..(MAX_SHORT_DURATION_MS / 1000L))
            }.getOrElse {
                tagged && (video.durationMs <= 0L || video.durationMs <= MAX_SHORT_DURATION_MS)
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

    private fun chooseAudio(streams: List<AudioStream>, family: String): AudioStream? {
        val compatible = streams.filter { containerFamily(it.format?.mimeType) == family }
        return compatible.maxByOrNull { stream ->
                when {
                    stream.averageBitrate > 0 -> stream.averageBitrate
                    stream.bitrate > 0 -> stream.bitrate
                    else -> 0
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

    private fun containerFamily(mimeType: String?): String = when {
        mimeType?.contains("webm", ignoreCase = true) == true -> FAMILY_WEBM
        else -> FAMILY_MP4
    }

    private fun isMp4(mimeType: String?): Boolean =
        mimeType?.contains("mp4", ignoreCase = true) == true

    private fun extensionFrom(mimeType: String?, uri: String): String {
        return when {
            mimeType?.contains("webm", ignoreCase = true) == true -> "webm"
            mimeType?.contains("mp4", ignoreCase = true) == true -> "mp4"
            mimeType?.contains("m4a", ignoreCase = true) == true -> "m4a"
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

    private fun cacheKey(
        videoId: String,
        dataSaver: Boolean,
        preferredHeight: Int?,
        preferProgressive: Boolean
    ): String =
        "$videoId:${if (dataSaver) "save" else "auto"}:${preferredHeight ?: 0}:${if (preferProgressive) "fast" else "adaptive"}"

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        val localization = Localization("es", "PE")
        NewPipe.init(AndroidDownloader(), localization, ContentCountry("PE"))
        initialized = true
    }

    private const val FAMILY_MP4 = "mp4"
    private const val FAMILY_WEBM = "webm"
    private const val CACHE_TTL_MS = 20L * 60L * 1000L
    private const val INFO_CACHE_TTL_MS = 12L * 60L * 1000L
    private const val MAX_CACHE_ENTRIES = 30
    private const val MAX_INFO_CACHE_ENTRIES = 12
    private const val PRELOAD_COUNT = 2
    private const val MAX_SHORT_DURATION_MS = 180_000L
}
