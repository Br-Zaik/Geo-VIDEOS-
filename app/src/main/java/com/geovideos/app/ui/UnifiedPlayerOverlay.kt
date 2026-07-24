package com.geovideos.app.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.VideoDetails
import com.geovideos.app.data.VideoItem
import com.geovideos.app.playback.GeoPlayerConnection
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reproductor único para los estados expandido y minimizado.
 *
 * La misma instancia de PlayerView permanece montada durante todo el gesto. Solo se transforma
 * su posición y escala. Esto evita el parpadeo, el cuadro negro y el salto que ocurrían al cambiar
 * entre dos PlayerView distintos.
 */
@Composable
internal fun UnifiedPlayerOverlay(
    video: VideoItem,
    expanded: Boolean,
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
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onWatchLater: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onPlayRelated: (VideoItem) -> Unit,
    onWatchLaterRelated: (VideoItem) -> Unit,
    onLoadMoreRelated: () -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onSavePlayback: (VideoItem, Long, Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivityLite()
    val playback by playerConnection.coreState.collectAsStateWithLifecycle()
    val progressState by playerConnection.progressState.collectAsStateWithLifecycle()
    val controller by playerConnection.controller.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var fullscreen by rememberSaveable(video.id) { mutableStateOf(false) }
    var transition by remember(video.id) { mutableFloatStateOf(if (expanded) 0f else 1f) }
    var dragging by remember(video.id) { mutableStateOf(false) }
    var dragDistancePx by remember(video.id) { mutableFloatStateOf(0f) }
    var settling by remember(video.id) { mutableStateOf(false) }

    val description = details?.description.orEmpty().ifBlank { video.description }
    val channelAvatar = details?.channelThumbnailUrl.orEmpty().ifBlank { video.channelThumbnailUrl }
    val published = details?.publishedAt.orEmpty().ifBlank { video.publishedAt }
    val related = remember(video.id, relatedVideos) { relatedVideos.distinctBy { it.id }.take(30) }

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

    fun settle(target: Float, thresholdCrossed: Boolean = false) {
        if (settling) return
        settling = true
        scope.launch {
            animate(
                initialValue = transition,
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = if (thresholdCrossed) 640f else 760f
                )
            ) { value: Float, _: Float -> transition = value.coerceIn(0f, 1f) }
            dragging = false
            dragDistancePx = 0f
            settling = false
            if (target >= 0.999f) {
                saveProgress()
                onMinimize()
            } else {
                onExpand()
            }
        }
    }

    LaunchedEffect(expanded, video.id) {
        if (!dragging && !settling && !fullscreen) {
            val target = if (expanded) 0f else 1f
            if (abs(transition - target) > 0.001f) {
                animate(
                    initialValue = transition,
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f)
                ) { value: Float, _: Float -> transition = value.coerceIn(0f, 1f) }
            }
        }
    }

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

    BackHandler(enabled = fullscreen || expanded || dragging || settling) {
        when {
            fullscreen -> fullscreen = false
            else -> settle(1f, thresholdCrossed = true)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val fullPlayerHeightPx = screenWidthPx * 9f / 16f
        val miniWidthPx = with(density) { 132.dp.toPx() }
        val miniHeightPx = miniWidthPx * 9f / 16f
        val miniBottomClearancePx = with(density) { 80.dp.toPx() }
        val miniTopPx = (screenHeightPx - miniBottomClearancePx - miniHeightPx).coerceAtLeast(0f)
        val miniScale = (miniWidthPx / screenWidthPx).coerceIn(0.18f, 1f)
        val dragThresholdPx = with(density) { 72.dp.toPx() }

        if (!fullscreen && (expanded || dragging || settling)) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = transition.coerceIn(0f, 1f)
                        alpha = (1f - p * 1.8f).coerceIn(0f, 1f)
                    },
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    NativePlayerDetailsList(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = with(density) { fullPlayerHeightPx.toDp() }),
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
                        relatedLoading = relatedLoading || detailsLoading,
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

        if (!fullscreen && (!expanded || dragging || settling)) {
            Surface(
                modifier = Modifier
                    .offset { IntOffset(0, miniTopPx.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { miniHeightPx.toDp() })
                    .graphicsLayer {
                        val p = transition.coerceIn(0f, 1f)
                        alpha = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    }
                    .pointerInput(video.id, miniTopPx, settling) {
                        if (!settling && miniTopPx > 1f) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    dragDistancePx = 0f
                                },
                                onVerticalDrag = { change: PointerInputChange, dragAmount: Float ->
                                    if (dragAmount < 0f || transition < 1f) {
                                        change.consume()
                                        dragDistancePx += dragAmount
                                        transition = (transition + dragAmount / miniTopPx)
                                            .coerceIn(0f, 1f)
                                    }
                                },
                                onDragEnd = {
                                    val target = if (
                                        dragDistancePx <= -dragThresholdPx || transition < 0.48f
                                    ) 0f else 1f
                                    settle(target, thresholdCrossed = abs(dragDistancePx) >= dragThresholdPx)
                                },
                                onDragCancel = { settle(1f) }
                            )
                        }
                    },
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(with(density) { miniWidthPx.toDp() }))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { settle(0f, thresholdCrossed = true) }
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            video.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            video.channelTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        if (playback.isPlaying) playerConnection.pause() else playerConnection.play()
                    }) {
                        Icon(
                            if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playback.isPlaying) "Pausar" else "Reproducir"
                        )
                    }
                    IconButton(onClick = {
                        saveProgress()
                        onClose()
                    }) {
                        Icon(Icons.Default.Close, "Cerrar")
                    }
                }
            }

            if (progressState.durationMs > 0L) {
                LinearProgressIndicator(
                    progress = {
                        (progressState.positionMs.toFloat() / progressState.durationMs.toFloat())
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                0,
                                (miniTopPx + miniHeightPx - with(density) { 3.dp.toPx() }).roundToInt()
                            )
                        }
                        .fillMaxWidth()
                        .height(3.dp)
                        .graphicsLayer {
                            val p = transition.coerceIn(0f, 1f)
                            alpha = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        }
                )
            }
        }

        val playerLayerModifier = if (fullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .width(maxWidth)
                .height(with(density) { fullPlayerHeightPx.toDp() })
                .graphicsLayer {
                    val p = transition.coerceIn(0f, 1f)
                    translationY = miniTopPx * p
                    scaleX = 1f - ((1f - miniScale) * p)
                    scaleY = 1f - ((1f - miniScale) * p)
                    transformOrigin = TransformOrigin(0f, 0f)
                    clip = p > 0.001f
                    shape = RoundedCornerShape((8f * p).dp)
                    shadowElevation = with(density) { (8f * p).dp.toPx() }
                }
        }

        Box(
            modifier = playerLayerModifier
                .background(Color.Black)
                .clickable(enabled = !fullscreen && !expanded && !dragging && !settling) {
                    settle(0f, thresholdCrossed = true)
                }
                .pointerInput(video.id, fullscreen, miniTopPx, settling) {
                    if (!fullscreen && !settling && miniTopPx > 1f) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragging = true
                                dragDistancePx = 0f
                            },
                            onVerticalDrag = { change: PointerInputChange, dragAmount: Float ->
                                val movingTowardValidAnchor =
                                    (dragAmount > 0f && transition < 1f) ||
                                        (dragAmount < 0f && transition > 0f)
                                if (movingTowardValidAnchor) {
                                    change.consume()
                                    dragDistancePx += dragAmount
                                    transition = (transition + dragAmount / miniTopPx)
                                        .coerceIn(0f, 1f)
                                }
                            },
                            onDragEnd = {
                                val startedExpanded = expanded
                                val target = when {
                                    startedExpanded && dragDistancePx >= dragThresholdPx -> 1f
                                    !startedExpanded && dragDistancePx <= -dragThresholdPx -> 0f
                                    transition >= 0.52f -> 1f
                                    else -> 0f
                                }
                                settle(target, thresholdCrossed = abs(dragDistancePx) >= dragThresholdPx)
                            },
                            onDragCancel = {
                                settle(if (expanded) 0f else 1f)
                            }
                        )
                    }
                }
        ) {
            LiteThumbnail(
                url = video.thumbnailUrl,
                description = video.title,
                modifier = Modifier.fillMaxSize(),
                widthPx = 1280,
                heightPx = 720,
                contentScale = ContentScale.Fit
            )

            if (
                controller != null &&
                controller?.currentMediaItem?.mediaId == video.id &&
                !playback.connecting &&
                !playback.resolving
            ) {
                LitePlayerView(
                    controller = controller!!,
                    modifier = Modifier.fillMaxSize(),
                    useController = fullscreen || (expanded && !dragging && !settling),
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    useTextureView = true
                )
            }

            if ((playback.connecting || playback.resolving) && playback.error == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(34.dp),
                    strokeWidth = 3.dp
                )
            }

            playback.error?.let { message: String ->
                if (fullscreen || (expanded && !dragging && !settling)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.9f))
                            .padding(22.dp),
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
                        ) {
                            Text("Reintentar")
                        }
                    }
                }
            }

            if (fullscreen || (expanded && !dragging && !settling)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (fullscreen) fullscreen = false else settle(1f, thresholdCrossed = true)
                        },
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
                                onClose()
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.42f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                        }
                    }
                }
            }

        }

    }
}
