package com.geovideos.app.ui

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.zIndex
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
import com.geovideos.app.MainActivity
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.VideoDetails
import com.geovideos.app.data.VideoItem
import com.geovideos.app.playback.DownloadStreamOption
import com.geovideos.app.playback.FloatingPlayerService
import com.geovideos.app.playback.GeoPlayerConnection
import com.geovideos.app.playback.StreamOptions
import com.geovideos.app.playback.enqueueResolvedMediaDownload
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class PlayerSettingsPage { ROOT, QUALITY, SPEED }

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
    isInPictureInPictureMode: Boolean = false,
    fullscreenRequestToken: Int = 0,
    isWatchLater: Boolean,
    isLiked: Boolean,
    isDisliked: Boolean,
    autoplay: Boolean,
    dataSaver: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
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
    val audioOnly by playerConnection.audioOnlyMode.collectAsStateWithLifecycle()
    val floatingSurfaceGeneration by FloatingPlayerService.surfaceGeneration.collectAsStateWithLifecycle()

    var fullscreen by rememberSaveable(video.id) { mutableStateOf(false) }
    var playerControlsVisible by remember(video.id) { mutableStateOf(true) }
    var screenLocked by rememberSaveable(video.id) { mutableStateOf(false) }
    var showPlayerSettings by rememberSaveable(video.id) { mutableStateOf(false) }
    var playerSettingsPage by rememberSaveable(video.id) { mutableStateOf(PlayerSettingsPage.ROOT) }
    var selectedSpeed by rememberSaveable(video.id) { mutableStateOf(1f) }
    var showDownloadSheet by rememberSaveable(video.id) { mutableStateOf(false) }
    var streamOptions by remember(video.id) { mutableStateOf<StreamOptions?>(null) }
    var streamOptionsHaveSizes by remember(video.id) { mutableStateOf(false) }
    var streamOptionsLoading by remember(video.id) { mutableStateOf(false) }
    var streamOptionsError by remember(video.id) { mutableStateOf<String?>(null) }
    var selectedDownloadHeight by rememberSaveable(video.id) { mutableStateOf<Int?>(null) }
    var pendingDownload by remember(video.id) { mutableStateOf<DownloadStreamOption?>(null) }
    var transition by remember(video.id) { mutableFloatStateOf(if (expanded) 0f else 1f) }
    var dragging by remember(video.id) { mutableStateOf(false) }
    var settling by remember(video.id) { mutableStateOf(false) }
    var zoomScale by rememberSaveable(video.id) { mutableFloatStateOf(1f) }
    var seekFeedback by remember(video.id) { mutableStateOf<String?>(null) }
    var seekFeedbackDirection by remember(video.id) { mutableStateOf(0) }
    var seekFeedbackSerial by remember(video.id) { mutableIntStateOf(0) }
    var lastSeekAtMs by remember(video.id) { mutableLongStateOf(0L) }
    var accumulatedSeekSeconds by remember(video.id) { mutableIntStateOf(0) }
    var zoomFeedback by remember(video.id) { mutableStateOf<String?>(null) }
    var zoomFeedbackSerial by remember(video.id) { mutableIntStateOf(0) }

    val description = details?.description.orEmpty().ifBlank { video.description }
    val channelAvatar = details?.channelThumbnailUrl.orEmpty().ifBlank { video.channelThumbnailUrl }
    val published = details?.publishedAt.orEmpty().ifBlank { video.publishedAt }
    val related = remember(video.id, relatedVideos) { relatedVideos.distinctBy { it.id }.take(30) }
    val mainActivity = activity as? MainActivity

    // La ventana emergente propia se activa únicamente con el botón correspondiente.
    // Se desactiva el auto-PiP del sistema para evitar dos ventanas distintas al salir.
    LaunchedEffect(mainActivity, video.id) {
        mainActivity?.setVideoPictureInPictureEnabled(false)
    }
    DisposableEffect(mainActivity, video.id) {
        onDispose { mainActivity?.setVideoPictureInPictureEnabled(false) }
    }

    LaunchedEffect(fullscreenRequestToken) {
        if (fullscreenRequestToken > 0) {
            onExpand()
            fullscreen = true
        }
    }

    LaunchedEffect(seekFeedbackSerial) {
        if (seekFeedbackSerial <= 0) return@LaunchedEffect
        val serial = seekFeedbackSerial
        delay(950L)
        if (serial == seekFeedbackSerial) seekFeedback = null
    }

    LaunchedEffect(zoomFeedbackSerial) {
        if (zoomFeedbackSerial <= 0) return@LaunchedEffect
        val serial = zoomFeedbackSerial
        delay(950L)
        if (serial == zoomFeedbackSerial) zoomFeedback = null
    }

    LaunchedEffect(playerControlsVisible, playback.isPlaying, showPlayerSettings, screenLocked, video.id) {
        if (playerControlsVisible && playback.isPlaying && !showPlayerSettings && !screenLocked) {
            delay(3_200L)
            if (playback.isPlaying && !showPlayerSettings && !screenLocked) {
                playerControlsVisible = false
            }
        }
    }

    LaunchedEffect(playback.isPlaying, video.id) {
        if (!playback.isPlaying && !screenLocked) playerControlsVisible = true
    }

    if (isInPictureInPictureMode) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (controller != null && controller?.currentMediaItem?.mediaId == video.id) {
                key(floatingSurfaceGeneration) {
                    LitePlayerView(
                        controller = controller!!,
                        modifier = Modifier.fillMaxSize(),
                        useController = false,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        useTextureView = true
                    )
                }
            } else {
                LiteThumbnail(
                    url = video.thumbnailUrl,
                    description = video.title,
                    modifier = Modifier.fillMaxSize(),
                    widthPx = 640,
                    heightPx = 360,
                    contentScale = ContentScale.Fit
                )
            }
        }
        return
    }

    fun enqueueOption(option: DownloadStreamOption) {
        val downloadId = enqueueResolvedMediaDownload(
            context = context,
            video = video,
            option = option
        )
        if (downloadId < -1L) {
            onRegisterDownload(
                "${video.title} (${option.label})",
                "geo-download://$downloadId",
                downloadId
            )
            showDownloadSheet = false
            onMessage(
                if (option.requiresMux) {
                    "Descarga ${option.height}p iniciada. Al final recibirás un solo video con audio."
                } else {
                    "Descarga ${option.height}p iniciada."
                }
            )
        } else {
            onMessage("No se pudo iniciar la descarga de esa calidad.")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val option = pendingDownload
        pendingDownload = null
        if (granted && option != null) {
            enqueueOption(option)
        } else if (!granted) {
            onMessage("Android necesita permiso de almacenamiento para descargar en esta versión.")
        }
    }

    val startDownload: (DownloadStreamOption) -> Unit = { option ->
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = option
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            enqueueOption(option)
        }
    }

    LaunchedEffect(showPlayerSettings, showDownloadSheet, video.id) {
        val dialogOpen = showPlayerSettings || showDownloadSheet
        val needsDownloadSizes = showDownloadSheet
        val alreadyLoaded = streamOptions != null && (!needsDownloadSizes || streamOptionsHaveSizes)
        if (!dialogOpen || alreadyLoaded || streamOptionsLoading) return@LaunchedEffect

        streamOptionsLoading = true
        streamOptionsError = null
        runCatching {
            playerConnection.streamOptions(
                video = video,
                includeDownloadSizes = needsDownloadSizes
            )
        }
            .onSuccess { options ->
                streamOptions = options
                streamOptionsHaveSizes = needsDownloadSizes
                if (selectedDownloadHeight == null) {
                    selectedDownloadHeight = options.downloads
                        .firstOrNull { it.height == preferredQuality }
                        ?.height
                        ?: options.downloads.maxByOrNull { it.height }?.height
                }
            }
            .onFailure {
                streamOptionsError = "No se pudieron obtener las calidades disponibles. Revisa tu conexión."
            }
        streamOptionsLoading = false
    }

    LaunchedEffect(showDownloadSheet, preferredQuality, streamOptions) {
        if (!showDownloadSheet) return@LaunchedEffect
        val downloads = streamOptions?.downloads.orEmpty()
        selectedDownloadHeight = downloads
            .firstOrNull { it.height == preferredQuality }
            ?.height
            ?: selectedDownloadHeight
            ?: downloads.maxByOrNull { it.height }?.height
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
            transition = 0f
            dragging = false
            settling = false
            onExpand()
            current.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(current.window, false)
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            screenLocked = false
            restorePortrait()
        }
    }

    DisposableEffect(Unit) {
        onDispose { restorePortrait() }
    }

    BackHandler(enabled = showPlayerSettings || fullscreen || expanded || dragging || settling || screenLocked) {
        when {
            screenLocked -> screenLocked = false
            showPlayerSettings && playerSettingsPage != PlayerSettingsPage.ROOT ->
                playerSettingsPage = PlayerSettingsPage.ROOT
            showPlayerSettings -> showPlayerSettings = false
            fullscreen -> fullscreen = false
            else -> settle(1f, fast = true)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Capture the BoxWithConstraints dimensions before entering nested layout scopes.
        // This avoids resolving maxHeight against another implicit receiver in older Compose versions.
        val availableMaxHeight = maxHeight
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { availableMaxHeight.toPx() }
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
                        qualityLabel = preferredQuality?.let { "${it}p" } ?: "Automático",
                        audioOnly = audioOnly,
                        mixAvailable = related.isNotEmpty()
                    ),
                    related = related,
                    relatedLoading = relatedLoading || detailsLoading,
                    relatedLoadingMore = relatedLoadingMore,
                    relatedCanLoadMore = relatedCanLoadMore,
                    onLike = onLike,
                    onDislike = onDislike,
                    onWatchLater = onWatchLater,
                    onShare = { shareVideoLite(context, video) },
                    onQuality = {
                        playerSettingsPage = PlayerSettingsPage.ROOT
                        showPlayerSettings = true
                    },
                    onDownload = { showDownloadSheet = true },
                    onToggleAudioOnly = {
                        playerConnection.setAudioOnly(
                            video = video,
                            enabled = !audioOnly,
                            autoplay = autoplay,
                            dataSaver = dataSaver
                        )
                        onMessage(
                            if (audioOnly) "Modo video activado." else "Modo música activado. El audio seguirá en segundo plano."
                        )
                    },
                    onPictureInPicture = {
                        if (mainActivity == null) {
                            onMessage("No se pudo abrir la ventana emergente.")
                        } else if (mainActivity.openFloatingPlayer(video, dataSaver)) {
                            settle(1f, fast = true)
                            onMessage("Ventana emergente activa. Toca el video para ver controles, muevela o cambia su tamano.")
                        } else {
                            onMessage("Activa Mostrar sobre otras apps. Al regresar, la ventana se abrirá automáticamente.")
                        }
                    },
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
                    .zIndex(70f)
                    .graphicsLayer {
                        alpha = ((p - 0.82f) / 0.18f).coerceIn(0f, 1f)
                    }
                    .then(dragModifier),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
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
                        .zIndex(73f)
                        .graphicsLayer {
                            alpha = ((p - 0.82f) / 0.18f).coerceIn(0f, 1f)
                        }
                )
            }
        }

        val playerLayerModifier = if (fullscreen) {
            Modifier.fillMaxSize().zIndex(100f)
        } else {
            Modifier
                .width(maxWidth)
                .height(with(density) { fullPlayerHeightPx.toDp() })
                .zIndex(if (p >= 0.82f) 72f else 50f)
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
                .then(if (fullscreen) Modifier else dragModifier)
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
                key(floatingSurfaceGeneration, fullscreen) {
                    LitePlayerView(
                        controller = controller!!,
                        modifier = Modifier.fillMaxSize(),
                        // Media3's stock controller was the reason the player still looked like
                        // the old version. Keep only the video surface and draw Geo Videos'
                        // DayliTube-style controls in Compose on top of it.
                        useController = false,
                        resizeMode = if (fullscreen) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        },
                        useTextureView = true,
                        zoomScale = zoomScale,
                        onZoomScaleChange = { zoomScale = it },
                        onSeekBy = { deltaMs ->
                            val direction = if (deltaMs >= 0L) 1 else -1
                            val now = SystemClock.uptimeMillis()
                            accumulatedSeekSeconds = if (
                                direction == seekFeedbackDirection && now - lastSeekAtMs <= 1_100L
                            ) {
                                accumulatedSeekSeconds + 10
                            } else {
                                10
                            }
                            seekFeedbackDirection = direction
                            lastSeekAtMs = now
                            seekFeedback = if (direction > 0) {
                                "+$accumulatedSeekSeconds segundos"
                            } else {
                                "−$accumulatedSeekSeconds segundos"
                            }
                            seekFeedbackSerial += 1
                        },
                        onZoomFeedback = { percent ->
                            zoomFeedback = "Zoom $percent%"
                            zoomFeedbackSerial += 1
                        },
                        onSingleTap = {
                            if (!screenLocked && (fullscreen || (p <= 0.01f && !dragging && !settling))) {
                                playerControlsVisible = !playerControlsVisible
                            }
                        },
                        gesturesEnabled = !screenLocked
                    )
                }

                if (
                    playerControlsVisible &&
                    !screenLocked &&
                    (fullscreen || (p <= 0.01f && !dragging && !settling))
                ) {
                    DayliPlayerControls(
                        modifier = Modifier.fillMaxSize(),
                        isPlaying = playback.isPlaying,
                        positionMs = progressState.positionMs,
                        durationMs = progressState.durationMs,
                        qualityLabel = preferredQuality?.let { "${it}p" }
                            ?: playback.videoHeight.takeIf { it > 0 }?.let { "Automático (${it}p)" }
                            ?: "Automático",
                        speed = selectedSpeed,
                        fullscreen = fullscreen,
                        onMinimize = {
                            if (fullscreen) fullscreen = false else settle(1f, fast = true)
                        },
                        onTogglePlayback = {
                            if (playback.isPlaying) playerConnection.pause() else playerConnection.play()
                            playerControlsVisible = true
                        },
                        onNext = {
                            related.firstOrNull { it.id != video.id }?.let(onPlayRelated)
                                ?: playerConnection.playNext()
                            playerControlsVisible = true
                        },
                        onQuality = {
                            playerSettingsPage = PlayerSettingsPage.QUALITY
                            showPlayerSettings = true
                            playerControlsVisible = true
                        },
                        onSpeed = {
                            playerSettingsPage = PlayerSettingsPage.SPEED
                            showPlayerSettings = true
                            playerControlsVisible = true
                        },
                        onMore = {
                            playerSettingsPage = PlayerSettingsPage.ROOT
                            showPlayerSettings = true
                            playerControlsVisible = true
                        },
                        onSeek = { position ->
                            playerConnection.seekTo(position)
                            playerControlsVisible = true
                        },
                        onFullscreen = {
                            fullscreen = !fullscreen
                            playerControlsVisible = true
                        },
                        onLock = if (fullscreen) ({ screenLocked = true }) else null
                    )
                }

                if (fullscreen && screenLocked) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .clickable { screenLocked = false },
                        color = Color.Black.copy(alpha = 0.62f),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = "Desbloquear controles",
                            tint = Color.White,
                            modifier = Modifier.padding(14.dp).size(28.dp)
                        )
                    }
                }

                seekFeedback?.let { message ->
                    Surface(
                        modifier = Modifier
                            .align(if (seekFeedbackDirection > 0) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(horizontal = 22.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            message,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }

                zoomFeedback?.let { message ->
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(
                            message,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
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

            if (!screenLocked && showPlayerSettings && (fullscreen || (p <= 0.01f && !dragging && !settling))) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                        .clickable { showPlayerSettings = false }
                )
                PlayerSettingsOverlay(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 8.dp)
                        .width(286.dp)
                        .heightIn(
                            max = if (fullscreen) {
                                availableMaxHeight - 56.dp
                            } else {
                                with(density) { fullPlayerHeightPx.toDp() } - 56.dp
                            }
                        ),
                    page = playerSettingsPage,
                    preferredQuality = preferredQuality,
                    selectedSpeed = selectedSpeed,
                    autoplay = autoplay,
                    qualities = streamOptions?.qualities.orEmpty(),
                    loading = streamOptionsLoading,
                    error = streamOptionsError,
                    onClose = { showPlayerSettings = false },
                    onBack = { playerSettingsPage = PlayerSettingsPage.ROOT },
                    onOpenQuality = { playerSettingsPage = PlayerSettingsPage.QUALITY },
                    onOpenSpeed = { playerSettingsPage = PlayerSettingsPage.SPEED },
                    onAutoplayChange = { enabled ->
                        onAutoplayChange(enabled)
                    },
                    onSelectQuality = { height ->
                        playerConnection.selectQuality(video, height, dataSaver)
                        showPlayerSettings = false
                    },
                    onSelectSpeed = { speed ->
                        selectedSpeed = speed
                        playerConnection.setSpeed(speed)
                        showPlayerSettings = false
                    }
                )
            }

            // Los controles aparecen al tocar el video mediante PlayerView.
            // El engranaje abre el selector de calidad dentro del propio video.
        }
    }

    if (showDownloadSheet) {
        val downloadOptions = streamOptions?.downloads.orEmpty()
        val selectedDownload = downloadOptions.firstOrNull { it.height == selectedDownloadHeight }
            ?: downloadOptions.maxByOrNull { it.height }

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
                    "Escoge cualquier calidad disponible. En HD la app descargará video y audio y los unirá automáticamente.",
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
                    downloadOptions.isEmpty() -> Text(
                        "No se encontraron calidades descargables para este video.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                    else -> {
                        downloadOptions.sortedByDescending { it.height }.forEachIndexed { index, option ->
                            if (index > 0) HorizontalDivider()
                            DownloadQualityChoiceRow(
                                option = option,
                                selected = option.height == selectedDownload?.height,
                                onClick = { selectedDownloadHeight = option.height }
                            )
                        }
                        Button(
                            onClick = { selectedDownload?.let(startDownload) },
                            enabled = selectedDownload != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Text(
                                selectedDownload?.let { "Descargar en ${it.label.removePrefix("Video ")}" }
                                    ?: "Selecciona una calidad",
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                        Text(
                            "Se guardará en Películas/GeoVideos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

}

@Composable
private fun DayliPlayerControls(
    modifier: Modifier,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    qualityLabel: String,
    speed: Float,
    fullscreen: Boolean,
    onMinimize: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onQuality: () -> Unit,
    onSpeed: () -> Unit,
    onMore: () -> Unit,
    onSeek: (Long) -> Unit,
    onFullscreen: () -> Unit,
    onLock: (() -> Unit)?
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = if (safeDuration > 0L) positionMs.coerceIn(0L, safeDuration) else 0L

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.34f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMinimize, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (fullscreen) "Salir de pantalla completa" else "Minimizar",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
            if (onLock != null) {
                IconButton(onClick = onLock, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Bloquear controles",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onQuality, modifier = Modifier.height(42.dp)) {
                Text(
                    qualityLabel,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onSpeed, modifier = Modifier.height(42.dp)) {
                Text(
                    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onMore, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Más opciones",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            IconButton(onClick = onTogglePlayback, modifier = Modifier.size(76.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(62.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Siguiente",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                formatPlayerTime(safePosition),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(48.dp)
            )
            Slider(
                value = safePosition.toFloat(),
                onValueChange = { value ->
                    if (safeDuration > 0L) onSeek(value.toLong().coerceIn(0L, safeDuration))
                },
                valueRange = if (safeDuration > 0L) 0f..safeDuration.toFloat() else 0f..1f,
                enabled = safeDuration > 0L,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatPlayerTime(safeDuration),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 6.dp)
            )
            IconButton(onClick = onFullscreen, modifier = Modifier.size(42.dp)) {
                Icon(
                    if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (fullscreen) "Salir de pantalla completa" else "Pantalla completa",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3_600L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun PlayerControlIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(38.dp)
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.28f),
            shape = CircleShape,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

@Composable
private fun PlayerSettingsOverlay(
    modifier: Modifier,
    page: PlayerSettingsPage,
    preferredQuality: Int?,
    selectedSpeed: Float,
    autoplay: Boolean,
    qualities: List<com.geovideos.app.playback.StreamQualityOption>,
    loading: Boolean,
    error: String?,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenSpeed: () -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onSelectQuality: (Int?) -> Unit,
    onSelectSpeed: (Float) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xF21B1A1F),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page != PlayerSettingsPage.ROOT) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                } else {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    when (page) {
                        PlayerSettingsPage.ROOT -> "Ajustes"
                        PlayerSettingsPage.QUALITY -> "Calidad"
                        PlayerSettingsPage.SPEED -> "Velocidad"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose) { Text("Cerrar") }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            when (page) {
                PlayerSettingsPage.ROOT -> {
                    PlayerSettingsMenuRow(
                        title = "Calidad",
                        value = preferredQuality?.let { "${it}p" } ?: "Automática",
                        onClick = onOpenQuality
                    )
                    PlayerSettingsMenuRow(
                        title = "Velocidad",
                        value = if (selectedSpeed == 1f) "Normal" else "${selectedSpeed}x",
                        onClick = onOpenSpeed
                    )
                    PlayerSettingsToggleRow(
                        title = "Reproducción automática",
                        checked = autoplay,
                        onCheckedChange = onAutoplayChange
                    )
                }

                PlayerSettingsPage.QUALITY -> when {
                    loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    error != null -> Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                    else -> {
                        InPlayerChoiceRow(
                            label = "Automática",
                            subtitle = "Se adapta a la conexión",
                            selected = preferredQuality == null,
                            onClick = { onSelectQuality(null) }
                        )
                        qualities.forEach { option ->
                            InPlayerChoiceRow(
                                label = option.label,
                                subtitle = if (option.height >= 720) "Alta definición" else "Ahorra datos",
                                selected = preferredQuality == option.height,
                                onClick = { onSelectQuality(option.height) }
                            )
                        }
                        if (qualities.isEmpty()) {
                            Text(
                                "No se encontraron varias calidades para este video.",
                                color = Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                PlayerSettingsPage.SPEED -> {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        InPlayerChoiceRow(
                            label = if (speed == 1f) "Normal" else "${speed}x",
                            subtitle = "",
                            selected = selectedSpeed == speed,
                            onClick = { onSelectSpeed(speed) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, modifier = Modifier.weight(1f))
        Text(
            if (checked) "ON" else "OFF",
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = 8.dp)
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PlayerSettingsMenuRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, modifier = Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = 0.72f))
        Text("  ›", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InPlayerChoiceRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DownloadQualityChoiceRow(
    option: DownloadStreamOption,
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
        Surface(
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(option.label, fontWeight = FontWeight.SemiBold)
            Text(
                buildString {
                    append(
                        if (option.requiresMux) {
                            "Video + audio · se unirán · ${option.extension.uppercase()}"
                        } else {
                            "Audio incluido · ${option.extension.uppercase()}"
                        }
                    )
                    formatDownloadSize(option.estimatedSizeBytes)?.let { append(" · aprox. $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun formatDownloadSize(bytes: Long): String? {
    if (bytes <= 0L) return null
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(java.util.Locale.getDefault(), "%.2f GB", mb / 1024.0)
    } else {
        String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    }
}
