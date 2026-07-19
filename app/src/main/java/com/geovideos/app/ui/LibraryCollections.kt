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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.geovideos.app.data.VideoItem
import com.geovideos.app.data.ChannelItem

internal enum class LibraryDestination {
    ROOT,
    HISTORY,
    WATCH_LATER,
    LIKED,
    UPLOADS,
    SUBSCRIPTIONS
}

internal fun LibraryDestination.title(): String = when (this) {
    LibraryDestination.ROOT -> "Colección"
    LibraryDestination.HISTORY -> "Historial"
    LibraryDestination.WATCH_LATER -> "Ver después"
    LibraryDestination.LIKED -> "Videos que me gustan"
    LibraryDestination.UPLOADS -> "Mis videos"
    LibraryDestination.SUBSCRIPTIONS -> "Suscripciones"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryCollectionScreen(
    modifier: Modifier,
    destination: LibraryDestination,
    videos: List<VideoItem>,
    loadingMore: Boolean = false,
    canLoadMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(listState, videos.size, canLoadMore) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            canLoadMore && videos.isNotEmpty() && last >= videos.lastIndex - 4
        }
    }
    LaunchedEffect(shouldLoadMore, loadingMore) {
        if (shouldLoadMore && !loadingMore) onLoadMore?.invoke()
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(destination.title(), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                }
            }
        )

        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No hay videos disponibles en esta sección.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${videos.size} videos",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { videos.randomOrNull()?.let(onPlay) }) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Reproducir aleatoriamente")
                }
                FilledIconButton(onClick = { videos.firstOrNull()?.let(onPlay) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir")
                }
            }
            HorizontalDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(videos, key = { "collection-${destination.name}-${it.id}" }) { video ->
                    CompactCollectionVideoRow(
                        video = video,
                        onPlay = { onPlay(video) },
                        onWatchLater = { onWatchLater(video) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 148.dp))
                }
                if (loadingMore) {
                    item(key = "collection-loading-more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionCollectionScreen(
    modifier: Modifier,
    channels: List<ChannelItem>,
    onBack: () -> Unit,
    onOpenChannel: (ChannelItem) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Suscripciones", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                }
            }
        )
        Text(
            "${channels.size} canales",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(channels, key = { "subscribed-${it.id}" }) { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChannel(channel) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(channel.thumbnailUrl)
                            .crossfade(false)
                            .size(160, 160)
                            .build(),
                        contentDescription = channel.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(54.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(channel.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (channel.description.isNotBlank()) {
                            Text(
                                channel.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "Abrir canal")
                }
                HorizontalDivider(modifier = Modifier.padding(start = 84.dp))
            }
        }
    }
}

@Composable
private fun CompactCollectionVideoRow(
    video: VideoItem,
    onPlay: () -> Unit,
    onWatchLater: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(124.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        ) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.thumbnailUrl)
                    .crossfade(false)
                    .size(480, 270)
                    .build(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (video.durationMs > 0L) {
                Surface(
                    color = Color.Black.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                ) {
                    Text(
                        compactDuration(video.durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            if (video.durationMs > 0L && video.resumePositionMs > 0L) {
                LinearProgressIndicator(
                    progress = {
                        (video.resumePositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 2.dp)) {
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
                modifier = Modifier.padding(top = 5.dp)
            )
            val metadata = buildList {
                formatCollectionPublishedAt(video.publishedAt).takeIf(String::isNotBlank)?.let(::add)
                if (video.resumePositionMs > 0L) add("Continuar")
            }.joinToString(" · ")
            if (metadata.isNotBlank()) {
                Text(
                    metadata,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        IconButton(onClick = onWatchLater, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.WatchLater, contentDescription = "Ver después")
        }
        IconButton(onClick = {}, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
        }
    }
}

private fun compactDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun formatCollectionPublishedAt(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        val instant = java.time.Instant.parse(value)
        val elapsed = (java.time.Instant.now().epochSecond - instant.epochSecond).coerceAtLeast(0L)
        when {
            elapsed < 3_600L -> "hace ${maxOf(1L, elapsed / 60L)} min"
            elapsed < 86_400L -> "hace ${elapsed / 3_600L} h"
            elapsed < 2_592_000L -> "hace ${elapsed / 86_400L} días"
            elapsed < 31_536_000L -> "hace ${elapsed / 2_592_000L} meses"
            else -> "hace ${elapsed / 31_536_000L} años"
        }
    }.getOrDefault("")
}
