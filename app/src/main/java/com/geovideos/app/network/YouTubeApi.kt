package com.geovideos.app.network

import com.geovideos.app.data.ChannelDetails
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.CommentItem
import com.geovideos.app.data.GoogleProfile
import com.geovideos.app.data.NotificationItem
import com.geovideos.app.data.PlaylistItem
import com.geovideos.app.data.VideoDetails
import com.geovideos.app.data.VideoItem
import org.json.JSONArray
import org.json.JSONObject

data class VideoPage(
    val items: List<VideoItem>,
    val nextPageToken: String = ""
)

data class YouTubeSearchPage(
    val videos: List<VideoItem> = emptyList(),
    val channels: List<ChannelItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val nextPageToken: String = ""
)

/**
 * Acceso a YouTube con la sesión de la app (un solo inicio de sesión, como Daylitube).
 * Mantiene los mismos nombres de funciones que usaba la API oficial para que el resto
 * de la app no cambie. El parámetro "token" ya no se usa: la sesión vive en [InnerTubeClient].
 */
class YouTubeApi(private val inner: InnerTubeClient) {

    // ------------------------------------------------------------- Cuenta

    suspend fun getUserInfo(token: String): GoogleProfile = session {
        val json = inner.call("account/account_menu")
        val header = inner.findFirst(json, "activeAccountHeaderRenderer") as? JSONObject
        val googleHeader = inner.findFirst(json, "googleAccountHeaderRenderer") as? JSONObject
        val name = inner.text(header?.opt("accountName")).trim()
        val handle = inner.text(header?.opt("channelHandle")).trim()
        val email = inner.text(googleHeader?.opt("email")).trim()
        val photo = header?.optJSONObject("accountPhoto")?.let { inner.lastThumb(it) }.orEmpty()
        val channelId = inner.findFirstString(json, "browseId") { it.startsWith("UC") }.orEmpty()
        if (header == null && name.isBlank()) {
            throw YouTubeApiException(401, "No se pudo leer tu cuenta de YouTube. Vuelve a iniciar sesión.")
        }
        val displayName = name.ifBlank { handle.removePrefix("@") }.ifBlank { "YouTube" }
        GoogleProfile(
            name = displayName,
            email = email.ifBlank { handle }.ifBlank { displayName },
            pictureUrl = photo,
            channelTitle = displayName,
            channelId = channelId
        )
    }

    suspend fun getMyChannel(token: String, baseProfile: GoogleProfile): ChannelDetails {
        val channelId = baseProfile.channelId
        return ChannelDetails(
            profile = baseProfile.copy(channelTitle = baseProfile.channelTitle.ifBlank { baseProfile.name }),
            likesPlaylistId = "LL",
            uploadsPlaylistId = if (channelId.startsWith("UC")) "UU" + channelId.drop(2) else ""
        )
    }

    // ------------------------------------------------------------ Videos

    suspend fun videoDetails(token: String, video: VideoItem): VideoDetails = session {
        val json = inner.next(video.id)
        val primary = inner.findFirst(json, "videoPrimaryInfoRenderer") as? JSONObject
        val secondary = inner.findFirst(json, "videoSecondaryInfoRenderer") as? JSONObject
        val owner = secondary?.optJSONObject("owner")?.optJSONObject("videoOwnerRenderer")

        val viewCount = (inner.findFirst(primary, "originalViewCount") as? String)?.toLongOrNull()
            ?: inner.parseCount(
                inner.text((inner.findFirst(primary, "videoViewCountRenderer") as? JSONObject)?.opt("viewCount"))
            )
        val likeButton = inner.findFirst(primary, "likeButtonViewModel")
        val likeTitle = inner.findFirstString(likeButton, "title") { it.any(Char::isDigit) }.orEmpty()
        val commentHeader = inner.findFirst(json, "commentsEntryPointHeaderRenderer") as? JSONObject
        val commentCount = inner.parseCount(inner.text(commentHeader?.opt("commentCount")))
        val description = secondary?.optJSONObject("attributedDescription")?.optString("content").orEmpty()
            .ifBlank { inner.text(secondary?.opt("description")) }
            .ifBlank { video.description }
        val published = inner.text(primary?.opt("relativeDateText"))
            .ifBlank { inner.text(primary?.opt("dateText")) }
            .ifBlank { video.publishedAt }

        val comments = runCatching { loadComments(json) }.getOrDefault(emptyList())

        VideoDetails(
            videoId = video.id,
            viewCount = viewCount,
            likeCount = inner.parseCount(likeTitle),
            commentCount = if (commentCount > 0L) commentCount else comments.size.toLong(),
            subscriberCount = inner.parseCount(inner.text(owner?.opt("subscriberCountText"))),
            channelThumbnailUrl = owner?.optJSONObject("thumbnail")?.let { inner.lastThumb(it) }
                .orEmpty()
                .ifBlank { video.channelThumbnailUrl },
            publishedAt = published,
            description = description,
            comments = comments
        )
    }

    private suspend fun loadComments(watchJson: JSONObject): List<CommentItem> {
        val sections = inner.collectObjects(watchJson, "itemSectionRenderer")
        val commentSection = sections.firstOrNull {
            it.optString("sectionIdentifier") == "comment-item-section"
        } ?: return emptyList()
        val token = inner.findFirstString(commentSection, "token") { it.isNotBlank() } ?: return emptyList()
        val json = inner.next("", continuation = token)
        val result = ArrayList<CommentItem>()

        // Formato nuevo: los comentarios llegan como "commentEntityPayload".
        inner.collectObjects(json, "commentEntityPayload").forEach { payload ->
            val properties = payload.optJSONObject("properties")
            val author = payload.optJSONObject("author")
            val text = properties?.optJSONObject("content")?.optString("content").orEmpty()
            if (text.isNotBlank()) {
                result.add(
                    CommentItem(
                        id = properties?.optString("commentId").orEmpty().ifBlank { "c${result.size}" },
                        author = author?.optString("displayName").orEmpty(),
                        authorThumbnailUrl = inner.fixUrl(author?.optString("avatarThumbnailUrl").orEmpty()),
                        text = text,
                        likeCount = inner.parseCount(
                            payload.optJSONObject("toolbar")?.optString("likeCountNotliked").orEmpty()
                        ),
                        publishedAt = properties?.optString("publishedTime").orEmpty()
                    )
                )
            }
        }
        // Formato anterior.
        if (result.isEmpty()) {
            inner.collectObjects(json, "commentRenderer").forEach { comment ->
                val text = inner.text(comment.opt("contentText"))
                if (text.isNotBlank()) {
                    result.add(
                        CommentItem(
                            id = comment.optString("commentId").ifBlank { "c${result.size}" },
                            author = inner.text(comment.opt("authorText")),
                            authorThumbnailUrl = comment.optJSONObject("authorThumbnail")
                                ?.let { inner.lastThumb(it) }.orEmpty(),
                            text = text,
                            likeCount = inner.parseCount(inner.text(comment.opt("voteCount"))),
                            publishedAt = inner.text(comment.opt("publishedTimeText"))
                        )
                    )
                }
            }
        }
        return result.take(20)
    }

    suspend fun relatedVideosPage(
        token: String,
        video: VideoItem,
        pageToken: String = "",
        maxResults: Int = 20
    ): VideoPage = session {
        val firstPage = pageToken.isBlank() || pageToken.startsWith("__")
        val page = if (firstPage) {
            val json = inner.next(video.id)
            val results = inner.findFirst(json, "secondaryResults") as? JSONObject
            inner.parse(results ?: json)
        } else {
            inner.parse(inner.next(video.id, continuation = pageToken))
        }
        VideoPage(page.videos.filterNot { it.id == video.id }, page.continuation)
    }

    suspend fun rateVideo(token: String, videoId: String, rating: String) = session {
        inner.rate(videoId, rating)
    }

    // -------------------------------------------------------- Suscripciones

    suspend fun subscribe(token: String, channelId: String): String = session {
        inner.setSubscribed(channelId, true)
        channelId
    }

    /** Con la sesión, el "id de suscripción" es el mismo id del canal. */
    suspend fun findSubscriptionId(token: String, channelId: String): String? = channelId

    suspend fun unsubscribe(token: String, subscriptionId: String) = session {
        inner.setSubscribed(subscriptionId, false)
    }

    suspend fun subscriptions(token: String, maxPages: Int = 4): List<ChannelItem> = session {
        val result = ArrayList<ChannelItem>()
        var page = inner.browsePage("FEchannels")
        result.addAll(page.channels)
        var pages = 1
        while (page.continuation.isNotBlank() && pages < maxPages * 2) {
            page = inner.browsePage("", continuation = page.continuation)
            result.addAll(page.channels)
            pages += 1
        }
        result.distinctBy { it.id }.map { it.copy(isSubscribed = true) }
    }

    // ------------------------------------------------------------- Listas

    suspend fun createPrivatePlaylist(token: String, title: String, description: String = ""): String = session {
        inner.createPlaylist(title)
    }

    suspend fun findPlaylistByTitle(token: String, title: String): String? =
        playlists(token).firstOrNull { it.title.equals(title, ignoreCase = true) }?.id

    suspend fun addVideoToPlaylist(token: String, playlistId: String, videoId: String): String = session {
        inner.editPlaylist(playlistId, videoId, add = true)
        "$playlistId|$videoId"
    }

    suspend fun findPlaylistItemId(token: String, playlistId: String, videoId: String): String? = session {
        var page = inner.browsePage(playlistBrowseId(playlistId))
        var pages = 1
        var found: String? = null
        while (found == null) {
            if (page.videos.any { it.id == videoId } || page.shorts.any { it.id == videoId }) {
                found = "$playlistId|$videoId"
            } else if (page.continuation.isBlank() || pages >= 4) {
                break
            } else {
                page = inner.browsePage("", continuation = page.continuation)
                pages += 1
            }
        }
        found
    }

    suspend fun removePlaylistItem(token: String, playlistItemId: String) = session {
        val playlistId = playlistItemId.substringBefore('|')
        val videoId = playlistItemId.substringAfter('|', "")
        if (playlistId.isNotBlank() && videoId.isNotBlank()) {
            inner.editPlaylist(playlistId, videoId, add = false)
        }
    }

    suspend fun playlists(token: String): List<PlaylistItem> = session {
        val aggregated = runCatching { inner.browsePage("FEplaylist_aggregation").playlists }.getOrDefault(emptyList())
        val list = aggregated.ifEmpty { inner.browsePage("FElibrary").playlists }
        list.filterNot { it.id == "WL" || it.id == "LL" }.distinctBy { it.id }
    }

    suspend fun playlistVideos(
        token: String,
        playlistId: String,
        maxResults: Int = 25
    ): List<VideoItem> = playlistVideosPage(token, playlistId, maxResults = maxResults).items

    suspend fun playlistVideosPage(
        token: String,
        playlistId: String,
        pageToken: String = "",
        maxResults: Int = 25
    ): VideoPage = session {
        if (playlistId.isBlank()) return@session VideoPage(emptyList())
        val page = if (pageToken.isBlank()) {
            inner.browsePage(playlistBrowseId(playlistId))
        } else {
            inner.browsePage("", continuation = pageToken)
        }
        VideoPage(page.videos + page.shorts, page.continuation)
    }

    private fun playlistBrowseId(playlistId: String): String =
        if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"

    // ---------------------------------------------------- Inicio y búsqueda

    suspend fun mostPopular(
        token: String,
        categoryId: String? = null
    ): List<VideoItem> = mostPopularPage(token, categoryId).items

    suspend fun mostPopularPage(
        token: String,
        categoryId: String? = null,
        pageToken: String = "",
        maxResults: Int = 24
    ): VideoPage = when (categoryId) {
        "20" -> chipOrSearch(listOf("juego", "gaming"), "videojuegos", PARAMS_VIDEOS, pageToken)
        "10" -> musicVideosPage(token, pageToken)
        else -> session {
            val page = inner.home(pageToken.removePrefix("b:"))
            VideoPage(page.videos, page.continuation.takeIf { it.isNotBlank() }?.let { "b:$it" }.orEmpty())
        }
    }

    suspend fun searchVideos(
        token: String,
        query: String,
        liveOnly: Boolean = false,
        shortOnly: Boolean = false
    ): List<VideoItem> = searchVideosPage(token, query, liveOnly, shortOnly).items

    suspend fun searchVideosPage(
        token: String,
        query: String,
        liveOnly: Boolean = false,
        shortOnly: Boolean = false,
        pageToken: String = "",
        maxResults: Int = 25
    ): VideoPage = session {
        val params = when {
            liveOnly -> PARAMS_LIVE
            shortOnly -> PARAMS_SHORTS
            else -> PARAMS_VIDEOS
        }
        val page = inner.search(query, params, pageToken)
        if (shortOnly) {
            var shorts = page.shorts + page.videos
                .filter { it.durationMs in 1L..180_000L }
                .map { it.copy(description = (it.description + " #shorts").trim()) }
            var next = page.continuation
            if (shorts.isEmpty() && pageToken.isBlank()) {
                val fallback = inner.search("$query #shorts", "", "")
                shorts = fallback.shorts
                next = fallback.continuation
            }
            VideoPage(shorts, next)
        } else {
            VideoPage(page.videos, page.continuation)
        }
    }

    suspend fun searchAllPage(
        token: String,
        query: String,
        pageToken: String = "",
        maxResults: Int = 25
    ): YouTubeSearchPage = session {
        val page = inner.search(query, "", pageToken)
        YouTubeSearchPage(
            videos = page.videos + page.shorts,
            channels = page.channels,
            playlists = page.playlists,
            nextPageToken = page.continuation
        )
    }

    suspend fun liveVideos(token: String): List<VideoItem> = liveVideosPage(token).items

    suspend fun liveVideosPage(
        token: String,
        pageToken: String = ""
    ): VideoPage = chipOrSearch(listOf("vivo", "directo", "live"), "en vivo", PARAMS_LIVE, pageToken)

    suspend fun musicVideos(token: String): List<VideoItem> = musicVideosPage(token).items

    suspend fun musicVideosPage(
        token: String,
        pageToken: String = ""
    ): VideoPage = chipOrSearch(listOf("música", "musica", "music"), "música", PARAMS_VIDEOS, pageToken)

    suspend fun shorts(token: String): List<VideoItem> = shortsPage(token).items

    suspend fun shortsPage(
        token: String,
        pageToken: String = ""
    ): VideoPage = searchVideosPage(token, "shorts", shortOnly = true, pageToken = pageToken)

    /**
     * Usa los filtros personalizados del inicio de YouTube (Videojuegos, Música, En vivo).
     * Si tu inicio no tiene ese filtro, busca el tema. El prefijo del token indica de dónde
     * viene la siguiente página: "b:" inicio, "s:" búsqueda.
     */
    private suspend fun chipOrSearch(
        words: List<String>,
        query: String,
        params: String,
        pageToken: String
    ): VideoPage = session {
        when {
            pageToken.startsWith("b:") ->
                toVideoPage(inner.browsePage("", continuation = pageToken.drop(2)), "b:")
            pageToken.startsWith("s:") ->
                toVideoPage(inner.search(query, params, pageToken.drop(2)), "s:")
            else -> {
                val chip = inner.homeChips().firstOrNull { chip ->
                    words.any { word -> chip.title.contains(word, ignoreCase = true) }
                }
                val fromChip = chip?.let {
                    runCatching { inner.browsePage("", continuation = it.token) }.getOrNull()
                }
                if (fromChip != null && fromChip.videos.isNotEmpty()) {
                    toVideoPage(fromChip, "b:")
                } else {
                    toVideoPage(inner.search(query, params, ""), "s:")
                }
            }
        }
    }

    private fun toVideoPage(page: InnerTubePage, prefix: String): VideoPage =
        VideoPage(page.videos, page.continuation.takeIf { it.isNotBlank() }?.let { prefix + it }.orEmpty())

    // -------------------------------------------------------------- Canales

    suspend fun channelActivities(
        token: String,
        channelId: String,
        maxResults: Int = 4
    ): List<VideoItem> = channelVideos(token, channelId).take(maxResults)

    suspend fun channelInfo(token: String, channelId: String): ChannelItem = session {
        val json = inner.call("browse") { put("browseId", channelId) }
        val meta = inner.findFirst(json, "channelMetadataRenderer") as? JSONObject
        val header = inner.findFirst(json, "pageHeaderViewModel") as? JSONObject
        val bannerSources = inner.findFirst(header?.opt("banner"), "sources") as? JSONArray
        val banner = if (bannerSources != null && bannerSources.length() > 0) {
            inner.fixUrl(bannerSources.optJSONObject(bannerSources.length() - 1)?.optString("url").orEmpty())
        } else {
            ""
        }
        val rows = inner.collectStrings(header?.opt("metadata"), "content")
        val avatarSources = inner.findFirst(header?.opt("image"), "sources") as? JSONArray
        val headerAvatar = if (avatarSources != null && avatarSources.length() > 0) {
            inner.fixUrl(avatarSources.optJSONObject(avatarSources.length() - 1)?.optString("url").orEmpty())
        } else {
            ""
        }
        val headerTitle = inner.findFirstString(header?.opt("title"), "content") { it.isNotBlank() }.orEmpty()
        ChannelItem(
            id = channelId,
            title = meta?.optString("title").orEmpty().ifBlank { headerTitle },
            thumbnailUrl = meta?.optJSONObject("avatar")?.let { inner.lastThumb(it) }.orEmpty().ifBlank { headerAvatar },
            description = meta?.optString("description").orEmpty(),
            bannerUrl = banner,
            handle = rows.firstOrNull { it.startsWith("@") }.orEmpty(),
            subscriberCount = inner.parseCount(
                rows.firstOrNull { it.contains("suscript", true) || it.contains("subscriber", true) }.orEmpty()
            ),
            videoCount = inner.parseCount(rows.firstOrNull { it.contains("video", true) }.orEmpty()),
            isSubscribed = (inner.findFirst(json, "subscribed") as? Boolean)
                ?: (inner.findFirst(json, "isSubscribed") as? Boolean)
                ?: false
        )
    }

    suspend fun channelPlaylists(token: String, channelId: String): List<PlaylistItem> = session {
        inner.browsePage(channelId, PARAMS_CHANNEL_PLAYLISTS).playlists
    }

    suspend fun channelVideos(token: String, channelId: String): List<VideoItem> = session {
        val videos = inner.browsePage(channelId, PARAMS_CHANNEL_VIDEOS).videos
        videos.ifEmpty { inner.browsePage(channelId).videos }
            .map { video -> if (video.channelId.isBlank()) video.copy(channelId = channelId) else video }
    }

    // --------------------------------------------------------- Notificaciones

    suspend fun homeActivities(token: String): List<NotificationItem> = session {
        val json = inner.call("notification/get_notification_menu") {
            put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX")
        }
        inner.collectObjects(json, "notificationRenderer").mapNotNull { notification ->
            val title = inner.text(notification.opt("shortMessage"))
            if (title.isBlank()) return@mapNotNull null
            val videoId = inner.findFirstString(notification.opt("navigationEndpoint"), "videoId") { it.length == 11 }
            val thumb = notification.optJSONObject("videoThumbnail")?.let { inner.lastThumb(it) }.orEmpty()
                .ifBlank { notification.optJSONObject("thumbnail")?.let { inner.lastThumb(it) }.orEmpty() }
            NotificationItem(
                id = notification.optString("notificationId").ifBlank { videoId ?: title },
                title = title,
                subtitle = inner.text(notification.opt("sentTimeText")),
                thumbnailUrl = thumb,
                video = videoId?.let {
                    VideoItem(
                        id = it,
                        title = title,
                        channelTitle = "",
                        thumbnailUrl = "https://i.ytimg.com/vi/$it/hqdefault.jpg"
                    )
                }
            )
        }.take(30)
    }

    // ---------------------------------------------------------- Metadatos

    /** Los datos de la sesión ya traen duración y vistas; no hace falta completar. */
    suspend fun enrichVideoDurations(token: String, videos: List<VideoItem>): List<VideoItem> = videos

    suspend fun enrichVideos(token: String, videos: List<VideoItem>): List<VideoItem> = videos

    private suspend fun <T> session(block: suspend () -> T): T = try {
        block()
    } catch (expired: YouTubeSessionExpiredException) {
        throw YouTubeApiException(401, expired.message ?: "La sesión de YouTube venció.")
    }

    companion object {
        /** Marca interna: la app está conectada con la sesión de YouTube. */
        const val SESSION_TOKEN = "youtube-session"

        private const val PARAMS_VIDEOS = "EgIQAQ=="
        private const val PARAMS_LIVE = "EgJAAQ=="
        private const val PARAMS_SHORTS = "EgIQCQ=="
        private const val PARAMS_CHANNEL_VIDEOS = "EgZ2aWRlb3PyBgQKAjoA"
        private const val PARAMS_CHANNEL_PLAYLISTS = "EglwbGF5bGlzdHPyBgQKAkIA"
    }
}

class YouTubeApiException(val statusCode: Int, override val message: String) : Exception(message)
