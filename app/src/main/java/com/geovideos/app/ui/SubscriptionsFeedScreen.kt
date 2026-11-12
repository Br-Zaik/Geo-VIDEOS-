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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsOff
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
import androidx.compose.ui.graphics.graphicsLayer
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
    LIVE("En vivo"),
    CONTINUE("Continuar viendo"),
    UNWATCHED("No vistos")
}

private enum class ChannelsOrder(val label: String) {
    RELEVANT("Más relevantes"),
    ACTIVITY("Actividad nueva"),
    AZ("A-Z")
}

/**
 * Pestaña Suscripciones como en YouTube: barra de canales con punto azul de contenido nuevo
 * (y aro rojo si está en vivo), tocar un canal filtra el feed, "Todos" abre la lista completa
 * con 3 órdenes, filtros arriba, Shorts y luego videos.
 */
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
    onWatchLater: (VideoItem) -> Unit,
    guide: List<ChannelItem> = emptyList(),
    filterChannel: ChannelItem? = null,
    filterVideos: List<VideoItem> = emptyList(),
    filterShorts: List<VideoItem> = emptyList(),
    filterLoading: Boolean = false,
    history: List<VideoItem> = emptyList(),
    onSelectChannel: (ChannelItem?) -> Unit = {},
    onOpenBell: (ChannelItem) -> Unit = {}
) {
    var filter by rememberSaveable { mutableStateOf(SubsFilter.ALL) }
    var showAllChannels by rememberSaveable { mutableStateOf(false) }

    if (showAllChannels) {
        AllChannelsList(
            modifier = modifier,
            channels = channels,
            guide = guide,
            recentVideos = videos,
            onBack = { showAllChannels = false },
            onOpenChannel = onOpenChannel,
            onOpenBell = onOpenBell
        )
        return
    }

    // La barra usa el orden de la guía de YouTube; si no llegó, primero los que publicaron.
    val orderedChannels = remember(channels, videos, guide) {
        if (guide.isNotEmpty()) {
            guide
        } else {
            val byId = channels.associateBy { it.id }
            val active = videos.map { it.channelId }.filter { it.isNotBlank() }.distinct().mapNotNull { byId[it] }
            (active + channels).distinctBy { it.id }.take(60)
        }
    }
    val recentChannelIds = remember(videos) {
        videos.filter { isPublishedToday(it.publishedAt) }.map { it.channelId }.toSet()
    }
    val historyById = remember(history) { history.associateBy { it.id } }
    val baseVideos = if (filterChannel != null) filterVideos else videos
    val baseShorts = if (filterChannel != null) filterShorts else shorts
    val shownVideos = remember(baseVideos, filter, historyById) {
        when (filter) {
            SubsFilter.ALL, SubsFilter.VIDEOS -> baseVideos
            SubsFilter.TODAY -> baseVideos.filter { isPublishedToday(it.publishedAt) }
            SubsFilter.LIVE -> baseVideos.filter { it.isLive || it.publishedAt.contains("vivo", true) }
            SubsFilter.CONTINUE -> baseVideos.filter { (historyById[it.id]?.resumePositionMs ?: 0L) > 5_000L }
            SubsFilter.UNWATCHED -> baseVideos.filter { it.id !in historyById }
            SubsFilter.SHORTS -> emptyList()
        }
    }
    val shownShorts = when (filter) {
        SubsFilter.ALL, SubsFilter.TODAY, SubsFilter.SHORTS -> baseShorts
        SubsFilter.UNWATCHED -> baseShorts.filter { it.id !in historyById }
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
    LaunchedEffect(nearEnd, canLoadMore, loadingMore, filterChannel?.id) {
        if (filterChannel == null && nearEnd && canLoadMore && !loadingMore) onLoadMore()
    }

    PullToRefreshBox(
        isRefreshing = loading && videos.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (orderedChannels.isNotEmpty()) {
                item(key = "channels") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(orderedChannels, key = { "ch-" + it.id }) { channel ->
                                ChannelBubble(
                                    channel = channel,
                                    hasNew = channel.hasNewContent || (guide.isEmpty() && channel.id in recentChannelIds),
                                    selected = filterChannel?.id == channel.id,
                                    dimmed = filterChannel != null && filterChannel.id != channel.id,
                                    onClick = { onSelectChannel(channel) }
                                )
                            }
                        }
                        Text(
                            "Todos",
                            color = Color(0xFF3EA6FF),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { showAllChannels = true }
                                .padding(horizontal = 14.dp, vertical = 18.dp)
                        )
                    }
                }
            }

            if (filterChannel != null) {
                item(key = "filter-channel") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            filterChannel.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Ver canal",
                            color = Color(0xFF3EA6FF),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onOpenChannel(filterChannel) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SubsFilter.values().toList(), key = { it.name }) { option ->
                        SubsChip(option.label, filter == option) { filter = option }
                    }
                }
            }

            if ((loading && videos.isEmpty() && shorts.isEmpty()) || (filterLoading && filterVideos.isEmpty())) {
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
                SubsVideoCard(
                    video = video,
                    resumeFraction = historyById[video.id]?.let { saved ->
                        val duration = maxOf(saved.durationMs, video.durationMs)
                        if (duration > 0L) (saved.resumePositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    } ?: 0f,
                    onPlay = { onPlay(video) },
                    onWatchLater = { onWatchLater(video) }
                )
            }

            if (loadingMore && filterChannel == null) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (!loading && !filterLoading && shownVideos.isEmpty() && shownShorts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        when (filter) {
                            SubsFilter.CONTINUE -> "No hay videos empezados de tus suscripciones."
                            SubsFilter.UNWATCHED -> "Ya viste todo lo nuevo. ¡Bien!"
                            else -> "No hay publicaciones para este filtro. Desliza hacia abajo para actualizar."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubsChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color(0xFF272727))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** "Todos": la lista completa de canales con 3 órdenes y la campana de cada uno. */
@Composable
private fun AllChannelsList(
    modifier: Modifier,
    channels: List<ChannelItem>,
    guide: List<ChannelItem>,
    recentVideos: List<VideoItem>,
    onBack: () -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onOpenBell: (ChannelItem) -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    var order by rememberSaveable { mutableStateOf(ChannelsOrder.RELEVANT) }
    var orderMenu by remember { mutableStateOf(false) }
    val merged = remember(channels, guide) {
        val guideById = guide.associateBy { it.id }
        val base = channels.ifEmpty { guide }
        base.map { channel ->
            val g = guideById[channel.id]
            if (g == null) channel else channel.copy(hasNewContent = g.hasNewContent, isLiveNow = g.isLiveNow)
        }
    }
    val sorted = remember(merged, order, recentVideos, guide) {
        when (order) {
            ChannelsOrder.RELEVANT -> merged
            ChannelsOrder.AZ -> merged.sortedBy { it.title.lowercase() }
            ChannelsOrder.ACTIVITY -> {
                val activity = recentVideos.map { it.channelId }.distinct().withIndex().associate { it.value to it.index }
                val guideOrder = guide.withIndex().associate { it.value.id to it.index }
                merged.sortedWith(
                    compareByDescending<ChannelItem> { it.isLiveNow }
                        .thenByDescending { it.hasNewContent }
                        .thenBy { activity[it.id] ?: Int.MAX_VALUE }
                        .thenBy { guideOrder[it.id] ?: Int.MAX_VALUE }
                )
            }
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
            Text("Todas las suscripciones", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        }
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF272727))
                    .clickable { orderMenu = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(order.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("  ⌄", fontSize = 14.sp)
            }
            DropdownMenu(expanded = orderMenu, onDismissRequest = { orderMenu = false }) {
                ChannelsOrder.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, fontWeight = if (option == order) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            order = option
                            orderMenu = false
                        }
                    )
                }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(sorted, key = { "all-" + it.id }) { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChannel(channel) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        AsyncImage(
                            model = channel.thumbnailUrl,
                            contentDescription = channel.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF2A2A30))
                        )
                        if (channel.hasNewContent) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3EA6FF))
                                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(channel.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(
                            channel.handle.takeIf { it.isNotBlank() },
                            channel.subscriberCount.takeIf { it > 0L }?.let { "${compactSubs(it)} de suscriptores" },
                            if (channel.isLiveNow) "En vivo ahora" else null
                        ).joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(onClick = { onOpenBell(channel) }) {
                        Icon(
                            when (channel.bellState) {
                                0 -> Icons.Default.NotificationsActive
                                2 -> Icons.Default.NotificationsOff
                                else -> Icons.Default.NotificationsNone
                            },
                            contentDescription = "Notificaciones"
                        )
                    }
                }
            }
        }
    }
}

private fun compactSubs(value: Long): String = when {
    value >= 1_000_000L -> String.format("%.1f M", value / 1_000_000.0).replace(".0 ", " ")
    value >= 1_000L -> String.format("%.1f K", value / 1_000.0).replace(".0 ", " ")
    else -> value.toString()
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
private fun ChannelBubble(
    channel: ChannelItem,
    hasNew: Boolean,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick)
            .graphicsLayer { alpha = if (dimmed) 0.45f else 1f },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            val ring = when {
                channel.isLiveNow -> Color(0xFFFF0033)
                selected -> Color.White
                else -> Color.Transparent
            }
            AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = channel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .border(2.dp, ring, CircleShape)
                    .padding(if (ring == Color.Transparent) 0.dp else 3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A30))
            )
            if (channel.isLiveNow) {
                Text(
                    "EN VIVO",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFFF0033))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            channel.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
        // Punto azul debajo del nombre: hay contenido nuevo sin ver (como YouTube).
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (hasNew) Color(0xFF3EA6FF) else Color.Transparent)
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
private fun SubsVideoCard(video: VideoItem, resumeFraction: Float = 0f, onPlay: () -> Unit, onWatchLater: () -> Unit) {
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
            if (resumeFraction > 0.02f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.35f))
                ) {
                    Box(Modifier.fillMaxWidth(resumeFraction).height(3.dp).background(Color(0xFFFF0033)))
                }
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
                if (video.isNew) {
                    Text(
                        "Nuevo",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(Color(0xFF3A3A42), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
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
