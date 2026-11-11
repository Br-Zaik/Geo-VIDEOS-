package com.geovideos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.VideoItem

private enum class SubsFilter(val label: String) {
    ALL("Todos"),
    TODAY("Hoy"),
    VIDEOS("Videos"),
    SHORTS("Shorts"),
    LIVE("En vivo")
}

/** Pestaña Suscripciones como en YouTube: canales arriba, filtros, Shorts y luego videos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionsFeedScreen(
    modifier: Modifier,
    channels: List<ChannelItem>,
    videos: List<VideoItem>,
    shorts: List<VideoItem>,
    loading: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) {
    var filter by rememberSaveable { mutableStateOf(SubsFilter.ALL) }

    // Primero los canales que publicaron hace poco (como la fila de YouTube).
    val orderedChannels = remember(channels, videos) {
        val byId = channels.associateBy { it.id }
        val active = videos.map { it.channelId }.filter { it.isNotBlank() }.distinct().mapNotNull { byId[it] }
        (active + channels).distinctBy { it.id }.take(50)
    }
    val recentChannelIds = remember(videos) {
        videos.filter { isPublishedToday(it.publishedAt) }.map { it.channelId }.toSet()
    }
    val shownVideos = remember(videos, filter) {
        when (filter) {
            SubsFilter.ALL, SubsFilter.VIDEOS -> videos
            SubsFilter.TODAY -> videos.filter { isPublishedToday(it.publishedAt) }
            SubsFilter.LIVE -> videos.filter { it.isLive }
            SubsFilter.SHORTS -> emptyList()
        }
    }
    val shownShorts = when (filter) {
        SubsFilter.ALL, SubsFilter.TODAY, SubsFilter.SHORTS -> shorts
        else -> emptyList()
    }

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, canLoadMore, loadingMore) {
        if (nearEnd && canLoadMore && !loadingMore) onLoadMore()
    }

    PullToRefreshBox(
        isRefreshing = loading && videos.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (orderedChannels.isNotEmpty()) {
                item(key = "channels") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(orderedChannels, key = { "ch-" + it.id }) { channel ->
                            ChannelBubble(
                                channel = channel,
                                hasNew = channel.id in recentChannelIds,
                                onClick = { onOpenChannel(channel) }
                            )
                        }
                    }
                }
            }

            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SubsFilter.values().toList(), key = { it.name }) { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option.label) }
                        )
                    }
                }
            }

            if (loading && videos.isEmpty() && shorts.isEmpty()) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (shownShorts.isNotEmpty() && filter != SubsFilter.SHORTS) {
                item(key = "shorts-title") { SectionTitle("Shorts") }
                item(key = "shorts-row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(shownShorts, key = { "sr-" + it.id }) { short ->
                            ShortCard(short, Modifier.width(150.dp)) { onOpenShort(short) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (filter == SubsFilter.SHORTS) {
                items(shownShorts.chunked(2), key = { "sg-" + it.first().id }) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { short ->
                            ShortCard(short, Modifier.weight(1f)) { onOpenShort(short) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            items(shownVideos, key = { "v-" + it.id }) { video ->
                SubsVideoCard(video = video, onPlay = { onPlay(video) }, onWatchLater = { onWatchLater(video) })
            }

            if (loadingMore) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (!loading && shownVideos.isEmpty() && shownShorts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No hay publicaciones para este filtro. Desliza hacia abajo para actualizar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp)
    )
}

@Composable
private fun ChannelBubble(channel: ChannelItem, hasNew: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(68.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = channel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A30))
            )
            if (hasNew) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3EA6FF))
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                )
            }
        }
        Text(
            channel.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ShortCard(video: VideoItem, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C22))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color(0xCC000000)
                    )
                )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(
                video.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (video.viewCountText.isNotBlank()) {
                Text(video.viewCountText, color = Color(0xFFDDDDDD), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SubsVideoCard(video: VideoItem, onPlay: () -> Unit, onWatchLater: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(bottom = 14.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF17171B))) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val badge = when {
                video.isLive -> "EN VIVO"
                video.durationMs > 0L -> formatSubsDuration(video.durationMs)
                else -> ""
            }
            if (badge.isNotBlank()) {
                Text(
                    badge,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            if (video.isLive) Color(0xFFD32F2F) else Color(0xCC000000),
                            RoundedCornerShape(5.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Row(Modifier.padding(start = 12.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = video.channelThumbnailUrl,
                contentDescription = video.channelTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2A2A30))
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    video.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(video.channelTitle, video.viewCountText, video.publishedAt)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Guardar en Ver más tarde") },
                        leadingIcon = { Icon(Icons.Default.WatchLater, null) },
                        onClick = {
                            menu = false
                            onWatchLater()
                        }
                    )
                }
            }
        }
    }
}

private fun isPublishedToday(published: String): Boolean {
    val lower = published.lowercase()
    return lower.contains("segundo") || lower.contains("minuto") || lower.contains("hora") ||
        Regex("""\d+\s*(h|min)\b""").containsMatchIn(lower)
}

private fun formatSubsDuration(ms: Long): String {
    val total = ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
