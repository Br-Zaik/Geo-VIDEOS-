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
    fun hasConnectedAccount(): Boolean = preferences.getBoolean(KEY_CONNECTED_ACCOUNT, false)
    fun loadLastSyncMs(): Long = preferences.getLong(KEY_LAST_SYNC, 0L)

    fun loadProfile(): GoogleProfile? = runCatching {
        val raw = preferences.getString(KEY_PROFILE, null) ?: return@runCatching null
        val item = JSONObject(raw)
        GoogleProfile(
            name = item.optString("name"),
            email = item.optString("email"),
            pictureUrl = item.optString("pictureUrl"),
            channelTitle = item.optString("channelTitle"),
            channelId = item.optString("channelId")
        )
    }.getOrNull()

    fun loadPersonalized(): List<VideoItem> = loadVideos(KEY_PERSONALIZED)
    fun loadPopular(): List<VideoItem> = loadVideos(KEY_POPULAR)
    fun loadLive(): List<VideoItem> = loadVideos(KEY_LIVE)
    fun loadGaming(): List<VideoItem> = loadVideos(KEY_GAMING)
    fun loadMusic(): List<VideoItem> = loadVideos(KEY_MUSIC)
    fun loadShorts(): List<VideoItem> = loadVideos(KEY_SHORTS)
    fun loadLiked(): List<VideoItem> = loadVideos(KEY_LIKED)
    fun loadSubscriptions(): List<ChannelItem> = decodeChannels(preferences.getString(KEY_SUBSCRIPTIONS, "[]").orEmpty())
    fun loadPlaylists(): List<PlaylistItem> = decodePlaylists(preferences.getString(KEY_PLAYLISTS, "[]").orEmpty())
    fun loadNotifications(): List<NotificationItem> = decodeNotifications(preferences.getString(KEY_REMOTE_NOTIFICATIONS, "[]").orEmpty())

    fun setAutoplay(value: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOPLAY, value).apply()
    }

    fun setDataSaver(value: Boolean) {
        preferences.edit().putBoolean(KEY_DATA_SAVER, value).apply()
    }

    fun setNotificationsEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
    }

    fun markConnected(value: Boolean) {
        preferences.edit().putBoolean(KEY_CONNECTED_ACCOUNT, value).apply()
    }

    fun saveRemoteSnapshot(
        profile: GoogleProfile,
        personalized: List<VideoItem>,
        popular: List<VideoItem>,
        live: List<VideoItem>,
        gaming: List<VideoItem>,
        music: List<VideoItem>,
        shorts: List<VideoItem>,
        liked: List<VideoItem>,
        subscriptions: List<ChannelItem>,
        playlists: List<PlaylistItem>,
        notifications: List<NotificationItem>,
        syncTimeMs: Long = System.currentTimeMillis()
    ) {
        val profileJson = JSONObject()
            .put("name", profile.name)
            .put("email", profile.email)
            .put("pictureUrl", profile.pictureUrl)
            .put("channelTitle", profile.channelTitle)
            .put("channelId", profile.channelId)

        preferences.edit()
            .putString(KEY_PROFILE, profileJson.toString())
            .putString(KEY_PERSONALIZED, encodeVideos(personalized).toString())
            .putString(KEY_POPULAR, encodeVideos(popular).toString())
            .putString(KEY_LIVE, encodeVideos(live).toString())
            .putString(KEY_GAMING, encodeVideos(gaming).toString())
            .putString(KEY_MUSIC, encodeVideos(music).toString())
            .putString(KEY_SHORTS, encodeVideos(shorts).toString())
            .putString(KEY_LIKED, encodeVideos(liked).toString())
            .putString(KEY_SUBSCRIPTIONS, encodeChannels(subscriptions).toString())
            .putString(KEY_PLAYLISTS, encodePlaylists(playlists).toString())
            .putString(KEY_REMOTE_NOTIFICATIONS, encodeNotifications(notifications).toString())
            .putLong(KEY_LAST_SYNC, syncTimeMs)
            .putBoolean(KEY_CONNECTED_ACCOUNT, true)
            .apply()
    }

    fun addToHistory(video: VideoItem): List<VideoItem> {
        val previous = loadHistory().firstOrNull { it.id == video.id }
        val merged = video.copy(
            resumePositionMs = if (video.resumePositionMs > 0L) video.resumePositionMs else previous?.resumePositionMs ?: 0L,
            durationMs = if (video.durationMs > 0L) video.durationMs else previous?.durationMs ?: 0L
        )
        val updated = loadHistory().filterNot { it.id == video.id }.toMutableList()
        updated.add(0, merged)
        return updated.take(80).also { saveVideos(KEY_HISTORY, it) }
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
        return current.take(80).also { saveVideos(KEY_HISTORY, it) }
    }

    fun toggleWatchLater(video: VideoItem): List<VideoItem> {
        val current = loadWatchLater().toMutableList()
        val existing = current.indexOfFirst { it.id == video.id }
        if (existing >= 0) current.removeAt(existing) else current.add(0, video)
        return current.take(150).also { saveVideos(KEY_WATCH_LATER, it) }
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
        val updated = loadDownloads()
            .filterNot { it.downloadId == downloadId }
            .toMutableList()
            .apply { add(0, item) }
            .take(100)
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
        return updated.take(25).also { saveStrings(KEY_SEARCH_HISTORY, it) }
    }

    fun clearLocalUserData() {
        preferences.edit()
            .remove(KEY_HISTORY)
            .remove(KEY_WATCH_LATER)
            .remove(KEY_DOWNLOADS)
            .remove(KEY_SEARCH_HISTORY)
            .apply()
    }

    fun clearAccountCache() {
        preferences.edit()
            .remove(KEY_PROFILE)
            .remove(KEY_PERSONALIZED)
            .remove(KEY_POPULAR)
            .remove(KEY_LIVE)
            .remove(KEY_GAMING)
            .remove(KEY_MUSIC)
            .remove(KEY_SHORTS)
            .remove(KEY_LIKED)
            .remove(KEY_SUBSCRIPTIONS)
            .remove(KEY_PLAYLISTS)
            .remove(KEY_REMOTE_NOTIFICATIONS)
            .remove(KEY_LAST_SYNC)
            .putBoolean(KEY_CONNECTED_ACCOUNT, false)
            .apply()
    }

    private fun loadVideos(key: String): List<VideoItem> = decodeVideos(preferences.getString(key, "[]").orEmpty())

    private fun saveVideos(key: String, videos: List<VideoItem>) {
        preferences.edit().putString(key, encodeVideos(videos).toString()).apply()
    }

    private fun encodeVideos(videos: List<VideoItem>): JSONArray {
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
        return array
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

    private fun encodeChannels(items: List<ChannelItem>): JSONArray {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("thumbnailUrl", it.thumbnailUrl)
                    .put("description", it.description)
            )
        }
        return array
    }

    private fun decodeChannels(raw: String): List<ChannelItem> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ChannelItem(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        thumbnailUrl = item.optString("thumbnailUrl"),
                        description = item.optString("description")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encodePlaylists(items: List<PlaylistItem>): JSONArray {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("thumbnailUrl", it.thumbnailUrl)
                    .put("itemCount", it.itemCount)
            )
        }
        return array
    }

    private fun decodePlaylists(raw: String): List<PlaylistItem> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    PlaylistItem(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        thumbnailUrl = item.optString("thumbnailUrl"),
                        itemCount = item.optInt("itemCount")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeNotifications(items: List<NotificationItem>): JSONArray {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("subtitle", it.subtitle)
                    .put("thumbnailUrl", it.thumbnailUrl)
                    .put("video", it.video?.let { video -> encodeVideos(listOf(video)).optJSONObject(0) })
            )
        }
        return array
    }

    private fun decodeNotifications(raw: String): List<NotificationItem> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val videoJson = item.optJSONObject("video")
                val video = videoJson?.let { decodeVideos(JSONArray().put(it).toString()).firstOrNull() }
                add(
                    NotificationItem(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        subtitle = item.optString("subtitle"),
                        thumbnailUrl = item.optString("thumbnailUrl"),
                        video = video
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun saveStrings(key: String, values: List<String>) {
        val array = JSONArray()
        values.forEach { value -> array.put(value) }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun decodeStrings(raw: String): List<String> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList<String> {
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
        const val KEY_CONNECTED_ACCOUNT = "connected_account"
        const val KEY_PROFILE = "profile"
        const val KEY_PERSONALIZED = "personalized"
        const val KEY_POPULAR = "popular"
        const val KEY_LIVE = "live"
        const val KEY_GAMING = "gaming"
        const val KEY_MUSIC = "music"
        const val KEY_SHORTS = "shorts"
        const val KEY_LIKED = "liked"
        const val KEY_SUBSCRIPTIONS = "subscriptions"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_REMOTE_NOTIFICATIONS = "remote_notifications"
        const val KEY_LAST_SYNC = "last_sync"
    }
}
