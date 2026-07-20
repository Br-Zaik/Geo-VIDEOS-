package com.geovideos.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    onWatchLater: (VideoItem) -> Unit
) {
    val videos = when (category) {
        HomeCategory.FOR_YOU -> personalized.ifEmpty { popular }
        HomeCategory.LIVE -> live
        HomeCategory.GAMING -> gaming
        HomeCategory.MUSIC -> music
    }
    val watchLaterIds = remember(watchLater) {
        watchLater.asSequence().map { it.id }.toHashSet()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
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
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(21.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, "Actualizar")
                }
            }
        }

        Text(
            when (category) {
                HomeCategory.FOR_YOU -> "Principal"
                HomeCategory.LIVE -> "En directo"
                HomeCategory.GAMING -> "Videojuegos"
                HomeCategory.MUSIC -> "Música"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            RecyclerHomeFeed(
                modifier = Modifier.fillMaxSize(),
                videos = videos,
                shorts = if (category == HomeCategory.FOR_YOU) shorts else emptyList(),
                loading = loading,
                loadingMore = loadingMore,
                canLoadMore = canLoadMore,
                watchLaterIds = watchLaterIds,
                onLoadMore = { onLoadMore(category) },
                onPlay = onPlay,
                onOpenShort = onOpenShort,
                onWatchLater = onWatchLater
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
