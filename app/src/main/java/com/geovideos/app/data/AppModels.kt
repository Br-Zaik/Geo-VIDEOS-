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
    val downloadId: Long = -1L,
    val watchedAtMs: Long = 0L,
    val isMix: Boolean = false,
    /** Texto de vistas tal como lo muestra YouTube, por ejemplo "150 K vistas". */
    val viewCountText: String = "",
    /** YouTube lo marca como "Nuevo". */
    val isNew: Boolean = false,
    /** Lista del Mix de YouTube (RD…) cuando la tarjeta es un Mix real. */
    val mixPlaylistId: String = "",
    /** Contadores de Shorts tal como los muestra YouTube ("12 K"). */
    val likeCountText: String = "",
    val commentCountText: String = "",
    /** Tokens del menú ⋮ de YouTube: "No me interesa" y "No recomendar el canal". */
    val notInterestedToken: String = "",
    val dontRecommendToken: String = "",
    /** Día del historial de YouTube ("Hoy", "Ayer", "martes"…). */
    val historySection: String = ""
)

/** Un elemento del inicio de YouTube, en el mismo orden en que YouTube lo envía. */
sealed interface HomeFeedEntry {
    data class Video(val video: VideoItem) : HomeFeedEntry
    data class Shorts(val shorts: List<VideoItem>) : HomeFeedEntry
    data class Mix(val mix: VideoItem) : HomeFeedEntry
}

@Immutable
data class ChannelItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val description: String = "",
    val bannerUrl: String = "",
    val handle: String = "",
    val subscriberCount: Long = 0L,
    val videoCount: Long = 0L,
    val isSubscribed: Boolean = false,
    /** Campana: 0 = Todas, 1 = Personalizadas, 2 = Ninguna, -1 = no se sabe. */
    val bellState: Int = -1,
    /** Parámetros de YouTube para cambiar la campana (en el orden Todas, Personalizadas, Ninguna). */
    val bellParams: List<String> = emptyList(),
    /** Punto azul de "contenido nuevo" y aro rojo de "en vivo" (barra de Suscripciones). */
    val hasNewContent: Boolean = false,
    val isLiveNow: Boolean = false,
    val isVerified: Boolean = false,
    val linksText: String = ""
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
    val video: VideoItem? = null,
    /** Foto del canal, sección ("Importante" / "Más notificaciones") y si ya se leyó. */
    val avatarUrl: String = "",
    val section: String = "",
    val read: Boolean = true
)

/** Una página de notificaciones con su continuación. */
data class NotificationsPage(
    val items: List<NotificationItem>,
    val continuation: String = ""
)

@Immutable
data class ChannelDetails(
    val profile: GoogleProfile,
    val likesPlaylistId: String = "",
    val uploadsPlaylistId: String = ""
)

@Immutable
data class CommentItem(
    val id: String,
    val author: String,
    val authorThumbnailUrl: String = "",
    val text: String,
    val likeCount: Long = 0L,
    val publishedAt: String = "",
    /** Número de respuestas y token para cargarlas (hilos de comentarios). */
    val replyCount: Int = 0,
    val repliesToken: String = "",
    val isPinned: Boolean = false,
    val creatorHeart: Boolean = false
)

/** Una página de comentarios: lista, siguiente página y tokens de orden (Destacados / Más recientes). */
data class CommentsPage(
    val comments: List<CommentItem>,
    val nextToken: String = "",
    val sortTokens: List<String> = emptyList(),
    val totalText: String = ""
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
    val description: String = "",
    val comments: List<CommentItem> = emptyList()
)
