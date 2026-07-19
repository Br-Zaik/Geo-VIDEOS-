package com.geovideos.app.data

import androidx.compose.runtime.Immutable

enum class MediaKind {
    YOUTUBE,
    DIRECT,
    LOCAL
}

@Immutable
data class GoogleProfile(
    val name: String,
    val email: String,
    val pictureUrl: String,
    val channelTitle: String = "",
    val channelId: String = ""
)

@Immutable
data class VideoItem(
    val id: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val channelId: String = "",
    val channelThumbnailUrl: String = "",
    val publishedAt: String = "",
    val description: String = "",
    val isLive: Boolean = false,
    val mediaKind: MediaKind = MediaKind.YOUTUBE,
    val source: String = id,
    val resumePositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val downloadId: Long = -1L
)

@Immutable
data class ChannelItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val description: String = ""
)

@Immutable
data class PlaylistItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val itemCount: Int = 0
)

@Immutable
data class NotificationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String,
    val video: VideoItem? = null
)

@Immutable
data class ChannelDetails(
    val profile: GoogleProfile,
    val likesPlaylistId: String = "",
    val uploadsPlaylistId: String = ""
)

@Immutable
data class VideoDetails(
    val videoId: String,
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    val subscriberCount: Long = 0L,
    val channelThumbnailUrl: String = "",
    val publishedAt: String = "",
    val description: String = ""
)
