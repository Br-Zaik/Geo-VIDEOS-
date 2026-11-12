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
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.geovideos.app.network.HomeChip
import com.geovideos.app.playback.DownloadStreamOption
import com.geovideos.app.playback.FloatingPlayerService
import com.geovideos.app.playback.GeoPlayerConnection
import com.geovideos.app.playback.StreamOptions
import com.geovideos.app.playback.AudioTrackOption
import com.geovideos.app.playback.PlaybackExtras
import com.geovideos.app.playback.StoryboardInfo
import com.geovideos.app.playback.VideoChapter
import coil.imageLoader
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbDownOffAlt
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.geovideos.app.playback.enqueueResolvedMediaDownload
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class PlayerSettingsPage { ROOT, QUALITY, QUALITY_ADVANCED, SPEED, AUDIO, DOUBLE_TAP, CHAPTERS }

/**
 * Reproductor unificado con gesto directo, inspirado en el comportamiento de YouTube/DayliTube.
 *
 * - El mismo PlayerView permanece montado al expandir y minimizar.
 * - El desplazamiento vertical sigue exactamente el dedo.
 * - La velocidad del gesto decide el destino, evitando esperas y rebotes artificiales.
 * - Durante el arrastre no se recalculan sombras ni esquinas en cada fotograma.
 */
internal data class PlayerExtras(
    val isSubscribed: Boolean = false,
    val relatedChips: List<HomeChip> = emptyList(),
    val selectedRelatedChip: String = "",
    val onSelectChip: (HomeChip) -> Unit = {},
    val onToggleSubscribe: () -> Unit = {},
    val onOpenComments: () -> Unit = {},
    val queueActive: Boolean = false,
    val queueTitle: String = "",
    val queueSubtitle: String = "",
    val queuePosition: Int = 0,
    val queueCount: Int = 0,
    val onOpenQueue: () -> Unit = {},
    /** Siguiente / anterior conociendo la cola y los relacionados (null = usar el reproductor). */
    val onNext: (() -> Unit)? = null,
    val onPrevious: (() -> Boolean)? = null,
    val repeatOne: Boolean = false,
    val onRepeatOneChange: (Boolean) -> Unit = {}
)

private fun formatSeekLabel(ms: Long): String {
    val total = ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val sec = total % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

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
    onMessage: (String) -> Unit,
    extras: PlayerExtras = PlayerExtras()
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
    val autoFloating by playerConnection.autoFloatingOnLeave.collectAsStateWithLifecycle()

    var fullscreen by rememberSaveable(video.id) { mutableStateOf(false) }
    var playerControlsVisible by remember(video.id) { mutableStateOf(true) }
    var screenLocked by rememberSaveable(video.id) { mutableStateOf(false) }
    var showPlayerSettings by rememberSaveable(video.id) { mutableStateOf(false) }
    var playerSettingsPage by rememberSaveable(video.id) { mutableStateOf(PlayerSettingsPage.ROOT) }
    var selectedSpeed by rememberSaveable(video.id) { mutableStateOf(1f) }
    var showDownloadSheet by rememberSaveable(video.id) { mutableStateOf(false) }
    var streamOptions by remember(video.id) { mutableStateOf<StreamOptions?>(null) }
    var streamOptionsVideoId by remember(video.id) { mutableStateOf("") }
    var streamOptionsHaveSizes by remember(video.id) { mutableStateOf(false) }
    var streamOptionsLoading by remember(video.id) { mutableStateOf(false) }
    var streamOptionsError by remember(video.id) { mutableStateOf<String?>(null) }
    var selectedDownloadHeight by rememberSaveable(video.id) { mutableStateOf<Int?>(null) }
    var pendingDownload by remember(video.id) { mutableStateOf<DownloadStreamOption?>(null) }
    var downloadTarget by remember(video.id) { mutableStateOf(video) }
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
    // Mini reproductor 2025: se mueve a una esquina, cambia de tamaño (doble toque)
    // y se esconde en un borde (pausando el video), como YouTube.
    var miniRight by rememberSaveable { mutableStateOf(true) }
    var miniBottom by rememberSaveable { mutableStateOf(true) }
    var miniLarge by rememberSaveable { mutableStateOf(true) }
    var miniHiddenSide by rememberSaveable { mutableIntStateOf(0) }
    var miniDragX by remember { mutableFloatStateOf(0f) }
    var miniDragY by remember { mutableFloatStateOf(0f) }
    var miniControlsVisible by remember(video.id) { mutableStateOf(true) }
    LaunchedEffect(miniControlsVisible, expanded) {
        if (miniControlsVisible && !expanded) {
            delay(3_000L)
            miniControlsVisible = false
        }
    }
    var zoomFeedbackSerial by remember(video.id) { mutableIntStateOf(0) }
    var controlsTouchSerial by remember(video.id) { mutableIntStateOf(0) }
    val doubleTapSeconds by playerConnection.doubleTapSeekSeconds.collectAsStateWithLifecycle()
    var playbackExtras by remember(video.id) { mutableStateOf(PlaybackExtras()) }
    var extrasReloadSerial by remember(video.id) { mutableIntStateOf(0) }
    val chapters = playbackExtras.chapters

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
    // La primera vez que se ve un video se ofrece (una sola vez) el permiso de ventana emergente.
    LaunchedEffect(mainActivity, playback.isPlaying) {
        if (playback.isPlaying && !isInPictureInPictureMode) {
            delay(3_000L)
            mainActivity?.offerFloatingPermission()
        }
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

    // Capítulos, storyboard y pistas de audio: cuando el video ya arrancó (la info está en caché).
    LaunchedEffect(video.id, playback.playbackState == androidx.media3.common.Player.STATE_READY, extrasReloadSerial) {
        if (playback.playbackState != androidx.media3.common.Player.STATE_READY) return@LaunchedEffect
        if (playbackExtras.storyboard != null && extrasReloadSerial == 0) return@LaunchedEffect
        runCatching { playerConnection.playbackExtras(video) }.getOrNull()?.let { playbackExtras = it }
    }

    LaunchedEffect(playerControlsVisible, playback.isPlaying, showPlayerSettings, screenLocked, video.id, controlsTouchSerial) {
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
            video = downloadTarget,
            option = option
        )
        if (downloadId < -1L) {
            onRegisterDownload(
                "${downloadTarget.title} (${option.label})",
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

    fun openDownloadFor(target: VideoItem) {
        downloadTarget = target
        streamOptions = null
        streamOptionsVideoId = ""
        streamOptionsHaveSizes = false
        streamOptionsLoading = false
        streamOptionsError = null
        selectedDownloadHeight = null
        showDownloadSheet = true
    }

    LaunchedEffect(showPlayerSettings, playerSettingsPage, showDownloadSheet, video.id, downloadTarget.id) {
        // Velocidad y ajustes generales son locales y deben abrir al instante. Solo resolver
        // streams cuando el usuario entra realmente a Calidad o a Descargas.
        val needsQualityOptions = showPlayerSettings &&
            (playerSettingsPage == PlayerSettingsPage.QUALITY || playerSettingsPage == PlayerSettingsPage.QUALITY_ADVANCED)
        val dialogOpen = needsQualityOptions || showDownloadSheet
        val needsDownloadSizes = showDownloadSheet
        val optionsTarget = if (showDownloadSheet) downloadTarget else video
        val alreadyLoaded = streamOptions != null &&
            streamOptionsVideoId == optionsTarget.id &&
            (!needsDownloadSizes || streamOptionsHaveSizes)
        if (!dialogOpen || alreadyLoaded || streamOptionsLoading) return@LaunchedEffect

        streamOptionsLoading = true
        streamOptionsError = null
        runCatching {
            playerConnection.streamOptions(
                video = optionsTarget,
                includeDownloadSizes = needsDownloadSizes
            )
        }
            .onSuccess { options ->
                streamOptions = options
                streamOptionsVideoId = optionsTarget.id
                streamOptionsHaveSizes = needsDownloadSizes
                if (showDownloadSheet && selectedDownloadHeight == null) {
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
            if (target >= 0.999f) {
                saveProgress()
                onMinimize()
            } else {
                onExpand()
            }
            dragging = false
            settling = false
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
        // Cuando no es un Mix, el usuario prefirió un mini reproductor grande tipo YouTube,
        // flotante y rectangular, para seguir viendo el feed de abajo. Para Mix se conserva
        // la barra compacta de toda la anchura.
        val compactMini = video.isMix
        val miniMarginPx = with(density) { 10.dp.toPx() }
        val miniBottomClearancePx = with(density) { 82.dp.toPx() }
        val miniWidthPx = if (compactMini) {
            (screenWidthPx - miniMarginPx * 2f).coerceAtLeast(1f)
        } else {
            minOf(
                with(density) { 300.dp.toPx() },
                (screenWidthPx * 0.56f).coerceAtLeast(with(density) { 220.dp.toPx() })
            ) * (if (miniLarge) 1f else 0.72f)
        }
        val miniHeightPx = if (compactMini) {
            with(density) { 62.dp.toPx() }
        } else {
            (miniWidthPx * 9f / 16f).coerceAtLeast(with(density) { 124.dp.toPx() })
        }
        val miniTopClearancePx = with(density) { 96.dp.toPx() }
        val miniHandlePx = with(density) { 30.dp.toPx() }
        fun miniLeftFor(right: Boolean): Float = if (compactMini) {
            miniMarginPx
        } else if (right) {
            (screenWidthPx - miniMarginPx - miniWidthPx).coerceAtLeast(miniMarginPx)
        } else {
            miniMarginPx
        }
        fun miniTopFor(bottom: Boolean): Float = if (compactMini || bottom) {
            (screenHeightPx - miniBottomClearancePx - miniHeightPx).coerceAtLeast(miniMarginPx)
        } else {
            miniTopClearancePx
        }
        val miniLeftPx = miniLeftFor(miniRight)
        val miniTopPx = miniTopFor(miniBottom)
        val miniMediaWidthPx = if (compactMini) {
            (miniHeightPx * 16f / 9f).coerceAtMost(miniWidthPx * 0.34f)
        } else {
            miniWidthPx
        }
        val miniShape = RoundedCornerShape(if (compactMini) 8.dp else 12.dp)
        val velocityThresholdPx = with(density) { 920.dp.toPx() }
        val p = transition.coerceIn(0f, 1f)
        // El player grande y los detalles permanecen montados durante TODO el gesto.
        // En lugar de desaparecer de golpe, el player interpola su rectángulo hasta el
        // mini reproductor y los detalles se deslizan fuera de la pantalla. Al terminar,
        // el mini existente toma el relevo exactamente en las mismas coordenadas.
        val showExpandedSurface = fullscreen || expanded || dragging || settling
        val miniScaleX = (miniWidthPx / screenWidthPx).coerceIn(0.1f, 1f)
        val miniScaleY = (miniHeightPx / fullPlayerHeightPx).coerceIn(0.1f, 1f)
        val playerScaleX = 1f - (1f - miniScaleX) * p
        val playerScaleY = 1f - (1f - miniScaleY) * p
        val playerTranslateX = miniLeftPx * p
        val playerTranslateY = miniTopPx * p

        val dragTravelPx = miniTopPx.coerceAtLeast(with(density) { 220.dp.toPx() })
        val dragState = rememberDraggableState { delta: Float ->
            val canMove = (delta > 0f && transition < 1f) || (delta < 0f && transition > 0f)
            if (canMove && !settling && !fullscreen) {
                transition = (transition + delta / dragTravelPx).coerceIn(0f, 1f)
            }
        }

        val dragModifier = Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            enabled = !fullscreen && !settling,
            onDragStarted = { _: Offset -> dragging = true },
            onDragStopped = { velocity: Float ->
                if (transition < 0.05f && velocity <= -velocityThresholdPx * 0.6f) {
                    // Deslizar hacia arriba en el video: pantalla completa.
                    transition = 0f
                    dragging = false
                    fullscreen = true
                    return@draggable
                }
                val target = when {
                    velocity >= velocityThresholdPx -> 1f
                    velocity <= -velocityThresholdPx -> 0f
                    transition >= 0.42f -> 1f
                    else -> 0f
                }
                settle(target, fast = abs(velocity) >= velocityThresholdPx)
            }
        )

        // En pantalla completa: deslizar hacia abajo para salir, como YouTube.
        var fullscreenDragTotal by remember { mutableFloatStateOf(0f) }
        val fullscreenExitThresholdPx = with(density) { 90.dp.toPx() }
        val fullscreenExitModifier = Modifier.draggable(
            state = rememberDraggableState { delta: Float -> fullscreenDragTotal += delta },
            orientation = Orientation.Vertical,
            enabled = fullscreen && !screenLocked,
            onDragStarted = { _: Offset -> fullscreenDragTotal = 0f },
            onDragStopped = { velocity: Float ->
                if (fullscreenDragTotal > fullscreenExitThresholdPx || velocity > velocityThresholdPx * 0.6f) {
                    fullscreen = false
                }
                fullscreenDragTotal = 0f
            }
        )

        if (!fullscreen && showExpandedSurface) {
            // Oscurece el inicio que va apareciendo arriba mientras se baja el video.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = ((1f - p) * 0.7f).coerceIn(0f, 0.7f) }
                    .background(Color.Black)
            )
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Como YouTube: la página baja pegada al video y SIN transparencia
                        // (nada de textos encima del feed); solo al final se desvanece.
                        val videoBottom = playerTranslateY + fullPlayerHeightPx * playerScaleY
                        translationY = (videoBottom - fullPlayerHeightPx).coerceAtLeast(0f)
                        alpha = ((1f - p) / 0.25f).coerceIn(0f, 1f)
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
                        mixAvailable = extras.queueActive,
                        mixCount = extras.queueCount,
                        queueTitle = extras.queueTitle,
                        queueSubtitle = extras.queueSubtitle,
                        queuePosition = extras.queuePosition,
                        isSubscribed = extras.isSubscribed,
                        relatedChips = extras.relatedChips,
                        selectedRelatedChip = extras.selectedRelatedChip
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
                    onDownload = { openDownloadFor(video) },
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
                            onMessage("Ventana emergente activa. Toca el video para ver controles, muévela o cambia su tamaño.")
                        }
                    },
                    onOpenChannel = onOpenChannel,
                    onPlayRelated = onPlayRelated,
                    onSaveRelated = onWatchLaterRelated,
                    onDownloadRelated = { relatedVideo -> openDownloadFor(relatedVideo) },
                    onLoadMore = onLoadMoreRelated,
                    onSelectChip = extras.onSelectChip,
                    onToggleSubscribe = extras.onToggleSubscribe,
                    onOpenComments = extras.onOpenComments,
                    onOpenQueue = extras.onOpenQueue,
                    onSeek = { position ->
                        playerConnection.seekTo(position)
                        onMessage("Saltando a " + formatSeekLabel(position))
                    }
                )
            }
        }

        val miniVisible = !fullscreen && !expanded && !dragging && !settling
        if (miniVisible) {
            val miniAlpha = 1f
            val miniMoveModifier = Modifier
                .pointerInput(miniHiddenSide, miniWidthPx, miniHeightPx, miniRight, miniBottom, screenWidthPx, screenHeightPx) {
                    detectDragGestures(
                        onDragStart = { miniControlsVisible = true },
                        onDragCancel = {
                            miniDragX = 0f
                            miniDragY = 0f
                        },
                        onDragEnd = {
                            val left = miniLeftPx + miniDragX
                            val top = miniTopPx + miniDragY
                            when {
                                left + miniWidthPx * 0.55f > screenWidthPx -> {
                                    miniHiddenSide = 1
                                    playerConnection.pause()
                                    miniDragX = 0f
                                    miniDragY = 0f
                                }
                                left < -miniWidthPx * 0.55f -> {
                                    miniHiddenSide = -1
                                    playerConnection.pause()
                                    miniDragX = 0f
                                    miniDragY = 0f
                                }
                                else -> {
                                    // Se "imanta" a la esquina más cercana con una animación corta.
                                    val right = left + miniWidthPx / 2f > screenWidthPx / 2f
                                    val bottom = top + miniHeightPx / 2f > screenHeightPx / 2f
                                    miniDragX = left - miniLeftFor(right)
                                    miniDragY = top - miniTopFor(bottom)
                                    miniRight = right
                                    miniBottom = bottom
                                    val startX = miniDragX
                                    val startY = miniDragY
                                    scope.launch {
                                        animate(startX, 0f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { value: Float, _: Float ->
                                            miniDragX = value
                                        }
                                    }
                                    scope.launch {
                                        animate(startY, 0f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { value: Float, _: Float ->
                                            miniDragY = value
                                        }
                                    }
                                }
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        if (miniHiddenSide == 0) {
                            miniDragX += dragAmount.x
                            miniDragY += dragAmount.y
                        }
                    }
                }
                .pointerInput(miniHiddenSide) {
                    detectTapGestures(
                        onTap = {
                            when {
                                miniHiddenSide != 0 -> {
                                    miniHiddenSide = 0
                                    playerConnection.play()
                                }
                                !miniControlsVisible -> miniControlsVisible = true
                                else -> settle(0f, fast = true)
                            }
                        },
                        onDoubleTap = { if (miniHiddenSide == 0) miniLarge = !miniLarge }
                    )
                }
            val miniOffsetX = when (miniHiddenSide) {
                1 -> screenWidthPx - miniHandlePx
                -1 -> miniHandlePx - miniWidthPx
                else -> miniLeftPx + miniDragX
            }
            Surface(
                modifier = Modifier
                    .offset { IntOffset(miniOffsetX.roundToInt(), (miniTopPx + miniDragY).roundToInt()) }
                    .width(with(density) { miniWidthPx.toDp() })
                    .height(with(density) { miniHeightPx.toDp() })
                    .zIndex(90f)
                    .graphicsLayer { alpha = miniAlpha }
                    .then(if (compactMini) dragModifier else Modifier),
                shape = miniShape,
                color = Color(0xFF17171B),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                if (compactMini) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(with(density) { miniMediaWidthPx.toDp() })
                                .fillMaxSize()
                                .background(Color.Black)
                                .clickable(enabled = !dragging && !settling) { settle(0f, fast = true) }
                        ) {
                            LiteThumbnail(
                                url = video.thumbnailUrl,
                                description = video.title,
                                modifier = Modifier.fillMaxSize(),
                                widthPx = 480,
                                heightPx = 270,
                                contentScale = ContentScale.Crop
                            )
                            if (
                                p >= 0.94f &&
                                controller != null &&
                                controller?.currentMediaItem?.mediaId == video.id &&
                                !playback.connecting &&
                                !playback.resolving
                            ) {
                                key(floatingSurfaceGeneration, "mini-bar") {
                                    LitePlayerView(
                                        controller = controller!!,
                                        modifier = Modifier.fillMaxSize(),
                                        useController = false,
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                        useTextureView = true,
                                        gesturesEnabled = false
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable(enabled = !dragging && !settling) { settle(0f, fast = true) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                video.title,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                video.channelTitle,
                                color = Color.White.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (playback.isPlaying) playerConnection.pause() else playerConnection.play()
                            },
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(
                                if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                                tint = Color.White,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                saveProgress()
                                onClose()
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar reproductor",
                                tint = Color.White,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        LiteThumbnail(
                            url = video.thumbnailUrl,
                            description = video.title,
                            modifier = Modifier.fillMaxSize(),
                            widthPx = 720,
                            heightPx = 405,
                            contentScale = ContentScale.Crop
                        )
                        if (
                            p >= 0.94f &&
                            controller != null &&
                            controller?.currentMediaItem?.mediaId == video.id &&
                            !playback.connecting &&
                            !playback.resolving
                        ) {
                            key(floatingSurfaceGeneration, "mini-window") {
                                LitePlayerView(
                                    controller = controller!!,
                                    modifier = Modifier.fillMaxSize(),
                                    useController = false,
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                    useTextureView = true,
                                    gesturesEnabled = false
                                )
                            }
                        }
                        // Capa transparente encima del video: mover, tocar y doble toque.
                        Box(modifier = Modifier.fillMaxSize().then(miniMoveModifier))

                        if (miniHiddenSide != 0) {
                            // Escondido en el borde: se ve solo una franja con una flecha.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = if (miniHiddenSide == 1) Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                Icon(
                                    if (miniHiddenSide == 1) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
                                    contentDescription = "Mostrar reproductor",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else {
                            val ended = !playback.isPlaying && progressState.durationMs > 0L &&
                                progressState.positionMs >= progressState.durationMs - 800L
                            val controlsAlpha by animateFloatAsState(
                                targetValue = if (miniControlsVisible || ended) 1f else 0f,
                                animationSpec = tween(220),
                                label = "miniControls"
                            )
                            // Play/pausa arriba a la izquierda (se desvanece) y X arriba a la derecha.
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .graphicsLayer { alpha = controlsAlpha },
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = CircleShape
                            ) {
                                IconButton(
                                    onClick = {
                                        if (ended) {
                                            playerConnection.seekTo(0L)
                                            playerConnection.play()
                                        } else if (playback.isPlaying) {
                                            playerConnection.pause()
                                        } else {
                                            playerConnection.play()
                                        }
                                        miniControlsVisible = true
                                    },
                                    enabled = controlsAlpha > 0.1f,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        when {
                                            ended -> Icons.Default.Replay
                                            playback.isPlaying -> Icons.Default.Pause
                                            else -> Icons.Default.PlayArrow
                                        },
                                        contentDescription = when {
                                            ended -> "Repetir"
                                            playback.isPlaying -> "Pausar"
                                            else -> "Reproducir"
                                        },
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = CircleShape
                            ) {
                                IconButton(
                                    onClick = {
                                        saveProgress()
                                        onClose()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cerrar reproductor",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            // Barra roja de progreso abajo.
                            val fraction = if (progressState.durationMs > 0L) {
                                (progressState.positionMs.toFloat() / progressState.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(3.dp)
                                        .background(Color(0xFFFF0033))
                                )
                            }
                        }
                    }
                }
            }
        }

        val playerLayerModifier = if (fullscreen) {
            Modifier.fillMaxSize().zIndex(100f)
        } else {
            Modifier
                .width(maxWidth)
                .height(with(density) { fullPlayerHeightPx.toDp() })
                .zIndex(50f)
                .graphicsLayer {
                    // Un único player sigue el dedo desde grande hasta el rectángulo mini.
                    // No se cambia tamaño/posición final del mini; solo se interpola hasta él.
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = playerTranslateX
                    translationY = playerTranslateY
                    scaleX = playerScaleX
                    scaleY = playerScaleY
                    alpha = 1f
                    clip = p > 0.02f
                    shape = miniShape
                    shadowElevation = 14.dp.toPx() * p
                }
        }

        if (showExpandedSurface) Box(
            modifier = playerLayerModifier
                .background(Color.Black)
                .then(if (fullscreen) fullscreenExitModifier else dragModifier)
        ) {
            LiteThumbnail(
                url = video.thumbnailUrl,
                description = video.title,
                modifier = Modifier.fillMaxSize(),
                widthPx = 1280,
                heightPx = 720,
                contentScale = if (!fullscreen && p > 0.02f) ContentScale.Crop else ContentScale.Fit
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
                        // Mientras se baja al mini reproductor, el video llena su recuadro
                        // (sin franjas negras), igual que YouTube.
                        resizeMode = if (fullscreen || p > 0.02f) {
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
                                accumulatedSeekSeconds + doubleTapSeconds
                            } else {
                                doubleTapSeconds
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
                        onHoldFeedback = { text ->
                            zoomFeedback = text
                            if (text != null) zoomFeedbackSerial += 1
                        },
                        onSingleTap = {
                            if (!screenLocked && (fullscreen || (expanded && !dragging && !settling))) {
                                playerControlsVisible = !playerControlsVisible
                            }
                        },
                        doubleTapSeekMs = doubleTapSeconds * 1000L,
                        onTwoFingerSwipe = { direction ->
                            // Dos dedos a los lados = capítulo siguiente / anterior (como YouTube).
                            val position = progressState.positionMs
                            if (chapters.isEmpty()) {
                                zoomFeedback = "Este video no tiene capítulos"
                            } else {
                                val target = if (direction > 0) {
                                    chapters.firstOrNull { it.startMs > position + 1_000L }
                                } else {
                                    val currentIndex = chapters.indexOfLast { it.startMs <= position }
                                    val current = chapters.getOrNull(currentIndex)
                                    if (current != null && position - current.startMs > 3_000L) current
                                    else chapters.getOrNull((currentIndex - 1).coerceAtLeast(0))
                                }
                                if (target != null) {
                                    playerConnection.seekTo(target.startMs)
                                    zoomFeedback = (if (direction > 0) "⏭  " else "⏮  ") + target.title
                                } else {
                                    zoomFeedback = "Último capítulo"
                                }
                            }
                            zoomFeedbackSerial += 1
                        },
                        gesturesEnabled = !screenLocked
                    )
                }

                if (
                    playerControlsVisible &&
                    !screenLocked &&
                    !showPlayerSettings &&
                    (fullscreen || (expanded && !dragging && !settling))
                ) {
                    DayliPlayerControls(
                        modifier = Modifier.fillMaxSize().zIndex(20f),
                        isPlaying = playback.isPlaying,
                        positionMs = progressState.positionMs,
                        durationMs = progressState.durationMs,
                        bufferedPercentage = progressState.bufferedPercentage,
                        speed = selectedSpeed,
                        fullscreen = fullscreen,
                        title = video.title,
                        chapters = chapters,
                        storyboard = playbackExtras.storyboard,
                        isLiked = isLiked,
                        isDisliked = isDisliked,
                        isSaved = isWatchLater,
                        onMinimize = {
                            if (fullscreen) fullscreen = false else settle(1f, fast = true)
                        },
                        onTogglePlayback = {
                            if (playback.isPlaying) playerConnection.pause() else playerConnection.play()
                            playerControlsVisible = true
                        },
                        onPrevious = {
                            // Como YouTube: si ya pasaron 3 s, vuelve al inicio; si no, al anterior.
                            if (progressState.positionMs > 3_000L) {
                                playerConnection.seekTo(0L)
                            } else if (extras.onPrevious?.invoke() != true) {
                                playerConnection.playPrevious()
                            }
                            playerControlsVisible = true
                        },
                        onNext = {
                            val handler = extras.onNext
                            if (handler != null) {
                                handler()
                            } else {
                                val index = related.indexOfFirst { it.id == video.id }
                                val nextVideo = (if (index >= 0) related.getOrNull(index + 1) else null)
                                    ?: related.firstOrNull { it.id != video.id }
                                nextVideo?.let(onPlayRelated) ?: playerConnection.playNext()
                            }
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
                        onLock = if (fullscreen) ({ screenLocked = true }) else null,
                        onChapters = {
                            playerSettingsPage = PlayerSettingsPage.CHAPTERS
                            showPlayerSettings = true
                        },
                        onLike = onLike,
                        onDislike = onDislike,
                        onComments = extras.onOpenComments,
                        onSave = onWatchLater,
                        onShare = { shareVideoLite(context, video) },
                        onInteraction = { controlsTouchSerial += 1 }
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
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                    strokeWidth = 2.2.dp
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
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(onClick = { playerConnection.open(video, autoplay, dataSaver, repeat = false) }) {
                                Text("Reintentar")
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("https://www.youtube.com/watch?v=${video.id}")
                                            )
                                        )
                                    }
                                }
                            ) {
                                Text("Abrir en YouTube", color = Color.White)
                            }
                        }
                    }
                }
            }

            if (!screenLocked && showPlayerSettings && (fullscreen || (expanded && !dragging && !settling))) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(29f)
                        .background(Color.Black.copy(alpha = 0.22f))
                        .clickable { showPlayerSettings = false }
                )
                PlayerSettingsOverlay(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .zIndex(30f)
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
                    currentHeight = playback.videoHeight,
                    selectedSpeed = selectedSpeed,
                    autoplay = autoplay,
                    repeatOne = extras.repeatOne,
                    autoFloating = autoFloating,
                    doubleTapSeconds = doubleTapSeconds,
                    qualities = streamOptions?.qualities.orEmpty(),
                    audioTracks = playbackExtras.audioTracks,
                    selectedAudioTrack = playbackExtras.selectedAudioTrackId,
                    chapters = chapters,
                    positionMs = progressState.positionMs,
                    loading = streamOptionsLoading,
                    error = streamOptionsError,
                    onClose = { showPlayerSettings = false },
                    onBack = {
                        playerSettingsPage = if (playerSettingsPage == PlayerSettingsPage.QUALITY_ADVANCED) {
                            PlayerSettingsPage.QUALITY
                        } else {
                            PlayerSettingsPage.ROOT
                        }
                    },
                    onOpenPage = { target -> playerSettingsPage = target },
                    onAutoplayChange = { enabled -> onAutoplayChange(enabled) },
                    onAutoFloatingChange = { enabled ->
                        playerConnection.setAutoFloatingOnLeave(enabled)
                        if (enabled) {
                            if (mainActivity?.canDrawFloatingPlayer() == true) {
                                onMessage("Listo: al salir de la app, el video seguirá en una ventana emergente.")
                            } else {
                                mainActivity?.offerFloatingPermission(force = true)
                            }
                        }
                    },
                    onRepeatOneChange = { enabled ->
                        extras.onRepeatOneChange(enabled)
                        playerConnection.setRepeat(enabled)
                    },
                    onSelectQuality = { height ->
                        playerConnection.selectQuality(video, height, dataSaver)
                        showPlayerSettings = false
                    },
                    onSelectSpeed = { speed ->
                        selectedSpeed = speed
                        playerConnection.setSpeed(speed)
                        showPlayerSettings = false
                    },
                    onSelectAudioTrack = { trackId ->
                        playerConnection.selectAudioTrack(video, trackId, dataSaver)
                        playbackExtras = playbackExtras.copy(selectedAudioTrackId = trackId)
                        showPlayerSettings = false
                        onMessage("Cambiando pista de audio…")
                    },
                    onSelectDoubleTap = { seconds ->
                        playerConnection.setDoubleTapSeekSeconds(seconds)
                        playerSettingsPage = PlayerSettingsPage.ROOT
                    },
                    onSeekChapter = { start ->
                        playerConnection.seekTo(start)
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
                        if (downloadTarget.id == video.id) "Descargar video" else "Descargar: ${downloadTarget.title.take(42)}",
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
    bufferedPercentage: Int,
    speed: Float,
    fullscreen: Boolean,
    title: String,
    chapters: List<VideoChapter>,
    storyboard: StoryboardInfo?,
    isLiked: Boolean,
    isDisliked: Boolean,
    isSaved: Boolean,
    onMinimize: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeed: () -> Unit,
    onMore: () -> Unit,
    onSeek: (Long) -> Unit,
    onFullscreen: () -> Unit,
    onLock: (() -> Unit)?,
    onChapters: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onComments: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onInteraction: () -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = if (safeDuration > 0L) positionMs.coerceIn(0L, safeDuration) else 0L
    var scrubPosition by remember { mutableStateOf<Long?>(null) }
    val shownPosition = scrubPosition ?: safePosition
    val currentChapter = chapters.lastOrNull { it.startMs <= shownPosition }

    // Diseño 2025: sin oscurecer todo el video; solo degradados arriba y abajo.
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(if (fullscreen) 96.dp else 64.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(if (fullscreen) 170.dp else 76.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))))
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = if (fullscreen) 12.dp else 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMinimize, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (fullscreen) "Salir de pantalla completa" else "Minimizar",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            if (fullscreen) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (onLock != null) {
                IconButton(onClick = onLock, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Bloquear pantalla", tint = Color.White, modifier = Modifier.size(21.dp))
                }
            }
            if (speed != 1f) {
                Text(
                    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = onSpeed)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            IconButton(onClick = onMore, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Settings, contentDescription = "Ajustes", tint = Color.White, modifier = Modifier.size(23.dp))
            }
        }

        // Botones centrales redondeados con fondo translúcido (no se oscurece al pausar).
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 52.dp else 30.dp)
        ) {
            RoundPlayerButton(size = 46.dp, onClick = onPrevious) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            RoundPlayerButton(size = if (fullscreen) 72.dp else 62.dp, onClick = onTogglePlayback) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(if (fullscreen) 46.dp else 40.dp)
                )
            }
            RoundPlayerButton(size = 46.dp, onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Siguiente", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = if (fullscreen) 18.dp else 8.dp, vertical = if (fullscreen) 10.dp else 0.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${formatPlayerTime(shownPosition)} / ${formatPlayerTime(safeDuration)}",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
                if (currentChapter != null) {
                    Text(
                        "  •  ${currentChapter.title}  ›",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clickable(onClick = onChapters)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = onFullscreen, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = if (fullscreen) "Salir de pantalla completa" else "Pantalla completa",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val barWidth = maxWidth
                CompactPlayerSeekBar(
                    positionMs = safePosition,
                    durationMs = safeDuration,
                    bufferedPercentage = bufferedPercentage,
                    chapters = chapters,
                    scrubPosition = scrubPosition,
                    enabled = safeDuration > 0L,
                    onScrub = { position ->
                        scrubPosition = position
                        onInteraction()
                    },
                    onSeek = { position ->
                        scrubPosition = null
                        onSeek(position)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                val scrub = scrubPosition
                if (scrub != null && safeDuration > 0L) {
                    val bubbleWidth = if (fullscreen) 176.dp else 132.dp
                    val fraction = (scrub.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                    val x = (barWidth * fraction - bubbleWidth / 2).coerceIn(0.dp, (barWidth - bubbleWidth).coerceAtLeast(0.dp))
                    StoryboardBubble(
                        storyboard = storyboard,
                        positionMs = scrub,
                        chapterTitle = chapters.lastOrNull { it.startMs <= scrub }?.title.orEmpty(),
                        width = bubbleWidth,
                        modifier = Modifier
                            .offset(x = x, y = if (fullscreen) (-118).dp else (-96).dp)
                    )
                }
            }
            if (fullscreen) {
                // Píldoras de acciones en horizontal (diseño 2025).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FullscreenPill(if (isLiked) Icons.Rounded.ThumbUp else androidx.compose.material.icons.Icons.Outlined.ThumbUpOffAlt, "Me gusta", isLiked, onLike)
                    FullscreenPill(if (isDisliked) Icons.Rounded.ThumbDown else androidx.compose.material.icons.Icons.Outlined.ThumbDownOffAlt, "No me gusta", isDisliked, onDislike)
                    FullscreenPill(androidx.compose.material.icons.Icons.Outlined.ChatBubbleOutline, "Comentarios", false, onComments)
                    FullscreenPill(if (isSaved) Icons.Rounded.PlaylistAddCheck else Icons.Rounded.PlaylistAdd, if (isSaved) "Guardado" else "Guardar", isSaved, onSave)
                    FullscreenPill(Icons.Rounded.Share, "Compartir", false, onShare)
                    FullscreenPill(Icons.Rounded.MoreHoriz, "Más", false, onMore)
                }
            }
        }
    }
}

@Composable
private fun RoundPlayerButton(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun FullscreenPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Burbuja con la miniatura del storyboard, el tiempo y el capítulo, como YouTube. */
@Composable
private fun StoryboardBubble(
    storyboard: StoryboardInfo?,
    positionMs: Long,
    chapterTitle: String,
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val frame = storyboard?.frameAt(positionMs)
    val url = frame?.first
    val cache = remember(storyboard) { HashMap<String, ImageBitmap>() }
    var sheet by remember(storyboard) { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        cache[url]?.let { sheet = url to it; return@LaunchedEffect }
        val bitmap = runCatching {
            val request = coil.request.ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(coil.size.Size.ORIGINAL)
                .build()
            val result = context.imageLoader.execute(request)
            ((result as? coil.request.SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        }.getOrNull()
        if (bitmap != null) {
            val image = bitmap.asImageBitmap()
            cache[url] = image
            sheet = url to image
        }
    }
    val ratio = if (storyboard != null && storyboard.frameWidth > 0) {
        storyboard.frameHeight.toFloat() / storyboard.frameWidth.toFloat()
    } else 9f / 16f
    Column(modifier = modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        if (storyboard != null) {
            Canvas(
                modifier = Modifier
                    .width(width)
                    .height(width * ratio)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF202020))
            ) {
                val current = sheet
                if (current != null && current.first == url && frame != null) {
                    val image = current.second
                    val fw = storyboard.frameWidth.coerceAtMost(image.width)
                    val fh = storyboard.frameHeight.coerceAtMost(image.height)
                    val left = frame.second.coerceIn(0, (image.width - fw).coerceAtLeast(0))
                    val top = frame.third.coerceIn(0, (image.height - fh).coerceAtLeast(0))
                    drawImage(
                        image = image,
                        srcOffset = IntOffset(left, top),
                        srcSize = IntSize(fw, fh),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
            }
        }
        if (chapterTitle.isNotBlank()) {
            Text(
                chapterTitle,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            formatPlayerTime(positionMs),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Barra roja de YouTube: capítulos separados por pequeños huecos, parte cargada en gris,
 * se engrosa al arrastrar y solo salta al soltar (así la vista previa no interrumpe el video).
 */
@Composable
private fun CompactPlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedPercentage: Int,
    chapters: List<VideoChapter>,
    scrubPosition: Long?,
    enabled: Boolean,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = Color(0xFFFF0033)
    val bufferedColor = Color.White.copy(alpha = 0.55f)
    val inactiveColor = Color.White.copy(alpha = 0.3f)
    val shown = scrubPosition ?: positionMs
    val fraction = if (durationMs > 0L) (shown.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferedFraction = (bufferedPercentage.coerceIn(0, 100) / 100f).coerceAtLeast(fraction)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnSeek by rememberUpdatedState(onSeek)

    Box(
        modifier = modifier
            .height(30.dp)
            .pointerInput(durationMs, enabled) {
                if (!enabled || durationMs <= 0L) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun positionAt(x: Float): Long {
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        return ((x / width).coerceIn(0f, 1f) * durationMs.toDouble()).toLong()
                    }
                    var last = positionAt(down.position.x)
                    currentOnScrub(last)
                    drag(down.id) { change ->
                        last = positionAt(change.position.x)
                        currentOnScrub(last)
                        change.consume()
                    }
                    currentOnSeek(last)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val centerY = size.height / 2f
            val stroke = if (scrubPosition != null) 5.dp.toPx() else 3.dp.toPx()
            val gap = 2.dp.toPx()
            val width = size.width
            val starts = chapters
                .map { if (durationMs > 0L) (it.startMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f }
                .filter { it > 0.001f && it < 0.999f }
                .distinct()
                .sorted()
            val bounds = listOf(0f) + starts + listOf(1f)
            for (i in 0 until bounds.size - 1) {
                val segStart = bounds[i] * width + if (i == 0) 0f else gap / 2f
                val segEnd = bounds[i + 1] * width - if (i == bounds.size - 2) 0f else gap / 2f
                if (segEnd <= segStart) continue
                fun part(color: Color, upTo: Float) {
                    val end = minOf(segEnd, upTo * width)
                    if (end > segStart) {
                        drawLine(color, Offset(segStart, centerY), Offset(end, centerY), strokeWidth = stroke)
                    }
                }
                part(inactiveColor, 1f)
                part(bufferedColor, bufferedFraction)
                part(activeColor, fraction)
            }
            drawCircle(
                color = activeColor,
                radius = if (scrubPosition != null) 8.dp.toPx() else 6.dp.toPx(),
                center = Offset(width * fraction, centerY)
            )
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
    currentHeight: Int,
    selectedSpeed: Float,
    autoplay: Boolean,
    repeatOne: Boolean,
    autoFloating: Boolean,
    doubleTapSeconds: Int,
    qualities: List<com.geovideos.app.playback.StreamQualityOption>,
    audioTracks: List<AudioTrackOption>,
    selectedAudioTrack: String?,
    chapters: List<VideoChapter>,
    positionMs: Long,
    loading: Boolean,
    error: String?,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onOpenPage: (PlayerSettingsPage) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onAutoFloatingChange: (Boolean) -> Unit,
    onRepeatOneChange: (Boolean) -> Unit,
    onSelectQuality: (Int?) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectAudioTrack: (String) -> Unit,
    onSelectDoubleTap: (Int) -> Unit,
    onSeekChapter: (Long) -> Unit
) {
    val heights = qualities.map { it.height }.distinct().sortedDescending()
    val highest = heights.firstOrNull()
    val saver = heights.filter { it <= 360 }.maxOrNull() ?: heights.lastOrNull()
    val qualityMode = when {
        preferredQuality == null -> "Automática"
        preferredQuality == highest && highest != null -> "Mayor calidad de imagen"
        preferredQuality == saver && saver != null -> "Ahorro de datos"
        else -> "Avanzado"
    }
    val qualityValue = when (preferredQuality) {
        null -> if (currentHeight > 0) "Automática (${currentHeight}p)" else "Automática"
        else -> "${preferredQuality}p"
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xF2212121),
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
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when (page) {
                        PlayerSettingsPage.ROOT -> "Ajustes"
                        PlayerSettingsPage.QUALITY -> "Calidad del video"
                        PlayerSettingsPage.QUALITY_ADVANCED -> "Avanzado"
                        PlayerSettingsPage.SPEED -> "Velocidad de reproducción"
                        PlayerSettingsPage.AUDIO -> "Pista de audio"
                        PlayerSettingsPage.DOUBLE_TAP -> "Doble toque para buscar"
                        PlayerSettingsPage.CHAPTERS -> "Capítulos"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            when (page) {
                PlayerSettingsPage.ROOT -> {
                    PlayerSettingsMenuRow(Icons.Rounded.Tune, "Calidad", qualityValue) { onOpenPage(PlayerSettingsPage.QUALITY) }
                    PlayerSettingsMenuRow(
                        Icons.Rounded.Speed,
                        "Velocidad",
                        if (selectedSpeed == 1f) "Normal" else "${selectedSpeed}x"
                    ) { onOpenPage(PlayerSettingsPage.SPEED) }
                    if (audioTracks.isNotEmpty()) {
                        PlayerSettingsMenuRow(
                            Icons.Rounded.RecordVoiceOver,
                            "Pista de audio",
                            audioTracks.firstOrNull { it.id == selectedAudioTrack }?.label ?: "Original"
                        ) { onOpenPage(PlayerSettingsPage.AUDIO) }
                    }
                    if (chapters.isNotEmpty()) {
                        PlayerSettingsMenuRow(Icons.Rounded.FormatListNumbered, "Capítulos", "${chapters.size}") {
                            onOpenPage(PlayerSettingsPage.CHAPTERS)
                        }
                    }
                    PlayerSettingsMenuRow(Icons.Rounded.TouchApp, "Doble toque para buscar", "$doubleTapSeconds s") {
                        onOpenPage(PlayerSettingsPage.DOUBLE_TAP)
                    }
                    PlayerSettingsToggleRow(Icons.Rounded.RepeatOne, "Repetir video", repeatOne, onRepeatOneChange)
                    PlayerSettingsToggleRow(Icons.Rounded.SlowMotionVideo, "Reproducción automática", autoplay, onAutoplayChange)
                    PlayerSettingsToggleRow(Icons.Rounded.PictureInPictureAlt, "Ventana emergente al salir", autoFloating, onAutoFloatingChange)
                }

                PlayerSettingsPage.QUALITY -> when {
                    loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color.White) }
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    else -> {
                        Text(
                            "Calidad actual: " + qualityValue,
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        InPlayerChoiceRow("Automática (recomendada)", "Se ajusta según tu conexión", qualityMode == "Automática") {
                            onSelectQuality(null)
                        }
                        InPlayerChoiceRow("Mayor calidad de imagen", "Usa más datos" + (highest?.let { " · ${it}p" } ?: ""), qualityMode == "Mayor calidad de imagen") {
                            highest?.let(onSelectQuality)
                        }
                        InPlayerChoiceRow("Ahorro de datos", "Menor calidad de imagen" + (saver?.let { " · ${it}p" } ?: ""), qualityMode == "Ahorro de datos") {
                            saver?.let(onSelectQuality)
                        }
                        InPlayerChoiceRow(
                            "Avanzado",
                            if (qualityMode == "Avanzado") "Elegida: ${preferredQuality}p" else "Elige una resolución específica",
                            qualityMode == "Avanzado",
                            trailing = "›"
                        ) { onOpenPage(PlayerSettingsPage.QUALITY_ADVANCED) }
                    }
                }

                PlayerSettingsPage.QUALITY_ADVANCED -> when {
                    loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = Color.White) }
                    heights.isEmpty() -> Text(
                        "No se encontraron varias calidades para este video.",
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(16.dp)
                    )
                    else -> heights.forEach { height ->
                        InPlayerChoiceRow(
                            label = if (height >= 720) "${height}p  HD" else "${height}p",
                            subtitle = "",
                            selected = preferredQuality == height
                        ) { onSelectQuality(height) }
                    }
                }

                PlayerSettingsPage.SPEED -> {
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                        InPlayerChoiceRow(
                            label = if (speed == 1f) "Normal" else "${speed}x",
                            subtitle = "",
                            selected = selectedSpeed == speed
                        ) { onSelectSpeed(speed) }
                    }
                }

                PlayerSettingsPage.AUDIO -> {
                    audioTracks.forEach { track ->
                        InPlayerChoiceRow(
                            label = track.label,
                            subtitle = if (track.isOriginal) "Idioma original del video" else "Doblaje",
                            selected = track.id == selectedAudioTrack
                        ) { onSelectAudioTrack(track.id) }
                    }
                }

                PlayerSettingsPage.DOUBLE_TAP -> {
                    listOf(5, 10, 15, 20, 30, 60).forEach { seconds ->
                        InPlayerChoiceRow(
                            label = "$seconds segundos",
                            subtitle = if (seconds == 10) "Predeterminado" else "",
                            selected = seconds == doubleTapSeconds
                        ) { onSelectDoubleTap(seconds) }
                    }
                }

                PlayerSettingsPage.CHAPTERS -> {
                    val current = chapters.lastOrNull { it.startMs <= positionMs }
                    chapters.forEach { chapter ->
                        InPlayerChoiceRow(
                            label = chapter.title,
                            subtitle = formatPlayerTime(chapter.startMs),
                            selected = chapter == current
                        ) { onSeekChapter(chapter.startMs) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PlayerSettingsMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f))
        Text(
            value,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 130.dp)
        )
        Text("  ›", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InPlayerChoiceRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    trailing: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(28.dp)) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Elegido", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (trailing.isNotBlank()) Text(trailing, color = Color.White, fontWeight = FontWeight.Bold)
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
