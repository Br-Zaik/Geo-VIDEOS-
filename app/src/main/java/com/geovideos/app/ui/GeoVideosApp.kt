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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
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
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.dp
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
import com.geovideos.app.playback.GeoPlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val PACKAGE_NAME = "com.geovideos.app"
private const val DEV_SHA1 = "61:39:FF:D0:D5:6B:DC:06:FA:13:AD:3D:7A:88:93:9F:6D:4A:52:7F"

@Composable
fun GeoVideosApp(
    viewModel: GeoVideosViewModel,
    onConnectGoogle: () -> Unit,
    onSwitchGoogleAccount: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val playerConnection = remember(context.applicationContext) {
        GeoPlayerConnection.get(context.applicationContext)
    }
    var autoAdvancedVideoId by remember { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val selectedVideo = state.selectedVideo
    LaunchedEffect(selectedVideo?.id, state.autoplay, state.dataSaver, state.section, state.playerExpanded) {
        autoAdvancedVideoId = ""
        selectedVideo?.let { video ->
            val shouldAutoplay = if (state.section == MainSection.SHORTS && !state.playerExpanded) true else state.autoplay
            playerConnection.open(
                video = video,
                autoplay = shouldAutoplay,
                dataSaver = state.dataSaver,
                repeat = state.section == MainSection.SHORTS && !state.playerExpanded
            )
        }
    }

    LaunchedEffect(state.autoplay, selectedVideo?.id) {
        playerConnection.endedEvents.collect { endedVideoId ->
            val current = selectedVideo
            if (state.section != MainSection.SHORTS && state.autoplay && current?.id == endedVideoId && autoAdvancedVideoId != endedVideoId) {
                autoAdvancedVideoId = endedVideoId
                delay(900L)
                viewModel.playNext()
            }
        }
    }

    PlaybackProgressSaver(
        video = selectedVideo,
        playerConnection = playerConnection,
        onSavePlayback = viewModel::savePlayback
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.authStatus != AuthStatus.CONNECTED) {
            GoogleConnectScreen(
                status = state.authStatus,
                error = state.authError,
                onConnect = onConnectGoogle
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                MainShell(
                    state = state,
                    snackbar = snackbar,
                    playerConnection = playerConnection,
                    onConnectGoogle = onConnectGoogle,
                    onSwitchGoogleAccount = onSwitchGoogleAccount,
                    onSection = viewModel::selectSection,
                    onCategory = viewModel::selectHomeCategory,
                    onPlay = viewModel::play,
                    onPreviewShort = viewModel::previewShort,
                    onWatchLater = viewModel::toggleWatchLater,
                    onLike = viewModel::toggleLocalLike,
                    onDislike = viewModel::toggleLocalDislike,
                    onSearch = viewModel::search,
                    onRefresh = viewModel::refresh,
                    onLoadMoreHome = viewModel::loadMoreHome,
                    onLoadMoreShorts = viewModel::loadMoreShorts,
                    onLoadMoreUploads = viewModel::loadMoreUploads,
                    onLoadMoreLiked = viewModel::loadMoreLiked,
                    onLoadMoreSearch = viewModel::loadMoreSearch,
                    onOpenChannel = viewModel::openChannel,
                    onCloseChannel = viewModel::closeChannel,
                    onDisconnect = { playerConnection.stop(); viewModel.disconnect() },
                    onClearData = viewModel::clearLocalData,
                    onRegisterDownload = viewModel::registerDownload,
                    onRemoveDownload = viewModel::removeDownload,
                    onAutoplayChange = viewModel::setAutoplay,
                    onDataSaverChange = viewModel::setDataSaver,
                    onNotificationsChange = viewModel::setNotificationsEnabled,
                    onMessage = viewModel::showMessage
                )

                if (state.playerExpanded && selectedVideo != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PlayerScreen(
                            video = selectedVideo,
                            playerConnection = playerConnection,
                            isWatchLater = state.watchLater.any { it.id == selectedVideo.id },
                            isLiked = selectedVideo.id in state.localLikedIds,
                            isDisliked = selectedVideo.id in state.localDislikedIds,
                            autoplay = state.autoplay,
                            dataSaver = state.dataSaver,
                            details = state.playerDetails,
                            detailsLoading = state.playerDetailsLoading,
                            relatedVideos = state.relatedVideos,
                            relatedLoading = state.relatedLoading,
                            relatedLoadingMore = state.relatedLoadingMore,
                            relatedCanLoadMore = state.relatedCanLoadMore,
                            onBack = viewModel::minimizePlayer,
                            onClose = {
                                playerConnection.stop()
                                viewModel.closePlayer()
                            },
                            onWatchLater = { viewModel.toggleWatchLater(selectedVideo) },
                            onLike = { viewModel.toggleLocalLike(selectedVideo) },
                            onDislike = { viewModel.toggleLocalDislike(selectedVideo) },
                            onPlayRelated = viewModel::play,
                            onPlayNext = viewModel::playNext,
                            onAutoplayChange = viewModel::setAutoplay,
                            onWatchLaterRelated = viewModel::toggleWatchLater,
                            onLoadMoreRelated = viewModel::loadMoreRelated,
                            onOpenChannel = { channel ->
                                viewModel.openChannel(channel)
                                viewModel.minimizePlayer()
                            },
                            onSavePlayback = viewModel::savePlayback,
                            onRegisterDownload = viewModel::registerDownload,
                            onMessage = viewModel::showMessage
                        )
                    }
                } else if (state.section != MainSection.SHORTS) {
                    selectedVideo?.let { video ->
                        MiniPlayer(
                            video = video,
                            playerConnection = playerConnection,
                            onExpand = viewModel::expandPlayer,
                            onClose = {
                                playerConnection.stop()
                                viewModel.closePlayer()
                            },
                            onSavePlayback = viewModel::savePlayback,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 80.dp)
                        )
                    }
                }
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
    val playback by playerConnection.state.collectAsStateWithLifecycle()
    var lastSavedVideoId by remember { mutableStateOf("") }
    var lastSavedPositionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(video?.id, playback.positionMs, playback.durationMs) {
        val selected = video ?: return@LaunchedEffect
        if (playback.currentVideoId != selected.id) return@LaunchedEffect
        if (lastSavedVideoId != selected.id) {
            lastSavedVideoId = selected.id
            lastSavedPositionMs = selected.resumePositionMs
        }
        if (kotlin.math.abs(playback.positionMs - lastSavedPositionMs) >= 10_000L) {
            lastSavedPositionMs = playback.positionMs
            onSavePlayback(selected, playback.positionMs, playback.durationMs)
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
                Text("Abriendo Google…")
            } else {
                Icon(Icons.Default.AccountCircle, null)
                Spacer(Modifier.width(10.dp))
                Text("Continuar con Google")
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
    onSection: (MainSection) -> Unit,
    onCategory: (HomeCategory) -> Unit,
    onPlay: (VideoItem) -> Unit,
    onPreviewShort: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onLike: (VideoItem) -> Unit,
    onDislike: (VideoItem) -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreHome: (HomeCategory) -> Unit,
    onLoadMoreShorts: () -> Unit,
    onLoadMoreUploads: () -> Unit,
    onLoadMoreLiked: () -> Unit,
    onLoadMoreSearch: () -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onCloseChannel: () -> Unit,
    onDisconnect: () -> Unit,
    onClearData: () -> Unit,
    onRegisterDownload: (String, String, Long) -> Unit,
    onRemoveDownload: (Long) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onDataSaverChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<PendingDownload?>(null) }
    var libraryDestination by rememberSaveable { mutableStateOf(LibraryDestination.ROOT) }

    LaunchedEffect(state.section) {
        if (state.section != MainSection.LIBRARY) libraryDestination = LibraryDestination.ROOT
    }
    BackHandler(enabled = state.section == MainSection.LIBRARY && libraryDestination != LibraryDestination.ROOT) {
        libraryDestination = LibraryDestination.ROOT
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
            onBack = { showNotifications = false },
            onPlay = {
                showNotifications = false
                onPlay(it)
            }
        )
        return
    }

    if (state.selectedChannelTitle.isNotBlank()) {
        ChannelScreen(
            title = state.selectedChannelTitle,
            videos = state.channelVideos,
            loading = state.loading,
            onBack = onCloseChannel,
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
                    IconButton(onClick = { showNotifications = true }) {
                        BadgedBox(badge = {
                            if (state.notifications.isNotEmpty()) Badge { Text(state.notifications.size.coerceAtMost(99).toString()) }
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
                    if (target == MainSection.LIBRARY && state.section == MainSection.LIBRARY) {
                        libraryDestination = LibraryDestination.ROOT
                    }
                    onSection(target)
                }
                BottomItem(MainSection.HOME, state.section, "Principal", Icons.Default.Home, navigate)
                BottomItem(MainSection.SHORTS, state.section, "Shorts", Icons.Default.AutoAwesome, navigate)
                BottomItem(MainSection.SEARCH, state.section, "Buscar", Icons.Default.Search, navigate)
                BottomItem(MainSection.LIBRARY, state.section, "Colección", Icons.Default.VideoLibrary, navigate)
                BottomItem(MainSection.ACCOUNT, state.section, "Cuenta", Icons.Default.Person, navigate)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when (state.section) {
            MainSection.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                category = state.homeCategory,
                personalized = state.personalized,
                popular = state.popular,
                live = state.live,
                gaming = state.gaming,
                music = state.music,
                shorts = state.shorts,
                loading = state.loading,
                refreshing = state.refreshing,
                loadingMore = state.isLoadingMore(state.homeCategory),
                canLoadMore = state.canLoadMore(state.homeCategory),
                lastSyncMs = state.lastSyncMs,
                watchLater = state.watchLater,
                onRefresh = onRefresh,
                onLoadMore = onLoadMoreHome,
                onCategory = onCategory,
                onPlay = onPlay,
                onOpenShort = onPreviewShort,
                onWatchLater = onWatchLater
            )
            MainSection.SHORTS -> ShortsScreen(
                modifier = Modifier.padding(padding),
                videos = state.shorts,
                selectedVideoId = state.selectedVideo?.id.orEmpty(),
                localLikedIds = state.localLikedIds,
                localDislikedIds = state.localDislikedIds,
                loading = state.loading,
                loadingMore = state.shortsLoadingMore,
                canLoadMore = state.shortsCanLoadMore,
                playerConnection = playerConnection,
                playerExpanded = state.playerExpanded,
                dataSaver = state.dataSaver,
                onLoadMore = onLoadMoreShorts,
                onPreview = onPreviewShort,
                onWatchLater = onWatchLater,
                onLike = onLike,
                onDislike = onDislike
            )
            MainSection.SEARCH -> SearchScreen(
                modifier = Modifier.padding(padding),
                results = state.searchResults,
                history = state.searchHistory,
                loading = state.loading,
                loadingMore = state.searchLoadingMore,
                onSearch = onSearch,
                onLoadMore = onLoadMoreSearch,
                onPlay = onPlay,
                onWatchLater = onWatchLater
            )
            MainSection.LIBRARY -> {
                if (libraryDestination == LibraryDestination.ROOT) {
                    LibraryScreen(
                        modifier = Modifier.padding(padding),
                        history = state.history,
                        watchLater = state.watchLater,
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
                        onOpenUploads = { libraryDestination = LibraryDestination.UPLOADS },
                        onOpenSubscriptions = { libraryDestination = LibraryDestination.SUBSCRIPTIONS },
                        onAddDownload = { showDownloadDialog = true },
                        onRemoveDownload = onRemoveDownload
                    )
                } else if (libraryDestination == LibraryDestination.SUBSCRIPTIONS) {
                    SubscriptionCollectionScreen(
                        modifier = Modifier.padding(padding),
                        channels = state.subscriptions,
                        onBack = { libraryDestination = LibraryDestination.ROOT },
                        onOpenChannel = onOpenChannel
                    )
                } else {
                    val collectionVideos = when (libraryDestination) {
                        LibraryDestination.HISTORY -> state.history
                        LibraryDestination.WATCH_LATER -> state.watchLater
                        LibraryDestination.LIKED -> (state.localLikedVideos + state.liked).distinctBy { it.id }
                        LibraryDestination.UPLOADS -> state.uploads
                        LibraryDestination.ROOT, LibraryDestination.SUBSCRIPTIONS -> emptyList()
                    }
                    LibraryCollectionScreen(
                        modifier = Modifier.padding(padding),
                        destination = libraryDestination,
                        videos = collectionVideos,
                        loadingMore = libraryDestination == LibraryDestination.LIKED && state.likedLoadingMore,
                        canLoadMore = libraryDestination == LibraryDestination.LIKED && state.likedCanLoadMore,
                        onLoadMore = if (libraryDestination == LibraryDestination.LIKED) onLoadMoreLiked else null,
                        onBack = { libraryDestination = LibraryDestination.ROOT },
                        onPlay = onPlay,
                        onWatchLater = onWatchLater
                    )
                }
            }
            MainSection.ACCOUNT -> AccountScreen(
                modifier = Modifier.padding(padding),
                profile = state.profile,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    modifier: Modifier,
    category: HomeCategory,
    personalized: List<VideoItem>,
    popular: List<VideoItem>,
    live: List<VideoItem>,
    gaming: List<VideoItem>,
    music: List<VideoItem>,
    shorts: List<VideoItem>,
    loading: Boolean,
    refreshing: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    lastSyncMs: Long,
    watchLater: List<VideoItem>,
    onRefresh: () -> Unit,
    onLoadMore: (HomeCategory) -> Unit,
    onCategory: (HomeCategory) -> Unit,
    onPlay: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) {
    val videos = when (category) {
        HomeCategory.FOR_YOU -> personalized.ifEmpty { popular }
        HomeCategory.LIVE -> live
        HomeCategory.GAMING -> gaming
        HomeCategory.MUSIC -> music
    }
    val mixVideo = if (category == HomeCategory.FOR_YOU) videos.firstOrNull() else null
    val displayVideos = if (category == HomeCategory.FOR_YOU && videos.size > 1) videos.drop(1) else videos
    val watchLaterIds = remember(watchLater) { watchLater.mapTo(HashSet<String>()) { it.id } }
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(listState, displayVideos.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && displayVideos.isNotEmpty() && lastVisible >= total - 4
        }
    }

    LaunchedEffect(shouldLoadMore, loadingMore, canLoadMore, category) {
        if (shouldLoadMore && !loadingMore && canLoadMore) onLoadMore(category)
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(contentType = "categories") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    item { CategoryChip("Para ti", Icons.Default.Explore, category == HomeCategory.FOR_YOU) { onCategory(HomeCategory.FOR_YOU) } }
                    item { CategoryChip("En vivo", Icons.Default.LiveTv, category == HomeCategory.LIVE) { onCategory(HomeCategory.LIVE) } }
                    item { CategoryChip("Juegos", Icons.Default.Games, category == HomeCategory.GAMING) { onCategory(HomeCategory.GAMING) } }
                    item { CategoryChip("Música", Icons.Default.PlaylistPlay, category == HomeCategory.MUSIC) { onCategory(HomeCategory.MUSIC) } }
                }
            }
            item(contentType = "heading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when (category) {
                                HomeCategory.FOR_YOU -> "Principal"
                                HomeCategory.LIVE -> "En directo"
                                HomeCategory.GAMING -> "Videojuegos"
                                HomeCategory.MUSIC -> "Música"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            when (category) {
                                HomeCategory.FOR_YOU -> "Novedades de tus canales, Me gusta e historial"
                                HomeCategory.LIVE -> "Transmisiones públicas disponibles"
                                HomeCategory.GAMING -> "Videos populares de juegos"
                                HomeCategory.MUSIC -> "Videos musicales populares"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (lastSyncMs > 0L) {
                item(contentType = "sync") {
                    Text(
                        "Última actualización: ${formatLastSync(lastSyncMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
            if (category == HomeCategory.FOR_YOU && mixVideo != null) {
                item(key = "home-mix", contentType = "home-mix") {
                    HomeMixSection(mixVideo, onPlay)
                }
            }
            if (category == HomeCategory.FOR_YOU && shorts.isNotEmpty()) {
                item(key = "home-shorts", contentType = "home-shorts") {
                    HomeShortsShelf(shorts, onOpenShort)
                }
            }
            if (loading && videos.isEmpty()) {
                item(contentType = "loading") { LoadingBlock() }
            } else if (videos.isEmpty()) {
                item(contentType = "empty") { EmptyBlock("No se encontraron videos en esta sección. Pulsa actualizar.") }
            } else if (displayVideos.isNotEmpty()) {
                items(
                    items = displayVideos,
                    key = { "home-${category.name}-${it.id}" },
                    contentType = { "video" }
                ) { video ->
                    VideoCard(
                        video = video,
                        isWatchLater = video.id in watchLaterIds,
                        onPlay = { onPlay(video) },
                        onWatchLater = { onWatchLater(video) }
                    )
                    HorizontalDivider()
                }
            }
            if (loadingMore) {
                item(contentType = "more-loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            } else if (!canLoadMore && videos.isNotEmpty()) {
                item(contentType = "end") {
                    Text(
                        "Ya viste los videos disponibles por ahora.",
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) }
    )
}

@Composable
private fun FeaturedCard(video: VideoItem, onPlay: (VideoItem) -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        onClick = { onPlay(video) }
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            Thumbnail(video.thumbnailUrl, Modifier.fillMaxSize())
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)))
                )
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                if (video.isLive) AssistChip(onClick = {}, label = { Text("EN VIVO") }, leadingIcon = { Icon(Icons.Default.LiveTv, null) })
                Text(video.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.channelTitle, color = Color.White.copy(alpha = 0.78f), modifier = Modifier.padding(top = 5.dp))
            }
            FilledIconButton(onClick = { onPlay(video) }, modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)) {
                Icon(Icons.Default.PlayArrow, "Reproducir")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortsScreen(
    modifier: Modifier,
    videos: List<VideoItem>,
    selectedVideoId: String,
    localLikedIds: Set<String>,
    localDislikedIds: Set<String>,
    loading: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    playerConnection: GeoPlayerConnection,
    playerExpanded: Boolean,
    dataSaver: Boolean,
    onLoadMore: () -> Unit,
    onPreview: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onLike: (VideoItem) -> Unit,
    onDislike: (VideoItem) -> Unit
) {
    if (loading && videos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingBlock() }
        return
    }
    if (videos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyBlock("No encontramos Shorts relacionados con tus suscripciones e historial.")
        }
        return
    }

    val initialShortPage = remember(videos, selectedVideoId) { videos.indexOfFirst { it.id == selectedVideoId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialShortPage, pageCount = { videos.size })
    val controller by playerConnection.controller.collectAsStateWithLifecycle()
    val playback by remember(playerConnection) {
        playerConnection.state
            .map { state ->
                state.copy(positionMs = 0L, durationMs = 0L, bufferedPercentage = 0)
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = com.geovideos.app.playback.PlaybackUiState())

    LaunchedEffect(selectedVideoId, videos.map { it.id }) {
        val index = videos.indexOfFirst { it.id == selectedVideoId }
        if (index >= 0 && index != pagerState.currentPage) pagerState.scrollToPage(index)
    }

    LaunchedEffect(pagerState.currentPage, videos.size) {
        val current = videos.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        onPreview(current)
        playerConnection.setRepeat(true)
        delay(700L)
        playerConnection.preload(videos.drop(pagerState.currentPage + 1).take(1), dataSaver)
    }
    LaunchedEffect(pagerState.currentPage, videos.size, loadingMore, canLoadMore) {
        if (pagerState.currentPage >= videos.lastIndex - 2 && !loadingMore && canLoadMore) onLoadMore()
    }
    LaunchedEffect(playerExpanded, pagerState.currentPage) {
        if (!playerExpanded) playerConnection.setRepeat(true)
    }
    DisposableEffect(Unit) {
        onDispose { playerConnection.setRepeat(false) }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val video = videos[page]
            val isCurrent = page == pagerState.currentPage && playback.currentVideoId == video.id
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black).clickable {
                    if (isCurrent && playback.isPlaying) playerConnection.pause()
                    else if (isCurrent) playerConnection.play()
                    else onPreview(video)
                }
            ) {
                Thumbnail(
                    url = video.thumbnailUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (!playerExpanded && isCurrent && controller != null && !playback.resolving) {
                    GeoMediaPlayerView(
                        controller = controller!!,
                        useController = false,
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.24f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.86f)
                            )
                        )
                    )
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.46f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 36.dp)
                ) {
                    Text(
                        "Para ti · Tus gustos",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }

                if (isCurrent && (playback.resolving || playback.connecting)) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(42.dp),
                        strokeWidth = 4.dp
                    )
                } else if (isCurrent && !playback.isPlaying) {
                    FilledIconButton(
                        onClick = { playerConnection.play() },
                        modifier = Modifier.align(Alignment.Center).size(66.dp)
                    ) { Icon(Icons.Default.PlayArrow, "Reproducir", modifier = Modifier.size(40.dp)) }
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.80f).padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelAvatar(video.channelThumbnailUrl, video.channelTitle, 38.dp)
                        Text(
                            video.channelTitle,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 10.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        video.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { onLike(video) },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.ThumbUp,
                            "Me gusta",
                            tint = if (video.id in localLikedIds) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(
                        onClick = { onDislike(video) },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.ThumbDown,
                            "No me gusta",
                            tint = if (video.id in localDislikedIds) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(
                        onClick = { onWatchLater(video) },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) { Icon(Icons.Outlined.WatchLater, "Ver después", tint = Color.White) }
                }
                if (isCurrent) {
                    ShortProgress(
                        playerConnection = playerConnection,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                    )
                }
            }
        }
        if (loadingMore) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(25.dp),
                strokeWidth = 3.dp
            )
        }
    }
}

@Composable
private fun ShortProgress(
    playerConnection: GeoPlayerConnection,
    modifier: Modifier = Modifier
) {
    val playback by playerConnection.state.collectAsStateWithLifecycle()
    if (playback.durationMs > 0L) {
        LinearProgressIndicator(
            progress = { (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f) },
            modifier = modifier
        )
    }
}

@Composable
private fun SearchScreen(
    modifier: Modifier,
    results: List<VideoItem>,
    history: List<String>,
    loading: Boolean,
    loadingMore: Boolean,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(listState, results.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && results.isNotEmpty() && lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, loadingMore) {
        if (shouldLoadMore && !loadingMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(contentType = "search-box") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    FilledIconButton(onClick = { onSearch(query) }, enabled = query.isNotBlank()) {
                        Icon(Icons.Default.Search, "Buscar")
                    }
                },
                label = { Text("Buscar videos, música, juegos…") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch(query) })
            )
        }
        if (query.isBlank() && results.isEmpty() && history.isNotEmpty()) {
            item(contentType = "history-title") { SectionHeader("Búsquedas recientes", "Toca una para repetirla") }
            items(history, key = { "history-$it" }, contentType = { "history" }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                        query = item
                        onSearch(item)
                    }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, null)
                    Spacer(Modifier.width(14.dp))
                    Text(item, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Search, null)
                }
            }
        }
        if (loading) {
            item(contentType = "search-loading") { LoadingBlock() }
        } else {
            items(results, key = { "search-${it.id}" }, contentType = { "search-result" }) { video ->
                CompactVideoRow(video, { onPlay(video) }, { onWatchLater(video) })
            }
        }
        if (loadingMore) {
            item(contentType = "search-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
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
    onOpenUploads: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onAddDownload: () -> Unit,
    onRemoveDownload: (Long) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LibraryShortcutRow(
                history = history.size,
                later = watchLater.size,
                liked = liked.size,
                onHistory = onOpenHistory,
                onWatchLater = onOpenWatchLater,
                onLiked = onOpenLiked,
                onDownload = onAddDownload
            )
        }
        if (subscriptions.isNotEmpty()) {
            item {
                SubscriptionShelf(
                    subscriptions = subscriptions,
                    recentVideos = subscriptionVideos,
                    onOpenChannel = onOpenChannel,
                    onOpenSubscriptions = onOpenSubscriptions,
                    onPlay = onPlay
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenUploads).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionHeader("Mis videos", "Videos subidos a tu canal de YouTube")
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Abrir Mis videos")
            }
        }
        if (uploads.isEmpty()) {
            item { EmptyBlock("Tu canal no tiene videos subidos o YouTube no devolvió esa lista.") }
        } else {
            items(uploads, key = { "upload-${it.id}" }) { video ->
                CompactVideoRow(video, { onPlay(video) }, { onWatchLater(video) })
            }
            if (uploadsLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) }
                }
            } else if (uploadsCanLoadMore) {
                item {
                    OutlinedButton(onClick = onLoadMoreUploads, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cargar más de Mis videos")
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item { SectionHeader("Continuar viendo", "Retoma cada video desde el punto donde lo dejaste") }
            item { HorizontalVideos(history.take(12), onPlay) }
        }
        if (watchLater.isNotEmpty()) {
            item { SectionHeader("Ver después", "Lista local disponible en esta app") }
            items(watchLater.take(12), key = { "later-${it.id}" }) { video ->
                CompactVideoRow(video, { onPlay(video) }, { onWatchLater(video) })
            }
        }
        if (liked.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLiked),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionHeader("Videos que te gustan", "Sincronizados con tu cuenta de YouTube")
                    }
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Abrir videos que me gustan")
                }
            }
            item { HorizontalVideos(liked.take(12), onPlay) }
        }
        if (playlists.isNotEmpty()) {
            item { SectionHeader("Tus playlists", "Listas visibles para la cuenta autorizada") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(playlists, key = { "playlist-${it.id}" }) { playlist -> PlaylistCard(playlist) }
                }
            }
        }
        item { SectionHeader("Descargas directas", "Archivos propios o autorizados. YouTube no entrega el archivo para descargar.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAddDownload, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nueva descarga")
                }
                OutlinedButton(onClick = { openSystemDownloads(context) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DownloadDone, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ver archivos")
                }
            }
        }
        if (downloads.isEmpty()) {
            item { EmptyBlock("Todavía no registraste descargas directas.") }
        } else {
            items(downloads, key = { "download-${it.id}" }) { video ->
                DownloadStatusCard(
                    video = video,
                    onOpen = { openSystemDownloads(context) },
                    onRemove = { onRemoveDownload(video.downloadId) }
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
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
                items(subscriptions, key = { "subscription-shelf-${it.id}" }) { channel ->
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

        Spacer(Modifier.height(24.dp))
        SettingSwitch("Reproducción automática", "Inicia el video y pasa al siguiente al terminar", autoplay, onAutoplayChange)
        SettingSwitch("Ahorro de datos", "Prioriza una calidad menor y reduce el consumo de red", dataSaver, onDataSaverChange)
        SettingSwitch("Avisos en la app", "Sincroniza actividad disponible para la campana", notificationsEnabled, onNotificationsChange)

        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onSwitchAccount, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AccountCircle, null)
            Spacer(Modifier.width(10.dp))
            Text("Cambiar cuenta de Google")
        }
        OutlinedButton(onClick = onReconnect, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(10.dp))
            Text("Renovar acceso")
        }
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(10.dp))
            Text("Desconectar")
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
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Actividad") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (notifications.isEmpty()) {
                item { EmptyBlock("YouTube no devolvió actividad reciente. La API pública no expone toda la bandeja privada de notificaciones.") }
            } else {
                items(notifications, key = { "notification-${it.id}" }) { item ->
                    Card(onClick = { item.video?.let(onPlay) }, enabled = item.video != null) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Thumbnail(item.thumbnailUrl, Modifier.size(92.dp).clip(RoundedCornerShape(12.dp)))
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                            }
                            Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelScreen(
    title: String,
    videos: List<VideoItem>,
    loading: Boolean,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) item { LoadingBlock() }
            else if (videos.isEmpty()) item { EmptyBlock("No se encontraron publicaciones reproducibles de este canal.") }
            else items(videos, key = { "channel-${it.id}" }) { video ->
                CompactVideoRow(video, { onPlay(video) }, { onWatchLater(video) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(
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
    val activity = context.findActivity()
    val playback by remember(playerConnection) {
        playerConnection.state
            .map { state ->
                state.copy(
                    positionMs = 0L,
                    durationMs = if (state.durationMs > 0L) 1L else 0L,
                    bufferedPercentage = 0
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = com.geovideos.app.playback.PlaybackUiState())
    val controller by playerConnection.controller.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var isMuted by rememberSaveable(video.id) { mutableStateOf(false) }
    var repeatEnabled by rememberSaveable(video.id) { mutableStateOf(false) }
    var playbackSpeed by rememberSaveable(video.id) { mutableFloatStateOf(1f) }
    var selectedQuality by rememberSaveable(video.id) { mutableStateOf(if (dataSaver) 360 else 0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTimer by rememberSaveable { mutableStateOf(false) }
    var timerMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var descriptionExpanded by rememberSaveable(video.id) { mutableStateOf(false) }
    var isFullscreen by rememberSaveable(video.id) { mutableStateOf(false) }
    var fillScreenMode by rememberSaveable(video.id) { mutableStateOf(false) }
    var showDelayedLoading by remember(video.id) { mutableStateOf(false) }

    val detectedRatio = if (playback.videoWidth > 0 && playback.videoHeight > 0) {
        playback.videoWidth.toFloat() / playback.videoHeight.toFloat()
    } else {
        16f / 9f
    }
    val playerRatio = if (detectedRatio >= 1f) detectedRatio.coerceIn(4f / 3f, 2.1f) else 16f / 9f
    val qualityLabel = if (selectedQuality <= 0) "Automático" else "${selectedQuality}p"
    val description = details?.description.orEmpty().ifBlank { video.description }
    val channelAvatar = details?.channelThumbnailUrl.orEmpty().ifBlank { video.channelThumbnailUrl }
    val publishedAt = details?.publishedAt.orEmpty().ifBlank { video.publishedAt }

    val shouldLoadMoreRelated by remember(listState, relatedVideos.size, relatedCanLoadMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && relatedVideos.isNotEmpty() && relatedCanLoadMore && lastVisible >= total - 4
        }
    }

    fun saveProgress() {
        val positionMs = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val durationMs = controller?.duration?.takeIf { it > 0L } ?: video.durationMs
        onSavePlayback(video, positionMs, durationMs)
    }

    fun restorePortraitMode() {
        val currentActivity = activity ?: return
        WindowCompat.setDecorFitsSystemWindows(currentActivity.window, true)
        WindowInsetsControllerCompat(currentActivity.window, currentActivity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun minimize() {
        if (isFullscreen) {
            isFullscreen = false
            restorePortraitMode()
            return
        }
        saveProgress()
        restorePortraitMode()
        onBack()
    }

    fun closePlayer() {
        saveProgress()
        restorePortraitMode()
        onClose()
    }

    BackHandler { minimize() }

    LaunchedEffect(isFullscreen) {
        val currentActivity = activity ?: return@LaunchedEffect
        val insets = WindowInsetsControllerCompat(currentActivity.window, currentActivity.window.decorView)
        if (isFullscreen) {
            currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(currentActivity.window, false)
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.setDecorFitsSystemWindows(currentActivity.window, true)
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose { restorePortraitMode() }
    }

    LaunchedEffect(playback.connecting, playback.resolving, video.id) {
        if (playback.connecting || playback.resolving) {
            showDelayedLoading = false
            delay(450L)
            if (playback.connecting || playback.resolving) showDelayedLoading = true
        } else {
            showDelayedLoading = false
        }
    }

    LaunchedEffect(selectedQuality) {
        playerConnection.setMaxVideoHeight(selectedQuality)
    }

    LaunchedEffect(relatedVideos.map { it.id }, dataSaver, playback.resolving, playback.currentVideoId) {
        if (!playback.resolving && playback.currentVideoId == video.id) {
            delay(1_500L)
            playerConnection.preload(relatedVideos.take(1), dataSaver)
        }
    }

    LaunchedEffect(shouldLoadMoreRelated, relatedLoadingMore) {
        if (shouldLoadMoreRelated && !relatedLoadingMore) onLoadMoreRelated()
    }

    LaunchedEffect(timerMinutes) {
        val minutes = timerMinutes ?: return@LaunchedEffect
        delay(minutes.toLong() * 60_000L)
        playerConnection.pause()
        timerMinutes = null
        onMessage("El temporizador detuvo la reproducción.")
    }


    val embeddedPlayerModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(playerRatio)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        PlayerViewport(
            video = video,
            controller = controller,
            playback = playback,
            showDelayedLoading = showDelayedLoading,
            qualityLabel = qualityLabel,
            playbackSpeed = playbackSpeed,
            modifier = if (isFullscreen) Modifier.fillMaxSize() else embeddedPlayerModifier,
            onMinimize = { if (isFullscreen) isFullscreen = false else minimize() },
            onClose = ::closePlayer,
            onSettings = { showSettings = true },
            onNext = onPlayNext,
            onFullscreen = { isFullscreen = !isFullscreen },
            fullscreen = isFullscreen,
            resizeMode = if (fillScreenMode) {
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            screenModeLabel = if (fillScreenMode) "Rellenar" else "Ajustar",
            onRetry = { playerConnection.open(video, autoplay, dataSaver, repeatEnabled) },
            onOpenExternal = { openExternalVideo(context, video, preferYouTubeApp = true) }
        )
        if (!isFullscreen) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                item(key = "details-${video.id}", contentType = "details") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                        if (video.isLive) {
                            Text("EN VIVO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(
                            video.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = if (descriptionExpanded) 6 else 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        val stats = buildList {
                            details?.viewCount?.takeIf { it > 0L }?.let { add("${formatCompactNumber(it)} visualizaciones") }
                            formatPublishedAt(publishedAt).takeIf { it.isNotBlank() }?.let(::add)
                        }.joinToString(" · ")
                        if (stats.isNotBlank()) {
                            Text(
                                stats,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 7.dp)
                            )
                        }
                        if (detailsLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clickable(enabled = video.channelId.isNotBlank()) {
                                    onOpenChannel(
                                        ChannelItem(
                                            id = video.channelId,
                                            title = video.channelTitle,
                                            thumbnailUrl = channelAvatar
                                        )
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelAvatar(channelAvatar, video.channelTitle, 46.dp)
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(video.channelTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                details?.subscriberCount?.takeIf { it > 0L }?.let {
                                    Text(
                                        "${formatCompactNumber(it)} suscriptores",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (video.channelId.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        onOpenChannel(
                                            ChannelItem(
                                                id = video.channelId,
                                                title = video.channelTitle,
                                                thumbnailUrl = channelAvatar
                                            )
                                        )
                                    }
                                ) { Text("Canal") }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = isLiked,
                                    onClick = onLike,
                                    label = {
                                        Text(details?.likeCount?.takeIf { it > 0L }?.let(::formatCompactNumber) ?: "Me gusta")
                                    },
                                    leadingIcon = { Icon(Icons.Default.ThumbUp, null) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = isDisliked,
                                    onClick = onDislike,
                                    label = { Text("No me gusta") },
                                    leadingIcon = { Icon(Icons.Default.ThumbDown, null) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = onWatchLater,
                                    label = { Text(if (isWatchLater) "Guardado" else "Ver después") },
                                    leadingIcon = { Icon(Icons.Default.WatchLater, null) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = ::minimize,
                                    label = { Text("Segundo plano") },
                                    leadingIcon = { Icon(Icons.Default.Headphones, null) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { shareVideo(context, video) },
                                    label = { Text("Compartir") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { enterPictureInPicture(context, onMessage) },
                                    label = { Text("Flotante") },
                                    leadingIcon = { Icon(Icons.Default.PictureInPictureAlt, null) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { showTimer = true },
                                    label = { Text(timerMinutes?.let { "$it min" } ?: "Temporizador") },
                                    leadingIcon = { Icon(Icons.Default.Timer, null) }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = { openExternalVideo(context, video, preferYouTubeApp = true) },
                                    label = { Text("Transmitir") },
                                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) }
                                )
                            }
                        }

                        if (description.isNotBlank()) {
                            OutlinedCard(
                                onClick = { descriptionExpanded = !descriptionExpanded },
                                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Descripción", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Icon(
                                            if (descriptionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            if (descriptionExpanded) "Contraer" else "Expandir"
                                        )
                                    }
                                    Text(
                                        description,
                                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }

                        if (video.mediaKind != MediaKind.YOUTUBE) {
                            OutlinedButton(
                                onClick = {
                                    val id = enqueueDirectDownload(context, video.title, video.source, false)
                                    if (id >= 0L) {
                                        onRegisterDownload(video.title, video.source, id)
                                        onMessage("Descarga iniciada.")
                                    } else {
                                        onMessage("No se pudo iniciar la descarga.")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                            ) {
                                Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Descargar archivo")
                            }
                        }
                    }
                }

                item(key = "related-title-${video.id}", contentType = "related-title") {
                    HorizontalDivider()
                    Text(
                        "Siguiente y relacionados",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)
                    )
                }

                if (relatedLoading && relatedVideos.isEmpty()) {
                    item(key = "related-loading", contentType = "loading") { LoadingBlock() }
                } else if (relatedVideos.isEmpty()) {
                    item(key = "related-empty", contentType = "empty") {
                        EmptyBlock("No se encontraron relacionados para este video.")
                    }
                } else {
                    items(relatedVideos, key = { "related-${it.id}" }, contentType = { "related" }) { related ->
                        RelatedVideoCard(
                            video = related,
                            onPlay = { onPlayRelated(related) },
                            onWatchLater = { onWatchLaterRelated(related) }
                        )
                        HorizontalDivider()
                    }
                }

                if (relatedLoadingMore) {
                    item(key = "related-more", contentType = "loading-more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) }
                    }
                } else if (!relatedCanLoadMore && relatedVideos.isNotEmpty()) {
                    item(key = "related-end", contentType = "end") {
                        Text(
                            "No hay más relacionados disponibles.",
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("Reproducción", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Calidad: $qualityLabel", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                    items(listOf(0, 360, 480, 720, 1080)) { quality ->
                        FilterChip(
                            selected = selectedQuality == quality,
                            onClick = { selectedQuality = quality },
                            label = { Text(if (quality == 0) "Auto" else "${quality}p") },
                            leadingIcon = if (selectedQuality == quality) {
                                { Icon(Icons.Default.HighQuality, null) }
                            } else null
                        )
                    }
                }
                Text("Velocidad: ${playbackSpeed}x", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                    items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { speed ->
                        FilterChip(
                            selected = playbackSpeed == speed,
                            onClick = {
                                playbackSpeed = speed
                                playerConnection.setSpeed(speed)
                            },
                            label = { Text("${speed}x") },
                            leadingIcon = if (playbackSpeed == speed) {
                                { Icon(Icons.Default.Speed, null) }
                            } else null
                        )
                    }
                }
                SettingSwitch(
                    title = "Reproducción automática",
                    subtitle = "Pasa al siguiente video al terminar",
                    checked = autoplay,
                    onChange = onAutoplayChange
                )
                Text("Pantalla", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !fillScreenMode,
                        onClick = { fillScreenMode = false },
                        label = { Text("Ajustar") },
                        leadingIcon = if (!fillScreenMode) { { Icon(Icons.Default.Fullscreen, null) } } else null
                    )
                    FilterChip(
                        selected = fillScreenMode,
                        onClick = { fillScreenMode = true },
                        label = { Text("Rellenar") },
                        leadingIcon = if (fillScreenMode) { { Icon(Icons.Default.Fullscreen, null) } } else null
                    )
                }
                SettingSwitch(
                    title = "Silencio",
                    subtitle = "Desactiva temporalmente el audio",
                    checked = isMuted,
                    onChange = {
                        isMuted = it
                        playerConnection.setMuted(it)
                    }
                )
                SettingSwitch(
                    title = "Repetir video",
                    subtitle = "Vuelve a comenzar al terminar",
                    checked = repeatEnabled,
                    onChange = {
                        repeatEnabled = it
                        playerConnection.setRepeat(it)
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { showSettings = false; showTimer = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Timer, null)
                        Spacer(Modifier.width(6.dp))
                        Text(timerMinutes?.let { "$it min" } ?: "Temporizador")
                    }
                    OutlinedButton(onClick = { showSettings = false; enterPictureInPicture(context, onMessage) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PictureInPictureAlt, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ventana")
                    }
                }
                OutlinedButton(
                    onClick = { showSettings = false; openExternalVideo(context, video, preferYouTubeApp = true) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir externamente o transmitir")
                }
                Text(
                    if (dataSaver) "Ahorro de datos activo. Puedes elevar la calidad manualmente."
                    else "Automático permite que Media3 elija según la conexión.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(22.dp))
            }
        }
    }

    if (showTimer) {
        AlertDialog(
            onDismissRequest = { showTimer = false },
            title = { Text("Temporizador") },
            text = {
                Column {
                    listOf(10, 20, 30, 45, 60).forEach { minutes ->
                        TextButton(
                            onClick = {
                                timerMinutes = minutes
                                showTimer = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Detener en $minutes minutos") }
                    }
                    if (timerMinutes != null) {
                        TextButton(
                            onClick = {
                                timerMinutes = null
                                showTimer = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancelar temporizador") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTimer = false }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun PlayerViewport(
    video: VideoItem,
    controller: MediaController?,
    playback: com.geovideos.app.playback.PlaybackUiState,
    showDelayedLoading: Boolean,
    qualityLabel: String,
    playbackSpeed: Float,
    modifier: Modifier,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onNext: () -> Unit,
    onFullscreen: () -> Unit,
    fullscreen: Boolean,
    resizeMode: Int,
    screenModeLabel: String,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit
) {
    var controlsVisible by remember(video.id) { mutableStateOf(true) }
    var interactionSerial by remember(video.id) { mutableStateOf(0) }
    var positionMs by remember(video.id) { mutableLongStateOf(0L) }
    var durationMs by remember(video.id) { mutableLongStateOf(video.durationMs.coerceAtLeast(0L)) }
    var draggingProgress by remember(video.id) { mutableStateOf(false) }
    var draggedPositionMs by remember(video.id) { mutableLongStateOf(0L) }

    fun revealControls() {
        controlsVisible = true
        interactionSerial += 1
    }

    LaunchedEffect(controller, video.id, controlsVisible) {
        while (true) {
            val activeController = controller
            if (!draggingProgress && activeController?.currentMediaItem?.mediaId == video.id) {
                positionMs = activeController.currentPosition.coerceAtLeast(0L)
                durationMs = activeController.duration.takeIf { it > 0L } ?: durationMs
            }
            delay(if (controlsVisible) 350L else 900L)
        }
    }

    LaunchedEffect(controlsVisible, interactionSerial, playback.isPlaying, fullscreen) {
        if (controlsVisible && playback.isPlaying) {
            delay(2_600L)
            controlsVisible = false
        }
    }

    LaunchedEffect(playback.isPlaying, playback.playbackState) {
        if (!playback.isPlaying || playback.playbackState == Player.STATE_ENDED) {
            controlsVisible = true
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        Thumbnail(
            video.thumbnailUrl,
            Modifier.fillMaxSize(),
            contentScale = if (resizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                ContentScale.Crop
            } else {
                ContentScale.Fit
            }
        )

        if (controller != null && controller.currentMediaItem?.mediaId == video.id && !playback.resolving) {
            GeoMediaPlayerView(
                controller = controller,
                useController = false,
                resizeMode = resizeMode
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(video.id, fullscreen) {
                    detectTapGestures(onTap = {
                        controlsVisible = !controlsVisible
                        interactionSerial += 1
                    })
                }
        )

        if (controlsVisible && playback.error == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(82.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { revealControls(); onMinimize() },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        if (fullscreen) Icons.Default.ArrowBack else Icons.Default.KeyboardArrowDown,
                        if (fullscreen) "Salir de pantalla completa" else "Minimizar",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${if (qualityLabel == "Automático") "Auto" else qualityLabel} · ${playbackSpeed}x",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                IconButton(onClick = { revealControls(); onSettings() }) {
                    Icon(Icons.Default.Settings, "Opciones de reproducción", tint = Color.White)
                }
                if (!fullscreen) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 34.dp else 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = {
                        revealControls()
                        controller?.let { active ->
                            active.seekTo((active.currentPosition - 5_000L).coerceAtLeast(0L))
                        }
                    },
                    modifier = Modifier.size(if (fullscreen) 50.dp else 44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.58f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Replay, "Retroceder 5 segundos", modifier = Modifier.size(30.dp))
                }
                FilledIconButton(
                    onClick = {
                        revealControls()
                        controller?.let { active ->
                            if (active.isPlaying) active.pause() else active.play()
                        }
                    },
                    modifier = Modifier.size(if (fullscreen) 64.dp else 56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playback.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(34.dp)
                    )
                }
                FilledIconButton(
                    onClick = {
                        revealControls()
                        val target = controller?.currentPosition?.plus(15_000L) ?: 15_000L
                        controller?.seekTo(if (durationMs > 0L) target.coerceAtMost(durationMs) else target)
                    },
                    modifier = Modifier.size(if (fullscreen) 50.dp else 44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.58f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.SkipNext, "Adelantar 15 segundos", modifier = Modifier.size(30.dp))
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Slider(
                    value = if (durationMs > 0L) {
                        (if (draggingProgress) draggedPositionMs else positionMs).toFloat()
                    } else 0f,
                    onValueChange = { value ->
                        draggingProgress = true
                        draggedPositionMs = value.toLong()
                        revealControls()
                    },
                    onValueChangeFinished = {
                        controller?.seekTo(draggedPositionMs.coerceAtLeast(0L))
                        positionMs = draggedPositionMs
                        draggingProgress = false
                        revealControls()
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier.fillMaxWidth().height(30.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatDuration(if (draggingProgress) draggedPositionMs else positionMs)} / ${formatDuration(durationMs)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        screenModeLabel,
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    IconButton(onClick = { revealControls(); onNext() }) {
                        Icon(Icons.Default.SkipNext, "Siguiente", tint = Color.White)
                    }
                    IconButton(onClick = { revealControls(); onFullscreen() }) {
                        Icon(
                            if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            if (fullscreen) "Salir de pantalla completa" else "Pantalla completa",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        if (showDelayedLoading && playback.error == null) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
            }
        }

        playback.error?.let { message ->
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(42.dp))
                Text(
                    message,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) { Text("Reintentar") }
                    OutlinedButton(onClick = onOpenExternal) { Text("Abrir externo") }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    video: VideoItem,
    playerConnection: GeoPlayerConnection,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onSavePlayback: (VideoItem, Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val playback by playerConnection.state.collectAsStateWithLifecycle()
    val isPlaying = playback.isPlaying
    val currentPositionMs = playback.positionMs
    val durationMs = playback.durationMs
    var dragOffset by remember(video.id) { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .graphicsLayer { translationY = dragOffset }
            .pointerInput(video.id) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> dragOffset += amount },
                    onDragEnd = {
                        when {
                            dragOffset <= -70f -> onExpand()
                            dragOffset >= 70f -> {
                                onSavePlayback(video, currentPositionMs, durationMs)
                                onClose()
                            }
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f }
                )
            },
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(132.dp).height(74.dp).background(Color.Black)) {
                    Thumbnail(video.thumbnailUrl, Modifier.fillMaxSize())
                    if (playback.resolving || playback.connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(24.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).clickable(onClick = onExpand).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        video.channelTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    if (isPlaying) playerConnection.pause() else playerConnection.play()
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pausar" else "Reproducir"
                    )
                }
                IconButton(onClick = {
                    onSavePlayback(video, currentPositionMs, durationMs)
                    onClose()
                }) { Icon(Icons.Default.Close, "Cerrar") }
            }
            if (durationMs > 0L) {
                LinearProgressIndicator(
                    progress = { (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                )
            }
        }
    }

}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun GeoMediaPlayerView(
    controller: MediaController,
    useController: Boolean = true,
    resizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = { context ->
            (LayoutInflater.from(context).inflate(R.layout.geo_player_surface, null, false) as PlayerView).apply {
                player = controller
                this.useController = useController
                controllerAutoShow = useController
                controllerHideOnTouch = true
                controllerShowTimeoutMs = 2_500
                keepScreenOn = true
                setKeepContentOnPlayerReset(true)
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.resizeMode = resizeMode
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setShowRewindButton(true)
                setShowFastForwardButton(true)
                setShowPreviousButton(false)
                setShowNextButton(false)
            }
        },
        update = { view ->
            if (view.player !== controller) view.player = controller
            view.useController = useController
            view.controllerAutoShow = useController
            view.resizeMode = resizeMode
        }
    )
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
private fun PlaylistCard(playlist: PlaylistItem) {
    Card(modifier = Modifier.width(220.dp)) {
        Box {
            Thumbnail(playlist.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Text("${playlist.itemCount}", modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(0.75f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp))
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(playlist.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Playlist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    contentScale: ContentScale = ContentScale.Crop
) {
    var imageFailed by remember(url) { mutableStateOf(false) }
    if (url.isBlank() || imageFailed) {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(listOf(Color(0xFF301A56), Color(0xFF7C4DFF), Color(0xFF111118)))
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(52.dp), tint = Color.White.copy(0.8f))
        }
    } else {
        val context = LocalContext.current
        val request = remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .size(640, 360)
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
    val completed: Boolean
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

    OutlinedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
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
            IconButton(onClick = {
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                if (video.downloadId >= 0L) manager.remove(video.downloadId)
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
                DownloadStatusInfo("Descargando$percent", progress, false)
            }
            DownloadManager.STATUS_PAUSED -> DownloadStatusInfo("Pausada por el sistema", progress, false)
            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatusInfo("Completada · toca para abrir Descargas", 1f, true)
            DownloadManager.STATUS_FAILED -> DownloadStatusInfo("Falló la descarga", progress, false)
            else -> DownloadStatusInfo("Estado desconocido", progress, false)
        }
    }
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
