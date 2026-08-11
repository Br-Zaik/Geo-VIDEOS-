package com.geovideos.app.network

import com.geovideos.app.data.ChannelDetails
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.CommentItem
import com.geovideos.app.data.GoogleProfile
import com.geovideos.app.data.NotificationItem
import com.geovideos.app.data.PlaylistItem
import com.geovideos.app.data.VideoItem
import com.geovideos.app.data.VideoDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

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

class YouTubeApi {
    suspend fun getUserInfo(token: String): GoogleProfile = withContext(Dispatchers.IO) {
        val json = requestJson("https://www.googleapis.com/oauth2/v3/userinfo", token)
        GoogleProfile(
            name = json.optString("name", "Cuenta de Google"),
            email = json.optString("email", ""),
            pictureUrl = json.optString("picture", "")
        )
    }

    suspend fun getMyChannel(token: String, baseProfile: GoogleProfile): ChannelDetails = withContext(Dispatchers.IO) {
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/channels?part=snippet,contentDetails&mine=true&maxResults=1",
            token
        )
        val item = json.optJSONArray("items")?.optJSONObject(0)
        val snippet = item?.optJSONObject("snippet")
        val playlists = item?.optJSONObject("contentDetails")
            ?.optJSONObject("relatedPlaylists")
        val profile = baseProfile.copy(
            name = baseProfile.name.ifBlank { snippet?.optString("title").orEmpty() },
            pictureUrl = baseProfile.pictureUrl.ifBlank { bestThumbnail(snippet) },
            channelTitle = snippet?.optString("title").orEmpty(),
            channelId = item?.optString("id").orEmpty()
        )
        ChannelDetails(
            profile = profile,
            likesPlaylistId = playlists?.optString("likes").orEmpty(),
            uploadsPlaylistId = playlists?.optString("uploads").orEmpty()
        )
    }


    suspend fun videoDetails(token: String, video: VideoItem): VideoDetails = withContext(Dispatchers.IO) {
        if (video.id.isBlank()) return@withContext VideoDetails(videoId = video.id)
        val videoJson = requestJson(
            "https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics&id=${encode(video.id)}&maxResults=1",
            token
        )
        val item = videoJson.optJSONArray("items")?.optJSONObject(0)
        val snippet = item?.optJSONObject("snippet")
        val statistics = item?.optJSONObject("statistics")
        val channelId = snippet?.optString("channelId").orEmpty().ifBlank { video.channelId }

        var subscriberCount = 0L
        var channelThumbnail = video.channelThumbnailUrl
        if (channelId.isNotBlank()) {
            val channelJson = requestJson(
                "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&id=${encode(channelId)}&maxResults=1",
                token
            )
            val channel = channelJson.optJSONArray("items")?.optJSONObject(0)
            subscriberCount = channel?.optJSONObject("statistics")
                ?.optString("subscriberCount")
                ?.toLongOrNull() ?: 0L
            channelThumbnail = bestThumbnail(channel?.optJSONObject("snippet")).ifBlank { channelThumbnail }
        }

        val comments = runCatching {
            val commentsJson = requestJson(
                "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId=${encode(video.id)}&order=relevance&textFormat=plainText&maxResults=8",
                token
            )
            val items = commentsJson.optJSONArray("items")
            buildList {
                if (items != null) {
                    for (index in 0 until items.length()) {
                        val thread = items.optJSONObject(index) ?: continue
                        val top = thread.optJSONObject("snippet")
                            ?.optJSONObject("topLevelComment")
                        val commentSnippet = top?.optJSONObject("snippet") ?: continue
                        val text = commentSnippet.optString("textDisplay").decodeHtml().trim()
                        if (text.isBlank()) continue
                        add(
                            CommentItem(
                                id = top.optString("id").ifBlank { thread.optString("id") },
                                author = commentSnippet.optString("authorDisplayName", "Usuario"),
                                authorThumbnailUrl = commentSnippet.optString("authorProfileImageUrl"),
                                text = text,
                                likeCount = commentSnippet.optLong("likeCount", 0L),
                                publishedAt = commentSnippet.optString("publishedAt")
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())

        VideoDetails(
            videoId = video.id,
            viewCount = statistics?.optString("viewCount")?.toLongOrNull() ?: 0L,
            likeCount = statistics?.optString("likeCount")?.toLongOrNull() ?: 0L,
            commentCount = statistics?.optString("commentCount")?.toLongOrNull() ?: 0L,
            subscriberCount = subscriberCount,
            channelThumbnailUrl = channelThumbnail,
            publishedAt = snippet?.optString("publishedAt").orEmpty().ifBlank { video.publishedAt },
            description = snippet?.optString("description").orEmpty().decodeHtml().ifBlank { video.description },
            comments = comments
        )
    }


    suspend fun rateVideo(token: String, videoId: String, rating: String) = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext
        require(rating in setOf("like", "dislike", "none")) { "Calificación no válida" }
        requestBody(
            url = "https://www.googleapis.com/youtube/v3/videos/rate?id=${encode(videoId)}&rating=${encode(rating)}",
            token = token,
            method = "POST"
        )
    }

    suspend fun subscribe(token: String, channelId: String): String = withContext(Dispatchers.IO) {
        if (channelId.isBlank()) return@withContext ""
        val body = JSONObject()
            .put("snippet", JSONObject()
                .put("resourceId", JSONObject()
                    .put("kind", "youtube#channel")
                    .put("channelId", channelId)))
        val json = requestJsonWrite(
            url = "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet",
            token = token,
            method = "POST",
            body = body
        )
        json.optString("id")
    }

    suspend fun findSubscriptionId(token: String, channelId: String): String? = withContext(Dispatchers.IO) {
        if (channelId.isBlank()) return@withContext null
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/subscriptions?part=id&mine=true&forChannelId=${encode(channelId)}&maxResults=1",
            token
        )
        json.optJSONArray("items")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
    }

    suspend fun unsubscribe(token: String, subscriptionId: String) = withContext(Dispatchers.IO) {
        if (subscriptionId.isBlank()) return@withContext
        requestBody(
            url = "https://www.googleapis.com/youtube/v3/subscriptions?id=${encode(subscriptionId)}",
            token = token,
            method = "DELETE"
        )
    }

    suspend fun createPrivatePlaylist(token: String, title: String, description: String = ""): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("snippet", JSONObject()
                .put("title", title)
                .put("description", description))
            .put("status", JSONObject().put("privacyStatus", "private"))
        val json = requestJsonWrite(
            url = "https://www.googleapis.com/youtube/v3/playlists?part=snippet,status",
            token = token,
            method = "POST",
            body = body
        )
        json.optString("id")
    }

    suspend fun findPlaylistByTitle(token: String, title: String): String? = withContext(Dispatchers.IO) {
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/playlists?part=snippet&mine=true&maxResults=50",
            token
        )
        val items = json.optJSONArray("items") ?: return@withContext null
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val currentTitle = item.optJSONObject("snippet")?.optString("title").orEmpty()
            if (currentTitle.equals(title, ignoreCase = true)) {
                return@withContext item.optString("id").takeIf { it.isNotBlank() }
            }
        }
        null
    }

    suspend fun addVideoToPlaylist(token: String, playlistId: String, videoId: String): String = withContext(Dispatchers.IO) {
        if (playlistId.isBlank() || videoId.isBlank()) return@withContext ""
        val body = JSONObject()
            .put("snippet", JSONObject()
                .put("playlistId", playlistId)
                .put("resourceId", JSONObject()
                    .put("kind", "youtube#video")
                    .put("videoId", videoId)))
        val json = requestJsonWrite(
            url = "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet",
            token = token,
            method = "POST",
            body = body
        )
        json.optString("id")
    }

    suspend fun findPlaylistItemId(token: String, playlistId: String, videoId: String): String? = withContext(Dispatchers.IO) {
        if (playlistId.isBlank() || videoId.isBlank()) return@withContext null
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/playlistItems?part=id&playlistId=${encode(playlistId)}&videoId=${encode(videoId)}&maxResults=1",
            token
        )
        json.optJSONArray("items")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
    }

    suspend fun removePlaylistItem(token: String, playlistItemId: String) = withContext(Dispatchers.IO) {
        if (playlistItemId.isBlank()) return@withContext
        requestBody(
            url = "https://www.googleapis.com/youtube/v3/playlistItems?id=${encode(playlistItemId)}",
            token = token,
            method = "DELETE"
        )
    }

    suspend fun relatedVideosPage(
        token: String,
        video: VideoItem,
        pageToken: String = "",
        maxResults: Int = 20
    ): VideoPage = withContext(Dispatchers.IO) {
        val query = relatedQuery(video)
        val page = searchVideosPage(
            token = token,
            query = query,
            pageToken = pageToken,
            maxResults = maxResults
        )
        val filtered = page.items
            .filterNot { it.id == video.id }
            .distinctBy { it.id }
        VideoPage(
            items = enrichVideos(token, filtered),
            nextPageToken = page.nextPageToken
        )
    }

    private fun relatedQuery(video: VideoItem): String {
        val cleaned = video.title
            .replace(Regex("""#[\p{L}\p{N}_-]+"""), " ")
            .replace(Regex("""[^\p{L}\p{N} ]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val words = cleaned.split(' ')
            .filter { it.length >= 3 }
            .take(8)
            .joinToString(" ")
        return words.ifBlank { video.channelTitle.ifBlank { "videos recomendados" } }
    }

    suspend fun mostPopular(
        token: String,
        categoryId: String? = null
    ): List<VideoItem> = mostPopularPage(token, categoryId).items

    suspend fun mostPopularPage(
        token: String,
        categoryId: String? = null,
        pageToken: String = "",
        maxResults: Int = 24
    ): VideoPage = withContext(Dispatchers.IO) {
        val category = categoryId?.takeIf { it.isNotBlank() }
            ?.let { "&videoCategoryId=${encode(it)}" }
            .orEmpty()
        val page = pageToken.takeIf { it.isNotBlank() }
            ?.let { "&pageToken=${encode(it)}" }
            .orEmpty()
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/videos?part=snippet,liveStreamingDetails&chart=mostPopular&regionCode=PE&maxResults=${maxResults.coerceIn(1, 50)}$category$page",
            token
        )
        VideoPage(parseVideoItems(json), json.optString("nextPageToken"))
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
    ): VideoPage = withContext(Dispatchers.IO) {
        val live = if (liveOnly) "&eventType=live" else ""
        val duration = if (shortOnly) "&videoDuration=short" else ""
        val safeSearch = if (shortOnly) "strict" else "moderate"
        val page = pageToken.takeIf { it.isNotBlank() }
            ?.let { "&pageToken=${encode(it)}" }
            .orEmpty()
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=${maxResults.coerceIn(1, 50)}&regionCode=PE&relevanceLanguage=es&videoEmbeddable=true&safeSearch=$safeSearch&q=${encode(query)}$live$duration$page",
            token
        )
        VideoPage(parseSearchItems(json), json.optString("nextPageToken"))
    }

    suspend fun searchAllPage(
        token: String,
        query: String,
        pageToken: String = "",
        maxResults: Int = 25
    ): YouTubeSearchPage = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext YouTubeSearchPage()
        val page = pageToken.takeIf { it.isNotBlank() }
            ?.let { "&pageToken=${encode(it)}" }
            .orEmpty()
        // No se fuerza type=video: la búsqueda general de YouTube puede devolver
        // videos, canales y playlists en una sola consulta. Tampoco se fuerza idioma,
        // para no ocultar resultados válidos de la cuenta.
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=${maxResults.coerceIn(1, 50)}&regionCode=PE&safeSearch=moderate&q=${encode(clean)}$page",
            token
        )
        val items = json.optJSONArray("items")
        val videos = ArrayList<VideoItem>()
        val channels = ArrayList<ChannelItem>()
        val playlists = ArrayList<PlaylistItem>()
        if (items != null) {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val idObject = item.optJSONObject("id") ?: continue
                val snippet = item.optJSONObject("snippet") ?: continue
                when (idObject.optString("kind")) {
                    "youtube#video" -> {
                        val id = idObject.optString("videoId")
                        if (id.isNotBlank()) videos += videoFromSnippet(id, snippet)
                    }
                    "youtube#channel" -> {
                        val id = idObject.optString("channelId")
                        if (id.isNotBlank()) {
                            channels += ChannelItem(
                                id = id,
                                title = snippet.optString("title", "Canal").decodeHtml(),
                                thumbnailUrl = bestThumbnail(snippet),
                                description = snippet.optString("description", "").decodeHtml()
                            )
                        }
                    }
                    "youtube#playlist" -> {
                        val id = idObject.optString("playlistId")
                        if (id.isNotBlank()) {
                            playlists += PlaylistItem(
                                id = id,
                                title = snippet.optString("title", "Lista").decodeHtml(),
                                thumbnailUrl = bestThumbnail(snippet)
                            )
                        }
                    }
                }
            }
        }
        YouTubeSearchPage(
            videos = videos.distinctBy { it.id },
            channels = channels.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id },
            nextPageToken = json.optString("nextPageToken")
        )
    }

    suspend fun liveVideos(token: String): List<VideoItem> = liveVideosPage(token).items

    suspend fun liveVideosPage(
        token: String,
        pageToken: String = ""
    ): VideoPage = searchVideosPage(
        token = token,
        query = "en vivo español",
        liveOnly = true,
        pageToken = pageToken
    )

    suspend fun musicVideos(token: String): List<VideoItem> = musicVideosPage(token).items

    suspend fun musicVideosPage(
        token: String,
        pageToken: String = ""
    ): VideoPage = mostPopularPage(token, categoryId = "10", pageToken = pageToken)

    suspend fun shorts(token: String): List<VideoItem> = shortsPage(token).items

    suspend fun shortsPage(
        token: String,
        pageToken: String = ""
    ): VideoPage = searchVideosPage(
        token = token,
        query = "shorts español",
        shortOnly = true,
        pageToken = pageToken
    )

    suspend fun channelActivities(
        token: String,
        channelId: String,
        maxResults: Int = 4
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        if (channelId.isBlank()) return@withContext emptyList()
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/activities?part=snippet,contentDetails&channelId=${encode(channelId)}&maxResults=${maxResults.coerceIn(1, 50)}",
            token
        )
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val snippet = item.optJSONObject("snippet") ?: continue
                val details = item.optJSONObject("contentDetails")
                val videoId = details?.optJSONObject("upload")?.optString("videoId").orEmpty()
                if (videoId.isNotBlank()) add(videoFromSnippet(videoId, snippet))
            }
        }
    }

    suspend fun subscriptions(token: String, maxPages: Int = 4): List<ChannelItem> = withContext(Dispatchers.IO) {
        // YouTube returns at most 50 subscriptions per page. Reading only the first page made
        // Principal depend on a tiny and incomplete slice of the real account. Paginate a
        // reasonable amount so the home feed can rotate through the user's actual channels.
        val result = ArrayList<ChannelItem>(100)
        var pageToken = ""
        var pageCount = 0
        do {
            val page = pageToken.takeIf { it.isNotBlank() }
                ?.let { "&pageToken=${encode(it)}" }
                .orEmpty()
            val json = requestJson(
                "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=50&order=relevance$page",
                token
            )
            val items = json.optJSONArray("items")
            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val snippet = item.optJSONObject("snippet") ?: continue
                    val resource = snippet.optJSONObject("resourceId")
                    val channelId = resource?.optString("channelId").orEmpty()
                    if (channelId.isBlank()) continue
                    result += ChannelItem(
                        id = channelId,
                        title = snippet.optString("title", "Canal").decodeHtml(),
                        thumbnailUrl = bestThumbnail(snippet),
                        description = snippet.optString("description", "").decodeHtml()
                    )
                }
            }
            pageToken = json.optString("nextPageToken")
            pageCount += 1
        } while (pageToken.isNotBlank() && pageCount < maxPages.coerceIn(1, 10))
        result.distinctBy { it.id }
    }

    suspend fun playlists(token: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<PlaylistItem>(50)
        var pageToken = ""
        var pageCount = 0
        do {
            val page = pageToken.takeIf { it.isNotBlank() }
                ?.let { "&pageToken=${encode(it)}" }
                .orEmpty()
            val json = requestJson(
                "https://www.googleapis.com/youtube/v3/playlists?part=snippet,contentDetails&mine=true&maxResults=50$page",
                token
            )
            val items = json.optJSONArray("items")
            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val snippet = item.optJSONObject("snippet") ?: continue
                    val playlistId = item.optString("id")
                    if (playlistId.isBlank()) continue
                    result += PlaylistItem(
                        id = playlistId,
                        title = snippet.optString("title", "Lista").decodeHtml(),
                        thumbnailUrl = bestThumbnail(snippet),
                        itemCount = item.optJSONObject("contentDetails")?.optInt("itemCount") ?: 0
                    )
                }
            }
            pageToken = json.optString("nextPageToken")
            pageCount += 1
        } while (pageToken.isNotBlank() && pageCount < 4)
        result.distinctBy { it.id }
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
    ): VideoPage = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext VideoPage(emptyList())
        val page = pageToken.takeIf { it.isNotBlank() }
            ?.let { "&pageToken=${encode(it)}" }
            .orEmpty()
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&playlistId=${encode(playlistId)}&maxResults=${maxResults.coerceIn(1, 50)}$page",
            token
        )
        val items = json.optJSONArray("items")
        val videos = buildList {
            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val snippet = item.optJSONObject("snippet") ?: continue
                    val videoId = item.optJSONObject("contentDetails")?.optString("videoId")
                        .orEmpty().ifBlank {
                            snippet.optJSONObject("resourceId")?.optString("videoId").orEmpty()
                        }
                    if (videoId.isBlank()) continue
                    val ownerChannelId = snippet.optString("videoOwnerChannelId")
                        .ifBlank { snippet.optString("channelId") }
                    val ownerChannelTitle = snippet.optString("videoOwnerChannelTitle")
                        .ifBlank { snippet.optString("channelTitle", "Canal") }
                    add(
                        videoFromSnippet(
                            id = videoId,
                            snippet = snippet,
                            channelIdOverride = ownerChannelId,
                            channelTitleOverride = ownerChannelTitle
                        )
                    )
                }
            }
        }
        VideoPage(videos, json.optString("nextPageToken"))
    }

    suspend fun homeActivities(token: String): List<NotificationItem> = withContext(Dispatchers.IO) {
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/activities?part=snippet,contentDetails&home=true&maxResults=25",
            token
        )
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val snippet = item.optJSONObject("snippet") ?: continue
                val details = item.optJSONObject("contentDetails")
                val videoId = details?.optJSONObject("upload")?.optString("videoId")
                    .orEmpty().ifBlank {
                        details?.optJSONObject("recommendation")
                            ?.optJSONObject("resourceId")
                            ?.optString("videoId").orEmpty()
                    }
                val video = if (videoId.isNotBlank()) videoFromSnippet(videoId, snippet) else null
                add(
                    NotificationItem(
                        id = item.optString("id", "activity-$index"),
                        title = snippet.optString("title", "Actividad nueva").decodeHtml(),
                        subtitle = snippet.optString("channelTitle", "Canal").decodeHtml(),
                        thumbnailUrl = bestThumbnail(snippet),
                        video = video
                    )
                )
            }
        }
    }

    suspend fun channelInfo(token: String, channelId: String): ChannelItem = withContext(Dispatchers.IO) {
        if (channelId.isBlank()) return@withContext ChannelItem("", "Canal", "")
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/channels?part=snippet,brandingSettings,statistics&id=${encode(channelId)}&maxResults=1",
            token
        )
        val item = json.optJSONArray("items")?.optJSONObject(0)
        val snippet = item?.optJSONObject("snippet")
        val statistics = item?.optJSONObject("statistics")
        val branding = item?.optJSONObject("brandingSettings")?.optJSONObject("image")
        ChannelItem(
            id = item?.optString("id").orEmpty().ifBlank { channelId },
            title = snippet?.optString("title", "Canal").orEmpty().decodeHtml().ifBlank { "Canal" },
            thumbnailUrl = bestThumbnail(snippet),
            description = snippet?.optString("description").orEmpty().decodeHtml(),
            bannerUrl = branding?.optString("bannerExternalUrl").orEmpty(),
            handle = snippet?.optString("customUrl").orEmpty(),
            subscriberCount = statistics?.optString("subscriberCount")?.toLongOrNull() ?: 0L,
            videoCount = statistics?.optString("videoCount")?.toLongOrNull() ?: 0L
        )
    }

    suspend fun channelPlaylists(token: String, channelId: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        if (channelId.isBlank()) return@withContext emptyList()
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/playlists?part=snippet,contentDetails&channelId=${encode(channelId)}&maxResults=25",
            token
        )
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val snippet = item.optJSONObject("snippet") ?: continue
                add(
                    PlaylistItem(
                        id = item.optString("id"),
                        title = snippet.optString("title", "Lista").decodeHtml(),
                        thumbnailUrl = bestThumbnail(snippet),
                        itemCount = item.optJSONObject("contentDetails")?.optInt("itemCount") ?: 0
                    )
                )
            }
        }
    }

    suspend fun channelVideos(token: String, channelId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        if (channelId.isBlank()) return@withContext emptyList()
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&order=date&maxResults=25&videoEmbeddable=true&channelId=${encode(channelId)}",
            token
        )
        parseSearchItems(json)
    }

    suspend fun enrichVideoDurations(token: String, videos: List<VideoItem>): List<VideoItem> = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext videos
        val idChunks = videos.asSequence()
            .filter { it.durationMs <= 0L && it.mediaKind == com.geovideos.app.data.MediaKind.YOUTUBE }
            .map { it.id }
            .filter { it.isNotBlank() }
            .distinct()
            .chunked(50)
            .toList()
        if (idChunks.isEmpty()) return@withContext videos

        // Principal necesita la duración para separar Shorts de videos normales. Las versiones
        // anteriores consultaban cada bloque de 50 de forma secuencial y alargaban mucho la
        // primera carga. Ejecutar los pocos bloques en paralelo reduce ese tiempo sin cambiar
        // la información que se solicita a YouTube.
        val durationMaps = coroutineScope {
            idChunks.map { ids ->
                async {
                    val found = LinkedHashMap<String, Long>()
                    val json = requestJson(
                        "https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id=${ids.joinToString(",")}&maxResults=50",
                        token
                    )
                    val items = json.optJSONArray("items")
                    if (items != null) {
                        for (index in 0 until items.length()) {
                            val item = items.optJSONObject(index) ?: continue
                            val id = item.optString("id")
                            val iso = item.optJSONObject("contentDetails")?.optString("duration").orEmpty()
                            val durationMs = runCatching { java.time.Duration.parse(iso).toMillis() }
                                .getOrDefault(0L)
                            if (id.isNotBlank() && durationMs > 0L) found[id] = durationMs
                        }
                    }
                    found
                }
            }.awaitAll()
        }
        val durations = LinkedHashMap<String, Long>()
        durationMaps.forEach { durations.putAll(it) }
        videos.map { video ->
            val duration = durations[video.id] ?: video.durationMs
            if (duration == video.durationMs) video else video.copy(durationMs = duration)
        }
    }

    suspend fun enrichVideos(token: String, videos: List<VideoItem>): List<VideoItem> = withContext(Dispatchers.IO) {
        if (videos.isEmpty()) return@withContext videos
        val ids = videos.asSequence()
            .map { it.channelId }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (ids.isEmpty()) return@withContext videos

        val avatars = LinkedHashMap<String, String>()
        ids.chunked(50).forEach { channelIds ->
            val json = requestJson(
                "https://www.googleapis.com/youtube/v3/channels?part=snippet&id=${channelIds.joinToString(",")}&maxResults=50",
                token
            )
            val items = json.optJSONArray("items") ?: return@forEach
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val image = bestThumbnail(item.optJSONObject("snippet"))
                if (id.isNotBlank() && image.isNotBlank()) avatars[id] = image
            }
        }
        videos.map { video ->
            val avatar = avatars[video.channelId].orEmpty()
            if (avatar.isBlank()) video else video.copy(channelThumbnailUrl = avatar)
        }
    }

    private fun parseVideoItems(json: JSONObject): List<VideoItem> {
        val items = json.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val snippet = item.optJSONObject("snippet") ?: continue
                if (id.isNotBlank()) add(videoFromSnippet(id, snippet))
            }
        }
    }

    private fun parseSearchItems(json: JSONObject): List<VideoItem> {
        val items = json.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
                val snippet = item.optJSONObject("snippet") ?: continue
                if (id.isNotBlank()) add(videoFromSnippet(id, snippet))
            }
        }
    }

    private fun videoFromSnippet(
        id: String,
        snippet: JSONObject,
        channelIdOverride: String = "",
        channelTitleOverride: String = ""
    ): VideoItem = VideoItem(
        id = id,
        title = snippet.optString("title", "Video").decodeHtml(),
        channelTitle = channelTitleOverride.ifBlank {
            snippet.optString("channelTitle", "Canal").decodeHtml()
        },
        thumbnailUrl = bestThumbnail(snippet),
        channelId = channelIdOverride.ifBlank { snippet.optString("channelId") },
        publishedAt = snippet.optString("publishedAt", ""),
        description = snippet.optString("description", "").decodeHtml(),
        isLive = snippet.optString("liveBroadcastContent") == "live",
        source = id
    )

    private fun bestThumbnail(snippet: JSONObject?): String {
        val thumbs = snippet?.optJSONObject("thumbnails") ?: return ""
        // Prefer a clear standard/high thumbnail for phone-sized cards. Glide/Coil
        // still decode it to the exact view size, so the feed stays lightweight.
        return listOf("standard", "high", "medium", "maxres", "default")
            .asSequence()
            .mapNotNull { key -> thumbs.optJSONObject(key)?.optString("url") }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun requestJson(url: String, token: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Accept-Language", "es-PE,es;q=0.9")

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = if (stream != null) {
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } else {
            ""
        }
        connection.disconnect()
        if (code !in 200..299) {
            val apiMessage = runCatching {
                JSONObject(body.ifBlank { "{}" })
                    .optJSONObject("error")
                    ?.optString("message")
                    .orEmpty()
            }.getOrDefault("")
            throw YouTubeApiException(
                code,
                apiMessage.ifBlank { "Error del servicio de video ($code)" }
            )
        }
        return JSONObject(body.ifBlank { "{}" })
    }

    private fun requestJsonWrite(
        url: String,
        token: String,
        method: String,
        body: JSONObject? = null
    ): JSONObject {
        val response = requestBody(url, token, method, body)
        return JSONObject(response.ifBlank { "{}" })
    }

    private fun requestBody(
        url: String,
        token: String,
        method: String,
        body: JSONObject? = null
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Accept-Language", "es-PE,es;q=0.9")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = if (stream != null) {
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } else {
            ""
        }
        connection.disconnect()
        if (code !in 200..299) {
            val apiMessage = runCatching {
                JSONObject(response.ifBlank { "{}" })
                    .optJSONObject("error")
                    ?.optString("message")
                    .orEmpty()
            }.getOrDefault("")
            throw YouTubeApiException(
                code,
                apiMessage.ifBlank { "Error del servicio de video ($code)" }
            )
        }
        return response
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun String.decodeHtml(): String = this
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}

class YouTubeApiException(val statusCode: Int, override val message: String) : Exception(message)
