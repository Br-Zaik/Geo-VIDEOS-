package com.geovideos.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class GeoVideosRepository(context: Context) {
    private val preferences = context.getSharedPreferences("geo_videos_v4", Context.MODE_PRIVATE)

    fun loadHistory(): List<VideoItem> = decodeVideos(preferences.getString(KEY_HISTORY, "[]").orEmpty())

    fun loadWatchLater(): List<VideoItem> = decodeVideos(preferences.getString(KEY_WATCH_LATER, "[]").orEmpty())

    fun loadDownloads(): List<VideoItem> = decodeVideos(preferences.getString(KEY_DOWNLOADS, "[]").orEmpty())

    fun loadSearchHistory(): List<String> = decodeStrings(preferences.getString(KEY_SEARCH_HISTORY, "[]").orEmpty())

    fun addToHistory(video: VideoItem): List<VideoItem> {
        val updated = loadHistory().filterNot { it.id == video.id }.toMutableList()
        updated.add(0, video)
        return updated.take(60).also { saveVideos(KEY_HISTORY, it) }
    }

    fun toggleWatchLater(video: VideoItem): List<VideoItem> {
        val current = loadWatchLater().toMutableList()
        val existing = current.indexOfFirst { it.id == video.id }
        if (existing >= 0) current.removeAt(existing) else current.add(0, video)
        return current.take(100).also { saveVideos(KEY_WATCH_LATER, it) }
    }

    fun addDownload(title: String, url: String): List<VideoItem> {
        val item = VideoItem(
            id = "download-${System.currentTimeMillis()}",
            title = title.ifBlank { "Video descargado" },
            channelTitle = "Descarga directa",
            thumbnailUrl = "",
            mediaKind = MediaKind.DIRECT,
            source = url
        )
        val updated = loadDownloads().toMutableList().apply { add(0, item) }.take(100)
        saveVideos(KEY_DOWNLOADS, updated)
        return updated
    }

    fun addSearch(query: String): List<String> {
        val clean = query.trim()
        if (clean.isBlank()) return loadSearchHistory()
        val updated = loadSearchHistory().filterNot { it.equals(clean, true) }.toMutableList()
        updated.add(0, clean)
        return updated.take(20).also { saveStrings(KEY_SEARCH_HISTORY, it) }
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun saveVideos(key: String, videos: List<VideoItem>) {
        val array = JSONArray()
        videos.forEach { video ->
            array.put(
                JSONObject()
                    .put("id", video.id)
                    .put("title", video.title)
                    .put("channelTitle", video.channelTitle)
                    .put("thumbnailUrl", video.thumbnailUrl)
                    .put("publishedAt", video.publishedAt)
                    .put("description", video.description)
                    .put("isLive", video.isLive)
                    .put("mediaKind", video.mediaKind.name)
                    .put("source", video.source)
            )
        }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun decodeVideos(raw: String): List<VideoItem> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    VideoItem(
                        id = item.optString("id"),
                        title = item.optString("title", "Video"),
                        channelTitle = item.optString("channelTitle", ""),
                        thumbnailUrl = item.optString("thumbnailUrl", ""),
                        publishedAt = item.optString("publishedAt", ""),
                        description = item.optString("description", ""),
                        isLive = item.optBoolean("isLive", false),
                        mediaKind = runCatching {
                            MediaKind.valueOf(item.optString("mediaKind", MediaKind.YOUTUBE.name))
                        }.getOrDefault(MediaKind.YOUTUBE),
                        source = item.optString("source", item.optString("id"))
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun saveStrings(key: String, values: List<String>) {
        val array = JSONArray()
        values.forEach(array::put)
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun decodeStrings(raw: String): List<String> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) add(array.optString(index))
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY_HISTORY = "history"
        const val KEY_WATCH_LATER = "watch_later"
        const val KEY_DOWNLOADS = "downloads"
        const val KEY_SEARCH_HISTORY = "search_history"
    }
}
