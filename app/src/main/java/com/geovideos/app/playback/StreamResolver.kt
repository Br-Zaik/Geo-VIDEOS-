package com.geovideos.app.playback

import androidx.media3.common.MimeTypes
import com.geovideos.app.data.MediaKind
import com.geovideos.app.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal data class ResolvedMedia(
    val uri: String,
    val mimeType: String? = null,
    val audioUri: String? = null,
    val audioMimeType: String? = null
) {
    val hasSeparateAudio: Boolean get() = !audioUri.isNullOrBlank()
}

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
    val audioExtension: String? = null,
    val videoSizeBytes: Long = -1L,
    val audioSizeBytes: Long = -1L,
    val estimatedSizeBytes: Long = -1L
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

    private data class CachedQualities(
        val qualities: List<StreamQualityOption>,
        val isLive: Boolean,
        val expiresAtMs: Long
    )

    private val resolvedCache = ConcurrentHashMap<String, CachedStream>()
    private val infoCache = ConcurrentHashMap<String, CachedInfo>()
    private val qualityCache = ConcurrentHashMap<String, CachedQualities>()
    private val infoLocks = ConcurrentHashMap<String, Mutex>()
    private val contentLengthCache = ConcurrentHashMap<String, Pair<Long, Long>>()

    suspend fun resolve(
        video: VideoItem,
        dataSaver: Boolean,
        preferredHeight: Int? = null,
        preferProgressive: Boolean = false,
        fallbackAttempt: Int = 0,
        forceFresh: Boolean = false
    ): ResolvedMedia {
        if (video.mediaKind != MediaKind.YOUTUBE) return ResolvedMedia(video.source)
        val safeAttempt = fallbackAttempt.coerceIn(0, MAX_PLAYBACK_FALLBACK_ATTEMPT)
        val key = cacheKey(video.id, dataSaver, preferredHeight, preferProgressive, safeAttempt)
        val now = System.currentTimeMillis()
        if (forceFresh) {
            // Un error real de Media3 suele significar que YouTube invalidó una URL firmada
            // o que la extracción ya quedó vieja. No reutilizar ni la URL ni StreamInfo.
            invalidatePlayback(video.id, includeInfo = true)
        } else {
            resolvedCache[key]?.takeIf { it.expiresAtMs > now }?.let { return it.media }
        }

        val media = withContext(Dispatchers.IO) {
            val info = streamInfo(video.id, forceRefresh = forceFresh)
            cacheQualities(video.id, info)

            if (video.isLive && info.hlsUrl.isNotBlank()) {
                return@withContext ResolvedMedia(info.hlsUrl, MimeTypes.APPLICATION_M3U8)
            }

            // La primera pasada respeta una calidad elegida explicitamente. Los reintentos
            // posteriores priorizan que el video arranque con otro formato compatible.
            if (safeAttempt == 0) {
                preferredHeight?.takeIf { it > 0 }?.let { exactHeight ->
                    val exactProgressive = selectExactProgressive(info.videoStreams, exactHeight)
                    if (exactProgressive != null) {
                        return@withContext ResolvedMedia(
                            uri = exactProgressive.content,
                            mimeType = exactProgressive.format?.mimeType
                        )
                    }

                    val adaptiveVideo = selectExactAdaptive(info, exactHeight)
                    if (adaptiveVideo != null) {
                        val family = containerFamily(adaptiveVideo.format?.mimeType)
                        val audioStreams = info.audioStreams.filter { it.isUrl && it.content.isNotBlank() }
                        val audio = chooseAudio(audioStreams, family) ?: chooseBestAudio(audioStreams)
                        if (audio != null) {
                            return@withContext ResolvedMedia(
                                uri = adaptiveVideo.content,
                                mimeType = adaptiveVideo.format?.mimeType,
                                audioUri = audio.content,
                                audioMimeType = audio.format?.mimeType
                            )
                        }
                    }
                }
            }

            val targetHeight = preferredHeight?.takeIf { safeAttempt == 0 && it > 0 }
                ?: if (dataSaver) 360 else 720
            val primaryProgressive = selectProgressive(
                streams = info.videoStreams,
                dataSaver = dataSaver,
                preferredHeight = targetHeight
            )
            val alternateProgressive = selectAlternateProgressive(
                streams = info.videoStreams,
                primary = primaryProgressive,
                targetHeight = targetHeight
            )
            val adaptive = selectAdaptivePlayback(info, targetHeight)

            when (safeAttempt) {
                0 -> {
                    // Inicio rapido: un stream progresivo directo evita solicitudes extra de
                    // manifiestos y suele empezar antes en redes moviles.
                    primaryProgressive?.let { stream ->
                        return@withContext ResolvedMedia(stream.content, stream.format?.mimeType)
                    }
                    adaptive?.let { return@withContext it }
                    if (!dataSaver && info.dashMpdUrl.isNotBlank()) {
                        return@withContext ResolvedMedia(info.dashMpdUrl, MimeTypes.APPLICATION_MPD)
                    }
                }

                1 -> {
                    // Segundo intento: no repetir exactamente la URL que ya fallo.
                    alternateProgressive?.let { stream ->
                        return@withContext ResolvedMedia(stream.content, stream.format?.mimeType)
                    }
                    adaptive?.let { return@withContext it }
                }

                2 -> {
                    // Tercer intento: usar video/audio adaptativos separados. PlaybackService
                    // los combina en Media3 sin depender del progresivo que fallo.
                    adaptive?.let { return@withContext it }
                    if (info.dashMpdUrl.isNotBlank()) {
                        return@withContext ResolvedMedia(info.dashMpdUrl, MimeTypes.APPLICATION_MPD)
                    }
                }

                else -> {
                    // Ultima salida controlada: manifiesto DASH/HLS y, si no existe, cualquier
                    // progresivo alternativo disponible.
                    if (info.dashMpdUrl.isNotBlank()) {
                        return@withContext ResolvedMedia(info.dashMpdUrl, MimeTypes.APPLICATION_MPD)
                    }
                    if (info.hlsUrl.isNotBlank()) {
                        return@withContext ResolvedMedia(info.hlsUrl, MimeTypes.APPLICATION_M3U8)
                    }
                    alternateProgressive?.let { stream ->
                        return@withContext ResolvedMedia(stream.content, stream.format?.mimeType)
                    }
                }
            }

            if (info.hlsUrl.isNotBlank()) {
                return@withContext ResolvedMedia(info.hlsUrl, MimeTypes.APPLICATION_M3U8)
            }
            if (info.dashMpdUrl.isNotBlank()) {
                return@withContext ResolvedMedia(info.dashMpdUrl, MimeTypes.APPLICATION_MPD)
            }
            primaryProgressive?.let { stream ->
                return@withContext ResolvedMedia(stream.content, stream.format?.mimeType)
            }
            adaptive?.let { return@withContext it }

            error("No se encontro una transmision compatible para este video.")
        }
        resolvedCache[key] = CachedStream(media, now + CACHE_TTL_MS)
        trimCache(now)
        return media
    }

    suspend fun resolveAudio(video: VideoItem): ResolvedMedia {
        if (video.mediaKind != MediaKind.YOUTUBE) {
            return ResolvedMedia(video.source)
        }
        val key = "${video.id}:audio-only"
        val now = System.currentTimeMillis()
        resolvedCache[key]?.takeIf { it.expiresAtMs > now }?.let { return it.media }

        val media = withContext(Dispatchers.IO) {
            val info = streamInfo(video.id)
            cacheQualities(video.id, info)
            val audio = chooseBestAudio(
                info.audioStreams.filter { it.isUrl && it.content.isNotBlank() }
            ) ?: error("No se encontró una pista de audio compatible para este video.")
            ResolvedMedia(
                uri = audio.content,
                mimeType = audio.format?.mimeType
            )
        }
        resolvedCache[key] = CachedStream(media, now + CACHE_TTL_MS)
        trimCache(now)
        return media
    }

    suspend fun options(video: VideoItem, includeDownloadSizes: Boolean = true): StreamOptions {
        if (video.mediaKind != MediaKind.YOUTUBE) {
            val direct = video.source.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val downloads = direct?.let {
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
            }.orEmpty()
            return StreamOptions(
                downloads = if (includeDownloadSizes) attachSizes(downloads) else downloads,
                isLive = video.isLive
            )
        }

        if (!includeDownloadSizes) {
            val now = System.currentTimeMillis()
            qualityCache[video.id]?.takeIf { it.expiresAtMs > now }?.let { cached ->
                return StreamOptions(qualities = cached.qualities, isLive = cached.isLive)
            }
        }

        return withContext(Dispatchers.IO) {
            val info = streamInfo(video.id)
            val qualities = qualitiesFromInfo(info)
            qualityCache[video.id] = CachedQualities(
                qualities = qualities,
                isLive = video.isLive || info.hlsUrl.isNotBlank(),
                expiresAtMs = System.currentTimeMillis() + INFO_CACHE_TTL_MS
            )
            if (!includeDownloadSizes) {
                return@withContext StreamOptions(qualities = qualities, isLive = video.isLive)
            }

            val progressive = info.videoStreams
                .filter { it.isUrl && !it.isVideoOnly && it.content.isNotBlank() && it.height > 0 }
            val adaptiveVideo = buildList {
                addAll(info.videoOnlyStreams)
                addAll(info.videoStreams.filter { it.isVideoOnly })
            }
                .filter { it.isUrl && it.content.isNotBlank() && it.height > 0 }
                .distinctBy { "${it.height}:${it.content}" }
            val audioStreams = info.audioStreams
                .filter { it.isUrl && it.content.isNotBlank() }

            val allHeights = (progressive.asSequence() + adaptiveVideo.asSequence())
                .map { it.height }
                .distinct()
                .sortedDescending()
                .toList()

            val downloadsWithoutSizes = allHeights.mapNotNull { height ->
                val direct = progressive
                    .filter { it.height == height }
                    .sortedWith(
                        compareByDescending<VideoStream> { if (isMp4(it.format?.mimeType)) 1 else 0 }
                            .thenByDescending { videoCodecCompatibility(it.codec) }
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

                val videoOnly = chooseAdaptiveVideo(adaptiveVideo.filter { it.height == height })
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

            val downloads = attachSizes(downloadsWithoutSizes)

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

    suspend fun preload(
        videos: List<VideoItem>,
        dataSaver: Boolean,
        preferredHeight: Int? = null
    ) {
        // Resolve likely next videos before they are tapped. Use exactly the same quality key
        // as normal playback so the result is consumed directly from resolvedCache.
        videos.asSequence()
            .filter { it.mediaKind == MediaKind.YOUTUBE && it.id.isNotBlank() }
            .distinctBy { it.id }
            .take(PRELOAD_COUNT)
            .forEach { video ->
                runCatching {
                    resolve(
                        video = video,
                        dataSaver = dataSaver,
                        preferredHeight = preferredHeight,
                        preferProgressive = false
                    )
                }
            }
    }

    private suspend fun streamInfo(videoId: String, forceRefresh: Boolean = false): StreamInfo {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            infoCache[videoId]?.takeIf { it.expiresAtMs > now }?.let { return it.info }
        }

        // If a home-feed preload and a tap request the same video at the same time, only run
        // the expensive NewPipe extraction once. Other videos are not blocked by this lock.
        val mutex = infoLocks.getOrPut(videoId) { Mutex() }
        return try {
            mutex.withLock {
                val lockedNow = System.currentTimeMillis()
                if (!forceRefresh) {
                    infoCache[videoId]?.takeIf { it.expiresAtMs > lockedNow }?.let { return@withLock it.info }
                }
                withContext(Dispatchers.IO) {
                    ensureInitialized()
                    val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                    StreamInfo.getInfo(watchUrl).also { info ->
                        infoCache[videoId] = CachedInfo(info, System.currentTimeMillis() + INFO_CACHE_TTL_MS)
                        trimCache(System.currentTimeMillis())
                    }
                }
            }
        } finally {
            if (infoLocks[videoId] === mutex) infoLocks.remove(videoId)
        }
    }

    private fun selectExactAdaptive(info: StreamInfo, height: Int): VideoStream? {
        val streams = buildList {
            addAll(info.videoOnlyStreams)
            addAll(info.videoStreams.filter { it.isVideoOnly })
        }.filter { it.isUrl && it.content.isNotBlank() && it.height == height }
        return chooseAdaptiveVideo(streams)
    }

    private fun chooseAdaptiveVideo(streams: List<VideoStream>): VideoStream? {
        if (streams.isEmpty()) return null
        return streams.sortedWith(
            compareByDescending<VideoStream> { if (isMp4(it.format?.mimeType)) 1 else 0 }
                .thenByDescending { videoCodecCompatibility(it.codec) }
                .thenByDescending { it.bitrate }
        ).firstOrNull()
    }

    private fun chooseAudio(streams: List<AudioStream>, family: String): AudioStream? {
        val compatible = streams.filter { containerFamily(it.format?.mimeType) == family }
        return chooseBestAudio(compatible)
    }

    private fun chooseBestAudio(streams: List<AudioStream>): AudioStream? = streams.maxByOrNull { stream ->
        val bitrate = when {
            stream.averageBitrate > 0 -> stream.averageBitrate
            stream.bitrate > 0 -> stream.bitrate
            else -> 0
        }
        val codecBonus = when {
            stream.codec.orEmpty().contains("mp4a", ignoreCase = true) -> 2_000_000
            stream.codec.orEmpty().contains("opus", ignoreCase = true) -> 1_000_000
            else -> 0
        }
        codecBonus + bitrate
    }

    private fun qualitiesFromInfo(info: StreamInfo): List<StreamQualityOption> {
        val heights = (info.videoStreams.asSequence() + info.videoOnlyStreams.asSequence())
            .filter { it.isUrl && it.content.isNotBlank() && it.height > 0 }
            .map { it.height }
            .distinct()
            .sortedDescending()
            .toList()
        return heights.map { height -> StreamQualityOption(height, "${height}p") }
    }

    private fun cacheQualities(videoId: String, info: StreamInfo) {
        if (videoId.isBlank()) return
        val qualities = qualitiesFromInfo(info)
        qualityCache[videoId] = CachedQualities(
            qualities = qualities,
            isLive = info.hlsUrl.isNotBlank(),
            expiresAtMs = System.currentTimeMillis() + INFO_CACHE_TTL_MS
        )
    }

    private fun selectAlternateProgressive(
        streams: List<VideoStream>,
        primary: VideoStream?,
        targetHeight: Int
    ): VideoStream? {
        return streams.asSequence()
            .filter {
                it.isUrl &&
                    !it.isVideoOnly &&
                    it.content.isNotBlank() &&
                    it.content != primary?.content
            }
            .sortedWith(
                compareByDescending<VideoStream> { if (isMp4(it.format?.mimeType)) 1 else 0 }
                    .thenByDescending { videoCodecCompatibility(it.codec) }
                    .thenBy { abs(it.height - targetHeight) }
                    .thenByDescending { it.bitrate }
            )
            .firstOrNull()
    }

    private fun selectAdaptivePlayback(info: StreamInfo, targetHeight: Int): ResolvedMedia? {
        val videos = buildList {
            addAll(info.videoOnlyStreams)
            addAll(info.videoStreams.filter { it.isVideoOnly })
        }.filter { it.isUrl && it.content.isNotBlank() && it.height > 0 }
        if (videos.isEmpty()) return null

        val bestHeight = videos
            .map { it.height }
            .distinct()
            .filter { it <= targetHeight }
            .maxOrNull()
            ?: videos.minByOrNull { abs(it.height - targetHeight) }?.height
            ?: return null
        val video = chooseAdaptiveVideo(videos.filter { it.height == bestHeight }) ?: return null
        val audioStreams = info.audioStreams.filter { it.isUrl && it.content.isNotBlank() }
        val family = containerFamily(video.format?.mimeType)
        val audio = chooseAudio(audioStreams, family) ?: chooseBestAudio(audioStreams) ?: return null
        return ResolvedMedia(
            uri = video.content,
            mimeType = video.format?.mimeType,
            audioUri = audio.content,
            audioMimeType = audio.format?.mimeType
        )
    }

    private fun selectExactProgressive(
        streams: List<VideoStream>,
        height: Int
    ): VideoStream? = streams
        .asSequence()
        .filter { it.isUrl && !it.isVideoOnly && it.content.isNotBlank() && it.height == height }
        .sortedWith(
            compareByDescending<VideoStream> { if (isMp4(it.format?.mimeType)) 1 else 0 }
                .thenByDescending { videoCodecCompatibility(it.codec) }
                .thenByDescending { it.bitrate }
        )
        .firstOrNull()

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

    private suspend fun attachSizes(options: List<DownloadStreamOption>): List<DownloadStreamOption> {
        if (options.isEmpty()) return options
        val urls = options.flatMap { option ->
            buildList {
                add(option.uri)
                option.audioUri?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct()

        val sizes = coroutineScope {
            urls.map { uri ->
                async(Dispatchers.IO) { uri to probeContentLength(uri) }
            }.awaitAll().toMap()
        }

        return options.map { option ->
            val videoBytes = sizes[option.uri] ?: -1L
            val audioBytes = option.audioUri?.let { sizes[it] ?: -1L } ?: 0L
            val total = when {
                option.requiresMux && videoBytes > 0L && audioBytes > 0L -> videoBytes + audioBytes
                !option.requiresMux && videoBytes > 0L -> videoBytes
                else -> -1L
            }
            option.copy(
                videoSizeBytes = videoBytes,
                audioSizeBytes = audioBytes,
                estimatedSizeBytes = total
            )
        }
    }

    private fun probeContentLength(uri: String): Long {
        val now = System.currentTimeMillis()
        contentLengthCache[uri]?.takeIf { it.second > now }?.let { return it.first }
        contentLengthFromQuery(uri)?.let { declaredLength ->
            contentLengthCache[uri] = declaredLength to (now + CONTENT_LENGTH_CACHE_TTL_MS)
            return declaredLength
        }

        val headLength = runCatching {
            openLengthConnection(uri, "HEAD", null).useConnection { connection ->
                if (connection.responseCode in 200..299) connection.contentLengthLong else -1L
            }
        }.getOrDefault(-1L)

        val length = if (headLength > 0L) {
            headLength
        } else {
            runCatching {
                openLengthConnection(uri, "GET", "bytes=0-0").useConnection { connection ->
                    val response = connection.responseCode
                    if (response !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                        return@useConnection -1L
                    }
                    val contentRange = connection.getHeaderField("Content-Range").orEmpty()
                    val totalFromRange = contentRange.substringAfterLast('/', "")
                        .toLongOrNull()
                        ?.takeIf { it > 0L }
                    totalFromRange ?: connection.contentLengthLong.takeIf { it > 0L } ?: -1L
                }
            }.getOrDefault(-1L)
        }

        contentLengthCache[uri] = length to (now + CONTENT_LENGTH_CACHE_TTL_MS)
        return length
    }


    private fun contentLengthFromQuery(uri: String): Long? = runCatching {
        URL(uri).query.orEmpty()
            .split('&')
            .firstOrNull { parameter -> parameter.substringBefore('=') == "clen" }
            ?.substringAfter('=', "")
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    }.getOrNull()

    private fun openLengthConnection(uri: String, method: String, range: String?): HttpURLConnection =
        (URL(uri).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = 8_000
            readTimeout = 8_000
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("Referer", "https://www.youtube.com/")
            if (!range.isNullOrBlank()) setRequestProperty("Range", range)
        }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            runCatching { inputStream.close() }
            disconnect()
        }
    }

    private fun containerFamily(mimeType: String?): String = when {
        mimeType?.contains("webm", ignoreCase = true) == true -> FAMILY_WEBM
        else -> FAMILY_MP4
    }

    private fun videoCodecCompatibility(codec: String?): Int = when (codec.orEmpty().lowercase()) {
        "avc1", "h264" -> 4
        "vp9", "vp09" -> 3
        "av01", "av1" -> 2
        else -> when {
            codec.orEmpty().contains("avc1", true) || codec.orEmpty().contains("h264", true) -> 4
            codec.orEmpty().contains("vp9", true) || codec.orEmpty().contains("vp09", true) -> 3
            codec.orEmpty().contains("av01", true) || codec.orEmpty().contains("av1", true) -> 2
            else -> 1
        }
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

    /**
     * Purga únicamente datos de reproducción de un video. Se usa cuando Media3 confirma que
     * una URL dejó de servir; no borra preferencias, historial ni ningún dato del usuario.
     */
    fun invalidatePlayback(videoId: String, includeInfo: Boolean = true) {
        if (videoId.isBlank()) return
        val prefix = "$videoId:"
        resolvedCache.keys
            .filter { it.startsWith(prefix) }
            .forEach { resolvedCache.remove(it) }
        if (includeInfo) {
            infoCache.remove(videoId)
            qualityCache.remove(videoId)
        }
    }

    private fun trimCache(now: Long) {
        resolvedCache.entries
            .filter { it.value.expiresAtMs <= now }
            .forEach { resolvedCache.remove(it.key, it.value) }
        infoCache.entries
            .filter { it.value.expiresAtMs <= now }
            .forEach { infoCache.remove(it.key, it.value) }
        qualityCache.entries
            .filter { it.value.expiresAtMs <= now }
            .forEach { qualityCache.remove(it.key, it.value) }
        contentLengthCache.entries
            .filter { it.value.second <= now }
            .forEach { contentLengthCache.remove(it.key, it.value) }

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
        preferProgressive: Boolean,
        fallbackAttempt: Int
    ): String =
        "$videoId:${if (dataSaver) "save" else "auto"}:${preferredHeight ?: 0}:${if (preferProgressive) "fast" else "adaptive"}:try$fallbackAttempt"

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
    private const val CONTENT_LENGTH_CACHE_TTL_MS = 30L * 60L * 1000L
    private const val MAX_CACHE_ENTRIES = 30
    private const val MAX_INFO_CACHE_ENTRIES = 12
    private const val PRELOAD_COUNT = 2
    private const val MAX_PLAYBACK_FALLBACK_ATTEMPT = 3
    private const val MAX_SHORT_DURATION_MS = 180_000L
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
}
