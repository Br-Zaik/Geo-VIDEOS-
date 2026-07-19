package com.geovideos.app.network

import com.geovideos.app.data.ChannelDetails
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.GoogleProfile
import com.geovideos.app.data.NotificationItem
import com.geovideos.app.data.PlaylistItem
import com.geovideos.app.data.VideoItem
import com.geovideos.app.data.VideoDetails
import kotlinx.coroutines.Dispatchers
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

        VideoDetails(
            videoId = video.id,
            viewCount = statistics?.optString("viewCount")?.toLongOrNull() ?: 0L,
            likeCount = statistics?.optString("likeCount")?.toLongOrNull() ?: 0L,
            commentCount = statistics?.optString("commentCount")?.toLongOrNull() ?: 0L,
            subscriberCount = subscriberCount,
            channelThumbnailUrl = channelThumbnail,
            publishedAt = snippet?.optString("publishedAt").orEmpty().ifBlank { video.publishedAt },
            description = snippet?.optString("description").orEmpty().decodeHtml().ifBlank { video.description }
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
        val page = pageToken.takeIf { it.isNotBlank() }
            ?.let { "&pageToken=${encode(it)}" }
            .orEmpty()
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=${maxResults.coerceIn(1, 50)}&regionCode=PE&relevanceLanguage=es&videoEmbeddable=true&safeSearch=moderate&q=${encode(query)}$live$duration$page",
            token
        )
        VideoPage(parseSearchItems(json), json.optString("nextPageToken"))
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

    suspend fun subscriptions(token: String): List<ChannelItem> = withContext(Dispatchers.IO) {
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=50&order=relevance",
            token
        )
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val snippet = item.optJSONObject("snippet") ?: continue
                val resource = snippet.optJSONObject("resourceId")
                add(
                    ChannelItem(
                        id = resource?.optString("channelId").orEmpty(),
                        title = snippet.optString("title", "Canal").decodeHtml(),
                        thumbnailUrl = bestThumbnail(snippet),
                        description = snippet.optString("description", "").decodeHtml()
                    )
                )
            }
        }
    }

    suspend fun playlists(token: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/playlists?part=snippet,contentDetails&mine=true&maxResults=25",
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

    suspend fun channelVideos(token: String, channelId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        if (channelId.isBlank()) return@withContext emptyList()
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&order=date&maxResults=25&videoEmbeddable=true&channelId=${encode(channelId)}",
            token
        )
        parseSearchItems(json)
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
        return listOf("maxres", "standard", "high", "medium", "default")
            .asSequence()
            .mapNotNull { key -> thumbs.optJSONObject(key)?.optString("url") }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun requestJson(url: String, token: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 20_000
        connection.readTimeout = 25_000
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

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun String.decodeHtml(): String = this
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}

class YouTubeApiException(val statusCode: Int, override val message: String) : Exception(message)
