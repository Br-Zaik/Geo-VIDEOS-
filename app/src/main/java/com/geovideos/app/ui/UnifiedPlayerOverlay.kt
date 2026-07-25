package com.geovideos.app.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.VideoDetails
import com.geovideos.app.data.VideoItem
import com.geovideos.app.playback.DownloadStreamOption
import com.geovideos.app.playback.GeoPlayerConnection
import com.geovideos.app.playback.StreamOptions
import com.geovideos.app.playback.enqueueResolvedMediaDownload
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reproductor unificado con gesto directo, inspirado en el comportamiento de YouTube/DayliTube.
 *
 * - El mismo PlayerView permanece montado al expandir y minimizar.
 * - El desplazamiento vertical sigue exactamente el dedo.
 * - La velocidad del gesto decide el destino, evitando esperas y rebotes artificiales.
 * - Durante el arrastre no se recalculan sombras ni esquinas en cada fotograma.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onSavePlayback: (VideoItem, Long, Long) -> Unit,
    onRegisterDownload: (String, String, Long) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivityLite()
    val playback by playerConnection.coreState.collectAsStateWithLifecycle()
    val progressState by playerConnection.progressState.collectAsStateWithLifecycle()
    val controller by playerConnection.controller.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val preferredQuality by playerConnection.preferredQualityHeight.collectAsStateWithLifecycle()

    var fullscreen by rememberSaveable(video.id) { mutableStateOf(false) }
    var showQualitySheet by rememberSaveable(video.id) { mutableStateOf(false) }
    var showDownloadSheet by rememberSaveable(video.id) { mutableStateOf(false) }
    var streamOptions by remember(video.id) { mutableStateOf<StreamOptions?>(null) }
    var streamOptionsLoading by remember(video.id) { mutableStateOf(false) }
    var streamOptionsError by remember(video.id) { mutableStateOf<String?>(null) }
    var pendingDownload by remember(video.id) { mutableStateOf<DownloadStreamOption?>(null) }
    var transition by remember(video.id) { mutableFloatStateOf(if (expanded) 0f else 1f) }
    var dragging by remember(video.id) { mutableStateOf(false) }
    var settling by remember(video.id) { mutableStateOf(false) }

    val description = details?.description.orEmpty().ifBlank { video.description }
    val channelAvatar = details?.channelThumbnailUrl.orEmpty().ifBlank { video.channelThumbnailUrl }
    val published = details?.publishedAt.orEmpty().ifBlank { video.publishedAt }
    val related = remember(video.id, relatedVideos) { relatedVideos.distinctBy { it.id }.take(30) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val option = pendingDownload
        pendingDownload = null
        if (granted && option != null) {
            val downloadId = enqueueResolvedMediaDownload(context, video, option)
            if (downloadId >= 0L) {
                onRegisterDownload("${video.title} (${option.label})", option.uri, downloadId)
                showDownloadSheet = false
            } else {
                onMessage("No se pudo iniciar la descarga de esa calidad.")
            }
        } else if (!granted) {
            onMessage("Android necesita permiso de almacenamiento para descargar en esta versión.")
        }
    }

    val startDownload: (DownloadStreamOption) -> Unit = { option ->
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = option
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            val downloadId = enqueueResolvedMediaDownload(context, video, option)
            if (downloadId >= 0L) {
                onRegisterDownload("${video.title} (${option.label})", option.uri, downloadId)
                showDownloadSheet = false
            } else {
                onMessage("No se pudo iniciar la descarga de esa calidad.")
            }
        }
    }

    LaunchedEffect(showQualitySheet, showDownloadSheet, video.id) {
        if (!(showQualitySheet || showDownloadSheet) || streamOptions != null || streamOptionsLoading) {
            return@LaunchedEffect
        }
        streamOptionsLoading = true
        streamOptionsError = null
        runCatching { playerConnection.streamOptions(video) }
            .onSuccess { streamOptions = it }
            .onFailure {
                streamOptionsError = "No se pudieron obtener las calidades disponibles. Revisa tu conexión."
            }
        streamOptionsLoading = false
    }

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

    fun settle(target: Float, fast: Boolean = false) {
        if (settling) return
        settling = true
        scope.launch {
            val distance = abs(target - transition).coerceIn(0f, 1f)
            val duration = if (fast) {
                (105 + 75 * distance).roundToInt()
            } else {
                (145 + 105 * distance).roundToInt()
            }
            animate(
                initialValue = transition,
                targetValue = target,
                animationSpec = tween(
                    durationMillis = duration.coerceIn(105, 250),
                    easing = FastOutSlowInEasing
                )
            ) { value: Float, _: Float -> transition = value.coerceIn(0f, 1f) }
            dragging = false
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
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
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
        if (fullscreen) fullscreen = false else settle(1f, fast = true)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val fullPlayerHeightPx = screenWidthPx * 9f / 16f
        val miniWidthPx = with(density) { 128.dp.toPx() }
        val miniHeightPx = miniWidthPx * 9f / 16f
        val miniBottomClearancePx = with(density) { 78.dp.toPx() }
        val miniTopPx = (screenHeightPx - miniBottomClearancePx - miniHeightPx).coerceAtLeast(1f)
        val miniScale = (miniWidthPx / screenWidthPx).coerceIn(0.18f, 1f)
        val velocityThresholdPx = with(density) { 920.dp.toPx() }
        val p = transition.coerceIn(0f, 1f)

        val dragState = rememberDraggableState { delta: Float ->
            val canMove = (delta > 0f && transition < 1f) || (delta < 0f && transition > 0f)
            if (canMove && !settling && !fullscreen) {
                transition = (transition + delta / miniTopPx).coerceIn(0f, 1f)
            }
        }

        val dragModifier = Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            enabled = !fullscreen && !settling,
            onDragStarted = { _: Offset -> dragging = true },
            onDragStopped = { velocity: Float ->
                val target = when {
                    velocity >= velocityThresholdPx -> 1f
                    velocity <= -velocityThresholdPx -> 0f
                    transition >= 0.42f -> 1f
                    else -> 0f
                }
                settle(target, fast = abs(velocity) >= velocityThresholdPx)
            }
        )

        if (!fullscreen && p < 0.999f) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = (1f - p * 1.08f).coerceIn(0f, 1f)
                        translationY = with(density) { 22.dp.toPx() } * p
                    },
                color = MaterialTheme.colorScheme.background
            ) {
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
                        publishedAt = published,
                        qualityLabel = preferredQuality?.let { "${it}p" } ?: "Calidad"
                    ),
                    related = related,
                    relatedLoading = relatedLoading || detailsLoading,
                    relatedLoadingMore = relatedLoadingMore,
                    relatedCanLoadMore = relatedCanLoadMore,
                    onLike = onLike,
                    onDislike = onDislike,
                    onWatchLater = onWatchLater,
                    onShare = { shareVideoLite(context, video) },
                    onQuality = { showQualitySheet = true },
                    onDownload = { showDownloadSheet = true },
                    onOpenChannel = onOpenChannel,
                    onPlayRelated = onPlayRelated,
                    onSaveRelated = onWatchLaterRelated,
                    onLoadMore = onLoadMoreRelated
                )
            }
        }

        val miniVisible = !fullscreen && (p > 0.82f || (!expanded && !dragging))
        if (miniVisible) {
            Surface(
                modifier = Modifier
                    .offset { IntOffset(0, miniTopPx.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { miniHeightPx.toDp() })
                    .graphicsLayer {
                        alpha = ((p - 0.82f) / 0.18f).coerceIn(0f, 1f)
                    }
                    .then(dragModifier),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(with(density) { miniWidthPx.toDp() }))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { settle(0f, fast = true) }
                            .padding(horizontal = 10.dp),
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
                            alpha = ((p - 0.82f) / 0.18f).coerceIn(0f, 1f)
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
                    translationY = miniTopPx * p
                    scaleX = 1f - ((1f - miniScale) * p)
                    scaleY = 1f - ((1f - miniScale) * p)
                    transformOrigin = TransformOrigin(0f, 0f)
                    if (p >= 0.97f) {
                        clip = true
                        shape = RoundedCornerShape(8.dp)
                        shadowElevation = with(density) { 6.dp.toPx() }
                    } else {
                        clip = false
                        shadowElevation = 0f
                    }
                }
        }

        Box(
            modifier = playerLayerModifier
                .background(Color.Black)
                .then(dragModifier)
                .clickable(enabled = !fullscreen && p >= 0.98f && !dragging && !settling) {
                    settle(0f, fast = true)
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
                    useController = fullscreen || (p <= 0.01f && !dragging && !settling),
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
                if (fullscreen || (p <= 0.01f && !dragging && !settling)) {
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

            // Los controles aparecen al tocar el video mediante PlayerView.
            // Se elimina la barra superior permanente con X y pantalla completa,
            // para mantener una vista limpia como DayliTube.
        }
    }

    if (showQualitySheet) {
        ModalBottomSheet(onDismissRequest = { showQualitySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HighQuality, contentDescription = null)
                    Text(
                        "Calidad del video",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                Text(
                    "Solo se muestran las resoluciones que este video ofrece realmente.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )
                when {
                    streamOptionsLoading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    streamOptionsError != null -> Text(
                        streamOptionsError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    else -> {
                        QualityChoiceRow(
                            label = "Automática",
                            subtitle = "Se adapta a la conexión",
                            selected = preferredQuality == null,
                            onClick = {
                                playerConnection.selectQuality(video, null, dataSaver)
                                showQualitySheet = false
                            }
                        )
                        streamOptions?.qualities.orEmpty().forEach { option ->
                            HorizontalDivider()
                            QualityChoiceRow(
                                label = option.label,
                                subtitle = if (option.height >= 720) "Alta definición" else "Menor consumo de datos",
                                selected = preferredQuality == option.height,
                                onClick = {
                                    playerConnection.selectQuality(video, option.height, dataSaver)
                                    showQualitySheet = false
                                }
                            )
                        }
                        if (streamOptions?.qualities.isNullOrEmpty()) {
                            Text(
                                "Este video no expone varias calidades seleccionables.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDownloadSheet) {
        ModalBottomSheet(onDismissRequest = { showDownloadSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text(
                        "Descargar video",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                Text(
                    "Elige una calidad disponible. El archivo se guardará en Películas/GeoVideos.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )
                when {
                    streamOptionsLoading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    streamOptionsError != null -> Text(
                        streamOptionsError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    streamOptions?.isLive == true -> Text(
                        "Las transmisiones en vivo no se pueden descargar desde esta pantalla.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                    streamOptions?.downloads.isNullOrEmpty() -> Text(
                        "Este video no ofrece un archivo de video con audio descargable directamente.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                    else -> streamOptions?.downloads.orEmpty().forEachIndexed { index, option ->
                        if (index > 0) HorizontalDivider()
                        DownloadChoiceRow(option = option, onClick = { startDownload(option) })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun QualityChoiceRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (selected) {
            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DownloadChoiceRow(option: DownloadStreamOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Download, contentDescription = null)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(option.label, fontWeight = FontWeight.SemiBold)
            Text(
                "Incluye audio · ${option.extension.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        TextButton(onClick = onClick) { Text("Descargar") }
    }
}
