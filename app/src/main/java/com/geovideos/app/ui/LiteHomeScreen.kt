package com.geovideos.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geovideos.app.data.MediaKind
import com.geovideos.app.data.VideoItem

@Composable
internal fun LiteHomeScreen(
    modifier: Modifier,
    category: HomeCategory,
    personalized: List<VideoItem>,
    popular: List<VideoItem>,
    live: List<VideoItem>,
    gaming: List<VideoItem>,
    music: List<VideoItem>,
    shorts: List<VideoItem>,
    loading: Boolean,
    refreshing: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    watchLater: List<VideoItem>,
    onRefresh: () -> Unit,
    onLoadMore: (HomeCategory) -> Unit,
    onCategory: (HomeCategory) -> Unit,
    onPlay: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    scrollToTopSignal: Long,
    onAtTopChanged: (Boolean) -> Unit
) {
    val baseVideos = remember(category, personalized, popular, live, gaming, music) {
        when (category) {
            // Nunca sustituir "Para ti" por Tendencias. En una cuenta recién conectada eso
            // mostraba videos ajenos durante la sincronización y daba la impresión de que la
            // cuenta equivocada estaba activa. Si todavía no hay feed personal, se muestran
            // skeletons hasta que lleguen señales reales de la cuenta.
            HomeCategory.FOR_YOU -> personalized
            HomeCategory.LIVE -> live
            HomeCategory.GAMING -> gaming
            HomeCategory.MUSIC -> music
        }
    }
    val homeShorts = remember(category, baseVideos, shorts) {
        if (category != HomeCategory.FOR_YOU) emptyList()
        else (shorts + baseVideos.filter(::looksLikeHomeShort)).distinctBy { it.id }.take(18)
    }
    // Si hay una señal musical real dentro del feed personal, mostrar una sola mezcla
    // compacta como DailyTube. Nunca se crea desde Tendencias ni desde búsquedas genéricas.
    val mixVideo: VideoItem? = remember(category, baseVideos) {
        if (category == HomeCategory.FOR_YOU) {
            baseVideos.firstOrNull { video ->
                isEligibleHomeVideoCard(video) && looksLikeMusicForMix(video)
            }?.copy(isMix = true)
        } else {
            null
        }
    }
    val videos = remember(baseVideos, homeShorts, mixVideo) {
        val hiddenIds = buildSet {
            homeShorts.forEach { add(it.id) }
            mixVideo?.id?.let { add(it) }
        }
        baseVideos.filter { video ->
            video.id !in hiddenIds && isEligibleHomeVideoCard(video)
        }
    }
    val watchLaterIds = remember(watchLater) {
        watchLater.asSequence().map { it.id }.toHashSet()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            LiteCategoryChip("Todos", Icons.Default.Explore, category == HomeCategory.FOR_YOU) {
                onCategory(HomeCategory.FOR_YOU)
            }
            LiteCategoryChip("En vivo", Icons.Default.LiveTv, category == HomeCategory.LIVE) {
                onCategory(HomeCategory.LIVE)
            }
            LiteCategoryChip("Juegos", Icons.Default.Games, category == HomeCategory.GAMING) {
                onCategory(HomeCategory.GAMING)
            }
            LiteCategoryChip("Música", Icons.Default.PlaylistPlay, category == HomeCategory.MUSIC) {
                onCategory(HomeCategory.MUSIC)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            RecyclerHomeFeed(
                modifier = Modifier.fillMaxSize(),
                videos = videos,
                shorts = homeShorts,
                showShortsShelf = category == HomeCategory.FOR_YOU,
                mixVideo = mixVideo,
                loading = loading,
                refreshing = refreshing,
                loadingMore = loadingMore,
                canLoadMore = canLoadMore,
                watchLaterIds = watchLaterIds,
                onRefresh = onRefresh,
                onLoadMore = { onLoadMore(category) },
                onPlay = onPlay,
                onOpenShort = onOpenShort,
                onWatchLater = onWatchLater,
                scrollToTopSignal = scrollToTopSignal,
                onAtTopChanged = onAtTopChanged
            )
        }
    }
}

@Composable
private fun LiteCategoryChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(17.dp)) }
    )
}

internal fun looksLikeHomeShort(video: VideoItem): Boolean {
    val text = (video.title + " " + video.description).lowercase()
    val taggedAsShort = listOf(
        "#shorts", " shorts", "short ", "tiktok", "reel", "vertical", "status video"
    ).any { it in text }
    return taggedAsShort || video.durationMs in 1..180_000L
}

private fun isEligibleHomeVideoCard(video: VideoItem): Boolean {
    if (video.isLive) return true
    if (video.mediaKind != MediaKind.YOUTUBE) return !looksLikeHomeShort(video)
    // Para Principal no mostrar elementos de YouTube sin duración confirmada: un Short con
    // metadatos incompletos era exactamente lo que terminaba apareciendo como tarjeta normal.
    return video.durationMs > 180_000L && !looksLikeHomeShort(video)
}

private fun looksLikeMusicForMix(video: VideoItem): Boolean {
    val text = (video.title + " " + video.channelTitle + " " + video.description).lowercase()
    return listOf(
        "music", "música", "musica", "song", "lyrics", "audio", "rap", "mix", "nightcore",
        "remix", "cover", "playlist", "álbum", "album"
    ).any { it in text }
}
