package com.geovideos.app.data

data class UserSession(
    val email: String,
    val displayName: String
)

data class VideoItem(
    val id: String,
    val title: String,
    val creator: String,
    val source: String,
    val isBuiltIn: Boolean = false
)

data class StoredSnapshot(
    val hasAccount: Boolean = false,
    val session: UserSession? = null,
    val customVideos: List<VideoItem> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val historyIds: List<String> = emptyList()
)
