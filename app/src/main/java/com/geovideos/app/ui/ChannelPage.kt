package com.geovideos.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.PlaylistItem
import com.geovideos.app.data.VideoItem
import com.geovideos.app.network.ChannelPost
import com.geovideos.app.network.ChannelShelf
import com.geovideos.app.network.ChannelTabInfo
import com.geovideos.app.network.HomeChip

/**
 * Canal como en YouTube: portada, foto, nombre con verificado, @usuario · suscriptores · videos,
 * descripción, enlaces, píldora Suscribirse / Suscrito con campana, pestañas reales del canal,
 * estanterías de Inicio y, en cada pestaña, el orden (Más recientes / Populares / Más antiguos).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelPage(
    channel: ChannelItem,
    loading: Boolean,
    tabs: List<ChannelTabInfo>,
    tabIndex: Int,
    shelves: List<ChannelShelf>,
    featured: VideoItem?,
    tabVideos: List<VideoItem>,
    tabShorts: List<VideoItem>,
    tabPlaylists: List<PlaylistItem>,
    tabPosts: List<ChannelPost>,
    tabChips: List<HomeChip>,
    selectedChip: String,
    tabLoading: Boolean,
    tabLoadingMore: Boolean,
    tabCanLoadMore: Boolean,
    onBack: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onSelectChip: (HomeChip) -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onSubscribe: () -> Unit,
    onOpenBell: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var descriptionExpanded by rememberSaveable(channel.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val currentTab = tabs.getOrNull(tabIndex)
    val isHome = currentTab?.selected == true && (shelves.isNotEmpty() || featured != null)
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, tabCanLoadMore, tabLoadingMore, tabIndex) {
        if (!isHome && nearEnd && tabCanLoadMore && !tabLoadingMore) onLoadMore()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Volver") }
            Text(
                channel.title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, "${channel.title}\nhttps://www.youtube.com/channel/${channel.id}")
                }
                runCatching { context.startActivity(android.content.Intent.createChooser(intent, "Compartir canal")) }
            }) { Icon(Icons.Rounded.Share, contentDescription = "Compartir canal") }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (channel.bannerUrl.isNotBlank()) {
                item(key = "banner") {
                    AsyncImage(
                        model = channel.bannerUrl,
                        contentDescription = "Portada",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxWidth()
                            .aspectRatio(16f / 4.4f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF202020))
                    )
                }
            }
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = channel.thumbnailUrl,
                            contentDescription = channel.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(76.dp).clip(CircleShape).background(Color(0xFF2A2A30))
                        )
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    channel.title,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (channel.isVerified) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Verificado", tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp))
                                }
                            }
                            val meta = listOfNotNull(
                                channel.handle.takeIf { it.isNotBlank() },
                                channel.subscriberCount.takeIf { it > 0L }?.let { "${channelCount(it)} de suscriptores" },
                                channel.videoCount.takeIf { it > 0L }?.let { "${channelCount(it)} videos" }
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(meta, color = Color(0xFFAAAAAA), fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }
                    if (channel.description.isNotBlank()) {
                        Text(
                            channel.description,
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp,
                            maxLines = if (descriptionExpanded) 40 else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .clickable { descriptionExpanded = !descriptionExpanded }
                        )
                        if (!descriptionExpanded) {
                            Text(
                                "...más",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable { descriptionExpanded = true }
                            )
                        }
                    }
                    if (channel.linksText.isNotBlank()) {
                        Text(
                            channel.linksText,
                            color = Color(0xFF3EA6FF),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    SubscribePill(
                        subscribed = channel.isSubscribed,
                        bellState = channel.bellState,
                        onSubscribe = onSubscribe,
                        onOpenBell = onOpenBell,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                    )
                }
            }

            if (tabs.isNotEmpty()) {
                stickyHeader(key = "tabs") {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 6.dp)) {
                            itemsIndexed(tabs, key = { i, tab -> "tab-$i-${tab.title}" }) { index, tab ->
                                val selected = index == tabIndex
                                Column(
                                    modifier = Modifier
                                        .clickable { if (!selected) onSelectTab(index) }
                                        .padding(horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        tab.title,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) Color.White else Color(0xFFAAAAAA),
                                        fontSize = 14.5.sp,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                    Box(
                                        Modifier
                                            .height(2.dp)
                                            .width(36.dp)
                                            .background(if (selected) Color.White else Color.Transparent)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Color(0xFF272727))
                    }
                }
            }

            when {
                loading -> item(key = "loading") { LoadingBox() }

                isHome -> {
                    featured?.let { video ->
                        item(key = "featured-${video.id}") {
                            ChannelBigVideo(video, onPlay = { onPlay(video) })
                        }
                    }
                    shelves.forEachIndexed { index, shelf ->
                        item(key = "shelf-title-$index") {
                            Text(
                                shelf.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
                        item(key = "shelf-row-$index") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(shelf.shorts.distinctBy { it.id }, key = { "ss-$index-" + it.id }) { short ->
                                    ChannelShortTile(short, Modifier.width(140.dp)) { onOpenShort(short) }
                                }
                                items(shelf.videos.distinctBy { it.id }, key = { "sv-$index-" + it.id }) { video ->
                                    ChannelShelfVideo(video) { onPlay(video) }
                                }
                                items(shelf.playlists.distinctBy { it.id }, key = { "sp-$index-" + it.id }) { playlist ->
                                    ChannelShelfPlaylist(playlist) { onOpenPlaylist(playlist) }
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (tabChips.isNotEmpty()) {
                        item(key = "chips") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(tabChips.distinctBy { it.title }, key = { "chip-" + it.title }) { chip ->
                                    val selected = chip.token == selectedChip
                                    Box(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) Color.White else Color(0xFF272727))
                                            .clickable { onSelectChip(chip) }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            chip.title,
                                            color = if (selected) Color.Black else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (tabLoading) {
                        item(key = "tab-loading") { LoadingBox() }
                    } else {
                        if (tabShorts.isNotEmpty()) {
                            // Shorts del canal en cuadrícula de 3 columnas, como YouTube.
                            items(tabShorts.distinctBy { it.id }.chunked(3), key = { "sg-" + it.first().id }) { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    row.forEach { short -> ChannelShortTile(short, Modifier.weight(1f)) { onOpenShort(short) } }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        items(tabVideos.distinctBy { it.id }, key = { "tv-" + it.id }) { video ->
                            ChannelVideoRow(video) { onPlay(video) }
                        }
                        items(tabPlaylists.distinctBy { it.id }, key = { "tp-" + it.id }) { playlist ->
                            ChannelPlaylistRow(playlist) { onOpenPlaylist(playlist) }
                        }
                        items(tabPosts.distinctBy { it.id }, key = { "post-" + it.id }) { post ->
                            ChannelPostCard(post, channel)
                        }
                        if (tabVideos.isEmpty() && tabShorts.isEmpty() && tabPlaylists.isEmpty() && tabPosts.isEmpty()) {
                            item(key = "tab-empty") {
                                Text(
                                    "Este canal no tiene contenido en esta pestaña.",
                                    color = Color(0xFFAAAAAA),
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }
                        if (tabLoadingMore) {
                            item(key = "tab-more") {
                                Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Píldora de suscripción de YouTube; con suscripción muestra la campana y abre sus opciones. */
@Composable
internal fun SubscribePill(
    subscribed: Boolean,
    bellState: Int,
    onSubscribe: () -> Unit,
    onOpenBell: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(if (subscribed) Color(0xFF272727) else Color.White)
            .clickable { if (subscribed) onOpenBell() else onSubscribe() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (subscribed) {
            Icon(
                when (bellState) {
                    0 -> Icons.Rounded.NotificationsActive
                    2 -> Icons.Rounded.NotificationsOff
                    else -> Icons.Rounded.NotificationsNone
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Suscrito", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        } else {
            Text("Suscribirse", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun ChannelBigVideo(video: VideoItem, onPlay: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 14.dp, vertical = 10.dp)) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF202020))
        )
        Text(video.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        val meta = listOf(video.viewCountText, video.publishedAt).filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) Text(meta, color = Color(0xFFAAAAAA), fontSize = 12.sp)
    }
}

@Composable
private fun ChannelShelfVideo(video: VideoItem, onPlay: () -> Unit) {
    Column(Modifier.width(250.dp).clickable(onClick = onPlay)) {
        Box {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF202020))
            )
            DurationBadge(video, Modifier.align(Alignment.BottomEnd))
        }
        Text(video.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        val meta = listOf(video.viewCountText, video.publishedAt).filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) Text(meta, color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ChannelVideoRow(video: VideoItem, onPlay: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(168.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF202020))
            )
            DurationBadge(video, Modifier.align(Alignment.BottomEnd))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(video.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val meta = listOf(video.viewCountText, video.publishedAt).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = Color(0xFFAAAAAA), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun DurationBadge(video: VideoItem, modifier: Modifier) {
    val label = when {
        video.isLive -> "EN VIVO"
        video.durationMs > 0L -> {
            val total = video.durationMs / 1000L
            val h = total / 3600L
            val m = (total % 3600L) / 60L
            val sec = total % 60L
            if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
        }
        else -> ""
    }
    if (label.isBlank()) return
    Text(
        label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (video.isLive) Color(0xFFFF0033) else Color(0xCC000000))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

@Composable
private fun ChannelShortTile(video: VideoItem, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF202020))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(6.dp)
        ) {
            Text(video.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (video.viewCountText.isNotBlank()) Text(video.viewCountText, color = Color(0xFFDDDDDD), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ChannelShelfPlaylist(playlist: PlaylistItem, onClick: () -> Unit) {
    Column(Modifier.width(220.dp).clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF202020))
            )
            if (playlist.itemCount > 0) {
                Text(
                    "${playlist.itemCount} videos",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Text(playlist.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ChannelPlaylistRow(playlist: PlaylistItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = playlist.thumbnailUrl,
            contentDescription = playlist.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(168.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF202020))
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(playlist.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (playlist.itemCount > 0) Text("${playlist.itemCount} videos", color = Color(0xFFAAAAAA), fontSize = 12.sp)
            Text("Ver la lista completa", color = Color(0xFFAAAAAA), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChannelPostCard(post: ChannelPost, channel: ChannelItem) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF2A2A30))
            )
            Text(
                listOf(channel.title, post.published).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (post.text.isNotBlank()) Text(post.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        if (post.imageUrl.isNotBlank()) {
            AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
            )
        }
        if (post.likes.isNotBlank()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(post.likes, fontSize = 12.sp, color = Color(0xFFAAAAAA), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

private fun channelCount(value: Long): String = when {
    value >= 1_000_000L -> String.format("%.1f M", value / 1_000_000.0).replace(".0 ", " ")
    value >= 1_000L -> String.format("%.1f K", value / 1_000.0).replace(".0 ", " ")
    else -> value.toString()
}
