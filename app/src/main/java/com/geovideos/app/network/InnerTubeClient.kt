package com.geovideos.app.network

import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.PlaylistItem
import com.geovideos.app.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/** La sesión guardada ya no sirve (se cerró sesión o Google la venció). */
class YouTubeSessionExpiredException :
    IOException("La sesión de YouTube venció. Vuelve a conectarla en Cuenta.")

data class InnerTubePage(
    val videos: List<VideoItem>,
    val shorts: List<VideoItem>,
    val continuation: String,
    val channels: List<ChannelItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val chips: List<HomeChip> = emptyList()
)

/** Filtro de YouTube (Videojuegos, Música, En vivo…) con su token para cargarlo. */
data class HomeChip(val title: String, val token: String)

/**
 * Cliente de la API interna de YouTube (la misma que usa youtube.com) con la sesión
 * del usuario. Da acceso a lo que la API oficial no entrega: historial real, Ver más
 * tarde, el inicio recomendado y el registro de lo visto en el historial de YouTube.
 *
 * Uso personal. YouTube puede cambiar este formato; si algo deja de cargar, la app
 * vuelve a sus listas locales sin romperse.
 */
class InnerTubeClient(private val cookieProvider: () -> String) {

    private data class ClientInfo(
        val name: String,
        val version: String,
        val id: String,
        val userAgent: String
    )

    private data class WatchSession(
        val videoId: String,
        val cpn: String,
        val playbackUrl: String,
        val watchtimeUrl: String,
        val client: ClientInfo,
        var lastSentSec: Double,
        var lastPingAt: Long
    )

    @Volatile
    private var watch: WatchSession? = null

    @Volatile
    private var lastHomeChips: List<HomeChip> = emptyList()

    private val nextCache = HashMap<String, Pair<Long, JSONObject>>()

    // ---------------------------------------------------------------- Listas

    suspend fun home(continuation: String = ""): InnerTubePage {
        val page = browse("FEwhat_to_watch", continuation)
        if (continuation.isBlank() && page.chips.isNotEmpty()) lastHomeChips = page.chips
        return page
    }

    /** Filtros personalizados del inicio de YouTube (se leen del último inicio cargado). */
    suspend fun homeChips(): List<HomeChip> {
        if (lastHomeChips.isEmpty()) runCatching { home() }
        return lastHomeChips
    }

    suspend fun subscriptionsFeed(continuation: String = ""): InnerTubePage =
        browse("FEsubscriptions", continuation)

    suspend fun browsePage(browseId: String, params: String = "", continuation: String = ""): InnerTubePage =
        withContext(Dispatchers.IO) {
            val body = baseBody(WEB)
            if (continuation.isNotBlank()) {
                body.put("continuation", continuation)
            } else {
                body.put("browseId", browseId)
                if (params.isNotBlank()) body.put("params", params)
            }
            parsePage(post("browse", body, WEB))
        }

    suspend fun search(query: String, params: String = "", continuation: String = ""): InnerTubePage =
        withContext(Dispatchers.IO) {
            val body = baseBody(WEB)
            if (continuation.isNotBlank()) {
                body.put("continuation", continuation)
            } else {
                body.put("query", query)
                if (params.isNotBlank()) body.put("params", params)
            }
            parsePage(post("search", body, WEB))
        }

    /** Página de un video (detalles, relacionados, comentarios). La primera se guarda 3 min. */
    suspend fun next(videoId: String, continuation: String = ""): JSONObject = withContext(Dispatchers.IO) {
        if (continuation.isNotBlank()) {
            return@withContext post("next", baseBody(WEB).put("continuation", continuation), WEB)
        }
        val now = System.currentTimeMillis()
        synchronized(nextCache) {
            nextCache[videoId]?.takeIf { now - it.first < 180_000L }?.let { return@withContext it.second }
        }
        val json = post("next", baseBody(WEB).put("videoId", videoId), WEB)
        synchronized(nextCache) {
            if (nextCache.size > 20) nextCache.clear()
            nextCache[videoId] = now to json
        }
        json
    }

    /** Llamada genérica a la API interna con el cliente web. */
    suspend fun call(endpoint: String, fill: JSONObject.() -> Unit = {}): JSONObject =
        withContext(Dispatchers.IO) {
            val body = baseBody(WEB)
            body.fill()
            post(endpoint, body, WEB)
        }

    fun parse(json: JSONObject): InnerTubePage = parsePage(json)

    suspend fun rate(videoId: String, rating: String) {
        val endpoint = when (rating) {
            "like" -> "like/like"
            "dislike" -> "like/dislike"
            else -> "like/removelike"
        }
        call(endpoint) { put("target", JSONObject().put("videoId", videoId)) }
    }

    suspend fun setSubscribed(channelId: String, subscribed: Boolean) {
        val endpoint = if (subscribed) "subscription/subscribe" else "subscription/unsubscribe"
        call(endpoint) { put("channelIds", JSONArray().put(channelId)) }
    }

    suspend fun editPlaylist(playlistId: String, videoId: String, add: Boolean) {
        val action = if (add) {
            JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
        } else {
            JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId)
        }
        val json = call("browse/edit_playlist") {
            put("playlistId", playlistId.removePrefix("VL"))
            put("actions", JSONArray().put(action))
        }
        val status = json.optString("status")
        if (status.isNotBlank() && status != "STATUS_SUCCEEDED") {
            throw IOException("YouTube no aceptó el cambio en la lista.")
        }
    }

    suspend fun createPlaylist(title: String): String {
        val json = call("playlist/create") {
            put("title", title)
            put("privacyStatus", "PRIVATE")
            put("videoIds", JSONArray())
        }
        return json.optString("playlistId")
    }

    suspend fun history(continuation: String = ""): InnerTubePage = browse("FEhistory", continuation)

    suspend fun watchLater(continuation: String = ""): InnerTubePage = browse("VLWL", continuation)

    suspend fun browse(browseId: String, continuation: String = ""): InnerTubePage =
        withContext(Dispatchers.IO) {
            val body = baseBody(WEB)
            if (continuation.isNotBlank()) {
                body.put("continuation", continuation)
            } else {
                body.put("browseId", browseId)
            }
            parsePage(post("browse", body, WEB))
        }

    suspend fun editWatchLater(videoId: String, add: Boolean) = editPlaylist("WL", videoId, add)

    // ------------------------------------------------- Historial de YouTube

    /** Avisa a YouTube que empezó a verse el video: así aparece en el historial. */
    suspend fun startWatch(videoId: String) = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || watch?.videoId == videoId) return@withContext
        for (client in listOf(WEB, MWEB)) {
            val json = runCatching {
                post("player", baseBody(client).put("videoId", videoId), client)
            }.getOrNull() ?: continue
            val tracking = json.optJSONObject("playbackTracking") ?: continue
            val playback = tracking.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl").orEmpty()
            val watchtime = tracking.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl").orEmpty()
            if (playback.isBlank()) continue
            val session = WatchSession(
                videoId = videoId,
                cpn = newCpn(),
                playbackUrl = normalizeStatsUrl(playback),
                watchtimeUrl = normalizeStatsUrl(watchtime),
                client = client,
                lastSentSec = 0.0,
                lastPingAt = System.currentTimeMillis()
            )
            watch = session
            ping(
                session.playbackUrl,
                linkedMapOf(
                    "ver" to "2",
                    "cpn" to session.cpn,
                    "c" to client.name,
                    "cver" to client.version,
                    "cmt" to "0",
                    "fs" to "0",
                    "el" to "detailpage"
                ),
                client
            )
            return@withContext
        }
    }

    /** Envía el minuto actual para que YouTube recuerde dónde te quedaste. */
    suspend fun reportProgress(
        videoId: String,
        positionMs: Long,
        durationMs: Long,
        force: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val session = watch?.takeIf { it.videoId == videoId } ?: return@withContext
        if (session.watchtimeUrl.isBlank()) return@withContext
        val now = System.currentTimeMillis()
        if (!force && now - session.lastPingAt < MIN_PROGRESS_INTERVAL_MS) return@withContext
        val current = positionMs.coerceAtLeast(0L) / 1000.0
        val start = session.lastSentSec.coerceAtMost(current)
        session.lastPingAt = now
        session.lastSentSec = current
        val params = linkedMapOf(
            "ver" to "2",
            "cpn" to session.cpn,
            "c" to session.client.name,
            "cver" to session.client.version,
            "st" to seconds(start),
            "et" to seconds(current),
            "cmt" to seconds(current),
            "state" to "playing",
            "el" to "detailpage"
        )
        if (durationMs > 0L) params["len"] = seconds(durationMs / 1000.0)
        ping(session.watchtimeUrl, params, session.client)
    }

    fun clearWatch() {
        watch = null
    }

    // ------------------------------------------------------------------ Red

    private fun baseBody(client: ClientInfo): JSONObject {
        val zone = TimeZone.getDefault()
        val offsetMinutes = zone.getOffset(System.currentTimeMillis()) / 60_000
        return JSONObject().put(
            "context",
            JSONObject()
                .put(
                    "client",
                    JSONObject()
                        .put("clientName", client.name)
                        .put("clientVersion", client.version)
                        .put("hl", "es-419")
                        .put("gl", "PE")
                        .put("timeZone", zone.id)
                        .put("utcOffsetMinutes", offsetMinutes)
                )
                .put("user", JSONObject().put("lockedSafetyMode", false))
        )
    }

    private fun post(endpoint: String, body: JSONObject, client: ClientInfo): JSONObject {
        val cookies = cookieProvider()
        if (cookies.isBlank()) throw YouTubeSessionExpiredException()
        val connection = URL("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            applyHeaders(connection, cookies, client)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401) throw YouTubeSessionExpiredException()
            if (code !in 200..299) throw IOException("YouTube respondió $code")
            val json = JSONObject(text)
            val loggedOut = json.optJSONObject("responseContext")
                ?.optJSONObject("mainAppWebResponseContext")
                ?.optBoolean("loggedOut", false) == true
            if (loggedOut) throw YouTubeSessionExpiredException()
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun ping(baseUrl: String, params: Map<String, String>, client: ClientInfo) {
        val cookies = cookieProvider()
        if (cookies.isBlank() || baseUrl.isBlank()) return
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val separator = if (baseUrl.contains('?')) "&" else "?"
        runCatching {
            val connection = URL(baseUrl + separator + query).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                applyHeaders(connection, cookies, client)
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun applyHeaders(connection: HttpURLConnection, cookies: String, client: ClientInfo) {
        connection.setRequestProperty("Cookie", cookies)
        connection.setRequestProperty("User-Agent", client.userAgent)
        connection.setRequestProperty("Origin", ORIGIN)
        connection.setRequestProperty("X-Origin", ORIGIN)
        connection.setRequestProperty("Referer", "$ORIGIN/")
        connection.setRequestProperty("Accept-Language", "es-419,es;q=0.9")
        connection.setRequestProperty("X-Goog-AuthUser", "0")
        connection.setRequestProperty("X-Youtube-Client-Name", client.id)
        connection.setRequestProperty("X-Youtube-Client-Version", client.version)
        authorization(cookies)?.let { connection.setRequestProperty("Authorization", it) }
    }

    private fun authorization(cookies: String): String? {
        val sapisid = cookieValue(cookies, "SAPISID")
            ?: cookieValue(cookies, "__Secure-3PAPISID")
            ?: return null
        val timestamp = System.currentTimeMillis() / 1000L
        return "SAPISIDHASH ${timestamp}_${sha1("$timestamp $sapisid $ORIGIN")}"
    }

    private fun cookieValue(cookies: String, name: String): String? =
        cookies.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun normalizeStatsUrl(url: String): String =
        url.replace("https://s.youtube.com", ORIGIN)

    private fun newCpn(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return buildString { repeat(16) { append(alphabet[Random.nextInt(alphabet.length)]) } }
    }

    private fun seconds(value: Double): String = String.format(Locale.US, "%.3f", value)

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    // --------------------------------------------------------------- Lectura

    private fun parsePage(root: JSONObject): InnerTubePage {
        val videos = ArrayList<VideoItem>()
        val shorts = ArrayList<VideoItem>()
        val channels = ArrayList<ChannelItem>()
        val playlists = ArrayList<PlaylistItem>()
        val chips = ArrayList<HomeChip>()
        val seen = HashSet<String>()
        var continuation = ""

        fun addChannel(channel: ChannelItem?) {
            if (channel != null && channel.id.isNotBlank() && seen.add("c:" + channel.id)) channels.add(channel)
        }

        fun addPlaylist(playlist: PlaylistItem?) {
            if (playlist != null && playlist.id.isNotBlank() && seen.add("p:" + playlist.id)) playlists.add(playlist)
        }

        fun addVideo(video: VideoItem?) {
            if (video != null && video.id.isNotBlank() && seen.add(video.id)) videos.add(video)
        }

        fun addShort(video: VideoItem?) {
            if (video != null && video.id.isNotBlank() && seen.add(video.id)) shorts.add(video)
        }

        fun visit(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = node.opt(key)
                        val obj = value as? JSONObject
                        when (key) {
                            "videoRenderer",
                            "gridVideoRenderer",
                            "compactVideoRenderer",
                            "playlistVideoRenderer" -> addVideo(obj?.let { parseVideoRenderer(it) })
                            "reelItemRenderer" -> addShort(obj?.let { parseReelItem(it) })
                            "shortsLockupViewModel" -> addShort(obj?.let { parseShortsLockup(it) })
                            "lockupViewModel" -> if (obj != null) {
                                val type = obj.optString("contentType")
                                if (type.isBlank() || type == "LOCKUP_CONTENT_TYPE_VIDEO") {
                                    addVideo(parseLockup(obj))
                                } else {
                                    addPlaylist(parseLockupPlaylist(obj))
                                }
                            }
                            "channelRenderer", "gridChannelRenderer" -> addChannel(obj?.let { parseChannelRenderer(it) })
                            "gridPlaylistRenderer", "playlistRenderer", "radioRenderer", "compactRadioRenderer" ->
                                addPlaylist(obj?.let { parsePlaylistRenderer(it) })
                            "chipCloudChipRenderer" -> {
                                val title = text(obj?.opt("text"))
                                val token = obj?.optJSONObject("navigationEndpoint")
                                    ?.let { findFirstString(it, "token") { t -> t.isNotBlank() } }
                                if (title.isNotBlank() && !token.isNullOrBlank()) chips.add(HomeChip(title, token))
                            }
                            "continuationItemRenderer" -> {
                                val token = obj?.let { findFirstString(it, "token") { t -> t.isNotBlank() } }
                                if (!token.isNullOrBlank()) continuation = token
                            }
                            else -> visit(value)
                        }
                    }
                }
                is JSONArray -> for (index in 0 until node.length()) visit(node.opt(index))
            }
        }

        visit(root)
        return InnerTubePage(videos, shorts, continuation, channels, playlists, chips)
    }

    private fun parseVideoRenderer(o: JSONObject): VideoItem? {
        val id = o.optString("videoId")
        if (id.isBlank()) return null
        val title = text(o.opt("title")).ifBlank { text(o.opt("headline")) }
        if (title.isBlank()) return null
        val byline = o.optJSONObject("ownerText")
            ?: o.optJSONObject("shortBylineText")
            ?: o.optJSONObject("longBylineText")
        val avatar = o.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
            ?.let { lastThumb(it) }
            ?: o.optJSONObject("channelThumbnail")?.let { lastThumb(it) }
            ?: ""
        val overlays = o.optJSONArray("thumbnailOverlays")
        val lengthText = text(o.opt("lengthText")).ifBlank { overlayTime(overlays) }
        val durationMs = parseDurationMs(lengthText).takeIf { it > 0L }
            ?: (o.optString("lengthSeconds").toLongOrNull()?.times(1000L) ?: 0L)
        val info = text(o.opt("videoInfo")).split("•").map { it.trim() }.filter { it.isNotBlank() }
        val published = text(o.opt("publishedTimeText"))
            .ifBlank { info.firstOrNull { looksLikePublished(it) }.orEmpty() }
        val views = text(o.opt("shortViewCountText"))
            .ifBlank { text(o.opt("viewCountText")) }
            .ifBlank { info.firstOrNull { looksLikeViews(it) }.orEmpty() }
        val percent = resumePercent(overlays)
        return VideoItem(
            id = id,
            title = title,
            channelTitle = text(byline),
            thumbnailUrl = videoThumb(id, o.optJSONObject("thumbnail")),
            channelId = byline?.let { findFirstString(it, "browseId") { b -> b.startsWith("UC") } }.orEmpty(),
            channelThumbnailUrl = avatar,
            publishedAt = published,
            description = text(o.opt("descriptionSnippet")),
            isLive = isLive(o, overlays),
            resumePositionMs = if (percent in 1L..99L && durationMs > 0L) durationMs * percent / 100L else 0L,
            durationMs = durationMs,
            viewCountText = views
        )
    }

    private fun parseLockup(o: JSONObject): VideoItem? {
        val type = o.optString("contentType")
        if (type.isNotBlank() && type != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = o.optString("contentId")
        if (id.isBlank()) return null
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = meta?.optJSONObject("title")?.optString("content").orEmpty()
        if (title.isBlank()) return null
        val parts = ArrayList<String>()
        val rows = meta?.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")
        if (rows != null) {
            for (r in 0 until rows.length()) {
                val rowParts = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
                for (p in 0 until rowParts.length()) {
                    val content = rowParts.optJSONObject(p)?.optJSONObject("text")?.optString("content").orEmpty()
                    if (content.isNotBlank()) parts.add(content.trim())
                }
            }
        }
        val image = o.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")
        val badges = collectObjects(image, "thumbnailBadgeViewModel")
            .map { it.optString("text") }
            .filter { it.isNotBlank() }
        val durationMs = badges.map { parseDurationMs(it) }.firstOrNull { it > 0L } ?: 0L
        val live = badges.any { it.contains("vivo", true) || it.contains("live", true) }
        val percent = (findFirst(image, "startPercent") as? Number)?.toLong() ?: 0L
        val avatarSources = findFirst(meta?.optJSONObject("image"), "sources") as? JSONArray
        val avatar = fixUrl(avatarSources?.optJSONObject(0)?.optString("url").orEmpty())
        return VideoItem(
            id = id,
            title = title,
            channelTitle = parts.firstOrNull { !looksLikeViews(it) && !looksLikePublished(it) }.orEmpty(),
            thumbnailUrl = videoThumb(id, image?.optJSONObject("image")),
            channelId = findFirstString(meta, "browseId") { it.startsWith("UC") }.orEmpty(),
            channelThumbnailUrl = avatar,
            publishedAt = parts.firstOrNull { looksLikePublished(it) }.orEmpty(),
            isLive = live,
            resumePositionMs = if (percent in 1L..99L && durationMs > 0L) durationMs * percent / 100L else 0L,
            durationMs = durationMs,
            viewCountText = parts.firstOrNull { looksLikeViews(it) }.orEmpty()
        )
    }

    private fun parseShortsLockup(o: JSONObject): VideoItem? {
        val id = findFirstString(o, "videoId") { it.length == 11 }
            ?: o.optString("entityId").removePrefix("shorts-shelf-item-").takeIf { it.length == 11 }
            ?: return null
        val overlay = o.optJSONObject("overlayMetadata")
        val title = overlay?.optJSONObject("primaryText")?.optString("content").orEmpty()
            .ifBlank { o.optString("accessibilityText").substringBefore(",") }
        val thumb = o.optJSONObject("thumbnail")?.let { lastThumb(it) }.orEmpty()
        return shortItem(
            id = id,
            title = title,
            thumb = thumb,
            views = overlay?.optJSONObject("secondaryText")?.optString("content").orEmpty()
        )
    }

    private fun parseReelItem(o: JSONObject): VideoItem? {
        val id = o.optString("videoId")
        if (id.isBlank()) return null
        return shortItem(
            id = id,
            title = text(o.opt("headline")),
            thumb = o.optJSONObject("thumbnail")?.let { lastThumb(it) }.orEmpty(),
            views = text(o.opt("viewCountText"))
        )
    }

    private fun shortItem(id: String, title: String, thumb: String, views: String): VideoItem =
        VideoItem(
            id = id,
            title = title.ifBlank { "Short" },
            channelTitle = "",
            thumbnailUrl = thumb.ifBlank { "https://i.ytimg.com/vi/$id/hq2.jpg" },
            // Marca interna: Geo Videos reconoce los Shorts por "#shorts".
            description = "#shorts",
            viewCountText = views
        )

    private fun parseChannelRenderer(o: JSONObject): ChannelItem? {
        val id = o.optString("channelId")
        if (id.isBlank()) return null
        val texts = listOf(
            text(o.opt("subscriberCountText")),
            text(o.opt("videoCountText"))
        ).filter { it.isNotBlank() }
        val handle = texts.firstOrNull { it.startsWith("@") }.orEmpty()
        val subscribers = texts.firstOrNull { it.contains("suscript", true) || it.contains("subscriber", true) }.orEmpty()
        return ChannelItem(
            id = id,
            title = text(o.opt("title")),
            thumbnailUrl = o.optJSONObject("thumbnail")?.let { lastThumb(it) }.orEmpty(),
            description = text(o.opt("descriptionSnippet")),
            handle = handle,
            subscriberCount = parseCount(subscribers),
            isSubscribed = (findFirst(o, "subscribed") as? Boolean) ?: false
        )
    }

    private fun parsePlaylistRenderer(o: JSONObject): PlaylistItem? {
        val id = o.optString("playlistId")
        if (id.isBlank()) return null
        val thumb = (findFirst(o.opt("thumbnailRenderer") ?: o.opt("thumbnail") ?: o.opt("thumbnails"), "thumbnails") as? JSONArray)
            ?.let { list -> if (list.length() > 0) fixUrl(list.optJSONObject(list.length() - 1)?.optString("url").orEmpty()) else "" }
            .orEmpty()
        val count = parseCount(text(o.opt("videoCountText")).ifBlank { text(o.opt("videoCountShortText")) })
            .takeIf { it > 0L } ?: o.optString("videoCount").toLongOrNull() ?: 0L
        return PlaylistItem(
            id = id,
            title = text(o.opt("title")).ifBlank { "Lista" },
            thumbnailUrl = thumb,
            itemCount = count.toInt()
        )
    }

    private fun parseLockupPlaylist(o: JSONObject): PlaylistItem? {
        val id = o.optString("contentId")
        if (id.isBlank()) return null
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = meta?.optJSONObject("title")?.optString("content").orEmpty()
        val sources = findFirst(o.optJSONObject("contentImage"), "sources") as? JSONArray
        val thumb = if (sources != null && sources.length() > 0) {
            fixUrl(sources.optJSONObject(sources.length() - 1)?.optString("url").orEmpty())
        } else {
            ""
        }
        val badges = collectObjects(o.optJSONObject("contentImage"), "thumbnailBadgeViewModel")
            .map { it.optString("text") }
        val count = badges.map { parseCount(it) }.firstOrNull { it > 0L } ?: 0L
        return PlaylistItem(
            id = id,
            title = title.ifBlank { if (id.startsWith("RD")) "Mix" else "Lista" },
            thumbnailUrl = thumb,
            itemCount = count.toInt()
        )
    }

    // ------------------------------------------------------------ Ayudantes

    internal fun text(node: Any?): String = when (node) {
        is String -> node
        is JSONObject -> {
            val simple = node.optString("simpleText")
            if (simple.isNotBlank()) {
                simple
            } else if (node.opt("content") is String) {
                node.optString("content")
            } else {
                val runs = node.optJSONArray("runs")
                if (runs == null) {
                    ""
                } else {
                    buildString {
                        for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty())
                    }
                }
            }
        }
        else -> ""
    }

    internal fun lastThumb(obj: JSONObject): String {
        val list = obj.optJSONArray("thumbnails") ?: obj.optJSONArray("sources") ?: return ""
        if (list.length() == 0) return ""
        return fixUrl(list.optJSONObject(list.length() - 1)?.optString("url").orEmpty())
    }

    private fun videoThumb(id: String, thumbnail: JSONObject?): String =
        thumbnail?.let { lastThumb(it) }.orEmpty().ifBlank { "https://i.ytimg.com/vi/$id/hqdefault.jpg" }

    internal fun fixUrl(url: String): String = if (url.startsWith("//")) "https:$url" else url

    private fun overlayTime(overlays: JSONArray?): String {
        if (overlays == null) return ""
        for (i in 0 until overlays.length()) {
            val status = overlays.optJSONObject(i)?.optJSONObject("thumbnailOverlayTimeStatusRenderer") ?: continue
            val value = text(status.opt("text"))
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun resumePercent(overlays: JSONArray?): Long {
        if (overlays == null) return 0L
        for (i in 0 until overlays.length()) {
            val resume = overlays.optJSONObject(i)?.optJSONObject("thumbnailOverlayResumePlaybackRenderer") ?: continue
            return resume.optLong("percentDurationWatched", 0L)
        }
        return 0L
    }

    private fun isLive(o: JSONObject, overlays: JSONArray?): Boolean {
        val badges = o.optJSONArray("badges")
        if (badges != null) {
            for (i in 0 until badges.length()) {
                val style = badges.optJSONObject(i)?.optJSONObject("metadataBadgeRenderer")?.optString("style").orEmpty()
                if (style == "BADGE_STYLE_TYPE_LIVE_NOW") return true
            }
        }
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val style = overlays.optJSONObject(i)
                    ?.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                    ?.optString("style")
                    .orEmpty()
                if (style == "LIVE") return true
            }
        }
        return false
    }

    private fun parseDurationMs(value: String): Long {
        val clean = value.trim()
        if (clean.isBlank() || !clean.contains(':')) return 0L
        val parts = clean.split(':').map { it.trim().toLongOrNull() ?: return 0L }
        var seconds = 0L
        parts.forEach { seconds = seconds * 60L + it }
        return seconds * 1000L
    }

    /** Convierte "10 M de suscriptores", "1,2 K", "957" o "1.234.567 vistas" en número. */
    internal fun parseCount(value: String): Long {
        val lower = value.lowercase().replace('\u00a0', ' ')
        val match = Regex("""(\d+(?:[.,]\d+)*)\s*(mil\s*m|mm|mil|k|m|b)?\b""").find(lower) ?: return 0L
        val digits = match.groupValues[1]
        val suffix = match.groupValues[2].replace(" ", "")
        val multiplier = when (suffix) {
            "k", "mil" -> 1_000.0
            "m" -> 1_000_000.0
            "milm", "mm", "b" -> 1_000_000_000.0
            else -> 1.0
        }
        val number = if (multiplier == 1.0) {
            digits.replace(".", "").replace(",", "").toDoubleOrNull()
        } else {
            val normalized = digits.replace(',', '.')
            if (normalized.count { it == '.' } > 1) normalized.replace(".", "").toDoubleOrNull()
            else normalized.toDoubleOrNull()
        }
        return ((number ?: 0.0) * multiplier).toLong()
    }

    /** Junta todos los textos de una clave (por ejemplo "content") dentro de un nodo. */
    internal fun collectStrings(node: Any?, key: String, out: MutableList<String> = ArrayList()): List<String> {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val value = node.opt(name)
                    if (name == key && value is String) out.add(value) else collectStrings(value, key, out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collectStrings(node.opt(i), key, out)
        }
        return out
    }

    private fun looksLikeViews(value: String): Boolean {
        val lower = value.lowercase()
        return lower.contains("vista") || lower.contains("visualiz") ||
            lower.contains("view") || lower.contains("reproducc") || lower.contains("mirando")
    }

    private fun looksLikePublished(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("hace") || lower.contains(" ago") ||
            lower.startsWith("transmitido") || lower.startsWith("streamed")
    }

    internal fun findFirst(node: Any?, key: String): Any? {
        when (node) {
            is JSONObject -> {
                if (node.has(key)) return node.opt(key)
                val keys = node.keys()
                while (keys.hasNext()) {
                    val found = findFirst(node.opt(keys.next()), key)
                    if (found != null) return found
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                val found = findFirst(node.opt(i), key)
                if (found != null) return found
            }
        }
        return null
    }

    internal fun findFirstString(node: Any?, key: String, accept: (String) -> Boolean): String? {
        when (node) {
            is JSONObject -> {
                val direct = node.opt(key)
                if (direct is String && accept(direct)) return direct
                val keys = node.keys()
                while (keys.hasNext()) {
                    val found = findFirstString(node.opt(keys.next()), key, accept)
                    if (found != null) return found
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                val found = findFirstString(node.opt(i), key, accept)
                if (found != null) return found
            }
        }
        return null
    }

    internal fun collectObjects(node: Any?, key: String, out: MutableList<JSONObject> = ArrayList()): List<JSONObject> {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val value = node.opt(name)
                    if (name == key && value is JSONObject) out.add(value) else collectObjects(value, key, out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collectObjects(node.opt(i), key, out)
        }
        return out
    }

    companion object {
        private const val ORIGIN = "https://www.youtube.com"
        private const val MIN_PROGRESS_INTERVAL_MS = 20_000L

        private val WEB = ClientInfo(
            name = "WEB",
            version = "2.20251020.01.00",
            id = "1",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        )

        private val MWEB = ClientInfo(
            name = "MWEB",
            version = "2.20251020.01.00",
            id = "2",
            userAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
        )
    }
}
