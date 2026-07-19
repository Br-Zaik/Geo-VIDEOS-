package com.geovideos.app.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.VideoDetails
import com.geovideos.app.data.VideoItem
import com.geovideos.app.playback.GeoPlayerConnection
import kotlin.math.roundToInt

@Composable
internal fun LitePlayerScreen(
    video: VideoItem,
    playerConnection: GeoPlayerConnection,
    isWatchLater: Boolean,
    isLiked: Boolean,
    isDisliked: Boolean,
    autoplay: Boolean,
    dataSaver: Boolean,
    details: VideoDetails?,
    detailsLoading: Boolean,
    relatedVideos: List<VideoItem>,
    relatedLoading: Boolean,
    relatedLoadingMore: Boolean,
    relatedCanLoadMore: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onWatchLater: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onPlayRelated: (VideoItem) -> Unit,
    onPlayNext: () -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onWatchLaterRelated: (VideoItem) -> Unit,
    onLoadMoreRelated: () -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onSavePlayback: (VideoItem, Long, Long) -> Unit,
    onRegisterDownload: (String, String, Long) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivityLite()
    val playback by playerConnection.coreState.collectAsStateWithLifecycle()
    val controller by playerConnection.controller.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable(video.id) { mutableStateOf(false) }
    var dragOffset by remember(video.id) { mutableFloatStateOf(0f) }
    val description = details?.description.orEmpty().ifBlank { video.description }
    val channelAvatar = details?.channelThumbnailUrl.orEmpty().ifBlank { video.channelThumbnailUrl }
    val published = details?.publishedAt.orEmpty().ifBlank { video.publishedAt }
    val related = remember(video.id, relatedVideos) { relatedVideos.distinctBy { it.id }.take(12) }

    fun saveProgress() {
        val active = controller
        onSavePlayback(
            video,
            active?.currentPosition?.coerceAtLeast(0L) ?: video.resumePositionMs,
            active?.duration?.takeIf { it > 0L } ?: video.durationMs
        )
    }

    fun restorePortrait() {
        val current = activity ?: return
        current.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(current.window, true)
        WindowInsetsControllerCompat(current.window, current.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    fun minimize() {
        if (fullscreen) {
            fullscreen = false
        } else {
            saveProgress()
            onBack()
        }
    }

    BackHandler { minimize() }

    LaunchedEffect(fullscreen) {
        val current = activity ?: return@LaunchedEffect
        val insets = WindowInsetsControllerCompat(current.window, current.window.decorView)
        if (fullscreen) {
            current.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(current.window, false)
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            restorePortrait()
        }
    }

    DisposableEffect(Unit) {
        onDispose { restorePortrait() }
    }

    Column(modifier = Modifier.fillMaxSize().background(if (fullscreen) Color.Black else MaterialTheme.colorScheme.background)) {
        val playerModifier = if (fullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        }

        Box(
            modifier = playerModifier
                .offset { IntOffset(0, if (fullscreen) 0 else dragOffset.roundToInt()) }
                .pointerInput(video.id, fullscreen) {
                    if (!fullscreen) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, amount ->
                                if (amount > 0f || dragOffset > 0f) {
                                    dragOffset = (dragOffset + amount).coerceIn(0f, 360f)
                                }
                            },
                            onDragEnd = {
                                if (dragOffset >= 105f) {
                                    saveProgress()
                                    onBack()
                                }
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f }
                        )
                    }
                }
                .background(Color.Black)
        ) {
            LiteThumbnail(
                url = video.thumbnailUrl,
                description = video.title,
                modifier = Modifier.fillMaxSize(),
                widthPx = 960,
                heightPx = 540,
                contentScale = ContentScale.Fit
            )

            if (controller != null && controller?.currentMediaItem?.mediaId == video.id && !playback.connecting && !playback.resolving) {
                LitePlayerView(
                    controller = controller!!,
                    modifier = Modifier.fillMaxSize(),
                    useController = true,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = ::minimize,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.42f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, "Bajar", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { fullscreen = !fullscreen },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.42f), CircleShape)
                ) {
                    Icon(
                        if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        if (fullscreen) "Salir de pantalla completa" else "Pantalla completa",
                        tint = Color.White
                    )
                }
                if (!fullscreen) {
                    IconButton(
                        onClick = {
                            saveProgress()
                            restorePortrait()
                            onClose()
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.42f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                    }
                }
            }

            if ((playback.connecting || playback.resolving) && playback.error == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(34.dp),
                    strokeWidth = 3.dp
                )
            }

            playback.error?.let { message ->
                Column(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.90f)).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(38.dp))
                    Text(
                        message,
                        color = Color.White,
                        modifier = Modifier.padding(top = 10.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = { playerConnection.open(video, autoplay, dataSaver, repeat = false) },
                        modifier = Modifier.padding(top = 12.dp)
                    ) { Text("Reintentar") }
                }
            }
        }

        if (!fullscreen) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                item(key = "lite-player-info-${video.id}", contentType = "info") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            video.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        val metaParts = mutableListOf<String>()
                        details?.viewCount?.takeIf { it > 0L }?.let {
                            metaParts.add("${formatCompactLite(it)} visualizaciones")
                        }
                        formatPublishedLite(published).takeIf { it.isNotBlank() }?.let {
                            metaParts.add(it)
                        }
                        val meta = metaParts.joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(
                                meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 7.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(46.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                LiteThumbnail(
                                    url = channelAvatar,
                                    description = video.channelTitle,
                                    modifier = Modifier.fillMaxSize(),
                                    widthPx = 112,
                                    heightPx = 112,
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(video.channelTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                details?.subscriberCount?.takeIf { it > 0L }?.let {
                                    Text(
                                        "${formatCompactLite(it)} suscriptores",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (video.channelId.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        onOpenChannel(ChannelItem(video.channelId, video.channelTitle, channelAvatar))
                                    }
                                ) { Text("Canal") }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = isLiked,
                                onClick = onLike,
                                label = { Text(details?.likeCount?.takeIf { it > 0L }?.let(::formatCompactLite) ?: "Me gusta") },
                                leadingIcon = { Icon(Icons.Default.ThumbUp, null) }
                            )
                            FilterChip(
                                selected = isDisliked,
                                onClick = onDislike,
                                label = { Text("No me gusta") },
                                leadingIcon = { Icon(Icons.Default.ThumbDown, null) }
                            )
                            AssistChip(
                                onClick = onWatchLater,
                                label = { Text(if (isWatchLater) "Guardado" else "Ver después") },
                                leadingIcon = { Icon(Icons.Default.WatchLater, null) }
                            )
                        }
                        AssistChip(
                            onClick = { shareVideoLite(context, video) },
                            label = { Text("Compartir") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        if (description.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Descripción", fontWeight = FontWeight.Bold)
                                    Text(
                                        description,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "lite-related-title-${video.id}", contentType = "title") {
                    HorizontalDivider()
                    Text(
                        "Videos similares",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }

                if (relatedLoading && related.isEmpty()) {
                    item(key = "lite-related-loading", contentType = "loading") {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        }
                    }
                } else {
                    items(related, key = { "lite-related-${it.id}" }, contentType = { "related" }) { item ->
                        LiteRelatedRow(
                            video = item,
                            onPlay = { onPlayRelated(item) },
                            onSave = { onWatchLaterRelated(item) }
                        )
                        HorizontalDivider()
                    }
                }

                if (relatedCanLoadMore) {
                    item(key = "lite-related-more", contentType = "more") {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            OutlinedButton(onClick = onLoadMoreRelated, enabled = !relatedLoadingMore) {
                                if (relatedLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (relatedLoadingMore) "Cargando…" else "Cargar más")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiteRelatedRow(video: VideoItem, onPlay: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiteThumbnail(
            url = video.thumbnailUrl,
            description = video.title,
            modifier = Modifier.width(160.dp).aspectRatio(16f / 9f).background(Color.Black),
            widthPx = 480,
            heightPx = 270,
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                video.channelTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            formatPublishedLite(video.publishedAt).takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onSave) { Icon(Icons.Default.WatchLater, "Guardar") }
    }
}

private fun formatCompactLite(value: Long): String = when {
    value >= 1_000_000_000L -> "%.1f mil M".format(value / 1_000_000_000.0).replace(".0", "")
    value >= 1_000_000L -> "%.1f M".format(value / 1_000_000.0).replace(".0", "")
    value >= 1_000L -> "%.1f K".format(value / 1_000.0).replace(".0", "")
    else -> value.toString()
}

private fun formatPublishedLite(value: String): String {
    if (value.isBlank()) return ""
    if (value.contains("hace", ignoreCase = true)) return value
    return value.take(10)
}
