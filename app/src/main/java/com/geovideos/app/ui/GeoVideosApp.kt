package com.geovideos.app.ui

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.History
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
        when {
            selectedVideo != null -> PlayerScreen(
                video = selectedVideo,
                isWatchLater = state.watchLater.any { it.id == selectedVideo.id },
                autoplay = state.autoplay,
                dataSaver = state.dataSaver,
                onBack = viewModel::closePlayer,
                onWatchLater = { viewModel.toggleWatchLater(selectedVideo) },
                onSavePlayback = viewModel::savePlayback,
                onRegisterDownload = viewModel::registerDownload,
                onMessage = viewModel::showMessage
            )
            state.authStatus != AuthStatus.CONNECTED -> GoogleConnectScreen(
                status = state.authStatus,
                error = state.authError,
                onConnect = onConnectGoogle
            )
            else -> MainShell(
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

        if (status == AuthStatus.NEEDS_CLOUD_SETUP || status == AuthStatus.ERROR) {
            Spacer(Modifier.height(26.dp))
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (status == AuthStatus.NEEDS_CLOUD_SETUP) "Falta registrar la APK en Google" else "No se pudo conectar",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (error.isNotBlank()) {
                        Text(error, modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (status == AuthStatus.NEEDS_CLOUD_SETUP) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                        Text("Datos que debes registrar en Google Cloud:", fontWeight = FontWeight.SemiBold)
                        Text("Paquete: $PACKAGE_NAME", modifier = Modifier.padding(top = 8.dp))
                        Text("SHA-1: $DEV_SHA1", modifier = Modifier.padding(top = 5.dp))
                        Text(
                            "Activa YouTube Data API v3 y crea un cliente OAuth de Android con esos dos datos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
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
                BottomItem(MainSection.HOME, state.section, "Inicio", Icons.Default.Home, onSection)
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
                popular = state.popular,
                live = state.live,
                gaming = state.gaming,
                loading = state.loading,
                watchLater = state.watchLater,
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
                onSearch = onSearch,
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

@Composable
private fun HomeScreen(
    modifier: Modifier,
    category: HomeCategory,
    popular: List<VideoItem>,
    live: List<VideoItem>,
    gaming: List<VideoItem>,
    loading: Boolean,
    watchLater: List<VideoItem>,
    onCategory: (HomeCategory) -> Unit,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) {
    val videos = when (category) {
        HomeCategory.DISCOVER -> popular
        HomeCategory.LIVE -> live
        HomeCategory.GAMING -> gaming
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { CategoryChip("Descubrir", Icons.Default.Explore, category == HomeCategory.DISCOVER) { onCategory(HomeCategory.DISCOVER) } }
                item { CategoryChip("En vivo", Icons.Default.LiveTv, category == HomeCategory.LIVE) { onCategory(HomeCategory.LIVE) } }
                item { CategoryChip("Gaming", Icons.Default.Games, category == HomeCategory.GAMING) { onCategory(HomeCategory.GAMING) } }
            }
        }
        if (category == HomeCategory.DISCOVER && videos.isNotEmpty()) {
            item { FeaturedCard(videos.first(), onPlay) }
            item { SectionHeader("Tendencias para ti", "Basadas en la región y actividad pública") }
        } else if (category == HomeCategory.LIVE) {
            item { SectionHeader("En vivo ahora", "Transmisiones disponibles para reproducir") }
        } else if (category == HomeCategory.GAMING) {
            item { SectionHeader("Zona gaming", "Videos populares de videojuegos") }
        }
        if (loading && videos.isEmpty()) {
            item { LoadingBlock() }
        } else if (videos.isEmpty()) {
            item { EmptyBlock("No se encontraron videos en esta sección.") }
        } else {
            items(videos.drop(if (category == HomeCategory.DISCOVER) 1 else 0), key = { "home-${category.name}-${it.id}" }) { video ->
                VideoCard(
                    video = video,
                    isWatchLater = watchLater.any { it.id == video.id },
                    onPlay = { onPlay(video) },
                    onWatchLater = { onWatchLater(video) }
                )
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
    onSearch: (String) -> Unit,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
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
            item { SectionHeader("Búsquedas recientes", "Toca una para repetirla") }
            items(history, key = { "history-$it" }) { item ->
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
        if (loading) item { LoadingBlock() }
        else items(results, key = { "search-${it.id}" }) { video ->
            CompactVideoRow(video, { onPlay(video) }, { onWatchLater(video) })
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
    var playerError by remember(video.id) { mutableStateOf<Int?>(null) }
    var playerReady by remember(video.id) { mutableStateOf(video.mediaKind != MediaKind.YOUTUBE) }

    fun saveProgress(force: Boolean = false) {
        if (force || kotlin.math.abs(currentPositionMs - lastSavedMs) >= 5_000L) {
            lastSavedMs = currentPositionMs
            onSavePlayback(video, currentPositionMs, durationMs)
        }
    }

    fun closePlayer() {
        saveProgress(force = true)
        onBack()
    }

    BackHandler(onBack = ::closePlayer)

    LaunchedEffect(video.id, playerReady, playerError) {
        if (video.mediaKind == MediaKind.YOUTUBE && !playerReady && playerError == null) {
            delay(12_000L)
            if (!playerReady && playerError == null) playerError = -1
        }
    }

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
                navigationIcon = { IconButton(onClick = ::closePlayer) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "Controles")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                if (video.mediaKind == MediaKind.YOUTUBE) {
                    YouTubeWebPlayer(
                        videoId = video.id,
                        startPositionMs = video.resumePositionMs,
                        autoplay = effectiveAutoplay,
                        controller = youtubeController,
                        onProgress = { position, duration ->
                            currentPositionMs = position
                            durationMs = duration
                            saveProgress()
                        },
                        onReady = { playerReady = true },
                        onPlayingChanged = { isPlaying = it },
                        onError = { code -> playerError = code }
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
                        onError = { onMessage(it) }
                    )
                }

                if (!playerReady && playerError == null && video.mediaKind == MediaKind.YOUTUBE) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                if (playerError != null) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(42.dp))
                        Text(
                            youtubeErrorMessage(playerError ?: 0),
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
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }

            Column(modifier = Modifier.padding(18.dp)) {
                if (video.isLive) Text("EN VIVO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                Text(video.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(video.channelTitle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 18.dp)) {
                    item {
                        AssistChip(
                            onClick = {
                                if (isPlaying) {
                                    youtubeController.pause(); directController.pause()
                                } else {
                                    youtubeController.play(); directController.play()
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
                                    onMessage("YouTube no entrega un archivo descargable a esta aplicación.")
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
                    item {
                        AssistChip(
                            onClick = { openExternalVideo(context, video, preferYouTubeApp = false) },
                            label = { Text("Navegador") },
                            leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) }
                        )
                    }
                }

                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Reproducción", fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                video.mediaKind == MediaKind.YOUTUBE && dataSaver -> "Calidad automática de YouTube. Ahorro de datos activo."
                                video.mediaKind == MediaKind.YOUTUBE -> "Calidad automática administrada por YouTube."
                                else -> "Controles completos para el archivo directo."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                        if (timerMinutes != null) {
                            Text(
                                "Temporizador: ${timerMinutes} min",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                if (video.description.isNotBlank()) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                        Text(video.description, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Controles de reproducción") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                    Text("Velocidad", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { rate ->
                            FilterChip(
                                selected = playbackSpeed == rate,
                                onClick = {
                                    playbackSpeed = rate
                                    youtubeController.rate(rate)
                                    directController.rate(rate)
                                },
                                label = { Text("${rate}x") },
                                leadingIcon = if (playbackSpeed == rate) { { Icon(Icons.Default.Speed, null) } } else null
                            )
                        }
                    }
                    OutlinedButton(onClick = { showTimer = true }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Icon(Icons.Default.Timer, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (timerMinutes == null) "Configurar temporizador" else "Cambiar temporizador")
                    }
                    OutlinedButton(onClick = { enterPictureInPicture(context, onMessage) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Icon(Icons.Default.PictureInPictureAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ventana flotante")
                    }
                    OutlinedButton(onClick = { openExternalVideo(context, video, preferYouTubeApp = true) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Icon(Icons.Default.OpenInBrowser, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir en YouTube / transmitir")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Cerrar") } }
        )
    }

    if (showTimer) {
        AlertDialog(
            onDismissRequest = { showTimer = false },
            title = { Text("Temporizador") },
            text = {
                Column {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        TextButton(
                            onClick = { timerMinutes = minutes; showTimer = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Detener en $minutes minutos") }
                    }
                    TextButton(
                        onClick = { timerMinutes = null; showTimer = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Desactivar temporizador") }
                }
            },
            confirmButton = {}
        )
    }
}

private class YouTubePlayerController {
    var webView: WebView? = null
    var chromeClient: FullscreenWebChromeClient? = null

    private fun evaluate(script: String) {
        webView?.post { webView?.evaluateJavascript(script, null) }
    }

    fun play() = evaluate("window.geoPlay && window.geoPlay();")
    fun pause() = evaluate("window.geoPause && window.geoPause();")
    fun mute(value: Boolean) = evaluate("window.geoMute && window.geoMute(${if (value) "true" else "false"});")
    fun rate(value: Float) = evaluate("window.geoRate && window.geoRate($value);")
    fun repeat(value: Boolean) = evaluate("window.geoRepeat && window.geoRepeat(${if (value) "true" else "false"});")
    fun stop() = evaluate("window.geoStop && window.geoStop();")
}

private class DirectPlayerController {
    var player: ExoPlayer? = null

    fun play() { player?.play() }
    fun pause() { player?.pause() }
    fun mute(value: Boolean) { player?.let { it.volume = if (value) 0f else 1f } }
    fun rate(value: Float) { player?.setPlaybackSpeed(value) }
    fun repeat(value: Boolean) { player?.let { it.repeatMode = if (value) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF } }
}

private class FullscreenWebChromeClient(
    private val activity: Activity?
) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var previousSystemUiVisibility: Int = 0
    private var previousOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        val host = activity?.window?.decorView as? ViewGroup
        if (view == null || activity == null || host == null || customView != null) {
            callback?.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        previousSystemUiVisibility = activity.window.decorView.systemUiVisibility
        previousOrientation = activity.requestedOrientation
        host.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onHideCustomView() {
        hideCustomViewIfNeeded()
    }

    fun hideCustomViewIfNeeded() {
        val view = customView ?: return
        val host = activity?.window?.decorView as? ViewGroup
        val callback = customViewCallback
        customView = null
        customViewCallback = null
        host?.removeView(view)
        activity?.window?.decorView?.systemUiVisibility = previousSystemUiVisibility
        if (activity != null) activity.requestedOrientation = previousOrientation
        callback?.onCustomViewHidden()
    }
}

private class YouTubeJavascriptBridge(
    private val onReadyCallback: () -> Unit,
    private val onProgressCallback: (Long, Long) -> Unit,
    private val onPlayingChangedCallback: (Boolean) -> Unit,
    private val onErrorCallback: (Int) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onReady() {
        mainHandler.post { onReadyCallback() }
    }

    @JavascriptInterface
    fun onStateChanged(state: Int) {
        mainHandler.post { onPlayingChangedCallback(state == 1) }
    }

    @JavascriptInterface
    fun onProgress(positionSeconds: Double, durationSeconds: Double) {
        mainHandler.post {
            onProgressCallback(
                (positionSeconds * 1000.0).toLong().coerceAtLeast(0L),
                (durationSeconds * 1000.0).toLong().coerceAtLeast(0L)
            )
        }
    }

    @JavascriptInterface
    fun onError(code: Int) {
        mainHandler.post { onErrorCallback(code) }
    }
}

@Composable
private fun YouTubeWebPlayer(
    videoId: String,
    startPositionMs: Long,
    autoplay: Boolean,
    controller: YouTubePlayerController,
    onProgress: (Long, Long) -> Unit,
    onReady: () -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onError: (Int) -> Unit
) {
    val webViewState = remember(videoId) { mutableStateOf<WebView?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = { context ->
            WebView(context).apply {
                webViewState.value = this
                controller.webView = this
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                addJavascriptInterface(
                    YouTubeJavascriptBridge(onReady, onProgress, onPlayingChanged, onError),
                    "Android"
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val uri = request?.url
                        if (uri?.host == "appassets.androidplatform.net" && uri.path == "/assets/youtube_player.html") {
                            return WebResourceResponse(
                                "text/html",
                                "UTF-8",
                                context.assets.open("youtube_player.html")
                            )
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }
                val fullscreenClient = FullscreenWebChromeClient(context.findActivity())
                controller.chromeClient = fullscreenClient
                webChromeClient = fullscreenClient
                val playerUrl = buildString {
                    append("https://appassets.androidplatform.net/assets/youtube_player.html")
                    append("?video=").append(Uri.encode(videoId))
                    append("&start=").append((startPositionMs / 1000L).coerceAtLeast(0L))
                    append("&autoplay=").append(if (autoplay) "1" else "0")
                }
                loadUrl(playerUrl, mapOf("Referer" to "https://appassets.androidplatform.net/"))
            }
        },
        update = { controller.webView = it }
    )
    DisposableEffect(videoId) {
        onDispose {
            controller.stop()
            webViewState.value?.stopLoading()
            webViewState.value?.loadUrl("about:blank")
            webViewState.value?.removeJavascriptInterface("Android")
            controller.chromeClient?.hideCustomViewIfNeeded()
            webViewState.value?.destroy()
            controller.webView = null
            controller.chromeClient = null
        }
    }
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

private fun youtubeErrorMessage(code: Int): String = when (code) {
    -1 -> "El reproductor de YouTube no respondió. Revisa Internet o abre el video en YouTube."
    2 -> "El identificador del video no es válido."
    5 -> "El reproductor HTML5 no está disponible en este dispositivo."
    100 -> "El video fue eliminado o es privado."
    101, 150 -> "El propietario no permite reproducir este video dentro de otras aplicaciones."
    153 -> "YouTube no reconoció la identidad del reproductor. Abre el video en YouTube."
    else -> "YouTube no pudo reproducir este video (error $code)."
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
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
            FilledIconButton(onClick = onPlay, modifier = Modifier.align(Alignment.Center)) { Icon(Icons.Default.PlayArrow, "Reproducir") }
        }
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(video.channelTitle.take(1).uppercase(), fontWeight = FontWeight.Bold)
            }
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
                Text(video.channelTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp), maxLines = 1)
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
private fun Thumbnail(url: String, modifier: Modifier) {
    if (url.isBlank()) {
        Box(modifier = modifier.background(Brush.linearGradient(listOf(Color(0xFF301A56), Color(0xFF7C4DFF), Color(0xFF111118)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(52.dp), tint = Color.White.copy(0.8f))
        }
    } else {
        AsyncImage(model = url, contentDescription = null, modifier = modifier.background(Color.Black), contentScale = ContentScale.Crop)
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
