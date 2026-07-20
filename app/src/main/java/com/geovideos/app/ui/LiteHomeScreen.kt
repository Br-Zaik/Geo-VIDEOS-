package com.geovideos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
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
    val listState = rememberLazyListState()
    val isScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    val lastVisibleIndex by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    val shouldLoadMore by remember(listState, videos.size, canLoadMore, loadingMore) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            !listState.isScrollInProgress &&
                canLoadMore &&
                !loadingMore &&
                videos.isNotEmpty() &&
                total > 0 &&
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 4
        }
    }

    LaunchedEffect(shouldLoadMore, category) {
        if (shouldLoadMore) onLoadMore(category)
    }

    // Warm only the next two feed thumbnails after the finger stops. This avoids
    // decoding/network work during the fling while keeping the next cards ready.
    val context = LocalContext.current
    LaunchedEffect(isScrolling, lastVisibleIndex, videos) {
        if (!isScrolling && videos.isNotEmpty()) {
            val firstVideoIndex = (lastVisibleIndex - 2).coerceAtLeast(0)
            videos.drop(firstVideoIndex).take(2).forEach { video ->
                val url = feedThumbnailUrl(video.thumbnailUrl)
                if (url.isNotBlank()) {
                    context.imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .size(480, 270)
                            .precision(Precision.INEXACT)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(true)
                            .build()
                    )
                }
            }
        }
    }

    LazyColumn(
        state = listState,
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

        if (loading && videos.isEmpty()) {
            item(key = "lite-home-loading", contentType = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        } else if (videos.isEmpty()) {
            item(key = "lite-home-empty", contentType = "empty") {
                Text(
                    "No hay videos disponibles. Pulsa actualizar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            items(
                items = videos,
                key = { "lite-home-${category.name}-${it.id}" },
                contentType = { "video" }
            ) { video ->
                LiteHomeVideoRow(
                    video = video,
                    saved = video.id in watchLaterIds,
                    isScrolling = isScrolling,
                    onPlay = { onPlay(video) },
                    onWatchLater = { onWatchLater(video) }
                )
            }
        }

        if (loadingMore) {
            item(key = "lite-home-more", contentType = "load-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
            items(videos, key = { "lite-short-shelf-${it.id}" }, contentType = { "short" }) { video ->
                Box(
                    modifier = Modifier
                        .width(148.dp)
                        .height(264.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .clickable { onOpenShort(video) }
                ) {
                    LiteThumbnail(
                        url = shortThumbnailUrl(video.thumbnailUrl),
                        description = video.title,
                        modifier = Modifier.fillMaxSize(),
                        widthPx = 320,
                        heightPx = 568,
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.58f))
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
    isScrolling: Boolean,
    onPlay: () -> Unit,
    onWatchLater: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            LiteThumbnail(
                url = feedThumbnailUrl(video.thumbnailUrl),
                description = video.title,
                modifier = Modifier.fillMaxSize(),
                widthPx = 480,
                heightPx = 270,
                contentScale = ContentScale.Crop,
                deferWhileScrolling = isScrolling
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                LiteThumbnail(
                    url = video.channelThumbnailUrl,
                    description = video.channelTitle,
                    modifier = Modifier.fillMaxSize(),
                    widthPx = 72,
                    heightPx = 72,
                    contentScale = ContentScale.Crop,
                    deferWhileScrolling = isScrolling
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

private fun feedThumbnailUrl(url: String): String = url
    .replace("maxresdefault.jpg", "mqdefault.jpg")
    .replace("sddefault.jpg", "mqdefault.jpg")
    .replace("hqdefault.jpg", "mqdefault.jpg")

private fun shortThumbnailUrl(url: String): String = url
    .replace("maxresdefault.jpg", "hqdefault.jpg")
    .replace("sddefault.jpg", "hqdefault.jpg")
