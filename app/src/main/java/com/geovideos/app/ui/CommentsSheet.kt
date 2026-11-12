package com.geovideos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbUp
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.geovideos.app.data.CommentItem

/**
 * Hoja de comentarios como la de YouTube: orden Destacados / Más recientes,
 * hilos con respuestas, "Me gusta", corazón del creador y carga infinita.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(
    comments: List<CommentItem>,
    totalText: String,
    loading: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    sortIndex: Int,
    sortAvailable: Boolean,
    replies: Map<String, List<CommentItem>>,
    repliesLoading: Set<String>,
    repliesCanLoadMore: Set<String>,
    onSort: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onLoadReplies: (CommentItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val listState = rememberLazyListState()
    // Respuestas abiertas (se pueden ocultar sin volver a pedirlas).
    var expanded by remember { mutableStateOf(setOf<String>()) }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, canLoadMore, loadingMore, comments.size) {
        if (nearEnd && canLoadMore && !loadingMore && !loading) onLoadMore()
    }
    LaunchedEffect(sortIndex) { listState.scrollToItem(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F0F0F),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF5A5A5A))
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Comentarios", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                if (totalText.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(totalText.filter { it.isDigit() || it == '.' || it == ',' || it == 'K' || it == 'M' || it == ' ' }.trim().ifBlank { totalText },
                        fontSize = 15.sp, color = Color(0xFFAAAAAA))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
            }
            if (sortAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SortPill("Destacados", sortIndex == 0) { onSort(0) }
                    SortPill("Más recientes", sortIndex == 1) { onSort(1) }
                }
            }
            HorizontalDivider(color = Color(0xFF272727))

            when {
                loading && comments.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
                comments.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("Los comentarios están desactivados o no se pudieron cargar.", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                }
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    items(comments, key = { it.id }) { comment ->
                        CommentRow(comment, isReply = false)
                        val open = comment.id in expanded
                        val loaded = replies[comment.id].orEmpty()
                        if (comment.replyCount > 0 && comment.repliesToken.isNotBlank()) {
                            RepliesToggle(
                                text = if (open) "Ocultar respuestas"
                                else if (comment.replyCount == 1) "1 respuesta" else "${comment.replyCount} respuestas",
                                loading = comment.id in repliesLoading && loaded.isEmpty()
                            ) {
                                if (open) {
                                    expanded = expanded - comment.id
                                } else {
                                    expanded = expanded + comment.id
                                    if (loaded.isEmpty()) onLoadReplies(comment)
                                }
                            }
                        }
                        if (open) {
                            loaded.forEach { reply -> CommentRow(reply, isReply = true) }
                            if (comment.id in repliesCanLoadMore) {
                                RepliesToggle("Mostrar más respuestas", comment.id in repliesLoading) { onLoadReplies(comment) }
                            }
                        }
                    }
                    if (loadingMore) {
                        item(key = "loading-more") {
                            Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color(0xFF272727))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CommentRow(comment: CommentItem, isReply: Boolean) {
    var showAll by rememberSaveable(comment.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 56.dp else 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp)
    ) {
        AsyncImage(
            model = comment.authorThumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(if (isReply) 26.dp else 36.dp)
                .clip(CircleShape)
                .background(Color(0xFF303030))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (comment.isPinned) {
                Text("📌 Fijado", fontSize = 11.5.sp, color = Color(0xFFAAAAAA))
            }
            Text(
                text = buildString {
                    append(comment.author.ifBlank { "Usuario" })
                    if (comment.publishedAt.isNotBlank()) append(" · ").append(comment.publishedAt)
                },
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = comment.text,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = if (showAll) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).clickable { showAll = !showAll }
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ThumbUp, contentDescription = null, tint = Color(0xFFDDDDDD), modifier = Modifier.size(15.dp))
                if (comment.likeCount > 0L) {
                    Spacer(Modifier.width(6.dp))
                    Text(shortCount(comment.likeCount), fontSize = 12.sp, color = Color(0xFFAAAAAA))
                }
                if (comment.creatorHeart) {
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Default.Favorite, contentDescription = "Le encanta al creador", tint = Color(0xFFFF4E45), modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun RepliesToggle(text: String, loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 62.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Color(0xFF3EA6FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (loading) {
            Spacer(Modifier.width(10.dp))
            CircularProgressIndicator(color = Color(0xFF3EA6FF), strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        }
    }
}

private fun shortCount(value: Long): String = when {
    value >= 1_000_000L -> String.format("%.1f M", value / 1_000_000.0).replace(".0 ", " ")
    value >= 1_000L -> String.format("%.1f K", value / 1_000.0).replace(".0 ", " ")
    else -> value.toString()
}
