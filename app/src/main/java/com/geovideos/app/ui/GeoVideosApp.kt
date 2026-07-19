package com.geovideos.app.ui

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
import kotlinx.coroutines.delay

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

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val selectedVideo = state.selectedVideo

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.authStatus != AuthStatus.CONNECTED) {
            GoogleConnectScreen(
                status = state.authStatus,
                error = state.authError,
                onConnect = onConnectGoogle
            )
        } else if (state.playerExpanded && selectedVideo != null) {
            PlayerScreen(
                video = selectedVideo,
                isWatchLater = state.watchLater.any { it.id == selectedVideo.id },
                autoplay = state.autoplay,
                dataSaver = state.dataSaver,
                onBack = viewModel::minimizePlayer,
                onClose = viewModel::closePlayer,
                onWatchLater = { viewModel.toggleWatchLater(selectedVideo) },
                onSavePlayback = viewModel::savePlayback,
                onRegisterDownload = viewModel::registerDownload,
                onMessage = viewModel::showMessage
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                MainShell(
                    state = state,
                    snackbar = snackbar,
                    onConnectGoogle = onConnectGoogle,
                    onSwitchGoogleAccount = onSwitchGoogleAccount,
                    onSection = viewModel::selectSection,
                    onCategory = viewModel::selectHomeCategory,
                    onPlay = viewModel::play,
                    onWatchLater = viewModel::toggleWatchLater,
                    onSearch = viewModel::search,
                    onRefresh = viewModel::refresh,
                    onLoadMoreHome = viewModel::loadMoreHome,
                    onLoadMoreSearch = viewModel::loadMoreSearch,
                    onOpenChannel = viewModel::openChannel,
                    onCloseChannel = viewModel::closeChannel,
                    onDisconnect = viewModel::disconnect,
                    onClearData = viewModel::clearLocalData,
                    onRegisterDownload = viewModel::registerDownload,
                    onRemoveDownload = viewModel::removeDownload,
                    onAutoplayChange = viewModel::setAutoplay,
                    onDataSaverChange = viewModel::setDataSaver,
                    onNotificationsChange = viewModel::setNotificationsEnabled,
                    onMessage = viewModel::showMessage
                )

                state.selectedVideo?.let { video ->
                    MiniPlayer(
                        video = video,
                        autoplay = true,
                        onExpand = viewModel::expandPlayer,
                        onClose = viewModel::closePlayer,
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
    onConnectGoogle: () -> Unit,
    onSwitchGoogleAccount: (String) -> Unit,
    onSection: (MainSection) -> Unit,
    onCategory: (HomeCategory) -> Unit,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreHome: (HomeCategory) -> Unit,
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
                    IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                        if (state.refreshing) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
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
        },
        bottomBar = {
            NavigationBar {
                BottomItem(MainSection.HOME, state.section, "Principal", Icons.Default.Home, onSection)
                BottomItem(MainSection.SHORTS, state.section, "Shorts", Icons.Default.AutoAwesome, onSection)
                BottomItem(MainSection.SEARCH, state.section, "Buscar", Icons.Default.Search, onSection)
                BottomItem(MainSection.LIBRARY, state.section, "Colección", Icons.Default.VideoLibrary, onSection)
                BottomItem(MainSection.ACCOUNT, state.section, "Cuenta", Icons.Default.Person, onSection)
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
                loading = state.loading,
                refreshing = state.refreshing,
                loadingMore = state.loadingMore,
                canLoadMore = state.canLoadMore,
                lastSyncMs = state.lastSyncMs,
                watchLater = state.watchLater,
                onRefresh = onRefresh,
                onLoadMore = onLoadMoreHome,
                onCategory = onCategory,
                onPlay = onPlay,
                onWatchLater = onWatchLater
            )
            MainSection.SHORTS -> ShortsScreen(
                modifier = Modifier.padding(padding),
                videos = state.shorts,
                loading = state.loading,
                onPlay = onPlay,
                onWatchLater = onWatchLater
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
            MainSection.LIBRARY -> LibraryScreen(
                modifier = Modifier.padding(padding),
                history = state.history,
                watchLater = state.watchLater,
                liked = state.liked,
                playlists = state.playlists,
                subscriptions = state.subscriptions,
                downloads = state.downloads,
                onPlay = onPlay,
                onWatchLater = onWatchLater,
                onOpenChannel = onOpenChannel,
                onAddDownload = { showDownloadDialog = true },
                onRemoveDownload = onRemoveDownload
            )
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
    onWatchLater: (VideoItem) -> Unit
) {
    val videos = when (category) {
        HomeCategory.FOR_YOU -> personalized.ifEmpty { popular }
        HomeCategory.LIVE -> live
        HomeCategory.GAMING -> gaming
        HomeCategory.MUSIC -> music
    }
    val watchLaterIds = remember(watchLater) { watchLater.mapTo(HashSet<String>()) { it.id } }
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(listState, videos.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && videos.isNotEmpty() && lastVisible >= total - 4
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
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        if (refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, "Actualizar")
                        }
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
            if (loading && videos.isEmpty()) {
                item(contentType = "loading") { LoadingBlock() }
            } else if (videos.isEmpty()) {
                item(contentType = "empty") { EmptyBlock("No se encontraron videos en esta sección. Pulsa actualizar.") }
            } else {
                items(
                    items = videos,
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

@Composable
private fun ShortsScreen(
    modifier: Modifier,
    videos: List<VideoItem>,
    loading: Boolean,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionHeader("Clips verticales", "Resultados cortos disponibles en YouTube") }
        if (loading && videos.isEmpty()) item { LoadingBlock() }
        else items(videos, key = { "short-${it.id}" }) { video ->
            Card(onClick = { onPlay(video) }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(9f / 14f)) {
                    Thumbnail(video.thumbnailUrl, Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)))))
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(18.dp).fillMaxWidth(0.82f)) {
                        Text(video.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(video.channelTitle, color = Color.White.copy(0.75f), modifier = Modifier.padding(top = 6.dp))
                    }
                    IconButton(onClick = { onWatchLater(video) }, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                        Icon(Icons.Outlined.WatchLater, "Ver después")
                    }
                }
            }
        }
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
    playlists: List<PlaylistItem>,
    subscriptions: List<ChannelItem>,
    downloads: List<VideoItem>,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onAddDownload: () -> Unit,
    onRemoveDownload: (Long) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { LibraryShortcutRow(history.size, watchLater.size, liked.size, onAddDownload) }
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
            item { SectionHeader("Videos que te gustan", "Sincronizados con tu cuenta de YouTube") }
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
        if (subscriptions.isNotEmpty()) {
            item { SectionHeader("Suscripciones", "Abre un canal para ver sus publicaciones recientes") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(subscriptions, key = { "sub-${it.id}" }) { channel -> ChannelCard(channel) { onOpenChannel(channel) } }
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
private fun LibraryShortcutRow(history: Int, later: Int, liked: Int, onDownload: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ShortcutCard(Icons.Default.History, "Historial", "$history videos") }
        item { ShortcutCard(Icons.Default.WatchLater, "Ver después", "$later videos") }
        item { ShortcutCard(Icons.Default.Favorite, "Me gustan", "$liked videos") }
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
        SettingSwitch("Reproducción automática", "Inicia el video al abrir el reproductor", autoplay, onAutoplayChange)
        SettingSwitch("Ahorro de datos", "Evita que el video empiece automáticamente al abrirlo", dataSaver, onDataSaverChange)
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
    isWatchLater: Boolean,
    autoplay: Boolean,
    dataSaver: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onWatchLater: () -> Unit,
    onSavePlayback: (VideoItem, Long, Long) -> Unit,
    onRegisterDownload: (String, String, Long) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val effectiveAutoplay = autoplay && !dataSaver
    val youtubeController = remember(video.id) { YouTubePlayerController() }
    val directController = remember(video.id) { DirectPlayerController() }
    var currentPositionMs by rememberSaveable(video.id) { mutableLongStateOf(video.resumePositionMs) }
    var durationMs by rememberSaveable(video.id) { mutableLongStateOf(video.durationMs) }
    var lastSavedMs by remember(video.id) { mutableLongStateOf(video.resumePositionMs) }
    var isPlaying by remember(video.id) { mutableStateOf(effectiveAutoplay) }
    var isMuted by rememberSaveable(video.id) { mutableStateOf(false) }
    var repeatEnabled by rememberSaveable(video.id) { mutableStateOf(false) }
    var playbackSpeed by rememberSaveable(video.id) { mutableFloatStateOf(1f) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showTimer by rememberSaveable { mutableStateOf(false) }
    var timerMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var playerError by remember(video.id) { mutableStateOf<String?>(null) }
    var playerReady by remember(video.id) { mutableStateOf(video.mediaKind != MediaKind.YOUTUBE) }

    fun saveProgress(force: Boolean = false) {
        if (force || kotlin.math.abs(currentPositionMs - lastSavedMs) >= 5_000L) {
            lastSavedMs = currentPositionMs
            onSavePlayback(video, currentPositionMs, durationMs)
        }
    }

    fun minimize() {
        saveProgress(force = true)
        onBack()
    }

    BackHandler(onBack = ::minimize)

    LaunchedEffect(timerMinutes) {
        val minutes = timerMinutes ?: return@LaunchedEffect
        delay(minutes.toLong() * 60_000L)
        youtubeController.pause()
        directController.pause()
        isPlaying = false
        timerMinutes = null
        onMessage("El temporizador detuvo la reproducción.")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reproduciendo", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = ::minimize) {
                        Icon(Icons.Default.KeyboardArrowDown, "Minimizar")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "Controles")
                    }
                    IconButton(onClick = {
                        saveProgress(force = true)
                        onClose()
                    }) {
                        Icon(Icons.Default.Close, "Cerrar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                if (video.mediaKind == MediaKind.YOUTUBE) {
                    YouTubeNativePlayer(
                        videoId = video.id,
                        startPositionMs = video.resumePositionMs,
                        autoplay = effectiveAutoplay,
                        compact = false,
                        controller = youtubeController,
                        onProgress = { position, duration ->
                            currentPositionMs = position
                            durationMs = duration
                            saveProgress()
                        },
                        onReady = { playerReady = true },
                        onPlayingChanged = { isPlaying = it },
                        onError = { playerError = it }
                    )
                } else {
                    DirectPlayer(
                        source = video.source,
                        startPositionMs = video.resumePositionMs,
                        autoplay = effectiveAutoplay,
                        controller = directController,
                        onProgress = { position, duration ->
                            currentPositionMs = position
                            durationMs = duration
                            saveProgress()
                        },
                        onPlayingChanged = { isPlaying = it },
                        onError = { playerError = it }
                    )
                }

                if (!playerReady && playerError == null && video.mediaKind == MediaKind.YOUTUBE) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                playerError?.let { message ->
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).padding(24.dp),
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
                        Button(
                            onClick = { openExternalVideo(context, video, preferYouTubeApp = true) },
                            modifier = Modifier.padding(top = 14.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Abrir en YouTube")
                        }
                    }
                }
            }

            if (durationMs > 0L) {
                val progress = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${formatDuration(currentPositionMs)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                )
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (video.isLive) {
                    Text("EN VIVO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                }
                Text(video.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    video.channelTitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    item {
                        AssistChip(
                            onClick = {
                                if (isPlaying) {
                                    youtubeController.pause()
                                    directController.pause()
                                } else {
                                    youtubeController.play()
                                    directController.play()
                                }
                            },
                            label = { Text(if (isPlaying) "Pausar" else "Reproducir") },
                            leadingIcon = { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = onWatchLater,
                            label = { Text(if (isWatchLater) "Guardado" else "Ver después") },
                            leadingIcon = { Icon(if (isWatchLater) Icons.Default.WatchLater else Icons.Outlined.WatchLater, null) }
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
                            onClick = {
                                if (video.mediaKind == MediaKind.YOUTUBE) {
                                    onMessage("YouTube no entrega el archivo del video. Descarga solo enlaces directos propios o autorizados desde Colección.")
                                } else {
                                    val id = enqueueDirectDownload(context, video.title, video.source, wifiOnly = false)
                                    if (id >= 0L) onRegisterDownload(video.title, video.source, id)
                                    else onMessage("El enlace directo no es válido.")
                                }
                            },
                            label = { Text("Descargar") },
                            leadingIcon = { Icon(Icons.Default.Download, null) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { enterPictureInPicture(context, onMessage) },
                            label = { Text("Ventana") },
                            leadingIcon = { Icon(Icons.Default.PictureInPictureAlt, null) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Información", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(28.dp))
                    Text("Comentarios", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                if (video.description.isNotBlank()) {
                    Text(
                        video.description,
                        modifier = Modifier.padding(top = 14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    .padding(horizontal = 22.dp, vertical = 8.dp)
            ) {
                Text("Controles de reproducción", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                SettingSwitch(
                    title = "Silencio",
                    subtitle = if (isMuted) "Audio desactivado" else "Audio activado",
                    checked = isMuted
                ) { value ->
                    isMuted = value
                    youtubeController.mute(value)
                    directController.mute(value)
                }
                SettingSwitch(
                    title = "Repetir video",
                    subtitle = "Vuelve al inicio al terminar",
                    checked = repeatEnabled
                ) { value ->
                    repeatEnabled = value
                    youtubeController.repeat(value)
                    directController.repeat(value)
                }
                Text("Velocidad", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { rate ->
                        FilterChip(
                            selected = playbackSpeed == rate,
                            onClick = {
                                playbackSpeed = rate
                                youtubeController.rate(rate)
                                directController.rate(rate)
                            },
                            label = { Text("${rate}x") }
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showTimer = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Icon(Icons.Default.Timer, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (timerMinutes == null) "Configurar temporizador" else "Temporizador: ${timerMinutes} min")
                }
                OutlinedButton(
                    onClick = { enterPictureInPicture(context, onMessage) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Icon(Icons.Default.PictureInPictureAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ventana flotante")
                }
                OutlinedButton(
                    onClick = { openExternalVideo(context, video, preferYouTubeApp = true) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir en YouTube / transmitir")
                }
                Spacer(Modifier.height(28.dp))
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
                        ) {
                            Text("Detener en $minutes minutos")
                        }
                    }
                    if (timerMinutes != null) {
                        TextButton(
                            onClick = {
                                timerMinutes = null
                                showTimer = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancelar temporizador")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTimer = false }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun MiniPlayer(
    video: VideoItem,
    autoplay: Boolean,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onSavePlayback: (VideoItem, Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val youtubeController = remember(video.id) { YouTubePlayerController() }
    val directController = remember(video.id) { DirectPlayerController() }
    var isPlaying by remember(video.id) { mutableStateOf(autoplay) }
    var currentPositionMs by remember(video.id) { mutableLongStateOf(video.resumePositionMs) }
    var durationMs by remember(video.id) { mutableLongStateOf(video.durationMs) }
    var lastSavedMs by remember(video.id) { mutableLongStateOf(video.resumePositionMs) }

    fun saveMiniProgress() {
        if (kotlin.math.abs(currentPositionMs - lastSavedMs) >= 5_000L) {
            lastSavedMs = currentPositionMs
            onSavePlayback(video, currentPositionMs, durationMs)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().height(72.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(128.dp).height(72.dp).background(Color.Black)) {
                if (video.mediaKind == MediaKind.YOUTUBE) {
                    YouTubeNativePlayer(
                        videoId = video.id,
                        startPositionMs = video.resumePositionMs,
                        autoplay = autoplay,
                        compact = true,
                        controller = youtubeController,
                        onProgress = { position, duration ->
                            currentPositionMs = position
                            durationMs = duration
                            saveMiniProgress()
                        },
                        onReady = {},
                        onPlayingChanged = { isPlaying = it },
                        onError = { _ -> }
                    )
                } else {
                    DirectPlayer(
                        source = video.source,
                        startPositionMs = video.resumePositionMs,
                        autoplay = autoplay,
                        controller = directController,
                        onProgress = { position, duration ->
                            currentPositionMs = position
                            durationMs = duration
                            saveMiniProgress()
                        },
                        onPlayingChanged = { isPlaying = it },
                        onError = { _ -> }
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onExpand).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(video.channelTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                if (isPlaying) {
                    youtubeController.pause()
                    directController.pause()
                } else {
                    youtubeController.play()
                    directController.play()
                }
            }) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pausar" else "Reproducir")
            }
            IconButton(onClick = {
                onSavePlayback(video, currentPositionMs, durationMs)
                onClose()
            }) {
                Icon(Icons.Default.Close, "Cerrar")
            }
        }
    }
}

private class YouTubePlayerController {
    var player: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer? = null
    var playerView: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView? = null
    var repeatEnabled: Boolean = false

    fun play() { player?.play() }
    fun pause() { player?.pause() }
    fun mute(value: Boolean) { if (value) player?.mute() else player?.unMute() }
    fun rate(value: Float) {
        val rate = when (value) {
            0.25f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_0_25
            0.5f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_0_5
            0.75f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_0_75
            1.25f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_1_25
            1.5f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_1_5
            1.75f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_1_75
            2f -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_2
            else -> com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlaybackRate.RATE_1
        }
        player?.setPlaybackRate(rate)
    }
    fun repeat(value: Boolean) { repeatEnabled = value }
    fun stop() { player?.pause() }
}

private class DirectPlayerController {
    var player: ExoPlayer? = null

    fun play() { player?.play() }
    fun pause() { player?.pause() }
    fun mute(value: Boolean) { player?.let { it.volume = if (value) 0f else 1f } }
    fun rate(value: Float) { player?.setPlaybackSpeed(value) }
    fun repeat(value: Boolean) {
        player?.let {
            it.repeatMode = if (value) androidx.media3.common.Player.REPEAT_MODE_ONE
            else androidx.media3.common.Player.REPEAT_MODE_OFF
        }
    }
}

@Composable
private fun YouTubeNativePlayer(
    videoId: String,
    startPositionMs: Long,
    autoplay: Boolean,
    compact: Boolean,
    controller: YouTubePlayerController,
    onProgress: (Long, Long) -> Unit,
    onReady: () -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val activity = context.findActivity()
    val playerView = remember(videoId, compact) {
        com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }

    DisposableEffect(playerView, videoId, compact) {
        controller.playerView = playerView
        lifecycleOwner.lifecycle.addObserver(playerView)
        var currentSeconds = startPositionMs.coerceAtLeast(0L) / 1000f
        var durationSeconds = 0f
        var fullscreenView: View? = null
        var exitFullscreenAction: (() -> Unit)? = null

        val fullscreenListener = object : com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener {
            override fun onEnterFullscreen(fullscreen: View, exitFullscreen: () -> Unit) {
                val host = activity?.window?.decorView as? ViewGroup ?: return
                fullscreenView = fullscreen
                exitFullscreenAction = exitFullscreen
                (fullscreen.parent as? ViewGroup)?.removeView(fullscreen)
                host.addView(
                    fullscreen,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                activity.window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }

            override fun onExitFullscreen() {
                val host = activity?.window?.decorView as? ViewGroup
                fullscreenView?.let { host?.removeView(it) }
                fullscreenView = null
                exitFullscreenAction = null
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
        playerView.addFullscreenListener(fullscreenListener)

        val listener = object : com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer) {
                controller.player = youTubePlayer
                if (autoplay) youTubePlayer.loadVideo(videoId, currentSeconds)
                else youTubePlayer.cueVideo(videoId, currentSeconds)
                onReady()
            }

            override fun onCurrentSecond(
                youTubePlayer: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer,
                second: Float
            ) {
                currentSeconds = second.coerceAtLeast(0f)
                onProgress((currentSeconds * 1000f).toLong(), (durationSeconds * 1000f).toLong())
            }

            override fun onVideoDuration(
                youTubePlayer: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer,
                duration: Float
            ) {
                durationSeconds = duration.coerceAtLeast(0f)
                onProgress((currentSeconds * 1000f).toLong(), (durationSeconds * 1000f).toLong())
            }

            override fun onStateChange(
                youTubePlayer: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer,
                state: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState
            ) {
                onPlayingChanged(state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.PLAYING)
                if (
                    state == com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerState.ENDED &&
                    controller.repeatEnabled
                ) {
                    youTubePlayer.seekTo(0f)
                    youTubePlayer.play()
                }
            }

            override fun onError(
                youTubePlayer: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer,
                error: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError
            ) {
                onError(youtubeErrorMessage(error))
            }
        }

        val options = com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
            .Builder(context)
            .controls(if (compact) 0 else 1)
            .fullscreen(if (compact) 0 else 1)
            .rel(0)
            .build()

        playerView.initialize(listener, true, options)

        onDispose {
            exitFullscreenAction?.invoke()
            fullscreenView?.let { (it.parent as? ViewGroup)?.removeView(it) }
            controller.player = null
            controller.playerView = null
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = {
            (playerView.parent as? ViewGroup)?.removeView(playerView)
            playerView
        },
        update = {
            if (it.parent == null) controller.playerView = it
        }
    )
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun DirectPlayer(
    source: String,
    startPositionMs: Long,
    autoplay: Boolean,
    controller: DirectPlayerController,
    onProgress: (Long, Long) -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val player = remember(source) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(source))
            prepare()
            if (startPositionMs > 0L) seekTo(startPositionMs)
            playWhenReady = autoplay
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    onPlayingChanged(isPlaying)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    onError(error.message ?: "No se pudo reproducir el archivo.")
                }
            })
        }
    }
    controller.player = player
    LaunchedEffect(player) {
        while (true) {
            onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
            delay(1_500L)
        }
    }
    DisposableEffect(player) {
        onDispose {
            onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
            controller.player = null
            player.release()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = { PlayerView(it).apply { this.player = player; useController = true } },
        update = { it.player = player }
    )
}

private fun youtubeErrorMessage(
    error: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError
): String = when (error) {
    com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST ->
        "El identificador del video no es válido."
    com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError.HTML_5_PLAYER ->
        "El reproductor HTML5 no está disponible en este dispositivo."
    com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError.VIDEO_NOT_FOUND ->
        "El video fue eliminado o es privado."
    com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ->
        "El propietario no permite reproducir este video dentro de otras aplicaciones."
    else -> "YouTube no pudo reproducir este video."
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
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
        AsyncImage(model = channel.thumbnailUrl, contentDescription = channel.title, modifier = Modifier.size(82.dp).clip(CircleShape), contentScale = ContentScale.Crop)
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
        AsyncImage(model = profile?.pictureUrl, contentDescription = "Cuenta", modifier = modifier, contentScale = ContentScale.Crop)
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
    if (url.isBlank()) {
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
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun Thumbnail(url: String, modifier: Modifier) {
    if (url.isBlank()) {
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
                .size(1280, 720)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = modifier.background(Color.Black),
            contentScale = ContentScale.Crop
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
