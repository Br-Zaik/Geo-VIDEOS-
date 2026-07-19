package com.geovideos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val visibleVideos = remember(videos) { videos.take(24) }
    val watchLaterIds = remember(watchLater) { watchLater.asSequence().map { it.id }.toHashSet() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 94.dp)
    ) {
        item(key = "lite-home-controls", contentType = "controls") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { LiteCategoryChip("Todos", Icons.Default.Explore, category == HomeCategory.FOR_YOU) { onCategory(HomeCategory.FOR_YOU) } }
                        item { LiteCategoryChip("En vivo", Icons.Default.LiveTv, category == HomeCategory.LIVE) { onCategory(HomeCategory.LIVE) } }
                        item { LiteCategoryChip("Juegos", Icons.Default.Games, category == HomeCategory.GAMING) { onCategory(HomeCategory.GAMING) } }
                        item { LiteCategoryChip("Música", Icons.Default.PlaylistPlay, category == HomeCategory.MUSIC) { onCategory(HomeCategory.MUSIC) } }
                    }
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        if (refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
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
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
        }

        if (category == HomeCategory.FOR_YOU && shorts.isNotEmpty()) {
            item(key = "lite-home-shorts", contentType = "shorts") {
                LiteHomeShorts(videos = shorts.take(8), onOpenShort = onOpenShort)
            }
        }

        if (loading && visibleVideos.isEmpty()) {
            item(key = "lite-home-loading", contentType = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        } else if (visibleVideos.isEmpty()) {
            item(key = "lite-home-empty", contentType = "empty") {
                Text(
                    "No hay videos disponibles. Pulsa actualizar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            items(
                items = visibleVideos,
                key = { "lite-home-${category.name}-${it.id}" },
                contentType = { "video" }
            ) { video ->
                LiteHomeVideoRow(
                    video = video,
                    saved = video.id in watchLaterIds,
                    onPlay = { onPlay(video) },
                    onWatchLater = { onWatchLater(video) }
                )
                HorizontalDivider()
            }
        }

        if (canLoadMore) {
            item(key = "lite-home-more", contentType = "load-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedButton(onClick = { onLoadMore(category) }, enabled = !loadingMore) {
                        if (loadingMore) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (loadingMore) "Cargando…" else "Cargar más")
                    }
                }
            }
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
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) }
    )
}

@Composable
private fun LiteHomeShorts(videos: List<VideoItem>, onOpenShort: (VideoItem) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "Shorts",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(videos, key = { "lite-short-shelf-${it.id}" }) { video ->
                Box(
                    modifier = Modifier
                        .width(148.dp)
                        .height(264.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .clickable { onOpenShort(video) }
                ) {
                    LiteThumbnail(
                        url = video.thumbnailUrl,
                        description = video.title,
                        modifier = Modifier.fillMaxSize(),
                        widthPx = 360,
                        heightPx = 640,
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(10.dp)
                    ) {
                        Text(
                            video.title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiteHomeVideoRow(
    video: VideoItem,
    saved: Boolean,
    onPlay: () -> Unit,
    onWatchLater: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            LiteThumbnail(
                url = video.thumbnailUrl,
                description = video.title,
                modifier = Modifier.fillMaxSize(),
                widthPx = 640,
                heightPx = 360,
                contentScale = ContentScale.Crop
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                LiteThumbnail(
                    url = video.channelThumbnailUrl,
                    description = video.channelTitle,
                    modifier = Modifier.fillMaxSize(),
                    widthPx = 96,
                    heightPx = 96,
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    video.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    video.channelTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(onClick = onWatchLater) {
                Icon(
                    Icons.Default.MoreVert,
                    if (saved) "Quitar de Ver después" else "Guardar en Ver después"
                )
            }
        }
    }
}
