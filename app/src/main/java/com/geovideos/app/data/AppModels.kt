package com.geovideos.app.data

enum class MediaKind {
    YOUTUBE,
    DIRECT,
    LOCAL
}

data class GoogleProfile(
    val name: String,
    val email: String,
    val pictureUrl: String,
    val channelTitle: String = "",
    val channelId: String = ""
)

data class VideoItem(
    val id: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val publishedAt: String = "",
    val description: String = "",
    val isLive: Boolean = false,
    val mediaKind: MediaKind = MediaKind.YOUTUBE,
    val source: String = id
)

data class ChannelItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val description: String = ""
)

data class PlaylistItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val itemCount: Int = 0
)

data class NotificationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String,
    val video: VideoItem? = null
)

data class ChannelDetails(
    val profile: GoogleProfile,
    val likesPlaylistId: String = "",
    val uploadsPlaylistId: String = ""
)
