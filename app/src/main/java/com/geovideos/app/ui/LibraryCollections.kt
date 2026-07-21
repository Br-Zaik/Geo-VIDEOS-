package com.geovideos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.VideoItem

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
    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text(destination.title(), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${videos.size} videos",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { videos.randomOrNull()?.let(onPlay) }, enabled = videos.isNotEmpty()) {
                Icon(Icons.Default.Shuffle, contentDescription = "Reproducir aleatoriamente")
            }
            FilledIconButton(onClick = { videos.firstOrNull()?.let(onPlay) }, enabled = videos.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir")
            }
        }
        HorizontalDivider()
        NativeVideoList(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            videos = videos,
            loading = false,
            loadingMore = loadingMore,
            canLoadMore = canLoadMore,
            mode = NativeVideoListMode.COMPACT,
            emptyMessage = "No hay videos disponibles en esta sección.",
            onLoadMore = onLoadMore,
            onPlay = onPlay,
            onSave = onWatchLater
        )
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
    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
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
        NativeChannelList(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            channels = channels,
            onOpenChannel = onOpenChannel
        )
    }
}
