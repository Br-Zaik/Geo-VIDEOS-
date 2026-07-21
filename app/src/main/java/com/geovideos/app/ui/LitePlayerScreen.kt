package com.geovideos.app.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch

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
    var dismissing by remember(video.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val dismissThresholdPx = with(density) { 64.dp.toPx() }
    val dismissTargetPx = with(density) { configuration.screenHeightDp.dp.toPx() * 0.72f }
    val dragProgress = (dragOffset / dismissThresholdPx).coerceIn(0f, 1f)
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

    fun settleBack() {
        if (dismissing) return
        val start = dragOffset
        scope.launch {
            animate(
                initialValue = start,
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f)
            ) { value, _ -> dragOffset = value }
        }
    }

    fun minimizeAnimated() {
        if (fullscreen) {
            fullscreen = false
            return
        }
        if (dismissing) return
        dismissing = true
        val start = dragOffset
        scope.launch {
            animate(
                initialValue = start,
                targetValue = dismissTargetPx,
                animationSpec = tween(durationMillis = 145)
            ) { value, _ -> dragOffset = value }
            saveProgress()
            onBack()
        }
    }

    BackHandler { minimizeAnimated() }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (fullscreen) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!fullscreen) {
                        translationY = dragOffset
                        val scale = 1f - (0.035f * dragProgress)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (0.10f * dragProgress)
                    }
                }
        ) {
        val playerModifier = if (fullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        }

        Box(
            modifier = playerModifier
                .pointerInput(video.id, fullscreen, dismissing) {
                    if (!fullscreen && !dismissing) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, amount ->
                                if (amount > 0f || dragOffset > 0f) {
                                    change.consume()
                                    dragOffset = (dragOffset + amount).coerceIn(0f, dismissTargetPx)
                                }
                            },
                            onDragEnd = {
                                if (dragOffset >= dismissThresholdPx) minimizeAnimated() else settleBack()
                            },
                            onDragCancel = { settleBack() }
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
                    onClick = ::minimizeAnimated,
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
            NativePlayerDetailsList(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                header = PlayerHeaderData(
                    video = video,
                    details = details,
                    isLiked = isLiked,
                    isDisliked = isDisliked,
                    isWatchLater = isWatchLater,
                    description = description,
                    channelAvatar = channelAvatar,
                    publishedAt = published
                ),
                related = related,
                relatedLoading = relatedLoading,
                relatedLoadingMore = relatedLoadingMore,
                relatedCanLoadMore = relatedCanLoadMore,
                onLike = onLike,
                onDislike = onDislike,
                onWatchLater = onWatchLater,
                onShare = { shareVideoLite(context, video) },
                onOpenChannel = onOpenChannel,
                onPlayRelated = onPlayRelated,
                onSaveRelated = onWatchLaterRelated,
                onLoadMore = onLoadMoreRelated
            )
        }
        }
    }
}
