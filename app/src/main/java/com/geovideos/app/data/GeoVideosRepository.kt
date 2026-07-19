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

    fun loadAutoplay(): Boolean = preferences.getBoolean(KEY_AUTOPLAY, true)

    fun loadDataSaver(): Boolean = preferences.getBoolean(KEY_DATA_SAVER, false)

    fun loadNotificationsEnabled(): Boolean = preferences.getBoolean(KEY_NOTIFICATIONS, true)

    fun setAutoplay(value: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOPLAY, value).apply()
    }

    fun setDataSaver(value: Boolean) {
        preferences.edit().putBoolean(KEY_DATA_SAVER, value).apply()
    }

    fun setNotificationsEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
    }

    fun addToHistory(video: VideoItem): List<VideoItem> {
        val previous = loadHistory().firstOrNull { it.id == video.id }
        val merged = video.copy(
            resumePositionMs = if (video.resumePositionMs > 0L) video.resumePositionMs else previous?.resumePositionMs ?: 0L,
            durationMs = if (video.durationMs > 0L) video.durationMs else previous?.durationMs ?: 0L
        )
        val updated = loadHistory().filterNot { it.id == video.id }.toMutableList()
        updated.add(0, merged)
        return updated.take(60).also { saveVideos(KEY_HISTORY, it) }
    }

    fun updatePlayback(video: VideoItem, positionMs: Long, durationMs: Long): List<VideoItem> {
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(0L)
        val normalizedPosition = if (safeDuration > 0L && safePosition >= safeDuration - 8_000L) 0L else safePosition
        val current = loadHistory().filterNot { it.id == video.id }.toMutableList()
        current.add(
            0,
            video.copy(
                resumePositionMs = normalizedPosition,
                durationMs = safeDuration
            )
        )
        return current.take(60).also { saveVideos(KEY_HISTORY, it) }
    }

    fun toggleWatchLater(video: VideoItem): List<VideoItem> {
        val current = loadWatchLater().toMutableList()
        val existing = current.indexOfFirst { it.id == video.id }
        if (existing >= 0) current.removeAt(existing) else current.add(0, video)
        return current.take(100).also { saveVideos(KEY_WATCH_LATER, it) }
    }

    fun addDownload(title: String, url: String, downloadId: Long): List<VideoItem> {
        val item = VideoItem(
            id = "download-$downloadId",
            title = title.ifBlank { "Video descargado" },
            channelTitle = "Descarga directa",
            thumbnailUrl = "",
            mediaKind = MediaKind.DIRECT,
            source = url,
            downloadId = downloadId
        )
        val updated = loadDownloads().filterNot { it.downloadId == downloadId }.toMutableList().apply { add(0, item) }.take(100)
        saveVideos(KEY_DOWNLOADS, updated)
        return updated
    }

    fun removeDownload(downloadId: Long): List<VideoItem> {
        val updated = loadDownloads().filterNot { it.downloadId == downloadId }
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
        val autoplay = loadAutoplay()
        val dataSaver = loadDataSaver()
        val notifications = loadNotificationsEnabled()
        preferences.edit().clear().apply()
        setAutoplay(autoplay)
        setDataSaver(dataSaver)
        setNotificationsEnabled(notifications)
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
                    .put("resumePositionMs", video.resumePositionMs)
                    .put("durationMs", video.durationMs)
                    .put("downloadId", video.downloadId)
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
                        source = item.optString("source", item.optString("id")),
                        resumePositionMs = item.optLong("resumePositionMs", 0L),
                        durationMs = item.optLong("durationMs", 0L),
                        downloadId = item.optLong("downloadId", -1L)
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
        const val KEY_AUTOPLAY = "autoplay"
        const val KEY_DATA_SAVER = "data_saver"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
    }
}
