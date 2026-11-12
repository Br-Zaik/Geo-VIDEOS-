package com.geovideos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geovideos.app.data.VideoItem

/**
 * Panel único de cola y Mix (como el de YouTube): título, posición, repetir,
 * aleatorio, el video actual marcado y carga continua al llegar al final.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    title: String,
    subtitle: String,
    queue: List<VideoItem>,
    currentId: String,
    repeatMode: Int,
    shuffle: Boolean,
    loadingMore: Boolean,
    onPlay: (VideoItem) -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val currentIndex = queue.indexOfFirst { it.id == currentId }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0))
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, queue.size) { if (nearEnd && !loadingMore) onLoadMore() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F0F0F),
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title.ifBlank { "Mix" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            if (subtitle.isNotBlank()) append(subtitle).append(" · ")
                            append("${(currentIndex + 1).coerceAtLeast(1)}/${queue.size}")
                        },
                        fontSize = 13.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Cerrar") }
            }
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRepeat) {
                    Icon(
                        if (repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = when (repeatMode) {
                            1 -> "Repetir lista"
                            2 -> "Repetir este video"
                            else -> "No repetir"
                        },
                        tint = if (repeatMode == 0) Color.White else Color(0xFF3EA6FF)
                    )
                }
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (shuffle) Color(0xFF3EA6FF) else Color.White
                    )
                }
                Spacer(Modifier.weight(1f))
                if (loadingMore) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(14.dp))
                }
            }
            HorizontalDivider(color = Color(0xFF272727))
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                    val current = item.id == currentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (current) Color(0xFF272727) else Color.Transparent)
                            .clickable(enabled = !current) { onPlay(item) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                            if (current) {
                                Icon(Icons.Rounded.VolumeUp, contentDescription = "Sonando", tint = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Text("${index + 1}", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                            }
                        }
                        Box {
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(112.dp)
                                    .height(63.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF202020))
                            )
                            if (item.durationMs > 0L) {
                                Text(
                                    queueDuration(item.durationMs),
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xCC000000))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                item.title,
                                fontSize = 14.sp,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.channelTitle,
                                fontSize = 12.sp,
                                color = Color(0xFFAAAAAA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun queueDuration(ms: Long): String {
    val total = ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
