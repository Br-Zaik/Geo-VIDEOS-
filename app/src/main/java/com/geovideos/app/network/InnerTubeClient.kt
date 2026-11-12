package com.geovideos.app.network

import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.CommentItem
import com.geovideos.app.data.NotificationItem
import com.geovideos.app.data.NotificationsPage
import com.geovideos.app.data.CommentsPage
import com.geovideos.app.data.HomeFeedEntry
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
    val chips: List<HomeChip> = emptyList(),
    /** Mixes reales de YouTube (listas RD…) listos para mostrar como tarjeta. */
    val mixes: List<VideoItem> = emptyList(),
    /** Todo en el orden exacto de YouTube: videos, bloques de Shorts y Mixes. */
    val feed: List<HomeFeedEntry> = emptyList()
)

/** Filtro de YouTube (Videojuegos, Música, En vivo…) con su token para cargarlo. */
data class MixPanel(
    val title: String,
    val owner: String,
    val videos: List<VideoItem>
)

data class HomeChip(val title: String, val token: String)

/** Resultados de búsqueda en el orden exacto de YouTube (videos, canales, listas, Shorts, estanterías). */
sealed interface SearchEntry {
    data class Video(val video: VideoItem) : SearchEntry
    data class Channel(val channel: ChannelItem) : SearchEntry
    data class Playlist(val playlist: PlaylistItem, val mix: VideoItem?) : SearchEntry
    data class Shorts(val shorts: List<VideoItem>) : SearchEntry
    data class Shelf(val title: String, val videos: List<VideoItem>) : SearchEntry
}

data class SearchResults(
    val entries: List<SearchEntry>,
    val continuation: String,
    val correctedQuery: String,
    val page: InnerTubePage
)

/** Pestaña de un canal tal como la manda YouTube (Inicio, Videos, Shorts, En vivo, Listas...). */
data class ChannelTabInfo(val title: String, val params: String, val selected: Boolean)

/** Estantería de la pestaña Inicio de un canal ("Videos", "Shorts", "Populares"...). */
data class ChannelShelf(
    val title: String,
    val videos: List<VideoItem>,
    val shorts: List<VideoItem>,
    val playlists: List<PlaylistItem>
)

/** Publicación de la comunidad del canal. */
data class ChannelPost(
    val id: String,
    val text: String,
    val published: String,
    val likes: String,
    val imageUrl: String
)

data class ChannelPageData(
    val channel: ChannelItem,
    val tabs: List<ChannelTabInfo>,
    val shelves: List<ChannelShelf>,
    val featured: VideoItem?
)

data class ChannelTabPage(
    val videos: List<VideoItem>,
    val shorts: List<VideoItem>,
    val playlists: List<PlaylistItem>,
    val posts: List<ChannelPost>,
    val chips: List<HomeChip>,
    val continuation: String
)

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

    /** Historial de YouTube con el día de cada video ("Hoy", "Ayer", "martes"…). */
    suspend fun history(continuation: String = ""): InnerTubePage = withContext(Dispatchers.IO) {
        val body = baseBody(WEB)
        if (continuation.isNotBlank()) body.put("continuation", continuation) else body.put("browseId", "FEhistory")
        val json = post("browse", body, WEB)
        val page = parsePage(json)
        val sectionById = HashMap<String, String>()
        collectObjects(json, "itemSectionRenderer").forEach { section ->
            val title = text(findFirst(section.opt("header"), "title"))
            if (title.isBlank()) return@forEach
            val inSection = parsePage(section)
            (inSection.videos + inSection.shorts).forEach { video ->
                if (!sectionById.containsKey(video.id)) sectionById[video.id] = title
            }
        }
        page.copy(
            videos = page.videos.map { it.copy(historySection = sectionById[it.id].orEmpty()) },
            shorts = page.shorts.map { it.copy(historySection = sectionById[it.id].orEmpty()) }
        )
    }

    /** Sugerencias de búsqueda (las mismas del cuadro de búsqueda de YouTube). */
    suspend fun searchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        val url = "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&hl=es&gl=pe&q=" + encode(clean)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 4_000
            setRequestProperty("User-Agent", WEB.userAgent)
        }
        try {
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            if (start < 0 || end <= start) return@withContext emptyList()
            val list = JSONArray(text.substring(start, end + 1)).optJSONArray(1) ?: return@withContext emptyList()
            (0 until list.length()).mapNotNull { list.optJSONArray(it)?.optString(0) }
                .filter { it.isNotBlank() }
                .take(12)
        } finally {
            connection.disconnect()
        }
    }

    /** Búsqueda con filtro (params) y resultados mezclados en el orden de YouTube. */
    suspend fun searchResults(query: String, params: String = "", continuation: String = ""): SearchResults =
        withContext(Dispatchers.IO) {
            val body = baseBody(WEB)
            if (continuation.isNotBlank()) {
                body.put("continuation", continuation)
            } else {
                body.put("query", query)
                if (params.isNotBlank()) body.put("params", params)
            }
            val json = post("search", body, WEB)
            val page = parsePage(json)
            val entries = ArrayList<SearchEntry>()
            val seen = HashSet<String>()
            var corrected = ""
            val skip = setOf(
                "adSlotRenderer", "promotedSparklesWebRenderer", "searchPyvRenderer",
                "horizontalCardListRenderer", "messageRenderer", "backgroundPromoRenderer",
                "continuationItemRenderer"
            )
            collectObjects(json, "itemSectionRenderer").forEach { section ->
                val contents = section.optJSONArray("contents") ?: return@forEach
                for (i in 0 until contents.length()) {
                    val item = contents.optJSONObject(i) ?: continue
                    val keys = item.keys()
                    if (!keys.hasNext()) continue
                    val key = keys.next()
                    if (key in skip) continue
                    val inner = item.optJSONObject(key)
                    if (key == "showingResultsForRenderer" || key == "didYouMeanRenderer") {
                        corrected = text(inner?.opt("correctedQuery"))
                        continue
                    }
                    val single = parsePage(item)
                    val title = text(inner?.opt("title")).ifBlank {
                        findFirstString(inner?.opt("title"), "content") { it.isNotBlank() }.orEmpty()
                    }
                    when {
                        single.channels.isNotEmpty() -> single.channels.forEach { channel ->
                            if (seen.add("c:" + channel.id)) entries.add(SearchEntry.Channel(channel))
                        }
                        single.playlists.isNotEmpty() && single.videos.size <= 1 && key != "shelfRenderer" -> {
                            val playlist = single.playlists.first()
                            if (seen.add("p:" + playlist.id)) entries.add(SearchEntry.Playlist(playlist, single.mixes.firstOrNull()))
                        }
                        single.shorts.isNotEmpty() && single.videos.isEmpty() -> {
                            val fresh = single.shorts.filter { seen.add(it.id) }
                            if (fresh.isNotEmpty()) {
                                val last = entries.lastOrNull()
                                if (last is SearchEntry.Shorts) entries[entries.size - 1] = SearchEntry.Shorts(last.shorts + fresh)
                                else entries.add(SearchEntry.Shorts(fresh))
                            }
                        }
                        single.videos.size == 1 && key != "shelfRenderer" -> {
                            val video = single.videos.first()
                            if (seen.add(video.id)) entries.add(SearchEntry.Video(video))
                        }
                        single.videos.isNotEmpty() -> {
                            val fresh = single.videos.filter { seen.add(it.id) }
                            if (fresh.isNotEmpty()) entries.add(SearchEntry.Shelf(title.ifBlank { "Más resultados" }, fresh))
                        }
                    }
                }
            }
            SearchResults(entries, page.continuation, corrected, page)
        }

    // ------------------------------------------------------------ Notificaciones

    /** Cuántas notificaciones sin ver hay (el número rojo de la campana). */
    suspend fun unseenNotifications(): Int = withContext(Dispatchers.IO) {
        val json = post("notification/get_unseen_count", baseBody(WEB), WEB)
        val direct = json.optInt("unseenCount", -1)
        if (direct >= 0) return@withContext direct
        collectObjects(json, "updateNotificationsUnseenCountAction").firstOrNull()?.optInt("unseenCount", 0) ?: 0
    }

    /** Bandeja de notificaciones con secciones ("Importante", "Más notificaciones") y continuación. */
    suspend fun notifications(continuation: String = ""): NotificationsPage = withContext(Dispatchers.IO) {
        val body = baseBody(WEB)
        if (continuation.isNotBlank()) {
            body.put("ctoken", continuation)
        } else {
            body.put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX")
        }
        val json = post("notification/get_notification_menu", body, WEB)
        val items = ArrayList<NotificationItem>()
        fun read(notification: JSONObject, section: String) {
            val title = text(notification.opt("shortMessage"))
            if (title.isBlank()) return
            val videoId = findFirstString(notification.opt("navigationEndpoint"), "videoId") { it.length == 11 }
            val videoThumb = notification.optJSONObject("videoThumbnail")?.let { lastThumb(it) }.orEmpty()
            val avatar = notification.optJSONObject("thumbnail")?.let { lastThumb(it) }.orEmpty()
            items.add(
                NotificationItem(
                    id = notification.optString("notificationId").ifBlank { videoId ?: title },
                    title = title,
                    subtitle = text(notification.opt("sentTimeText")),
                    thumbnailUrl = videoThumb.ifBlank { avatar },
                    video = videoId?.let {
                        VideoItem(
                            id = it,
                            title = title,
                            channelTitle = "",
                            thumbnailUrl = videoThumb.ifBlank { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
                        )
                    },
                    avatarUrl = avatar,
                    section = section,
                    read = notification.optBoolean("read", true)
                )
            )
        }
        val sections = collectObjects(json, "multiPageMenuNotificationSectionRenderer")
        if (sections.isEmpty()) {
            collectObjects(json, "notificationRenderer").forEach { read(it, "") }
        } else {
            sections.forEach { section ->
                val title = text(section.opt("sectionTitle")).ifBlank { text(section.opt("title")) }
                collectObjects(section, "notificationRenderer").forEach { read(it, title) }
            }
        }
        val next = findFirstString(json, "token") { it.isNotBlank() }
            ?: findFirstString(json, "continuation") { it.isNotBlank() }
            ?: ""
        NotificationsPage(items.distinctBy { it.id }, if (items.isEmpty()) "" else next)
    }

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
        val mixes = ArrayList<VideoItem>()
        val feed = ArrayList<HomeFeedEntry>()
        val seen = HashSet<String>()
        var continuation = ""

        fun addChannel(channel: ChannelItem?) {
            if (channel != null && channel.id.isNotBlank() && seen.add("c:" + channel.id)) channels.add(channel)
        }

        fun addPlaylist(playlist: PlaylistItem?) {
            if (playlist != null && playlist.id.isNotBlank() && seen.add("p:" + playlist.id)) playlists.add(playlist)
        }

        fun addVideo(video: VideoItem?) {
            if (video != null && video.id.isNotBlank() && seen.add(video.id)) {
                videos.add(video)
                feed.add(HomeFeedEntry.Video(video))
            }
        }

        fun addShort(video: VideoItem?) {
            if (video != null && video.id.isNotBlank() && seen.add(video.id)) {
                shorts.add(video)
                // Shorts seguidos forman un mismo bloque, como la estantería de YouTube.
                val last = feed.lastOrNull()
                if (last is HomeFeedEntry.Shorts) {
                    feed[feed.size - 1] = HomeFeedEntry.Shorts(last.shorts + video)
                } else {
                    feed.add(HomeFeedEntry.Shorts(listOf(video)))
                }
            }
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
                            "playlistVideoRenderer",
                            "playlistPanelVideoRenderer" -> addVideo(obj?.let { parseVideoRenderer(it) })
                            "reelItemRenderer" -> addShort(obj?.let { parseReelItem(it) })
                            "shortsLockupViewModel" -> addShort(obj?.let { parseShortsLockup(it) })
                            "lockupViewModel" -> if (obj != null) {
                                val type = obj.optString("contentType")
                                if (type.isBlank() || type == "LOCKUP_CONTENT_TYPE_VIDEO") {
                                    addVideo(parseLockup(obj))
                                } else {
                                    val playlist = parseLockupPlaylist(obj)
                                    addPlaylist(playlist)
                                    if (playlist != null && playlist.id.startsWith("RD")) {
                                        parseMix(obj, playlist)?.let { mix ->
                                            if (seen.add("m:" + mix.mixPlaylistId)) {
                                                mixes.add(mix)
                                                feed.add(HomeFeedEntry.Mix(mix))
                                            }
                                        }
                                    }
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
        return InnerTubePage(videos, shorts, continuation, channels, playlists, chips, mixes, feed)
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
            viewCountText = views,
            isNew = hasNewBadge(o),
            notInterestedToken = feedbackTokens(o).getOrNull(0).orEmpty(),
            dontRecommendToken = feedbackTokens(o).getOrNull(1).orEmpty()
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
            viewCountText = parts.firstOrNull { looksLikeViews(it) }.orEmpty(),
            isNew = hasNewBadge(o),
            notInterestedToken = feedbackTokens(o).getOrNull(0).orEmpty(),
            dontRecommendToken = feedbackTokens(o).getOrNull(1).orEmpty()
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
        ).copy(isNew = hasNewBadge(o))
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
            isSubscribed = (findFirst(o, "subscribed") as? Boolean) ?: false,
            bellState = parseBell(o).first,
            bellParams = parseBell(o).second
        )
    }

    // ------------------------------------------------------------ Campana de notificaciones

    private fun bellIndex(icon: String): Int {
        val upper = icon.uppercase()
        return when {
            upper.contains("ACTIVE") || upper.endsWith("_ALL") -> 0
            upper.contains("NONE") || upper.contains("PERSONALIZED") -> 1
            upper.contains("OFF") -> 2
            else -> -1
        }
    }

    /**
     * Estado de la campana y parámetros para cambiarla. Sirve para el botón antiguo
     * (subscriptionNotificationToggleButtonRenderer + menuServiceItemRenderer) y el nuevo
     * (subscribeButtonViewModel + listItemViewModel + subscriptionNotificationStateEntity).
     */
    internal fun parseBell(root: Any?): Pair<Int, List<String>> {
        val params = arrayOfNulls<String>(3)
        collectObjects(root, "menuServiceItemRenderer").forEach { item ->
            val endpoint = findFirst(item, "modifyChannelNotificationPreferenceEndpoint") as? JSONObject ?: return@forEach
            val index = bellIndex(item.optJSONObject("icon")?.optString("iconType").orEmpty())
            if (index >= 0 && params[index] == null) params[index] = endpoint.optString("params").takeIf { it.isNotBlank() }
        }
        collectObjects(root, "listItemViewModel").forEach { item ->
            val endpoint = findFirst(item, "modifyChannelNotificationPreferenceEndpoint") as? JSONObject ?: return@forEach
            val icon = findFirstString(item, "imageName") { it.isNotBlank() }
                ?: findFirstString(item, "iconType") { it.isNotBlank() }
                ?: ""
            val index = bellIndex(icon)
            if (index >= 0 && params[index] == null) params[index] = endpoint.optString("params").takeIf { it.isNotBlank() }
        }
        var current = -1
        collectObjects(root, "subscriptionNotificationToggleButtonRenderer").firstOrNull()?.let { toggle ->
            val currentId = toggle.optInt("currentStateId", -1)
            val states = toggle.optJSONArray("states")
            if (states != null) {
                for (i in 0 until states.length()) {
                    val state = states.optJSONObject(i) ?: continue
                    if (state.optInt("stateId", -2) == currentId) {
                        current = bellIndex(findFirstString(state, "iconType") { it.isNotBlank() }.orEmpty())
                    }
                }
            }
        }
        if (current < 0) {
            collectObjects(root, "subscriptionNotificationStateEntity").firstOrNull()?.let { entity ->
                current = when {
                    entity.optString("state").contains("ALL") -> 0
                    entity.optString("state").contains("PERSONALIZED") -> 1
                    entity.optString("state").contains("OFF") -> 2
                    else -> -1
                }
            }
        }
        val list = if (params.all { it != null }) params.map { it.orEmpty() } else params.map { it.orEmpty() }.takeIf { p -> p.any { it.isNotBlank() } } ?: emptyList()
        return current to list
    }

    /** Cambia la campana (Todas / Personalizadas / Ninguna). */
    suspend fun setBell(params: String) {
        if (params.isBlank()) return
        call("notification/modify_channel_preference") { put("params", params) }
    }

    /** Lee la campana de un canal desde su página. */
    suspend fun channelBell(channelId: String): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
        val json = post("browse", baseBody(WEB).put("browseId", channelId), WEB)
        parseBell(json)
    }

    // ------------------------------------------------------------ Suscripciones (barra con punto azul)

    /** Canales de la guía: en orden de YouTube, con punto de contenido nuevo y en vivo. */
    suspend fun subscriptionGuide(): List<ChannelItem> = withContext(Dispatchers.IO) {
        val json = post("guide", baseBody(WEB), WEB)
        val section = collectObjects(json, "guideSubscriptionsSectionRenderer").firstOrNull() ?: return@withContext emptyList()
        collectObjects(section, "guideEntryRenderer").mapNotNull { entry ->
            val id = findFirstString(entry.opt("navigationEndpoint"), "browseId") { it.startsWith("UC") }
                ?: return@mapNotNull null
            ChannelItem(
                id = id,
                title = text(entry.opt("formattedTitle")).ifBlank { text(entry.opt("title")) },
                thumbnailUrl = entry.optJSONObject("thumbnail")?.let { lastThumb(it) }.orEmpty(),
                isSubscribed = true,
                hasNewContent = entry.optString("presentationStyle").contains("NEW_CONTENT"),
                isLiveNow = entry.optJSONObject("badges")?.optBoolean("liveBroadcasting") == true
            )
        }.distinctBy { it.id }
    }

    // ------------------------------------------------------------ Página del canal

    /** Encabezado, pestañas y estanterías de Inicio de un canal. */
    suspend fun channelPage(channelId: String): ChannelPageData = withContext(Dispatchers.IO) {
        val json = post("browse", baseBody(WEB).put("browseId", channelId), WEB)
        val meta = findFirst(json, "channelMetadataRenderer") as? JSONObject
        val header = findFirst(json, "pageHeaderViewModel") as? JSONObject
        fun lastSource(node: Any?): String {
            val sources = findFirst(node, "sources") as? JSONArray ?: return ""
            if (sources.length() == 0) return ""
            return fixUrl(sources.optJSONObject(sources.length() - 1)?.optString("url").orEmpty())
        }
        val rows = collectStrings(header?.opt("metadata"), "content")
        val headerTitle = findFirstString(header?.opt("title"), "content") { it.isNotBlank() }.orEmpty()
        val headerDescription = findFirstString(header?.opt("description"), "content") { it.isNotBlank() }.orEmpty()
        val links = findFirstString(header?.opt("attribution"), "content") { it.isNotBlank() }.orEmpty()
        val verified = header?.toString()?.contains("CHECK_CIRCLE") == true ||
            header?.toString()?.contains("BADGE_STYLE_TYPE_VERIFIED") == true
        val bell = parseBell(json)
        val subscribed = collectObjects(json, "subscriptionStateEntity").firstOrNull()?.optBoolean("subscribed")
            ?: (findFirst(header, "subscribed") as? Boolean)
            ?: false
        val channel = ChannelItem(
            id = channelId,
            title = meta?.optString("title").orEmpty().ifBlank { headerTitle },
            thumbnailUrl = meta?.optJSONObject("avatar")?.let { lastThumb(it) }.orEmpty().ifBlank { lastSource(header?.opt("image")) },
            description = meta?.optString("description").orEmpty().ifBlank { headerDescription },
            bannerUrl = lastSource(header?.opt("banner")),
            handle = rows.firstOrNull { it.startsWith("@") }.orEmpty(),
            subscriberCount = parseCount(rows.firstOrNull { it.contains("suscript", true) || it.contains("subscriber", true) }.orEmpty()),
            videoCount = parseCount(rows.firstOrNull { it.contains("video", true) }.orEmpty()),
            isSubscribed = subscribed,
            bellState = bell.first,
            bellParams = bell.second,
            isVerified = verified,
            linksText = links
        )
        val tabs = ArrayList<ChannelTabInfo>()
        collectObjects(json, "tabRenderer").forEach { tab ->
            val title = tab.optString("title").ifBlank { text(tab.opt("title")) }
            if (title.isBlank()) return@forEach
            val params = findFirstString(tab.opt("endpoint"), "params") { it.isNotBlank() }.orEmpty()
            tabs.add(ChannelTabInfo(title, params, tab.optBoolean("selected")))
        }
        val featured = collectObjects(json, "channelVideoPlayerRenderer").firstOrNull()?.let { player ->
            val id = player.optString("videoId")
            if (id.isBlank()) null else VideoItem(
                id = id,
                title = text(player.opt("title")),
                channelTitle = channel.title,
                channelId = channelId,
                channelThumbnailUrl = channel.thumbnailUrl,
                thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                description = text(player.opt("description")),
                viewCountText = text(player.opt("viewCountText")),
                publishedAt = text(player.opt("publishedTimeText"))
            )
        }
        ChannelPageData(channel, tabs.distinctBy { it.title }, collectShelves(json, channel), featured)
    }

    /** Estanterías en el orden en que las manda YouTube. */
    private fun collectShelves(root: JSONObject, channel: ChannelItem): List<ChannelShelf> {
        val shelfKeys = setOf("shelfRenderer", "reelShelfRenderer", "richShelfRenderer")
        val found = ArrayList<JSONObject>()
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val name = keys.next()
                        val value = node.opt(name)
                        if (name in shelfKeys && value is JSONObject) found.add(value) else walk(value)
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(root)
        return found.mapNotNull { shelf ->
            val title = text(shelf.opt("title")).ifBlank { findFirstString(shelf.opt("title"), "content") { it.isNotBlank() }.orEmpty() }
            val page = parsePage(shelf)
            val fill = { v: VideoItem ->
                v.copy(
                    channelId = v.channelId.ifBlank { channel.id },
                    channelTitle = v.channelTitle.ifBlank { channel.title },
                    channelThumbnailUrl = v.channelThumbnailUrl.ifBlank { channel.thumbnailUrl }
                )
            }
            if (page.videos.isEmpty() && page.shorts.isEmpty() && page.playlists.isEmpty()) null
            else ChannelShelf(title.ifBlank { "Videos" }, page.videos.map(fill), page.shorts.map(fill), page.playlists)
        }
    }

    /** Una pestaña del canal (o un orden de esa pestaña con su token). */
    suspend fun channelTab(channelId: String, params: String, continuation: String = ""): ChannelTabPage =
        withContext(Dispatchers.IO) {
            val body = baseBody(WEB)
            if (continuation.isNotBlank()) {
                body.put("continuation", continuation)
            } else {
                body.put("browseId", channelId)
                if (params.isNotBlank()) body.put("params", params)
            }
            val json = post("browse", body, WEB)
            val page = parsePage(json)
            val posts = collectObjects(json, "backstagePostRenderer").mapNotNull { post ->
                val textValue = text(post.opt("contentText"))
                val image = collectObjects(post.opt("backstageAttachment"), "backstageImageRenderer").firstOrNull()
                    ?.optJSONObject("image")?.let { lastThumb(it) }.orEmpty()
                if (textValue.isBlank() && image.isBlank()) null else ChannelPost(
                    id = post.optString("postId").ifBlank { textValue.hashCode().toString() },
                    text = textValue,
                    published = text(post.opt("publishedTimeText")),
                    likes = text(post.opt("voteCount")),
                    imageUrl = image
                )
            }
            val fill = { v: VideoItem -> if (v.channelId.isBlank()) v.copy(channelId = channelId) else v }
            ChannelTabPage(
                videos = page.videos.map(fill),
                shorts = page.shorts.map(fill),
                playlists = page.playlists,
                posts = posts,
                chips = page.chips,
                continuation = page.continuation
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

    /** Tarjeta de Mix: se reproduce el primer video con la lista del Mix. */
    private fun parseMix(o: JSONObject, playlist: PlaylistItem): VideoItem? {
        val seedId = findFirstString(o, "videoId") { it.length == 11 }
            ?: playlist.id.removePrefix("RD").takeIf { it.length == 11 }
            ?: return null
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val parts = collectStrings(meta?.opt("metadata"), "content").filter { it.isNotBlank() }
        return VideoItem(
            id = seedId,
            title = playlist.title,
            channelTitle = parts.firstOrNull().orEmpty().ifBlank { "Mix de YouTube" },
            thumbnailUrl = playlist.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/$seedId/hqdefault.jpg" },
            isMix = true,
            mixPlaylistId = playlist.id
        )
    }

    /** Tokens de "No me interesa" y "No recomendar el canal" del menú ⋮ (en ese orden). */
    private fun feedbackTokens(o: JSONObject): List<String> =
        collectObjects(o, "feedbackEndpoint")
            .map { it.optString("feedbackToken") }
            .filter { it.isNotBlank() }

    /** Envía "No me interesa" / "No recomendar el canal" a YouTube. */
    suspend fun sendFeedback(token: String) {
        if (token.isBlank()) return
        call("feedback") {
            put("feedbackTokens", JSONArray().put(token))
            put("isFeedbackTokenUnencrypted", false)
            put("shouldMerge", false)
        }
    }

    /** Token inicial de la sección de comentarios de un video (sale de /next). */
    suspend fun commentsStartToken(videoId: String): String {
        val json = next(videoId)
        val section = collectObjects(json, "itemSectionRenderer")
            .firstOrNull { it.optString("sectionIdentifier") == "comment-item-section" }
            ?: return ""
        return findFirstString(section, "token") { it.isNotBlank() }.orEmpty()
    }

    /**
     * Una página de comentarios o de respuestas. YouTube manda el texto en
     * frameworkUpdates (commentEntityPayload) y el orden en commentThreadRenderer.
     */
    suspend fun comments(token: String): CommentsPage = withContext(Dispatchers.IO) {
        val json = post("next", baseBody(WEB).put("continuation", token), WEB)
        val payloads = HashMap<String, JSONObject>()
        collectObjects(json, "commentEntityPayload").forEach { payload ->
            val key = payload.optString("key")
            if (key.isNotBlank()) payloads[key] = payload
        }
        val result = ArrayList<CommentItem>()
        var nextToken = ""
        val sortTokens = ArrayList<String>()
        var total = ""

        fun toComment(payload: JSONObject?, repliesToken: String, pinned: Boolean): CommentItem? {
            if (payload == null) return null
            val properties = payload.optJSONObject("properties")
            val author = payload.optJSONObject("author")
            val toolbar = payload.optJSONObject("toolbar")
            val text = properties?.optJSONObject("content")?.optString("content").orEmpty()
            if (text.isBlank()) return null
            return CommentItem(
                id = properties?.optString("commentId").orEmpty().ifBlank { "c${result.size}" },
                author = author?.optString("displayName").orEmpty(),
                authorThumbnailUrl = fixUrl(author?.optString("avatarThumbnailUrl").orEmpty()),
                text = text,
                likeCount = parseCount(toolbar?.optString("likeCountNotliked").orEmpty()),
                publishedAt = properties?.optString("publishedTime").orEmpty(),
                replyCount = parseCount(toolbar?.optString("replyCount").orEmpty()).toInt(),
                repliesToken = repliesToken,
                isPinned = pinned,
                creatorHeart = toolbar?.optString("creatorThumbnailUrl").orEmpty().isNotBlank()
            )
        }

        val endpoints = json.optJSONArray("onResponseReceivedEndpoints")
        if (endpoints != null) {
            for (e in 0 until endpoints.length()) {
                val endpoint = endpoints.optJSONObject(e) ?: continue
                val action = endpoint.optJSONObject("reloadContinuationItemsCommand")
                    ?: endpoint.optJSONObject("appendContinuationItemsAction")
                    ?: continue
                val items = action.optJSONArray("continuationItems") ?: continue
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    item.optJSONObject("commentsHeaderRenderer")?.let { header ->
                        total = text(header.opt("countText"))
                        val menu = findFirst(header, "subMenuItems") as? JSONArray
                        if (menu != null) {
                            for (m in 0 until menu.length()) {
                                findFirstString(menu.opt(m), "token") { it.isNotBlank() }?.let { sortTokens.add(it) }
                            }
                        }
                    }
                    item.optJSONObject("commentThreadRenderer")?.let { thread ->
                        val key = findFirstString(thread.opt("commentViewModel"), "commentKey") { it.isNotBlank() }
                        val replies = findFirstString(thread.opt("replies"), "token") { it.isNotBlank() }.orEmpty()
                        val pinned = findFirst(thread, "pinnedText") != null
                        toComment(key?.let { payloads[it] }, replies, pinned)?.let { result.add(it) }
                    }
                    item.optJSONObject("commentViewModel")?.let { reply ->
                        val key = findFirstString(reply, "commentKey") { it.isNotBlank() }
                        toComment(key?.let { payloads[it] }, "", false)?.let { result.add(it) }
                    }
                    item.optJSONObject("continuationItemRenderer")?.let { more ->
                        findFirstString(more, "token") { it.isNotBlank() }?.let { nextToken = it }
                    }
                }
            }
        }
        // Si YouTube cambió el orden de las piezas, al menos mostrar los textos.
        if (result.isEmpty()) {
            payloads.values.forEach { payload -> toComment(payload, "", false)?.let { result.add(it) } }
        }
        CommentsPage(result.distinctBy { it.id }, nextToken, sortTokens, total)
    }

    private fun hasNewBadge(o: JSONObject): Boolean {
        val texts = collectStrings(o, "label") + collectStrings(o, "text") + collectStrings(o, "content")
        return texts.any { value ->
            val clean = value.trim()
            clean.equals("Nuevo", ignoreCase = true) || clean.equals("New", ignoreCase = true)
        }
    }

    /**
     * Feed real de Shorts como la app de YouTube: reel/reel_watch_sequence con sequenceParams.
     * La primera página se arma con el Short semilla (protobuf ReelSequence, como YouTube.js).
     */
    suspend fun shortsSequence(seedVideoId: String, continuation: String = ""): InnerTubePage =
        withContext(Dispatchers.IO) {
            val params = continuation.ifBlank { reelSequenceParams(seedVideoId) }
            val json = post("reel/reel_watch_sequence", baseBody(WEB).put("sequenceParams", params), WEB)
            val shorts = ArrayList<VideoItem>()
            val seenIds = HashSet<String>()
            val entries = json.optJSONArray("entries")
            if (entries != null) {
                for (i in 0 until entries.length()) {
                    val endpoint = findFirst(entries.opt(i), "reelWatchEndpoint") as? JSONObject ?: continue
                    val id = endpoint.optString("videoId")
                    if (id.length != 11 || !seenIds.add(id)) continue
                    val thumb = endpoint.optJSONObject("thumbnail")?.let { lastThumb(it) }.orEmpty()
                    shorts.add(shortItem(id = id, title = "", thumb = thumb, views = ""))
                }
            }
            val next = findFirstString(json.opt("continuationEndpoint"), "token") { it.isNotBlank() }.orEmpty()
            InnerTubePage(videos = emptyList(), shorts = shorts, continuation = next)
        }

    /** Datos de un Short (título, canal, me gusta, comentarios) con reel/reel_item_watch. */
    suspend fun shortDetails(videoId: String): VideoItem? = withContext(Dispatchers.IO) {
        val body = baseBody(WEB)
            .put("playerRequest", JSONObject().put("videoId", videoId))
            .put("params", "CAUwAg==")
            .put("disablePlayerResponse", false)
        val json = runCatching { post("reel/reel_item_watch", body, WEB) }.getOrNull() ?: return@withContext null
        val details = findFirst(json, "videoDetails") as? JSONObject
        val header = findFirst(json, "reelPlayerHeaderRenderer") as? JSONObject
        val title = details?.optString("title").orEmpty()
            .ifBlank { text(header?.opt("reelTitleText")) }
        val channel = details?.optString("author").orEmpty()
            .ifBlank { text(header?.opt("channelTitleText")) }
        val channelId = details?.optString("channelId").orEmpty()
            .ifBlank { findFirstString(header, "browseId") { it.startsWith("UC") }.orEmpty() }
        val avatarNode = header?.optJSONObject("channelThumbnail")
            ?: (findFirst(json, "channelThumbnail") as? JSONObject)
        val avatar = avatarNode?.let { lastThumb(it) }.orEmpty().ifBlank {
            val sources = findFirst(findFirst(json, "reelChannelBarViewModel"), "sources") as? JSONArray
            if (sources != null && sources.length() > 0) fixUrl(sources.optJSONObject(0)?.optString("url").orEmpty()) else ""
        }
        val likeRenderer = findFirst(json, "likeButtonRenderer") as? JSONObject
        val likeText = text(likeRenderer?.opt("likeCountWithoutLikeText"))
            .ifBlank { text(likeRenderer?.opt("likeCountText")) }
            .ifBlank {
                val count = likeRenderer?.optLong("likeCount", 0L) ?: 0L
                if (count > 0L) compactCount(count) else ""
            }
            .ifBlank {
                findFirstString(findFirst(json, "likeButtonViewModel"), "title") { it.any(Char::isDigit) }.orEmpty()
            }
        val commentsText = text(findFirst(findFirst(json, "viewCommentsButton"), "text"))
            .takeIf { it.any(Char::isDigit) }
            .orEmpty()
        val views = details?.optString("viewCount")?.toLongOrNull()?.let { compactCount(it) + " vistas" }.orEmpty()
        if (title.isBlank() && channel.isBlank()) return@withContext null
        VideoItem(
            id = videoId,
            title = title.ifBlank { "Short" },
            channelTitle = channel,
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hq2.jpg",
            channelId = channelId,
            channelThumbnailUrl = avatar,
            description = "#shorts",
            durationMs = (details?.optString("lengthSeconds")?.toLongOrNull() ?: 0L) * 1000L,
            viewCountText = views,
            likeCountText = likeText,
            commentCountText = commentsText
        )
    }

    private fun compactCount(value: Long): String = when {
        value >= 1_000_000_000L -> String.format(Locale.US, "%.1f mil M", value / 1_000_000_000.0).replace(".0 ", " ")
        value >= 1_000_000L -> String.format(Locale.US, "%.1f M", value / 1_000_000.0).replace(".0 ", " ")
        value >= 1_000L -> String.format(Locale.US, "%.1f K", value / 1_000.0).replace(".0 ", " ")
        else -> value.toString()
    }

    /** protobuf ReelSequence { short_id=1, params{number=3}=5, feature_2=10 } en base64 URL. */
    private fun reelSequenceParams(videoId: String): String {
        val out = java.io.ByteArrayOutputStream()
        val idBytes = videoId.toByteArray(Charsets.UTF_8)
        out.write(0x0A)
        out.write(idBytes.size)
        out.write(idBytes)
        out.write(0x2A) // campo 5 (mensaje)
        out.write(2)
        out.write(0x18) // campo 3 = 5
        out.write(5)
        out.write(0x50) // campo 10 = 25
        out.write(25)
        val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        return URLEncoder.encode(base64, Charsets.UTF_8.name())
    }

    /** Videos de un Mix (lista RD…) para reproducirlos en orden, como YouTube. */
    /** Panel del Mix / lista: título, dueño y videos alrededor del actual. */
    suspend fun mixPanel(videoId: String, playlistId: String): MixPanel = withContext(Dispatchers.IO) {
        val body = baseBody(WEB).put("videoId", videoId).put("playlistId", playlistId)
        val json = post("next", body, WEB)
        val panel = findFirst(json, "playlist") as? JSONObject
        val title = panel?.let { text(it.opt("title")) }.orEmpty()
        val owner = panel?.let { text(it.opt("ownerName")) }.orEmpty()
        val videos = parsePage(panel ?: json).videos.map { it.copy(isMix = true, mixPlaylistId = playlistId) }
        MixPanel(title, owner, videos)
    }

    suspend fun mixVideos(videoId: String, playlistId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val body = baseBody(WEB).put("videoId", videoId).put("playlistId", playlistId)
        val json = post("next", body, WEB)
        val panel = findFirst(json, "playlist") as? JSONObject
        parsePage(panel ?: json).videos.map { it.copy(isMix = true, mixPlaylistId = playlistId) }
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
