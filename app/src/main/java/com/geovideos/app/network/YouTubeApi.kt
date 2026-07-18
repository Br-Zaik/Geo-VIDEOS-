package com.geovideos.app.network

import com.geovideos.app.data.ChannelDetails
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.GoogleProfile
import com.geovideos.app.data.NotificationItem
import com.geovideos.app.data.PlaylistItem
import com.geovideos.app.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

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

    suspend fun mostPopular(token: String, categoryId: String? = null): List<VideoItem> = withContext(Dispatchers.IO) {
        val category = categoryId?.let { "&videoCategoryId=${encode(it)}" }.orEmpty()
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/videos?part=snippet,liveStreamingDetails&chart=mostPopular&regionCode=PE&maxResults=24$category",
            token
        )
        parseVideoItems(json)
    }

    suspend fun searchVideos(
        token: String,
        query: String,
        liveOnly: Boolean = false,
        shortOnly: Boolean = false
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val live = if (liveOnly) "&eventType=live" else ""
        val duration = if (shortOnly) "&videoDuration=short" else ""
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=25&regionCode=PE&relevanceLanguage=es&videoEmbeddable=true&safeSearch=moderate&q=${encode(query)}$live$duration",
            token
        )
        parseSearchItems(json)
    }

    suspend fun liveVideos(token: String): List<VideoItem> = searchVideos(token, "en vivo", liveOnly = true)

    suspend fun shorts(token: String): List<VideoItem> = searchVideos(token, "shorts populares español", shortOnly = true)

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
                        title = snippet.optString("title", "Canal"),
                        thumbnailUrl = bestThumbnail(snippet),
                        description = snippet.optString("description", "")
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
                        title = snippet.optString("title", "Lista"),
                        thumbnailUrl = bestThumbnail(snippet),
                        itemCount = item.optJSONObject("contentDetails")?.optInt("itemCount") ?: 0
                    )
                )
            }
        }
    }

    suspend fun playlistVideos(token: String, playlistId: String, maxResults: Int = 25): List<VideoItem> = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext emptyList()
        val json = requestJson(
            "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet,contentDetails&playlistId=${encode(playlistId)}&maxResults=$maxResults",
            token
        )
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val snippet = item.optJSONObject("snippet") ?: continue
                val videoId = item.optJSONObject("contentDetails")?.optString("videoId")
                    .orEmpty().ifBlank {
                        snippet.optJSONObject("resourceId")?.optString("videoId").orEmpty()
                    }
                if (videoId.isBlank()) continue
                add(videoFromSnippet(videoId, snippet))
            }
        }
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
                        title = snippet.optString("title", "Actividad nueva"),
                        subtitle = snippet.optString("channelTitle", "YouTube"),
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

    private fun videoFromSnippet(id: String, snippet: JSONObject): VideoItem = VideoItem(
        id = id,
        title = snippet.optString("title", "Video").decodeHtml(),
        channelTitle = snippet.optString("channelTitle", "Canal").decodeHtml(),
        thumbnailUrl = bestThumbnail(snippet),
        publishedAt = snippet.optString("publishedAt", ""),
        description = snippet.optString("description", "").decodeHtml(),
        isLive = snippet.optString("liveBroadcastContent") == "live",
        source = id
    )

    private fun bestThumbnail(snippet: JSONObject?): String {
        val thumbs = snippet?.optJSONObject("thumbnails") ?: return ""
        return thumbs.optJSONObject("maxres")?.optString("url")
            ?: thumbs.optJSONObject("high")?.optString("url")
            ?: thumbs.optJSONObject("medium")?.optString("url")
            ?: thumbs.optJSONObject("default")?.optString("url")
            ?: ""
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
                apiMessage.ifBlank { "Error de YouTube ($code)" }
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
