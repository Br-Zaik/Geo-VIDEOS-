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
            HomeCategory.FOR_YOU -> personalized.ifEmpty { popular }
            HomeCategory.LIVE -> live
            HomeCategory.GAMING -> gaming
            HomeCategory.MUSIC -> music
        }
    }
    val homeShorts = remember(category, baseVideos, shorts) {
        if (category != HomeCategory.FOR_YOU) emptyList()
        else (shorts + baseVideos.filter(::looksLikeHomeShort)).distinctBy { it.id }.take(18)
    }
    val mixVideo = remember(category, music, personalized, popular, loading) {
        if (category != HomeCategory.FOR_YOU) null
        else music.firstOrNull()
            ?: personalized.firstOrNull(::looksLikeMusicForMix)
            ?: popular.firstOrNull(::looksLikeMusicForMix)
            // Durante la primera carga no inventar un Mix con cualquier video solo
            // para llenar el hueco. Esperar a que exista una senal musical real.
            ?: if (!loading) personalized.firstOrNull() ?: popular.firstOrNull() else null
    }
    val videos = remember(baseVideos, homeShorts, mixVideo) {
        val hiddenIds = buildSet {
            homeShorts.forEach { add(it.id) }
            mixVideo?.id?.let { add(it) }
        }
        baseVideos.filterNot { video -> video.id in hiddenIds || looksLikeHomeShort(video) }
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

private fun looksLikeHomeShort(video: VideoItem): Boolean {
    val text = (video.title + " " + video.description).lowercase()
    val taggedAsShort = listOf(
        "#shorts", " shorts", "short ", "tiktok", "reel", "vertical", "status video"
    ).any { it in text }
    return taggedAsShort || video.durationMs in 1..90_000L
}

private fun looksLikeMusicForMix(video: VideoItem): Boolean {
    val text = (video.title + " " + video.channelTitle + " " + video.description).lowercase()
    return listOf(
        "music", "música", "musica", "song", "lyrics", "audio", "rap", "mix", "nightcore",
        "remix", "cover", "playlist", "álbum", "album"
    ).any { it in text }
}
