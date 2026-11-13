package com.geovideos.app.ui

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Rational
import android.view.LayoutInflater
import android.view.View
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.geovideos.app.R
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.GoogleProfile
import com.geovideos.app.data.MediaKind
import com.geovideos.app.data.NotificationItem
import com.geovideos.app.data.PlaylistItem
import com.geovideos.app.data.VideoItem
import com.geovideos.app.data.VideoDetails
import com.geovideos.app.playback.GeoDownloadStage
import com.geovideos.app.playback.FloatingPlayerService
import com.geovideos.app.playback.GeoPlayerConnection
import com.geovideos.app.playback.cancelResolvedMediaDownload
import com.geovideos.app.playback.openResolvedMediaDownload
import com.geovideos.app.playback.pauseResolvedMediaDownload
import com.geovideos.app.playback.queryResolvedMediaDownload
import com.geovideos.app.playback.resumeResolvedMediaDownload
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

private const val PACKAGE_NAME = "com.geovideos.app"
private const val DEV_SHA1 = "61:39:FF:D0:D5:6B:DC:06:FA:13:AD:3D:7A:88:93:9F:6D:4A:52:7F"

@Composable
fun GeoVideosApp(
    viewModel: GeoVideosViewModel,
    onConnectGoogle: () -> Unit,
    onSwitchGoogleAccount: (String) -> Unit,
    onRequestYouTubeSync: () -> Unit,
    isInPictureInPictureMode: Boolean = false,
    fullscreenRequestToken: Int = 0,
    expandPlayerRequestToken: Int = 0
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // La autenticacion conserva exactamente su flujo original. El reproductor y sus
    // servicios solo se inicializan despues de que la cuenta ya esta conectada.
    if (state.authStatus != AuthStatus.CONNECTED) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            GoogleConnectScreen(
                status = state.authStatus,
                error = state.authError,
                onConnect = onConnectGoogle
            )
        }
        return
    }

    val context = LocalContext.current
    val playerConnection = remember(context.applicationContext) {
        GeoPlayerConnection.get(context.applicationContext)
    }
    val mainStateHolder = rememberSaveableStateHolder()
    val selectedVideo = state.selectedVideo
    LaunchedEffect(expandPlayerRequestToken, selectedVideo?.id) {
        if (expandPlayerRequestToken > 0 && selectedVideo != null) {
            viewModel.expandPlayer()
        }
    }
    val shortPlaybackMode = state.section == MainSection.SHORTS && !state.playerExpanded
    LaunchedEffect(selectedVideo?.id, state.autoplay, state.dataSaver, shortPlaybackMode) {
        selectedVideo?.let { video ->
            playerConnection.open(
                video = video,
                autoplay = if (shortPlaybackMode) true else state.autoplay,
                dataSaver = state.dataSaver,
                repeat = shortPlaybackMode || state.queueRepeat == 2
            )
        }
    }

    LaunchedEffect(playerConnection) {
        playerConnection.mediaTransitionEvents.collect { videoId ->
            viewModel.onPlayerTransition(videoId)
        }
    }

    LaunchedEffect(state.autoplay) {
        playerConnection.setAutoplayEnabled(state.autoplay)
    }

    LaunchedEffect(selectedVideo?.id, state.relatedVideos, state.dataSaver, state.queue, state.queueShuffle, state.queueRepeat) {
        selectedVideo?.let { current ->
            // Con un Mix / cola activa, lo siguiente sale de la cola (en su orden o aleatorio).
            val upcoming = if (state.queueActive && state.queueIndex >= 0) state.upcomingQueue(4) else emptyList()
            playerConnection.updateQueue(
                current = current,
                candidates = upcoming.ifEmpty { state.relatedVideos },
                dataSaver = state.dataSaver
            )
        }
    }

    LaunchedEffect(state.queueRepeat) {
        if (selectedVideo != null && state.section != MainSection.SHORTS) {
            playerConnection.setRepeat(state.queueRepeat == 2)
        }
    }

    // Warm only the first few videos the user is most likely to tap. Stream extraction is
    // normally the slowest part of opening a YouTube item; doing it while the home feed is
    // idle makes the player reuse the resolved URL immediately instead of starting from zero.
    LaunchedEffect(
        state.section,
        state.homeCategory,
        state.dataSaver,
        state.personalized,
        state.popular,
        state.live,
        state.gaming,
        state.music,
        state.refreshing,
        selectedVideo?.id
    ) {
        if (state.section != MainSection.HOME || selectedVideo != null || state.refreshing) return@LaunchedEffect
        // No competir con cold start, refresh ni la comprobacion silenciosa de cuenta.
        // La precarga empieza solo cuando Principal ya lleva un momento estable.
        delay(1_100L)
        if (state.section != MainSection.HOME || selectedVideo != null || state.refreshing) return@LaunchedEffect
        val candidates = when (state.homeCategory) {
            HomeCategory.FOR_YOU -> state.personalized
            HomeCategory.LIVE -> state.live
            HomeCategory.GAMING -> state.gaming
            HomeCategory.MUSIC -> state.music
        }.asSequence()
            .filterNot(::looksLikeHomeShort)
            .distinctBy { it.id }
            .take(2)
            .toList()
        playerConnection.preload(candidates, state.dataSaver)
    }

    PlaybackProgressSaver(
        video = selectedVideo,
        playerConnection = playerConnection,
        onSavePlayback = viewModel::savePlayback
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
                mainStateHolder.SaveableStateProvider("main-shell") {
                    MainShell(
                        state = state,
                        snackbar = snackbar,
                        playerConnection = playerConnection,
                        onConnectGoogle = onConnectGoogle,
                        onSwitchGoogleAccount = onSwitchGoogleAccount,
                        onRequestYouTubeSync = onRequestYouTubeSync,
                        onSection = { target ->
                            if (state.section == MainSection.SHORTS && target != MainSection.SHORTS) {
                                playerConnection.stop()
                            }
                            viewModel.selectSection(target)
                        },
                        onCategory = viewModel::selectHomeCategory,
                        onPlay = viewModel::play,
                        onOpenVideo = viewModel::openShortDetails,
                        onPreviewShort = viewModel::previewShort,
                        onWatchLater = { video ->
                            if (!state.youtubeSessionConnected && !state.youtubeSyncAuthorized && !state.youtubeSyncBusy) {
                                onRequestYouTubeSync()
                            }
                            viewModel.toggleWatchLater(video)
                        },
                        onLike = { video ->
                            if (!state.youtubeSyncAuthorized && !state.youtubeSyncBusy) onRequestYouTubeSync()
                            viewModel.toggleLocalLike(video)
                        },
                        onDislike = { video ->
                            if (!state.youtubeSyncAuthorized && !state.youtubeSyncBusy) onRequestYouTubeSync()
                            viewModel.toggleLocalDislike(video)
                        },
                        onSearch = viewModel::search,
                        onRefresh = viewModel::refresh,
                        onHomeAtTopChanged = viewModel::setHomeAtTop,
                        onLoadMoreHome = viewModel::loadMoreHome,
                        onLoadMoreShorts = viewModel::loadMoreShorts,
                        onRefreshShorts = viewModel::refreshShorts,
                        onLoadMoreUploads = viewModel::loadMoreUploads,
                        onLoadMoreLiked = viewModel::loadMoreLiked,
                        onLoadMoreSearch = viewModel::loadMoreSearch,
                        onOpenChannel = viewModel::openChannel,
                        onCloseChannel = viewModel::closeChannel,
                        onOpenPlaylist = viewModel::openPlaylist,
                        onClosePlaylist = viewModel::closePlaylist,
                        onHomeChip = viewModel::selectHomeChip,
                        onSelectChannelTab = viewModel::selectChannelTab,
                        onSelectChannelChip = viewModel::selectChannelChip,
                        onLoadMoreChannelTab = viewModel::loadMoreChannelTab,
                        onSelectSubsChannel = viewModel::selectSubsChannel,
                        onOpenBell = viewModel::openBellSheet,
                        onSearchQueryChange = viewModel::updateSearchSuggestions,
                        onSearchFilter = viewModel::setSearchFilter,
                        onRemoveSearchHistory = viewModel::removeSearchHistory,
                        onClearSearch = viewModel::clearSearchResults,
                        onOpenNotifications = viewModel::refreshNotifications,
                        onLoadMoreNotifications = viewModel::loadMoreNotifications,
                        onVideoMenu = { video, action ->
                            when (action) {
                                "later" -> viewModel.toggleWatchLater(video)
                                "share" -> shareVideoLite(context, video)
                                "download" -> viewModel.play(video)
                                "not_interested" -> viewModel.notInterested(video)
                                "dont_recommend" -> viewModel.dontRecommendChannel(video)
                            }
                        },
                        onToggleSubscription = { channel ->
                            if (!state.youtubeSyncAuthorized) {
                                viewModel.queueSubscriptionToggle(channel)
                                if (!state.youtubeSyncBusy) onRequestYouTubeSync()
                            } else {
                                viewModel.toggleSubscription(channel)
                            }
                        },
                        onDisconnect = { playerConnection.stop(); viewModel.disconnect() },
                        onClearData = viewModel::clearLocalData,
                        onRegisterDownload = viewModel::registerDownload,
                        onRemoveDownload = viewModel::removeDownload,
                        onRemoveHistory = viewModel::removeHistory,
                        onLoadMoreHistory = viewModel::loadMoreHistory,
                        onRefreshSubscriptions = { viewModel.loadSubscriptionFeed(force = true) },
                        onLoadMoreSubscriptions = viewModel::loadMoreSubscriptionFeed,
                        onLoadMoreWatchLater = viewModel::loadMoreWatchLater,
                        onYouTubeSessionConnected = viewModel::onYouTubeSessionConnected,
                        onDisconnectYouTubeSession = viewModel::disconnectYouTubeSession,
                        onAutoplayChange = viewModel::setAutoplay,
                        onDataSaverChange = viewModel::setDataSaver,
                        onNotificationsChange = viewModel::setNotificationsEnabled,
                        onMessage = viewModel::showMessage,
                        // Solo la barra compacta (Mix) necesita espacio abajo; el mini flotante va encima,
                        // como en YouTube (antes quedaba una franja negra vacía).
                        miniPlayerVisible = selectedVideo?.isMix == true && !state.playerExpanded &&
                            state.section != MainSection.SHORTS
                    )
                }

                if (selectedVideo != null && state.section != MainSection.SHORTS) {
                    UnifiedPlayerOverlay(
                        video = selectedVideo,
                        expanded = state.playerExpanded,
                        playerConnection = playerConnection,
                        isInPictureInPictureMode = isInPictureInPictureMode,
                        fullscreenRequestToken = fullscreenRequestToken,
                        isWatchLater = state.libraryWatchLater.any { it.id == selectedVideo.id },
                        isLiked = selectedVideo.id in state.localLikedIds,
                        isDisliked = selectedVideo.id in state.localDislikedIds,
                        autoplay = state.autoplay,
                        dataSaver = state.dataSaver,
                        onAutoplayChange = viewModel::setAutoplay,
                        details = state.playerDetails,
                        detailsLoading = state.playerDetailsLoading,
                        relatedVideos = state.relatedVideos,
                        relatedLoading = state.relatedLoading,
                        relatedLoadingMore = state.relatedLoadingMore,
                        relatedCanLoadMore = state.relatedCanLoadMore,
                        onExpand = viewModel::expandPlayer,
                        onMinimize = viewModel::minimizePlayer,
                        onClose = {
                            FloatingPlayerService.stop(context)
                            playerConnection.stop()
                            viewModel.closePlayer()
                        },
                        onWatchLater = {
                            if (!state.youtubeSessionConnected && !state.youtubeSyncAuthorized && !state.youtubeSyncBusy) {
                                onRequestYouTubeSync()
                            }
                            viewModel.toggleWatchLater(selectedVideo)
                        },
                        onLike = {
                            if (!state.youtubeSyncAuthorized && !state.youtubeSyncBusy) onRequestYouTubeSync()
                            viewModel.toggleLocalLike(selectedVideo)
                        },
                        onDislike = {
                            if (!state.youtubeSyncAuthorized && !state.youtubeSyncBusy) onRequestYouTubeSync()
                            viewModel.toggleLocalDislike(selectedVideo)
                        },
                        onPlayRelated = viewModel::play,
                        onWatchLaterRelated = viewModel::toggleWatchLater,
                        onLoadMoreRelated = viewModel::loadMoreRelated,
                        onOpenChannel = { channel ->
                            viewModel.openChannel(channel)
                            viewModel.minimizePlayer()
                        },
                        onSavePlayback = viewModel::savePlayback,
                        onRegisterDownload = viewModel::registerDownload,
                        onMessage = viewModel::showMessage,
                        extras = PlayerExtras(
                            isSubscribed = selectedVideo.channelId.isNotBlank() &&
                                state.subscriptions.any { it.id == selectedVideo.channelId },
                            relatedChips = state.relatedChips,
                            selectedRelatedChip = state.selectedRelatedChip,
                            onSelectChip = viewModel::selectRelatedChip,
                            onToggleSubscribe = {
                                val channel = ChannelItem(
                                    selectedVideo.channelId,
                                    selectedVideo.channelTitle,
                                    state.playerDetails?.channelThumbnailUrl.orEmpty().ifBlank { selectedVideo.channelThumbnailUrl }
                                )
                                val alreadySubscribed = state.subscriptions.any { it.id == channel.id }
                                if (alreadySubscribed) {
                                    viewModel.openBellSheet(state.subscriptions.first { it.id == channel.id })
                                } else if (!state.youtubeSyncAuthorized) {
                                    viewModel.queueSubscriptionToggle(channel)
                                    if (!state.youtubeSyncBusy) onRequestYouTubeSync()
                                } else {
                                    viewModel.toggleSubscription(channel)
                                }
                            },
                            onOpenComments = { viewModel.openComments(selectedVideo) },
                            queueActive = state.queueActive,
                            queueTitle = state.queueTitle,
                            queueSubtitle = state.queueSubtitle,
                            queuePosition = state.queueIndex + 1,
                            queueCount = state.queue.size,
                            onOpenQueue = viewModel::openQueue,
                            onNext = viewModel::playNext,
                            onPrevious = { viewModel.playPreviousInQueue() },
                            repeatOne = state.queueRepeat == 2,
                            onRepeatOneChange = viewModel::setRepeatOne
                        )
                    )
                }

                if (state.queueOpen && selectedVideo != null) {
                    QueueSheet(
                        title = state.queueTitle,
                        subtitle = state.queueSubtitle,
                        queue = state.queue,
                        currentId = selectedVideo.id,
                        repeatMode = state.queueRepeat,
                        shuffle = state.queueShuffle,
                        loadingMore = state.queueLoadingMore,
                        onPlay = viewModel::playFromQueue,
                        onRepeat = viewModel::cycleQueueRepeat,
                        onShuffle = viewModel::toggleQueueShuffle,
                        onLoadMore = { viewModel.maybeLoadMoreQueue(force = true) },
                        onDismiss = viewModel::closeQueue
                    )
                }

                state.bellSheetChannel?.let { bellChannel ->
                    BellSheet(
                        channel = bellChannel,
                        loading = state.bellSheetLoading,
                        onSelect = viewModel::setBell,
                        onUnsubscribe = {
                            viewModel.closeBellSheet()
                            if (!state.youtubeSyncAuthorized) {
                                viewModel.queueSubscriptionToggle(bellChannel)
                                if (!state.youtubeSyncBusy) onRequestYouTubeSync()
                            } else {
                                viewModel.toggleSubscription(bellChannel)
                            }
                        },
                        onDismiss = viewModel::closeBellSheet
                    )
                }

                if (state.commentsOpen) {
                    CommentsSheet(
                        comments = state.commentsList,
                        totalText = state.commentsTotalText,
                        loading = state.commentsLoading,
                        loadingMore = state.commentsLoadingMore,
                        canLoadMore = state.commentsCanLoadMore,
                        sortIndex = state.commentsSort,
                        sortAvailable = state.commentsSortAvailable,
                        replies = state.commentReplies,
                        repliesLoading = state.commentRepliesLoading,
                        repliesCanLoadMore = state.commentRepliesCanLoadMore,
                        onSort = viewModel::setCommentsSort,
                        onLoadMore = viewModel::loadMoreComments,
                        onLoadReplies = viewModel::loadReplies,
                        onDismiss = viewModel::closeComments
                    )
                }
            }
        }
    }

@Composable
private fun PlaybackProgressSaver(
    video: VideoItem?,
    playerConnection: GeoPlayerConnection,
    onSavePlayback: (VideoItem, Long, Long) -> Unit
) {
    val playback by playerConnection.progressState.collectAsStateWithLifecycle()
    var lastSavedVideoId by remember { mutableStateOf("") }
    var lastSavedPositionMs by remember { mutableLongStateOf(0L) }
    val latestPlayback by rememberUpdatedState(playback)
    val latestVideo by rememberUpdatedState(video)

    LaunchedEffect(video?.id) {
        val selected = video ?: return@LaunchedEffect
        lastSavedVideoId = selected.id
        lastSavedPositionMs = selected.resumePositionMs
        // Registrar la reproducción de inmediato evita un historial vacío si el usuario
        // sale antes del primer intervalo de guardado.
        onSavePlayback(selected, selected.resumePositionMs, selected.durationMs)
    }

    LaunchedEffect(video?.id, playback.positionMs, playback.durationMs) {
        val selected = video ?: return@LaunchedEffect
        if (playback.currentVideoId != selected.id) return@LaunchedEffect
        if (kotlin.math.abs(playback.positionMs - lastSavedPositionMs) >= 5_000L) {
            lastSavedPositionMs = playback.positionMs
            onSavePlayback(selected, playback.positionMs, playback.durationMs)
        }
    }

    DisposableEffect(video?.id) {
        onDispose {
            val selected = latestVideo
            val progress = latestPlayback
            if (selected != null && progress.currentVideoId == selected.id) {
                onSavePlayback(selected, progress.positionMs, progress.durationMs)
            }
        }
    }
}

@Composable
private fun GoogleConnectScreen(
    status: AuthStatus,
    error: String,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "Geo Videos",
            modifier = Modifier.size(112.dp)
        )
        Text("Geo Videos", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text(
            "Videos, directos, Shorts, suscripciones y listas en una interfaz propia.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(34.dp))
        Button(
            onClick = onConnect,
            enabled = status != AuthStatus.CONNECTING,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (status == AuthStatus.CONNECTING) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Abriendo YouTube…")
            } else {
                Icon(Icons.Default.AccountCircle, null)
                Spacer(Modifier.width(10.dp))
                Text("Iniciar sesión con YouTube")
            }
        }
        Text(
            "La contraseña se escribe únicamente en la pantalla oficial de Google. Geo Videos no la recibe ni la guarda.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (status == AuthStatus.ERROR) {
            Spacer(Modifier.height(26.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "No se pudo conectar",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (error.isNotBlank()) {
                        Text(error, modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    state: GeoVideosUiState,
    snackbar: SnackbarHostState,
    playerConnection: GeoPlayerConnection,
    onConnectGoogle: () -> Unit,
    onSwitchGoogleAccount: (String) -> Unit,
    onRequestYouTubeSync: () -> Unit,
    onSection: (MainSection) -> Unit,
    onCategory: (HomeCategory) -> Unit,
    onPlay: (VideoItem) -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onPreviewShort: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onLike: (VideoItem) -> Unit,
    onDislike: (VideoItem) -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onHomeAtTopChanged: (Boolean) -> Unit = {},
    onLoadMoreHome: (HomeCategory) -> Unit,
    onLoadMoreShorts: () -> Unit,
    onRefreshShorts: () -> Unit,
    onLoadMoreUploads: () -> Unit,
    onLoadMoreLiked: () -> Unit,
    onLoadMoreSearch: () -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onCloseChannel: () -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit,
    onClosePlaylist: () -> Unit,
    onToggleSubscription: (ChannelItem) -> Unit,
    onHomeChip: (com.geovideos.app.network.HomeChip?) -> Unit = {},
    onSelectChannelTab: (Int) -> Unit = {},
    onSelectChannelChip: (com.geovideos.app.network.HomeChip) -> Unit = {},
    onLoadMoreChannelTab: () -> Unit = {},
    onSelectSubsChannel: (ChannelItem?) -> Unit = {},
    onOpenBell: (ChannelItem) -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchFilter: (Int) -> Unit = {},
    onRemoveSearchHistory: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onLoadMoreNotifications: () -> Unit = {},
    onVideoMenu: (VideoItem, String) -> Unit = { _, _ -> },
    onDisconnect: () -> Unit,
    onClearData: () -> Unit,
    onRegisterDownload: (String, String, Long) -> Unit,
    onRemoveDownload: (Long) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onLoadMoreHistory: () -> Unit,
    onRefreshSubscriptions: () -> Unit,
    onLoadMoreSubscriptions: () -> Unit,
    onLoadMoreWatchLater: () -> Unit,
    onYouTubeSessionConnected: () -> Unit,
    onDisconnectYouTubeSession: () -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onDataSaverChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    miniPlayerVisible: Boolean
) {
    val context = LocalContext.current
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<PendingDownload?>(null) }
    var libraryDestination by rememberSaveable { mutableStateOf(LibraryDestination.ROOT) }
    var homeAtTop by rememberSaveable { mutableStateOf(true) }
    var homeScrollToTopSignal by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(state.section) {
        if (state.section != MainSection.LIBRARY) libraryDestination = LibraryDestination.ROOT
    }
    BackHandler(enabled = state.section == MainSection.LIBRARY && libraryDestination != LibraryDestination.ROOT) {
        libraryDestination = LibraryDestination.ROOT
    }
    BackHandler(
        enabled = libraryDestination == LibraryDestination.ROOT && state.section in setOf(
            MainSection.SEARCH,
            MainSection.ACCOUNT,
            MainSection.SUBSCRIPTIONS,
            MainSection.LIBRARY
        )
    ) {
        onSection(MainSection.HOME)
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingDownload
        pendingDownload = null
        if (granted && pending != null) {
            val id = enqueueDirectDownload(context, pending.title, pending.url, pending.wifiOnly)
            if (id >= 0L) {
                onRegisterDownload(pending.title, pending.url, id)
                showDownloadDialog = false
            } else {
                onMessage("El enlace debe empezar con http:// o https://")
            }
        } else if (!granted) {
            onMessage("Android necesita permiso de almacenamiento para descargar en esta versión.")
        }
    }

    if (showNotifications) {
        NotificationsScreen(
            notifications = state.notifications,
            loading = state.notificationsLoading,
            loadingMore = state.notificationsLoadingMore,
            canLoadMore = state.notificationsCanLoadMore,
            onRefresh = onOpenNotifications,
            onLoadMore = onLoadMoreNotifications,
            onBack = { showNotifications = false },
            onPlay = {
                showNotifications = false
                onPlay(it)
            }
        )
        return
    }

    state.selectedChannel?.let { channel ->
        ChannelPage(
            channel = channel,
            loading = state.loading && state.channelTabs.isEmpty(),
            tabs = state.channelTabs,
            tabIndex = state.channelTabIndex,
            shelves = state.channelShelves,
            featured = state.channelFeatured,
            tabVideos = state.channelTabVideos,
            tabShorts = state.channelTabShorts,
            tabPlaylists = state.channelTabPlaylists,
            tabPosts = state.channelTabPosts,
            tabChips = state.channelTabChips,
            selectedChip = state.channelTabSelectedChip,
            tabLoading = state.channelTabLoading,
            tabLoadingMore = state.channelTabLoadingMore,
            tabCanLoadMore = state.channelTabCanLoadMore,
            onBack = onCloseChannel,
            onSelectTab = onSelectChannelTab,
            onSelectChip = onSelectChannelChip,
            onLoadMore = onLoadMoreChannelTab,
            onPlay = onPlay,
            onOpenShort = onPreviewShort,
            onOpenPlaylist = onOpenPlaylist,
            onSubscribe = { onToggleSubscription(channel) },
            onOpenBell = { onOpenBell(channel) }
        )
        return
    }

    state.selectedPlaylist?.let { playlist ->
        SearchPlaylistScreen(
            playlist = playlist,
            videos = state.selectedPlaylistVideos,
            loading = state.playlistLoading,
            onBack = onClosePlaylist,
            onPlay = onPlay,
            onWatchLater = onWatchLater
        )
        return
    }

    Scaffold(
        topBar = {
            if (state.section != MainSection.SHORTS && libraryDestination == LibraryDestination.ROOT) {
                TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_logo), null, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Geo Videos", fontWeight = FontWeight.ExtraBold)
                    }
                },
                actions = {
                    IconButton(onClick = { onSection(MainSection.SEARCH) }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = {
                        showNotifications = true
                        onOpenNotifications()
                    }) {
                        BadgedBox(badge = {
                            // Número rojo de YouTube: notificaciones sin ver.
                            if (state.notificationsUnseen > 0) {
                                Badge { Text(if (state.notificationsUnseen > 9) "9+" else state.notificationsUnseen.toString()) }
                            }
                        }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Notificaciones")
                        }
                    }
                    ProfileAvatar(profile = state.profile, size = 34.dp, onClick = { onSection(MainSection.ACCOUNT) })
                    Spacer(Modifier.width(8.dp))
                }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                val navigate: (MainSection) -> Unit = { target ->
                    when {
                        target == MainSection.HOME && state.section == MainSection.HOME -> {
                            if (homeAtTop) onRefresh() else homeScrollToTopSignal += 1L
                        }
                        target == MainSection.SUBSCRIPTIONS && state.section == MainSection.SUBSCRIPTIONS -> {
                            onRefreshSubscriptions()
                        }
                        target == MainSection.LIBRARY && state.section == MainSection.LIBRARY -> {
                            libraryDestination = LibraryDestination.ROOT
                            onSection(target)
                        }
                        else -> onSection(target)
                    }
                }
                BottomItem(MainSection.HOME, state.section, "Principal", Icons.Default.Home, navigate)
                BottomItem(MainSection.SHORTS, state.section, "Shorts", Icons.Default.AutoAwesome, navigate)
                BottomItem(MainSection.SUBSCRIPTIONS, state.section, "Suscripciones", Icons.Default.Subscriptions, navigate)
                BottomItem(MainSection.LIBRARY, state.section, "Tú", Icons.Default.VideoLibrary, navigate)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val contentModifier = Modifier
            .padding(padding)
            .padding(bottom = if (miniPlayerVisible) 64.dp else 0.dp)
        when (state.section) {
            MainSection.HOME -> LiteHomeScreen(
                modifier = contentModifier,
                category = state.homeCategory,
                personalized = state.personalized,
                popular = state.popular,
                live = state.live,
                gaming = state.gaming,
                music = state.music,
                shorts = state.shorts,
                mixes = state.homeMixes,
                homeFeed = state.homeFeed,
                loading = state.loading,
                refreshing = state.refreshing,
                loadingMore = state.isLoadingMore(state.homeCategory),
                canLoadMore = state.canLoadMore(state.homeCategory),
                watchLater = state.libraryWatchLater,
                onRefresh = onRefresh,
                onLoadMore = onLoadMoreHome,
                onCategory = onCategory,
                onPlay = onPlay,
                onOpenShort = onPreviewShort,
                onWatchLater = onWatchLater,
                scrollToTopSignal = homeScrollToTopSignal,
                onAtTopChanged = {
                    homeAtTop = it
                    onHomeAtTopChanged(it)
                },
                youtubeChips = state.homeChips,
                selectedYouTubeChip = state.selectedHomeChip,
                chipLoading = state.homeChipLoading,
                onYouTubeChip = onHomeChip,
                onVideoMenu = onVideoMenu
            )
            MainSection.SHORTS -> RecyclerShortsScreen(
                modifier = contentModifier,
                videos = state.shorts,
                selectedVideoId = state.selectedVideo?.id.orEmpty(),
                localLikedIds = state.localLikedIds,
                localDislikedIds = state.localDislikedIds,
                loading = state.loading || (state.shorts.isEmpty() && state.shortsLoadingMore),
                loadingMore = state.shortsLoadingMore,
                canLoadMore = state.shortsCanLoadMore,
                dataSaver = state.dataSaver,
                playerConnection = playerConnection,
                onLoadMore = onLoadMoreShorts,
                onRetry = onRefreshShorts,
                onPreview = onPreviewShort,
                onOpenVideo = onOpenVideo,
                onWatchLater = onWatchLater,
                onLike = onLike,
                onDislike = onDislike,
                onRegisterDownload = onRegisterDownload,
                onMessage = onMessage,
                onSubscribe = { video ->
                    if (video.channelId.isNotBlank()) {
                        onToggleSubscription(
                            ChannelItem(
                                id = video.channelId,
                                title = video.channelTitle,
                                thumbnailUrl = video.channelThumbnailUrl
                            )
                        )
                    }
                }
            )
            MainSection.SUBSCRIPTIONS -> SubscriptionsFeedScreen(
                modifier = contentModifier,
                channels = state.subscriptions,
                videos = state.subsFeedVideos,
                shorts = state.subsFeedShorts,
                loading = state.subsFeedLoading,
                loadingMore = state.subsFeedLoadingMore,
                canLoadMore = state.subsFeedCanLoadMore,
                onRefresh = onRefreshSubscriptions,
                onLoadMore = onLoadMoreSubscriptions,
                onPlay = onPlay,
                onOpenShort = onPreviewShort,
                onOpenChannel = onOpenChannel,
                onWatchLater = onWatchLater,
                guide = state.subsGuide,
                filterChannel = state.subsChannelFilter,
                filterVideos = state.subsChannelVideos,
                filterShorts = state.subsChannelShorts,
                filterLoading = state.subsChannelLoading,
                history = state.libraryHistory,
                onSelectChannel = onSelectSubsChannel,
                onOpenBell = onOpenBell
            )
            MainSection.SEARCH -> SearchPage(
                modifier = contentModifier,
                currentQuery = state.searchQuery,
                entries = state.searchEntries,
                filter = state.searchFilter,
                suggestions = state.searchSuggestions,
                history = state.searchHistory,
                corrected = state.searchCorrected,
                loading = state.loading,
                loadingMore = state.searchLoadingMore,
                canLoadMore = state.searchCanLoadMore,
                subscribedIds = state.subscriptions.mapTo(HashSet()) { it.id },
                onBack = {
                    onClearSearch()
                    onSection(MainSection.HOME)
                },
                onQueryChange = onSearchQueryChange,
                onSearch = onSearch,
                onFilter = onSearchFilter,
                onRemoveHistory = onRemoveSearchHistory,
                onLoadMore = onLoadMoreSearch,
                onPlay = onPlay,
                onOpenShort = onPreviewShort,
                onOpenChannel = onOpenChannel,
                onOpenPlaylist = onOpenPlaylist,
                onWatchLater = onWatchLater,
                onSubscribe = onToggleSubscription,
                onOpenBell = onOpenBell
            )
            MainSection.LIBRARY -> {
                if (libraryDestination == LibraryDestination.ROOT) {
                    LibraryScreen(
                        modifier = contentModifier,
                        profile = state.profile,
                        onOpenAccount = { onSection(MainSection.ACCOUNT) },
                        onOpenPlaylist = onOpenPlaylist,
                        history = state.libraryHistory,
                        watchLater = state.libraryWatchLater,
                        liked = (state.localLikedVideos + state.liked).distinctBy { it.id },
                        uploads = state.uploads,
                        uploadsLoadingMore = state.uploadsLoadingMore,
                        uploadsCanLoadMore = state.uploadsCanLoadMore,
                        playlists = state.playlists,
                        subscriptions = state.subscriptions,
                        subscriptionVideos = state.personalized.filter { video ->
                            state.subscriptions.any { channel -> channel.id == video.channelId }
                        },
                        downloads = state.downloads,
                        onPlay = onPlay,
                        onWatchLater = onWatchLater,
                        onLoadMoreUploads = onLoadMoreUploads,
                        onOpenChannel = onOpenChannel,
                        onOpenHistory = { libraryDestination = LibraryDestination.HISTORY },
                        onOpenWatchLater = { libraryDestination = LibraryDestination.WATCH_LATER },
                        onOpenLiked = { libraryDestination = LibraryDestination.LIKED },
                        onOpenMusic = { libraryDestination = LibraryDestination.MUSIC },
                        onOpenUploads = { libraryDestination = LibraryDestination.UPLOADS },
                        onOpenSubscriptions = { libraryDestination = LibraryDestination.SUBSCRIPTIONS },
                        onAddDownload = { showDownloadDialog = true },
                        onRemoveDownload = onRemoveDownload
                    )
                } else if (libraryDestination == LibraryDestination.SUBSCRIPTIONS) {
                    SubscriptionCollectionScreen(
                        modifier = contentModifier,
                        channels = state.subscriptions,
                        videos = state.personalized.filter { video ->
                            state.subscriptions.any { channel -> channel.id == video.channelId }
                        },
                        onBack = { libraryDestination = LibraryDestination.ROOT },
                        onOpenChannel = onOpenChannel,
                        onPlay = onPlay,
                        onWatchLater = onWatchLater
                    )
                } else {
                    val collectionVideos = when (libraryDestination) {
                        LibraryDestination.HISTORY -> state.libraryHistory
                        LibraryDestination.WATCH_LATER -> state.libraryWatchLater
                        LibraryDestination.LIKED -> (state.localLikedVideos + state.liked).distinctBy { it.id }
                        LibraryDestination.MUSIC -> (state.libraryHistory + state.localLikedVideos + state.liked)
                            .filter(::looksLikeMusicCollection)
                            .distinctBy { it.id }
                        LibraryDestination.UPLOADS -> state.uploads
                        LibraryDestination.ROOT, LibraryDestination.SUBSCRIPTIONS -> emptyList()
                    }
                    LibraryCollectionScreen(
                        modifier = contentModifier,
                        destination = libraryDestination,
                        videos = collectionVideos,
                        loadingMore = when (libraryDestination) {
                            LibraryDestination.LIKED -> state.likedLoadingMore
                            LibraryDestination.UPLOADS -> state.uploadsLoadingMore
                            LibraryDestination.HISTORY -> state.historyLoadingMore
                            LibraryDestination.WATCH_LATER -> state.watchLaterLoadingMore
                            else -> false
                        },
                        canLoadMore = when (libraryDestination) {
                            LibraryDestination.LIKED -> state.likedCanLoadMore
                            LibraryDestination.UPLOADS -> state.uploadsCanLoadMore
                            LibraryDestination.HISTORY -> state.historyCanLoadMore
                            LibraryDestination.WATCH_LATER -> state.watchLaterCanLoadMore
                            else -> false
                        },
                        onLoadMore = when (libraryDestination) {
                            LibraryDestination.LIKED -> onLoadMoreLiked
                            LibraryDestination.UPLOADS -> onLoadMoreUploads
                            LibraryDestination.HISTORY -> onLoadMoreHistory
                            LibraryDestination.WATCH_LATER -> onLoadMoreWatchLater
                            else -> null
                        },
                        onBack = { libraryDestination = LibraryDestination.ROOT },
                        onPlay = onPlay,
                        onWatchLater = onWatchLater,
                        onRemoveHistory = onRemoveHistory
                    )
                }
            }
            MainSection.ACCOUNT -> AccountScreen(
                modifier = contentModifier,
                profile = state.profile,
                subscriptionsCount = state.subscriptions.size,
                likedCount = state.liked.size,
                playlistsCount = state.playlists.size,
                uploadsCount = state.uploads.size,
                lastSyncMs = state.lastSyncMs,
                youtubeWriteSyncEnabled = state.youtubeSyncEnabled,
                youtubeWriteSyncAuthorized = state.youtubeSyncAuthorized,
                youtubeSessionConnected = state.youtubeSessionConnected,
                youtubeSessionBusy = state.youtubeSessionBusy,
                youtubeHistoryCount = state.youtubeHistory.size,
                youtubeWatchLaterCount = state.youtubeWatchLater.size,
                onYouTubeSessionConnected = onYouTubeSessionConnected,
                onDisconnectYouTubeSession = onDisconnectYouTubeSession,
                autoplay = state.autoplay,
                dataSaver = state.dataSaver,
                notificationsEnabled = state.notificationsEnabled,
                onAutoplayChange = onAutoplayChange,
                onDataSaverChange = onDataSaverChange,
                onNotificationsChange = onNotificationsChange,
                onReconnect = onConnectGoogle,
                onSwitchAccount = { onSwitchGoogleAccount(state.profile?.email.orEmpty()) },
                onDisconnect = onDisconnect,
                onClearData = onClearData
            )
        }
    }

    if (showDownloadDialog) {
        DirectDownloadDialog(
            onDismiss = { showDownloadDialog = false },
            onDownload = { title, url, wifiOnly ->
                val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                if (needsLegacyPermission) {
                    pendingDownload = PendingDownload(title, url, wifiOnly)
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    val downloadId = enqueueDirectDownload(context, title, url, wifiOnly)
                    if (downloadId >= 0L) {
                        onRegisterDownload(title, url, downloadId)
                        showDownloadDialog = false
                    } else {
                        onMessage("El enlace debe empezar con http:// o https://")
                    }
                }
            }
        )
    }
}

@Composable
private fun RowScope.BottomItem(
    section: MainSection,
    selected: MainSection,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSection: (MainSection) -> Unit
) {
    NavigationBarItem(
        selected = selected == section,
        onClick = { onSection(section) },
        icon = { Icon(icon, label) },
        label = { Text(label, maxLines = 1) }
    )
}

@Composable
private fun SearchScreen(
    modifier: Modifier,
    results: List<VideoItem>,
    channels: List<ChannelItem>,
    playlists: List<PlaylistItem>,
    history: List<String>,
    loading: Boolean,
    loadingMore: Boolean,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onOpenPlaylist: (PlaylistItem) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                FilledIconButton(onClick = { onSearch(query) }, enabled = query.isNotBlank()) {
                    Icon(Icons.Default.Search, "Buscar")
                }
            },
            label = { Text("Buscar en YouTube") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch(query) })
        )
        if (query.isBlank() && results.isEmpty() && channels.isEmpty() && playlists.isEmpty() && history.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history.take(10), key = { "search-history-$it" }) { item ->
                    AssistChip(
                        onClick = { query = item; onSearch(item) },
                        label = { Text(item, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.History, null) }
                    )
                }
            }
        }

        if (channels.isNotEmpty()) {
            Text(
                "Canales",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(channels.take(12), key = { "yt-channel-${it.id}" }) { channel ->
                    Column(
                        modifier = Modifier.width(92.dp).clickable { onOpenChannel(channel) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Thumbnail(
                            url = channel.thumbnailUrl,
                            modifier = Modifier.size(58.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            channel.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            Text(
                "Listas de reproducción",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(playlists.take(12), key = { "yt-playlist-${it.id}" }) { playlist ->
                    Column(modifier = Modifier.width(150.dp).clickable { onOpenPlaylist(playlist) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1D1D22))
                        ) {
                            Thumbnail(playlist.thumbnailUrl, Modifier.fillMaxSize(), ContentScale.Crop)
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                                color = Color.Black.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlaylistPlay, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Lista", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Text(
                            playlist.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }
        }

        if (results.isNotEmpty() || loading || (query.isNotBlank() && channels.isEmpty() && playlists.isEmpty())) {
            if (results.isNotEmpty()) {
                Text(
                    "Videos",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            NativeVideoList(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                videos = results,
                loading = loading,
                loadingMore = loadingMore,
                canLoadMore = results.isNotEmpty(),
                mode = NativeVideoListMode.COMPACT,
                emptyMessage = if (query.isBlank()) "Busca contenido de YouTube." else "No se encontraron resultados.",
                onLoadMore = onLoadMore,
                onPlay = onPlay,
                onSave = onWatchLater
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPlaylistScreen(
    playlist: PlaylistItem,
    videos: List<VideoItem>,
    loading: Boolean,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = {
                Column {
                    Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text("Lista de YouTube", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
            }
        )
        NativeVideoList(
            modifier = Modifier.fillMaxSize(),
            videos = videos,
            loading = loading,
            loadingMore = false,
            canLoadMore = false,
            mode = NativeVideoListMode.COMPACT,
            emptyMessage = "Esta lista no tiene videos disponibles.",
            onLoadMore = {},
            onPlay = onPlay,
            onSave = onWatchLater
        )
    }
}

private fun looksLikeMusicCollection(video: VideoItem): Boolean {
    val text = "${video.title} ${video.description} ${video.channelTitle}".lowercase()
    return listOf(
        "music", "música", "musica", "song", "lyrics", "audio", "rap", "mix",
        "nightcore", "remix", "cover", "karaoke", "official audio"
    ).any { it in text }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    profile: GoogleProfile? = null,
    onOpenAccount: () -> Unit = {},
    onOpenPlaylist: (PlaylistItem) -> Unit = {},
    history: List<VideoItem>,
    watchLater: List<VideoItem>,
    liked: List<VideoItem>,
    uploads: List<VideoItem>,
    uploadsLoadingMore: Boolean,
    uploadsCanLoadMore: Boolean,
    playlists: List<PlaylistItem>,
    subscriptions: List<ChannelItem>,
    subscriptionVideos: List<VideoItem>,
    downloads: List<VideoItem>,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onLoadMoreUploads: () -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenWatchLater: () -> Unit,
    onOpenLiked: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenUploads: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onAddDownload: () -> Unit,
    onRemoveDownload: (Long) -> Unit
) {
    val context = LocalContext.current
    val uniqueHistory = remember(history) { history.distinctBy { it.id } }
    val uniqueLiked = remember(liked) { liked.distinctBy { it.id } }
    val musicVideos = remember(history, liked) {
        (history + liked).filter(::looksLikeMusicCollection).distinctBy { it.id }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(top = 8.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Encabezado de la pestaña Tú como YouTube: foto, nombre, @canal y accesos.
        item(key = "library-profile") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(profile = profile, size = 68.dp, onClick = onOpenAccount)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            profile?.name?.ifBlank { null } ?: profile?.channelTitle?.ifBlank { null } ?: "Tu cuenta",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                profile?.channelTitle?.takeIf { it.isNotBlank() && it != profile.name }?.let { append(it).append(" · ") }
                                append("Ver canal ›")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                val id = profile?.channelId.orEmpty()
                                if (id.isNotBlank()) {
                                    onOpenChannel(ChannelItem(id, profile?.channelTitle.orEmpty().ifBlank { profile?.name.orEmpty() }, profile?.pictureUrl.orEmpty()))
                                } else {
                                    onOpenAccount()
                                }
                            }
                        )
                    }
                }
                LazyRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "chip-account") {
                        AssistChip(onClick = onOpenAccount, label = { Text("Cambiar de cuenta") }, leadingIcon = { Icon(Icons.Default.AccountCircle, null) })
                    }
                    item(key = "chip-google") {
                        AssistChip(
                            onClick = {
                                runCatching {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://myaccount.google.com")))
                                }
                            },
                            label = { Text("Cuenta de Google") }
                        )
                    }
                    item(key = "chip-settings") {
                        AssistChip(onClick = onOpenAccount, label = { Text("Ajustes") })
                    }
                }
            }
        }
        item(key = "library-history-title") {
            CompactLibraryHeader("Historial", "Ver todo", onOpenHistory)
        }
        if (uniqueHistory.isEmpty()) {
            item(key = "library-history-empty") {
                Text(
                    "Los videos que veas aparecerán aquí.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        } else {
            item(key = "library-history-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uniqueHistory.take(14), key = { "history-${it.id}" }) { video ->
                        CompactHistoryCard(video = video, onClick = { onPlay(video) })
                    }
                }
            }
        }

        item(key = "library-playlists-title") {
            CompactLibraryHeader("Playlists", "Ver todo", onOpenWatchLater)
        }
        item(key = "library-playlists-row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "liked-playlist") {
                    LibraryCoverCard(
                        title = "Videos que me gustan",
                        count = uniqueLiked.size,
                        icon = Icons.Default.ThumbUp,
                        thumbnailUrl = uniqueLiked.firstOrNull()?.thumbnailUrl.orEmpty(),
                        subtitle = "Privado",
                        onClick = onOpenLiked
                    )
                }
                item(key = "music-playlist") {
                    LibraryCoverCard(
                        title = "Música",
                        count = musicVideos.size,
                        icon = Icons.Default.MusicNote,
                        thumbnailUrl = musicVideos.firstOrNull()?.thumbnailUrl.orEmpty(),
                        subtitle = "Reproducción y Me gusta",
                        onClick = onOpenMusic
                    )
                }
                item(key = "watch-later-playlist") {
                    LibraryCoverCard(
                        title = "Ver más tarde",
                        count = watchLater.size,
                        icon = Icons.Default.WatchLater,
                        thumbnailUrl = watchLater.firstOrNull()?.thumbnailUrl.orEmpty(),
                        subtitle = "Privado",
                        onClick = onOpenWatchLater
                    )
                }
                items(playlists.take(10), key = { "library-playlist-${it.id}" }) { playlist ->
                    PlaylistCard(playlist, onClick = { onOpenPlaylist(playlist) })
                }
            }
        }

        item(key = "library-subscriptions-row") {
            LibraryNavigationRow(
                icon = Icons.Default.Subscriptions,
                title = "Suscripciones",
                subtitle = "${subscriptions.size} canales",
                onClick = onOpenSubscriptions
            )
        }
        item(key = "library-history-navigation-row") {
            LibraryNavigationRow(
                icon = Icons.Default.History,
                title = "Historial",
                subtitle = "${uniqueHistory.size} videos",
                onClick = onOpenHistory
            )
        }
        item(key = "library-watch-later-row") {
            LibraryNavigationRow(
                icon = Icons.Default.WatchLater,
                title = "Ver más tarde",
                subtitle = "${watchLater.size} videos",
                onClick = onOpenWatchLater
            )
        }
        item(key = "library-liked-row") {
            LibraryNavigationRow(
                icon = Icons.Default.ThumbUp,
                title = "Videos que me gustan",
                subtitle = "${uniqueLiked.size} videos",
                onClick = onOpenLiked
            )
        }
        item(key = "library-uploads-row") {
            LibraryNavigationRow(
                icon = Icons.Default.VideoLibrary,
                title = "Tus videos",
                subtitle = when {
                    uploadsLoadingMore -> "Cargando…"
                    uploadsCanLoadMore -> "${uploads.size} videos · hay más"
                    else -> "${uploads.size} videos"
                },
                onClick = {
                    if (uploadsCanLoadMore && !uploadsLoadingMore) onLoadMoreUploads()
                    onOpenUploads()
                }
            )
        }
        item(key = "library-downloads-row") {
            LibraryNavigationRow(
                icon = Icons.Default.DownloadDone,
                title = "Descargas",
                subtitle = "${downloads.size} archivos",
                onClick = { openSystemDownloads(context) }
            )
        }

        if (downloads.isNotEmpty()) {
            items(downloads.take(3), key = { "download-${it.downloadId}-${it.id}" }) { video ->
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                    DownloadStatusCard(
                        video = video,
                        onOpen = { openSystemDownloads(context) },
                        onRemove = { onRemoveDownload(video.downloadId) }
                    )
                }
            }
        }

        item(key = "library-add-download") {
            TextButton(
                onClick = onAddDownload,
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Agregar descarga por enlace")
            }
        }
    }
}

@Composable
private fun CompactLibraryHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAction)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            action,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun CompactHistoryCard(video: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(148.dp).clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF1B1A20))
        ) {
            Thumbnail(video.thumbnailUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            ResumeProgress(video, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            if (video.durationMs > 0L) {
                Text(
                    formatDuration(video.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            video.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 5.dp)
        )
        Text(
            video.channelTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

@Composable
private fun LibraryCoverCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    thumbnailUrl: String,
    subtitle: String = "Playlist",
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(148.dp).clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF1B1A20))
        ) {
            Thumbnail(thumbnailUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.76f))
                    )
                )
            )
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(
                    count.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    subtitle,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LibraryWideTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LibraryNavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(25.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SubscriptionShelf(
    subscriptions: List<ChannelItem>,
    recentVideos: List<VideoItem>,
    onOpenChannel: (ChannelItem) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSubscriptions)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Subscriptions, null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Suscripciones", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${subscriptions.size} canales · toca uno para ver sus publicaciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                items(subscriptions.take(18), key = { "subscription-shelf-${it.id}" }) { channel ->
                    ChannelCard(channel) { onOpenChannel(channel) }
                }
            }
            if (recentVideos.isNotEmpty()) {
                Text(
                    "Últimos de tus suscripciones",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentVideos.take(10), key = { "subscription-video-${it.id}" }) { video ->
                        Card(onClick = { onPlay(video) }, modifier = Modifier.width(230.dp)) {
                            Thumbnail(video.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    video.channelTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryShortcutRow(
    history: Int,
    later: Int,
    liked: Int,
    onHistory: () -> Unit,
    onWatchLater: () -> Unit,
    onLiked: () -> Unit,
    onDownload: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ShortcutCard(Icons.Default.History, "Historial", "$history videos", onHistory) }
        item { ShortcutCard(Icons.Default.WatchLater, "Ver después", "$later videos", onWatchLater) }
        item { ShortcutCard(Icons.Default.Favorite, "Me gustan", "$liked videos", onLiked) }
        item { ShortcutCard(Icons.Default.Download, "Descargas", "Agregar", onDownload) }
    }
}

@Composable
private fun ShortcutCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.width(150.dp),
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccountScreen(
    modifier: Modifier,
    profile: GoogleProfile?,
    subscriptionsCount: Int,
    likedCount: Int,
    playlistsCount: Int,
    uploadsCount: Int,
    lastSyncMs: Long,
    youtubeWriteSyncEnabled: Boolean,
    youtubeWriteSyncAuthorized: Boolean,
    youtubeSessionConnected: Boolean,
    youtubeSessionBusy: Boolean,
    youtubeHistoryCount: Int,
    youtubeWatchLaterCount: Int,
    onYouTubeSessionConnected: () -> Unit,
    onDisconnectYouTubeSession: () -> Unit,
    autoplay: Boolean,
    dataSaver: Boolean,
    notificationsEnabled: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    onDataSaverChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onReconnect: () -> Unit,
    onSwitchAccount: () -> Unit,
    onDisconnect: () -> Unit,
    onClearData: () -> Unit
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProfileAvatar(profile, 94.dp, null)
        Text(profile?.channelTitle?.ifBlank { profile.name } ?: "Cuenta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text(profile?.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(18.dp))
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Text("Datos de YouTube", fontWeight = FontWeight.Bold)
                }
                Text(
                    if (lastSyncMs > 0L) "Lectura automática conectada a esta cuenta" else "Sincronizando datos de esta cuenta…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 7.dp)
                )
                Text(
                    "$subscriptionsCount suscripciones · $likedCount Me gusta · $playlistsCount listas · $uploadsCount subidos",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 7.dp)
                )
                Text(
                    when {
                        youtubeWriteSyncAuthorized -> "Acciones de YouTube autorizadas y sincronizadas."
                        youtubeWriteSyncEnabled -> "Las acciones de YouTube se renovarán cuando vuelvas a usarlas."
                        else -> "Me gusta, suscribirse y listas pedirán permiso solo cuando sea necesario."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Text("Sesión de YouTube", fontWeight = FontWeight.Bold)
                }
                Text(
                    when {
                        youtubeSessionBusy -> "Cargando historial, Ver más tarde e inicio…"
                        youtubeSessionConnected ->
                            "Conectada · $youtubeHistoryCount en historial · $youtubeWatchLaterCount en Ver más tarde. " +
                                "Lo que veas aquí se guarda en tu historial de YouTube."
                        else ->
                            "Sin sesión. Vuelve a iniciar sesión con YouTube."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingSwitch("Reproducción automática", "Inicia el video y pasa al siguiente al terminar", autoplay, onAutoplayChange)
        SettingSwitch("Ahorro de datos", "Prioriza una calidad menor y reduce el consumo de red", dataSaver, onDataSaverChange)
        SettingSwitch("Avisos en la app", "Sincroniza actividad disponible para la campana", notificationsEnabled, onNotificationsChange)

        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onSwitchAccount, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AccountCircle, null)
            Spacer(Modifier.width(10.dp))
            Text("Cambiar de cuenta")
        }
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(10.dp))
            Text("Cerrar sesión")
        }
        TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(top = 12.dp)) {
            Icon(Icons.Default.Delete, null)
            Spacer(Modifier.width(8.dp))
            Text("Eliminar historial y datos locales")
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Eliminar datos locales") },
            text = { Text("Se borrarán el historial de Geo Videos, Ver después, búsquedas y descargas registradas. No se elimina nada de tu cuenta de Google.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearData() }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsScreen(
    notifications: List<NotificationItem>,
    loading: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    BackHandler(onBack = onBack)
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, canLoadMore, loadingMore) {
        if (nearEnd && canLoadMore && !loadingMore) onLoadMore()
    }
    val sections = remember(notifications) { notifications.groupBy { it.section }.entries.toList() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notificaciones", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") }
                }
            )
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
            if (loading && notifications.isEmpty()) {
                item(key = "notif-loading") {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else if (notifications.isEmpty()) {
                item(key = "notif-empty") { EmptyBlock("No tienes notificaciones. Cuando tus canales publiquen algo, aparecerá aquí.") }
            }
            sections.forEach { (title, group) ->
                if (title.isNotBlank()) {
                    item(key = "notif-section-$title") {
                        Text(
                            title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
                        )
                    }
                }
                items(group, key = { "notification-${it.id}" }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = item.video != null) { item.video?.let(onPlay) }
                            .padding(start = 6.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Punto azul de "sin leer", como YouTube.
                        Box(
                            Modifier
                                .padding(top = 18.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (!item.read) Color(0xFF3EA6FF) else Color.Transparent)
                        )
                        Spacer(Modifier.width(6.dp))
                        Thumbnail(
                            item.avatarUrl.ifBlank { item.thumbnailUrl },
                            Modifier.size(44.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.title, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (item.subtitle.isNotBlank()) {
                                Text(item.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        if (item.video != null) {
                            Thumbnail(
                                item.video.thumbnailUrl.ifBlank { item.thumbnailUrl },
                                Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
            if (loadingMore) {
                item(key = "notif-more") {
                    Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

private enum class ChannelTab(val label: String) {
    HOME("Principal"), VIDEOS("Videos"), SHORTS("Shorts"), PLAYLISTS("Playlists")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelScreen(
    channel: ChannelItem,
    videos: List<VideoItem>,
    playlists: List<PlaylistItem>,
    loading: Boolean,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onToggleSubscription: (ChannelItem) -> Unit,
    onMessage: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    var tab by rememberSaveable(channel.id) { mutableStateOf(ChannelTab.HOME) }
    var descriptionExpanded by rememberSaveable(channel.id) { mutableStateOf(false) }
    val normalVideos = remember(videos) { videos.filterNot { it.isShortForChannel() } }
    val shorts = remember(videos) { videos.filter { it.isShortForChannel() } }
    val visibleVideos = when (tab) {
        ChannelTab.HOME -> normalVideos.take(12)
        ChannelTab.VIDEOS -> normalVideos
        ChannelTab.SHORTS -> shorts
        ChannelTab.PLAYLISTS -> emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(channel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { onMessage("Usa Buscar para encontrar videos dentro de Geo Videos.") }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = { onMessage("Canal cargado desde tu cuenta de YouTube.") }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().background(Color.Black),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "channel-banner-${channel.id}") {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 4.6f).background(Color(0xFF251A2F))) {
                    if (channel.bannerUrl.isNotBlank()) {
                        AsyncImage(
                            model = channel.bannerUrl,
                            contentDescription = "Portada de ${channel.title}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            item(key = "channel-header-${channel.id}") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelAvatar(channel.thumbnailUrl, channel.title, 78.dp)
                        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(channel.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                            if (channel.handle.isNotBlank()) {
                                Text(channel.handle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                            }
                            Text(
                                buildString {
                                    if (channel.subscriberCount > 0L) append("${formatCompactNumber(channel.subscriberCount)} suscriptores")
                                    if (channel.videoCount > 0L) {
                                        if (isNotEmpty()) append(" · ")
                                        append("${formatCompactNumber(channel.videoCount)} videos")
                                    }
                                }.ifBlank { "Canal de YouTube" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                    if (channel.description.isNotBlank()) {
                        Text(
                            channel.description,
                            maxLines = if (descriptionExpanded) 12 else 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable { descriptionExpanded = !descriptionExpanded }.padding(top = 12.dp)
                        )
                    }
                    Button(
                        onClick = { onToggleSubscription(channel) },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(if (channel.isSubscribed) "Suscrito" else "Suscribirse")
                    }
                }
            }
            item(key = "channel-tabs-${channel.id}") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ChannelTab.entries, key = { it.name }) { option ->
                        FilterChip(
                            selected = tab == option,
                            onClick = { tab = option },
                            label = { Text(option.label) }
                        )
                    }
                }
                HorizontalDivider()
            }
            if (loading) {
                item(key = "channel-loading") {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (tab == ChannelTab.PLAYLISTS) {
                if (playlists.isEmpty()) {
                    item(key = "channel-playlists-empty") {
                        Text(
                            "Este canal no tiene playlists públicas disponibles.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    items(playlists, key = { "channel-playlist-${it.id}" }) { playlist ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Thumbnail(
                                playlist.thumbnailUrl,
                                Modifier.width(168.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(9.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(playlist.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                Text("${playlist.itemCount} videos", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else if (visibleVideos.isEmpty()) {
                item(key = "channel-videos-empty-${tab.name}") {
                    Text(
                        if (tab == ChannelTab.SHORTS) "No se encontraron Shorts recientes." else "No se encontraron videos recientes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                if (tab == ChannelTab.HOME) {
                    item(key = "channel-recent-title") {
                        Text("Videos recientes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                    }
                }
                items(visibleVideos, key = { "channel-video-${tab.name}-${it.id}" }) { video ->
                    VideoCard(
                        video = video,
                        isWatchLater = false,
                        onPlay = { onPlay(video) },
                        onWatchLater = { onWatchLater(video) }
                    )
                }
            }
        }
    }
}

private fun VideoItem.isShortForChannel(): Boolean {
    if (durationMs in 1L..180_000L) return true
    val text = "$title $description $source".lowercase()
    return "/shorts/" in text || "#shorts" in text || "#short " in text
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}


private fun formatCompactNumber(value: Long): String = when {
    value >= 1_000_000_000L -> "%.1f mil M".format(value / 1_000_000_000.0).replace(".0", "")
    value >= 1_000_000L -> "%.1f M".format(value / 1_000_000.0).replace(".0", "")
    value >= 1_000L -> "%.1f K".format(value / 1_000.0).replace(".0", "")
    else -> value.toString()
}

private fun formatPublishedAt(value: String): String {
    if (value.isBlank()) return ""
    if (value.contains("hace", ignoreCase = true)) return value

    val parsers = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd"
    )
    val publishedMs = parsers.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(value.take(if (pattern == "yyyy-MM-dd") 10 else value.length))?.time
        }.getOrNull()
    } ?: return value.take(10)

    val elapsedMs = (System.currentTimeMillis() - publishedMs).coerceAtLeast(0L)
    val minutes = elapsedMs / 60_000L
    val hours = elapsedMs / 3_600_000L
    val days = elapsedMs / 86_400_000L
    val weeks = days / 7L
    val months = days / 30L
    val years = days / 365L

    return when {
        minutes < 1L -> "hace unos segundos"
        minutes < 60L -> "hace $minutes ${if (minutes == 1L) "minuto" else "minutos"}"
        hours < 24L -> "hace $hours ${if (hours == 1L) "hora" else "horas"}"
        days < 7L -> "hace $days ${if (days == 1L) "día" else "días"}"
        weeks < 5L -> "hace $weeks ${if (weeks == 1L) "semana" else "semanas"}"
        months < 12L -> "hace $months ${if (months == 1L) "mes" else "meses"}"
        else -> "hace $years ${if (years == 1L) "año" else "años"}"
    }
}


private fun formatLastSync(timestampMs: Long): String {
    val elapsed = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    return when {
        minutes < 1L -> "ahora"
        minutes < 60L -> "hace $minutes min"
        minutes < 1_440L -> "hace ${minutes / 60L} h"
        else -> "hace ${minutes / 1_440L} días"
    }
}

private fun enterPictureInPicture(context: Context, onMessage: (String) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        onMessage("La ventana flotante requiere Android 8 o posterior.")
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onMessage("No se pudo abrir la ventana flotante.")
        return
    }
    val params = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .build()
    runCatching { activity.enterPictureInPictureMode(params) }
        .onFailure { onMessage("El dispositivo no permitió la ventana flotante.") }
}

private fun openExternalVideo(context: Context, video: VideoItem, preferYouTubeApp: Boolean) {
    val webUrl = if (video.mediaKind == MediaKind.YOUTUBE) {
        "https://www.youtube.com/watch?v=${video.id}"
    } else {
        video.source
    }
    if (preferYouTubeApp && video.mediaKind == MediaKind.YOUTUBE) {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:${video.id}"))
        if (appIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(appIntent)
            return
        }
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
}

@Composable
private fun VideoCard(
    video: VideoItem,
    isWatchLater: Boolean,
    onPlay: () -> Unit,
    onWatchLater: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Thumbnail(video.thumbnailUrl, Modifier.fillMaxSize())
            if (video.isLive) {
                Text(
                    "EN VIVO",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color(0xFFD32F2F), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            ResumeProgress(video, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.Top) {
            ChannelAvatar(
                url = video.channelThumbnailUrl,
                channelTitle = video.channelTitle,
                size = 42.dp
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(video.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.channelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isWatchLater) "Quitar de Ver después" else "Guardar en Ver después") },
                        leadingIcon = { Icon(Icons.Default.WatchLater, null) },
                        onClick = { menu = false; onWatchLater() }
                    )
                    DropdownMenuItem(
                        text = { Text("Reproducir") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                        onClick = { menu = false; onPlay() }
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedVideoCard(video: VideoItem, onPlay: () -> Unit, onWatchLater: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(168.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.Black)
        ) {
            Thumbnail(video.thumbnailUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (video.durationMs > 0L) {
                Text(
                    formatDuration(video.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.84f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            if (video.isLive) {
                Text(
                    "EN VIVO",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .background(Color(0xFFD32F2F), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            ResumeProgress(video, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp, top = 1.dp)) {
            Text(
                video.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                video.channelTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp)
            )
            val published = formatPublishedAt(video.publishedAt)
            if (published.isNotBlank()) {
                Text(
                    published,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, "Opciones")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Ver después") },
                    leadingIcon = { Icon(Icons.Outlined.WatchLater, null) },
                    onClick = {
                        menuExpanded = false
                        onWatchLater()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Reproducir") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                    onClick = {
                        menuExpanded = false
                        onPlay()
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactVideoRow(video: VideoItem, onPlay: () -> Unit, onWatchLater: () -> Unit) {
    Card(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(142.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
                Thumbnail(video.thumbnailUrl, Modifier.fillMaxSize())
                if (video.isLive) Text("LIVE", modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Red, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp), fontWeight = FontWeight.Bold)
                ResumeProgress(video, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(
                    modifier = Modifier.padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChannelAvatar(video.channelThumbnailUrl, video.channelTitle, 20.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        video.channelTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onWatchLater) { Icon(Icons.Outlined.WatchLater, "Ver después") }
        }
    }
}

@Composable
private fun HorizontalVideos(videos: List<VideoItem>, onPlay: (VideoItem) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(videos, key = { "horizontal-${it.id}" }) { video ->
            Card(onClick = { onPlay(video) }, modifier = Modifier.width(240.dp)) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    Thumbnail(video.thumbnailUrl, Modifier.fillMaxSize())
                    ResumeProgress(video, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                }
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(video.channelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                }
            }
        }
    }
}


@Composable
private fun ResumeProgress(video: VideoItem, modifier: Modifier = Modifier) {
    if (video.durationMs <= 0L || video.resumePositionMs <= 0L) return
    val progress = (video.resumePositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.height(4.dp)
    )
}

@Composable
private fun PlaylistCard(playlist: PlaylistItem, onClick: () -> Unit = {}) {
    Column(modifier = Modifier.width(148.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF1B1A20))
        ) {
            Thumbnail(playlist.thumbnailUrl, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(
                "${playlist.itemCount}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.76f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Playlist",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ChannelCard(channel: ChannelItem, onClick: () -> Unit) {
    Column(modifier = Modifier.width(112.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val context = LocalContext.current
        val request = remember(channel.thumbnailUrl) {
            ImageRequest.Builder(context)
                .data(channel.thumbnailUrl)
                .size(180)
                .crossfade(false)
                .build()
        }
        AsyncImage(model = request, contentDescription = channel.title, modifier = Modifier.size(82.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        Text(channel.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ProfileAvatar(profile: GoogleProfile?, size: androidx.compose.ui.unit.Dp, onClick: (() -> Unit)?) {
    val modifier = Modifier.size(size).clip(CircleShape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    if (profile?.pictureUrl.isNullOrBlank()) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(profile?.name?.take(1)?.uppercase() ?: "G", fontWeight = FontWeight.Bold)
        }
    } else {
        val context = LocalContext.current
        val request = remember(profile?.pictureUrl) {
            ImageRequest.Builder(context)
                .data(profile?.pictureUrl)
                .size(220)
                .crossfade(false)
                .build()
        }
        AsyncImage(model = request, contentDescription = "Cuenta", modifier = modifier, contentScale = ContentScale.Crop)
    }
}

@Composable
private fun ChannelAvatar(
    url: String,
    channelTitle: String,
    size: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val modifier = Modifier.size(size).clip(CircleShape)
    var imageFailed by remember(url) { mutableStateOf(false) }
    if (url.isBlank() || imageFailed) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(channelTitle.take(1).uppercase().ifBlank { "G" }, fontWeight = FontWeight.Bold)
        }
    } else {
        val request = remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .size(160)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = channelTitle,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            onError = { imageFailed = true }
        )
    }
}

@Composable
private fun Thumbnail(
    url: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    requestWidth: Int = 640,
    requestHeight: Int = 360
) {
    var imageFailed by remember(url) { mutableStateOf(false) }
    if (url.isBlank() || imageFailed) {
        Box(
            modifier = modifier.background(Color(0xFF202024)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(42.dp), tint = Color.White.copy(alpha = 0.34f))
        }
    } else {
        val context = LocalContext.current
        val request = remember(url, requestWidth, requestHeight) {
            ImageRequest.Builder(context)
                .data(url)
                .size(requestWidth, requestHeight)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = modifier.background(Color.Black),
            contentScale = contentScale,
            onError = { imageFailed = true }
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun LoadingBlock() {
    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun EmptyBlock(message: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Text(message, modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class PendingDownload(
    val title: String,
    val url: String,
    val wifiOnly: Boolean
)

private data class DownloadStatusInfo(
    val label: String,
    val progress: Float?,
    val completed: Boolean,
    val active: Boolean = false,
    val canResume: Boolean = false
)

@Composable
private fun DownloadStatusCard(
    video: VideoItem,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val status by produceState(
        initialValue = DownloadStatusInfo("Consultando…", null, false),
        key1 = video.downloadId
    ) {
        while (true) {
            value = queryDownloadStatus(context, video.downloadId)
            delay(if (value.completed) 8_000L else 1_500L)
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable {
            if (video.downloadId < -1L && status.completed) {
                if (!openResolvedMediaDownload(context, video.downloadId)) onOpen()
            } else {
                onOpen()
            }
        }
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (status.completed) Icons.Default.DownloadDone else Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(video.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(status.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                status.progress?.let { progress ->
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            if (video.downloadId < -1L && (status.active || status.canResume)) {
                IconButton(onClick = {
                    if (status.active) {
                        pauseResolvedMediaDownload(context, video.downloadId)
                    } else {
                        resumeResolvedMediaDownload(context, video.downloadId)
                    }
                }) {
                    Icon(
                        if (status.active) Icons.Default.Pause else Icons.Default.Replay,
                        if (status.active) "Pausar descarga" else "Continuar descarga"
                    )
                }
            }
            IconButton(onClick = {
                if (video.downloadId < -1L) {
                    cancelResolvedMediaDownload(context, video.downloadId, deletePublishedFile = true)
                } else {
                    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    if (video.downloadId >= 0L) manager.remove(video.downloadId)
                }
                onRemove()
            }) {
                Icon(Icons.Default.Delete, "Eliminar descarga")
            }
        }
    }
}

@Composable
private fun DirectDownloadDialog(onDismiss: () -> Unit, onDownload: (String, String, Boolean) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var wifiOnly by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descarga directa") },
        text = {
            Column {
                Text(
                    "Pega una URL directa a un archivo de video propio o autorizado. No acepta enlaces normales de YouTube.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Enlace directo http/https") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    singleLine = true
                )
                SettingSwitch(
                    title = "Solo Wi-Fi",
                    subtitle = "Espera una red no medida antes de descargar",
                    checked = wifiOnly,
                    onChange = { wifiOnly = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDownload(title.ifBlank { "Geo Video" }, url, wifiOnly) },
                enabled = url.isNotBlank()
            ) { Text("Descargar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}


private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun enqueueDirectDownload(context: Context, title: String, url: String, wifiOnly: Boolean): Long {
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return -1L
    if (uri.scheme != "http" && uri.scheme != "https") return -1L
    val extension = uri.lastPathSegment
        ?.substringAfterLast('.', "mp4")
        ?.takeIf { it.matches(Regex("[A-Za-z0-9]{2,5}")) }
        ?.lowercase()
        ?: "mp4"
    val safeName = title.ifBlank { "GeoVideo" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('_')
        .ifBlank { "GeoVideo" }
        .take(80)
    val request = DownloadManager.Request(uri)
        .setTitle(title.ifBlank { "Geo Video" })
        .setDescription(if (wifiOnly) "Esperando Wi-Fi si es necesario" else "Descarga iniciada desde Geo Videos")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(!wifiOnly)
        .setAllowedOverRoaming(false)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "GeoVideos/$safeName.$extension")
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return runCatching { manager.enqueue(request) }.getOrDefault(-1L)
}

private fun queryDownloadStatus(context: Context, downloadId: Long): DownloadStatusInfo {
    if (downloadId < -1L) {
        val status = queryResolvedMediaDownload(context, downloadId)
            ?: return DownloadStatusInfo("No encontrada en el teléfono", null, false)
        val percent = status.progress?.let { " ${(it * 100).toInt()}%" }.orEmpty()
        val size = if (status.bytesDownloaded > 0L) {
            val downloaded = formatDownloadBytes(status.bytesDownloaded)
            val total = status.totalBytes.takeIf { it > 0L }?.let(::formatDownloadBytes)
            if (total != null) " · $downloaded de $total" else " · $downloaded"
        } else ""
        val transfer = buildString {
            if (status.speedBytesPerSecond > 0L) {
                append(" · ${formatDownloadBytes(status.speedBytesPerSecond)}/s")
            }
            if (status.remainingTimeMs > 0L) {
                append(" · ${formatRemainingDownloadTime(status.remainingTimeMs)}")
            }
        }
        val label = when (status.stage) {
            GeoDownloadStage.QUEUED -> "En cola"
            GeoDownloadStage.VIDEO -> "Descargando video$percent$size$transfer"
            GeoDownloadStage.AUDIO -> "Descargando audio$percent$size$transfer"
            GeoDownloadStage.MERGING -> "Uniendo video y audio"
            GeoDownloadStage.PUBLISHING -> "Guardando el archivo final"
            GeoDownloadStage.PAUSED -> "Pausada$percent$size"
            GeoDownloadStage.COMPLETED -> "Completada · toca para reproducir"
            GeoDownloadStage.FAILED -> status.error ?: "Falló la descarga"
            GeoDownloadStage.CANCELED -> "Cancelada"
        }
        return DownloadStatusInfo(
            label = label,
            progress = status.progress,
            completed = status.stage == GeoDownloadStage.COMPLETED,
            active = status.isActive,
            canResume = status.stage == GeoDownloadStage.PAUSED || status.stage == GeoDownloadStage.FAILED
        )
    }
    if (downloadId < 0L) return DownloadStatusInfo("Registro antiguo", null, false)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val cursor = runCatching { manager.query(DownloadManager.Query().setFilterById(downloadId)) }.getOrNull()
        ?: return DownloadStatusInfo("No se pudo consultar", null, false)
    cursor.use {
        if (!it.moveToFirst()) return DownloadStatusInfo("No encontrada en el teléfono", null, false)
        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val progress = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
        return when (status) {
            DownloadManager.STATUS_PENDING -> DownloadStatusInfo("En cola", progress, false)
            DownloadManager.STATUS_RUNNING -> {
                val percent = progress?.let { value -> " ${(value * 100).toInt()}%" }.orEmpty()
                DownloadStatusInfo("Descargando$percent", progress, false, active = true)
            }
            DownloadManager.STATUS_PAUSED -> DownloadStatusInfo("Pausada por el sistema", progress, false)
            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatusInfo("Completada · toca para abrir Descargas", 1f, true)
            DownloadManager.STATUS_FAILED -> DownloadStatusInfo("Falló la descarga", progress, false)
            else -> DownloadStatusInfo("Estado desconocido", progress, false)
        }
    }
}

private fun formatDownloadBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    }
}

private fun formatRemainingDownloadTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(1L)
    val minutes = seconds / 60L
    val remainder = seconds % 60L
    return if (minutes > 0L) "${minutes}m ${remainder}s" else "${remainder}s"
}

private fun openSystemDownloads(context: Context) {
    val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
    runCatching { context.startActivity(intent) }
}

private fun shareVideo(context: Context, video: VideoItem) {
    val url = if (video.mediaKind == MediaKind.YOUTUBE) "https://www.youtube.com/watch?v=${video.id}" else video.source
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${video.title}\n$url")
    }
    context.startActivity(Intent.createChooser(intent, "Compartir video"))
}
