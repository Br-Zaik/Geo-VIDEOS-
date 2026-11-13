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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.PlaylistItem
import com.geovideos.app.data.VideoItem
import com.geovideos.app.network.SearchEntry

/**
 * Búsqueda como YouTube: cuadro con historial y sugerencias mientras escribes,
 * "Mostrando resultados de…", filtros, y resultados mezclados en el orden de YouTube
 * (videos grandes, canales con Suscribirse, listas, filas de Shorts y estanterías).
 */
@Composable
internal fun SearchPage(
    modifier: Modifier,
    currentQuery: String,
    entries: List<SearchEntry>,
    filter: Int,
    suggestions: List<String>,
    history: List<String>,
    corrected: String,
    loading: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    subscribedIds: Set<String>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onFilter: (Int) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onSubscribe: (ChannelItem) -> Unit,
    onOpenBell: (ChannelItem) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(currentQuery) }
    var typing by rememberSaveable { mutableStateOf(currentQuery.isBlank()) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (typing) runCatching { focus.requestFocus() } }

    fun submit(value: String) {
        val clean = value.trim()
        if (clean.isBlank()) return
        text = clean
        typing = false
        focusManager.clearFocus()
        onSearch(clean)
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Volver", tint = Color.White) }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF272727))
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text("Buscar en YouTube", color = Color(0xFFAAAAAA), fontSize = 15.sp)
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            typing = true
                            onQueryChange(it)
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(text) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .clickable { typing = true }
                    )
                }
                if (text.isNotEmpty()) {
                    IconButton(onClick = {
                        text = ""
                        typing = true
                        onQueryChange("")
                        runCatching { focus.requestFocus() }
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Borrar", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            IconButton(onClick = { submit(text) }) { Icon(Icons.Rounded.Search, contentDescription = "Buscar", tint = Color.White) }
        }

        if (typing || (entries.isEmpty() && !loading && currentQuery.isBlank())) {
            // Historial (con reloj) primero y luego sugerencias de YouTube, como la app oficial.
            val matchingHistory = history.filter { text.isBlank() || it.contains(text.trim(), true) }.take(if (text.isBlank()) 15 else 4)
            val shownSuggestions = suggestions.filterNot { s -> matchingHistory.any { it.equals(s, true) } }
            LazyColumn(Modifier.fillMaxSize()) {
                items(matchingHistory, key = { "h-$it" }) { item ->
                    SuggestionRow(Icons.Rounded.History, item, onClick = { submit(item) }, onFill = {
                        text = "$item "
                        onQueryChange(text)
                    }, onRemove = { onRemoveHistory(item) })
                }
                if (text.isNotBlank()) {
                    items(shownSuggestions, key = { "s-$it" }) { item ->
                        SuggestionRow(Icons.Rounded.Search, item, onClick = { submit(item) }, onFill = {
                            text = "$item "
                            onQueryChange(text)
                        }, onRemove = null)
                    }
                }
            }
            return@Column
        }

        val listState = rememberLazyListState()
        val nearEnd by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
            }
        }
        LaunchedEffect(nearEnd, canLoadMore, loadingMore, entries.size) {
            if (nearEnd && canLoadMore && !loadingMore && !loading) onLoadMore()
        }
        LaunchedEffect(currentQuery, filter) { if (entries.isEmpty()) listState.scrollToItem(0) }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(SEARCH_FILTERS, key = { i, _ -> "f$i" }) { index, option ->
                        val selected = index == filter
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color.White else Color(0xFF272727))
                                .clickable { onFilter(index) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(option.first, color = if (selected) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (corrected.isNotBlank()) {
                item(key = "corrected") {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text("Mostrando resultados de", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                        Text(corrected, color = Color(0xFF3EA6FF), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.clickable { submit(corrected) })
                    }
                }
            }
            if (loading && entries.isEmpty()) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                }
            } else if (entries.isEmpty()) {
                item(key = "empty") {
                    Text("No se encontraron resultados.", color = Color(0xFFAAAAAA), modifier = Modifier.padding(24.dp))
                }
            }
            itemsIndexed(entries, key = { index, entry -> "e$index-" + entryKey(entry) }) { _, entry ->
                when (entry) {
                    is SearchEntry.Video -> SearchVideoCard(entry.video, onPlay = { onPlay(entry.video) }, onWatchLater = { onWatchLater(entry.video) })
                    is SearchEntry.Channel -> SearchChannelRow(
                        channel = entry.channel,
                        subscribed = entry.channel.id in subscribedIds || entry.channel.isSubscribed,
                        onOpen = { onOpenChannel(entry.channel) },
                        onSubscribe = { onSubscribe(entry.channel) },
                        onOpenBell = { onOpenBell(entry.channel) }
                    )
                    is SearchEntry.Playlist -> SearchPlaylistCard(entry.playlist, isMix = entry.mix != null) {
                        val mix = entry.mix
                        if (mix != null) onPlay(mix) else onOpenPlaylist(entry.playlist)
                    }
                    is SearchEntry.Shorts -> Column(Modifier.padding(vertical = 8.dp)) {
                        Text("Shorts", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(entry.shorts, key = { "sh-" + it.id }) { short ->
                                SearchShortTile(short) { onOpenShort(short) }
                            }
                        }
                    }
                    is SearchEntry.Shelf -> Column(Modifier.padding(vertical = 8.dp)) {
                        Text(entry.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(entry.videos, key = { "sv-" + it.id }) { video ->
                                Column(Modifier.width(250.dp).clickable { onPlay(video) }) {
                                    SearchThumb(video, Modifier.fillMaxWidth())
                                    Text(video.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                    Text(listOf(video.channelTitle, video.viewCountText).filter { it.isNotBlank() }.joinToString(" · "), color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            if (loadingMore) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                }
            }
        }
    }
}

private fun entryKey(entry: SearchEntry): String = when (entry) {
    is SearchEntry.Video -> "v" + entry.video.id
    is SearchEntry.Channel -> "c" + entry.channel.id
    is SearchEntry.Playlist -> "p" + entry.playlist.id
    is SearchEntry.Shorts -> "s" + entry.shorts.firstOrNull()?.id.orEmpty()
    is SearchEntry.Shelf -> "h" + entry.videos.firstOrNull()?.id.orEmpty()
}

@Composable
private fun SuggestionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    onFill: () -> Unit,
    onRemove: (() -> Unit)?
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        )
        if (onRemove != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones", tint = Color(0xFFAAAAAA)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Quitar del historial de búsqueda") }, onClick = {
                        menu = false
                        onRemove()
                    })
                }
            }
        }
        IconButton(onClick = onFill) { Icon(Icons.Rounded.NorthWest, contentDescription = "Usar esta búsqueda", tint = Color.White) }
    }
}

@Composable
private fun SearchThumb(video: VideoItem, modifier: Modifier) {
    Box(modifier) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF202020))
        )
        val badge = when {
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
        if (badge.isNotBlank()) {
            Text(
                badge,
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (video.isLive) Color(0xFFFF0033) else Color(0xCC000000))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun SearchVideoCard(video: VideoItem, onPlay: () -> Unit, onWatchLater: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(bottom = 14.dp)) {
        SearchThumb(video, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        Row(Modifier.padding(start = 12.dp, top = 10.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = video.channelThumbnailUrl,
                contentDescription = video.channelTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2A2A30))
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(video.title, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(video.channelTitle, video.viewCountText, video.publishedAt).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (com.geovideos.app.data.NewBadges.showOnVideo(video)) {
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
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Guardar en Ver más tarde") }, onClick = { menu = false; onWatchLater() })
                    DropdownMenuItem(text = { Text("Compartir") }, onClick = { menu = false; shareVideoLite(context, video) })
                }
            }
        }
    }
}

@Composable
private fun SearchChannelRow(
    channel: ChannelItem,
    subscribed: Boolean,
    onOpen: () -> Unit,
    onSubscribe: () -> Unit,
    onOpenBell: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = channel.thumbnailUrl,
            contentDescription = channel.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(88.dp).clip(CircleShape).background(Color(0xFF2A2A30))
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(channel.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                channel.handle.takeIf { it.isNotBlank() },
                channel.subscriberCount.takeIf { it > 0L }?.let { "${searchCount(it)} de suscriptores" }
            ).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 2)
            SubscribePill(
                subscribed = subscribed,
                bellState = channel.bellState,
                onSubscribe = onSubscribe,
                onOpenBell = onOpenBell,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SearchPlaylistCard(playlist: PlaylistItem, isMix: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Box {
            // Tarjetas apiladas detrás, como las listas de YouTube.
            Box(
                Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(Color(0xFF3A3A3A))
            )
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 5.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF202020))
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    if (isMix) "Mix" else if (playlist.itemCount > 0) "${playlist.itemCount} videos" else "Lista",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(playlist.title, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(if (isMix) "Mix · YouTube" else "Lista de reproducción · Ver la lista completa", color = Color(0xFFAAAAAA), fontSize = 12.sp)
    }
}

@Composable
private fun SearchShortTile(video: VideoItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(150.dp)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF202020))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(model = video.thumbnailUrl, contentDescription = video.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(8.dp)
        ) {
            Text(video.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (video.viewCountText.isNotBlank()) Text(video.viewCountText, color = Color(0xFFDDDDDD), fontSize = 11.sp, maxLines = 1)
        }
    }
}

private fun searchCount(value: Long): String = when {
    value >= 1_000_000L -> String.format("%.1f M", value / 1_000_000.0).replace(".0 ", " ")
    value >= 1_000L -> String.format("%.1f K", value / 1_000.0).replace(".0 ", " ")
    else -> value.toString()
}
