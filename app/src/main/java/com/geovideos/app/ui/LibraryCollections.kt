package com.geovideos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.VideoItem

internal enum class LibraryDestination {
    ROOT,
    HISTORY,
    WATCH_LATER,
    LIKED,
    MUSIC,
    UPLOADS,
    SUBSCRIPTIONS
}

internal fun LibraryDestination.title(): String = when (this) {
    LibraryDestination.ROOT -> "Colección"
    LibraryDestination.HISTORY -> "Historial"
    LibraryDestination.WATCH_LATER -> "Ver después"
    LibraryDestination.LIKED -> "Videos que me gustan"
    LibraryDestination.MUSIC -> "Música"
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
    onWatchLater: (VideoItem) -> Unit,
    onRemoveHistory: (String) -> Unit = {}
) {
    if (destination == LibraryDestination.HISTORY) {
        HistoryCollectionScreen(
            modifier = modifier,
            videos = videos,
            onBack = onBack,
            onPlay = onPlay,
            onRemove = onRemoveHistory
        )
        return
    }

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${videos.size} videos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (destination == LibraryDestination.HISTORY) {
                    Text(
                        "Continúa desde donde lo dejaste",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            IconButton(
                onClick = { videos.randomOrNull()?.let(onPlay) },
                enabled = videos.isNotEmpty()
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = "Aleatorio")
            }
            FilledIconButton(
                onClick = { videos.firstOrNull()?.let(onPlay) },
                enabled = videos.isNotEmpty()
            ) {
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
            emptyMessage = when (destination) {
                LibraryDestination.WATCH_LATER -> "Guarda videos y aparecerán aquí para verlos después."
                LibraryDestination.LIKED -> "Los videos que marques con Me gusta aparecerán aquí."
                LibraryDestination.MUSIC -> "La música que reproduzcas o marques con Me gusta aparecerá aquí."
                LibraryDestination.HISTORY -> "Todavía no hay videos en tu historial."
                else -> "No hay videos disponibles en esta sección."
            },
            onLoadMore = onLoadMore,
            onPlay = onPlay,
            onSave = onWatchLater
        )
    }
}

private enum class HistoryMediaFilter(val label: String) {
    ALL("Todo"), VIDEOS("Videos"), SHORTS("Shorts"), PODCASTS("Podcasts"), MUSIC("Música")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryCollectionScreen(
    modifier: Modifier,
    videos: List<VideoItem>,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onRemove: (String) -> Unit
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(videos, query) {
        videos.asSequence()
            .sortedByDescending { it.watchedAtMs }
            .distinctBy { it.id }
            .filter { video ->
                query.isBlank() || video.title.contains(query, true) || video.channelTitle.contains(query, true)
            }
            .toList()
    }
    val grouped = remember(filtered) { filtered.groupBy(::historyDayLabel).entries.toList() }

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text("Historial", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
            },
            actions = {
                IconButton(onClick = {
                    showSearch = !showSearch
                    if (!showSearch) query = ""
                }) {
                    Icon(
                        if (showSearch) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (showSearch) "Cerrar búsqueda" else "Buscar en historial"
                    )
                }
            }
        )
        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text("Buscar en el historial de Geo Videos") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
        }
        HorizontalDivider()
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "Todavía no hay reproducciones en Geo Videos." else "No se encontraron coincidencias.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(28.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                grouped.forEach { (label, group) ->
                    item(key = "history-label-$label") {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                    items(group, key = { "history-row-${it.id}" }) { video ->
                        HistoryVideoRow(video, onPlay = { onPlay(video) }, onRemove = { onRemove(video.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryVideoRow(
    video: VideoItem,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.width(126.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF202024))
        ) {
            LiteThumbnail(
                url = video.thumbnailUrl,
                description = video.title,
                modifier = Modifier.fillMaxSize(),
                widthPx = 480,
                heightPx = 270,
                contentScale = ContentScale.Crop
            )
            if (video.durationMs > 0L) {
                Text(
                    historyDuration(video.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            if (video.durationMs > 0L && video.resumePositionMs > 0L) {
                LinearProgressIndicator(
                    progress = { (video.resumePositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(
                video.channelTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (video.resumePositionMs > 0L) {
                Text(
                    "Continuar en ${historyDuration(video.resumePositionMs)}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Opciones") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Eliminar del historial") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menu = false; onRemove() }
                )
            }
        }
    }
}

private fun historyDayLabel(video: VideoItem): String {
    val time = video.watchedAtMs
    if (time <= 0L) return "Anteriores"
    val now = Calendar.getInstance()
    val watched = Calendar.getInstance().apply { timeInMillis = time }
    val nowDay = now.get(Calendar.DAY_OF_YEAR)
    val watchedDay = watched.get(Calendar.DAY_OF_YEAR)
    val sameYear = now.get(Calendar.YEAR) == watched.get(Calendar.YEAR)
    return when {
        sameYear && nowDay == watchedDay -> "Hoy"
        sameYear && nowDay - watchedDay == 1 -> "Ayer"
        else -> "Anteriores"
    }
}

private fun historyDuration(milliseconds: Long): String {
    val total = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun VideoItem.looksLikePodcastForLibrary(): Boolean {
    val text = "$title $description $channelTitle".lowercase()
    return listOf("podcast", "entrevista", "episodio", "conversación", "conversacion").any { it in text }
}

private fun VideoItem.looksLikeMusicForLibrary(): Boolean {
    val text = "$title $description $channelTitle".lowercase()
    return listOf("music", "música", "musica", "song", "lyrics", "audio", "rap", "mix", "nightcore").any { it in text }
}

private enum class SubscriptionMediaFilter { ALL, VIDEOS, SHORTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionCollectionScreen(
    modifier: Modifier,
    channels: List<ChannelItem>,
    videos: List<VideoItem>,
    onBack: () -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) {
    var selectedChannelId by rememberSaveable { mutableStateOf("") }
    var mediaFilter by rememberSaveable { mutableStateOf(SubscriptionMediaFilter.ALL) }

    val selectedChannel = remember(channels, selectedChannelId) {
        channels.firstOrNull { it.id == selectedChannelId }
    }
    val filteredVideos = remember(videos, selectedChannelId, mediaFilter) {
        videos.asSequence()
            .filter { selectedChannelId.isBlank() || it.channelId == selectedChannelId }
            .filter {
                when (mediaFilter) {
                    SubscriptionMediaFilter.ALL -> true
                    SubscriptionMediaFilter.VIDEOS -> !it.looksLikeShortForLibrary()
                    SubscriptionMediaFilter.SHORTS -> it.looksLikeShortForLibrary()
                }
            }
            .distinctBy { it.id }
            .toList()
    }

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text("Suscripciones", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                }
            }
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SubscriptionChannelButton(
                    title = "Todos",
                    thumbnailUrl = "",
                    selected = selectedChannelId.isBlank(),
                    onClick = { selectedChannelId = "" }
                )
            }
            items(channels, key = { channel: ChannelItem -> "subscription-channel-${channel.id}" }) { channel: ChannelItem ->
                SubscriptionChannelButton(
                    title = channel.title,
                    thumbnailUrl = channel.thumbnailUrl,
                    selected = selectedChannelId == channel.id,
                    onClick = { selectedChannelId = channel.id }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = mediaFilter == SubscriptionMediaFilter.ALL,
                onClick = { mediaFilter = SubscriptionMediaFilter.ALL },
                label = { Text("Todo") }
            )
            FilterChip(
                selected = mediaFilter == SubscriptionMediaFilter.VIDEOS,
                onClick = { mediaFilter = SubscriptionMediaFilter.VIDEOS },
                label = { Text("Videos") }
            )
            FilterChip(
                selected = mediaFilter == SubscriptionMediaFilter.SHORTS,
                onClick = { mediaFilter = SubscriptionMediaFilter.SHORTS },
                label = { Text("Shorts") }
            )
            Spacer(Modifier.weight(1f))
            if (selectedChannel != null) {
                OutlinedButton(onClick = { onOpenChannel(selectedChannel) }) {
                    Text("Ver canal", maxLines = 1)
                }
            }
        }

        Text(
            if (selectedChannel == null) {
                "${channels.size} canales · ${filteredVideos.size} publicaciones recientes"
            } else {
                "${selectedChannel.title} · ${filteredVideos.size} publicaciones"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        HorizontalDivider()

        NativeVideoList(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            videos = filteredVideos,
            loading = false,
            mode = NativeVideoListMode.COMPACT,
            emptyMessage = if (selectedChannel == null) {
                "No se encontraron publicaciones recientes de tus suscripciones."
            } else {
                "No se encontraron publicaciones recientes de este canal. Pulsa Ver canal para buscar más."
            },
            onPlay = onPlay,
            onSave = onWatchLater
        )
    }
}

@Composable
private fun SubscriptionChannelButton(
    title: String,
    thumbnailUrl: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(82.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(62.dp),
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF26232B),
            border = if (selected) {
                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else null
        ) {
            if (thumbnailUrl.isBlank()) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Todo", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            } else {
                val context = LocalContext.current
                val request = remember(thumbnailUrl) {
                    ImageRequest.Builder(context)
                        .data(thumbnailUrl)
                        .size(180)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
        )
    }
}

private fun VideoItem.looksLikeShortForLibrary(): Boolean {
    if (durationMs in 1L..180_000L) return true
    val text = "$title $description".lowercase()
    return "#shorts" in text || "#short" in text || " youtube shorts" in text
}
