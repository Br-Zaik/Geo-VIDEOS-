package com.geovideos.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.geovideos.app.R
import com.geovideos.app.data.VideoItem

@Composable
fun GeoVideosApp(viewModel: GeoVideosViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    val selectedVideo = state.selectedVideo

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.loading -> LoadingScreen()
            state.session == null -> AuthScreen(
                hasAccount = state.hasAccount,
                snackbarHostState = snackbarHostState,
                onCreateAccount = viewModel::createAccount,
                onLogin = viewModel::login
            )
            selectedVideo != null -> PlayerScreen(
                video = selectedVideo,
                isFavorite = selectedVideo.id in state.favoriteIds,
                snackbarHostState = snackbarHostState,
                onBack = viewModel::closePlayer,
                onToggleFavorite = { viewModel.toggleFavorite(selectedVideo.id) }
            )
            else -> MainShell(
                state = state,
                snackbarHostState = snackbarHostState,
                onSection = viewModel::changeSection,
                onPlay = viewModel::play,
                onFavorite = viewModel::toggleFavorite,
                onAddDirect = viewModel::addDirectVideo,
                onAddLocal = viewModel::addLocalVideo,
                onRemove = viewModel::removeVideo,
                onLogout = viewModel::logout,
                onDeleteAccount = viewModel::deleteAccount
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AuthScreen(
    hasAccount: Boolean,
    snackbarHostState: SnackbarHostState,
    onCreateAccount: (String, String, String) -> Unit,
    onLogin: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 26.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = "Logo Geo Videos",
                modifier = Modifier.size(112.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "Geo Videos",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = if (hasAccount) "Inicia sesion en tu cuenta del dispositivo" else "Crea una cuenta para guardar tu biblioteca",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
            )

            if (!hasAccount) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contrasena") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (hasAccount) onLogin(email, password)
                    else onCreateAccount(name, email, password)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (hasAccount) "Iniciar sesion" else "Crear cuenta")
            }
            Text(
                text = "La cuenta se guarda solo en este telefono. No es una cuenta de Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    state: GeoVideosUiState,
    snackbarHostState: SnackbarHostState,
    onSection: (MainSection) -> Unit,
    onPlay: (VideoItem) -> Unit,
    onFavorite: (String) -> Unit,
    onAddDirect: (String, String, String) -> Unit,
    onAddLocal: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val localVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onAddLocal(getDisplayName(context, it), it.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Geo Videos", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationItem(MainSection.HOME, state.section, "Inicio", Icons.Default.Home, onSection)
                NavigationItem(MainSection.SEARCH, state.section, "Buscar", Icons.Default.Search, onSection)
                NavigationItem(MainSection.LIBRARY, state.section, "Biblioteca", Icons.Default.VideoLibrary, onSection)
                NavigationItem(MainSection.PROFILE, state.section, "Perfil", Icons.Default.Person, onSection)
            }
        },
        floatingActionButton = {
            if (state.section != MainSection.PROFILE) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Agregar video") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (state.section) {
            MainSection.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                videos = state.videos,
                favoriteIds = state.favoriteIds,
                historyIds = state.historyIds,
                onPlay = onPlay,
                onFavorite = onFavorite
            )
            MainSection.SEARCH -> SearchScreen(
                modifier = Modifier.padding(padding),
                videos = state.videos,
                favoriteIds = state.favoriteIds,
                onPlay = onPlay,
                onFavorite = onFavorite
            )
            MainSection.LIBRARY -> LibraryScreen(
                modifier = Modifier.padding(padding),
                videos = state.videos,
                customVideoIds = state.customVideoIds,
                favoriteIds = state.favoriteIds,
                historyIds = state.historyIds,
                onPlay = onPlay,
                onFavorite = onFavorite,
                onRemove = onRemove
            )
            MainSection.PROFILE -> ProfileScreen(
                modifier = Modifier.padding(padding),
                name = state.session?.displayName.orEmpty(),
                email = state.session?.email.orEmpty(),
                onLogout = onLogout,
                onDeleteAccount = onDeleteAccount
            )
        }
    }

    if (showAddDialog) {
        AddVideoDialog(
            onDismiss = { showAddDialog = false },
            onAddDirect = { title, creator, url ->
                onAddDirect(title, creator, url)
                showAddDialog = false
            },
            onPickLocal = {
                showAddDialog = false
                localVideoPicker.launch(arrayOf("video/*"))
            }
        )
    }
}

@Composable
private fun RowScope.NavigationItem(
    section: MainSection,
    selected: MainSection,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSection: (MainSection) -> Unit
) {
    NavigationBarItem(
        selected = section == selected,
        onClick = { onSection(section) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    videos: List<VideoItem>,
    favoriteIds: Set<String>,
    historyIds: List<String>,
    onPlay: (VideoItem) -> Unit,
    onFavorite: (String) -> Unit
) {
    val history = historyIds.mapNotNull { id -> videos.firstOrNull { it.id == id } }.take(8)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            HeroCard(onClick = { videos.firstOrNull()?.let(onPlay) })
        }
        if (history.isNotEmpty()) {
            item { SectionTitle("Continuar viendo") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(history, key = { it.id }) { video ->
                        CompactVideoCard(video = video, onPlay = { onPlay(video) })
                    }
                }
            }
        }
        item { SectionTitle("Para ti") }
        items(videos, key = { it.id }) { video ->
            VideoCard(
                video = video,
                isFavorite = video.id in favoriteIds,
                onPlay = { onPlay(video) },
                onFavorite = { onFavorite(video.id) }
            )
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun HeroCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3B1766), Color(0xFF8B5CF6), Color(0xFF22102F))
                    )
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text("Tu biblioteca, a tu manera", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Reproduce archivos locales y enlaces directos autorizados.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.BottomEnd).size(58.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir", modifier = Modifier.size(34.dp))
            }
        }
    }
}

@Composable
private fun SearchScreen(
    modifier: Modifier,
    videos: List<VideoItem>,
    favoriteIds: Set<String>,
    onPlay: (VideoItem) -> Unit,
    onFavorite: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, videos) {
        val cleaned = query.trim()
        if (cleaned.isBlank()) videos
        else videos.filter {
            it.title.contains(cleaned, ignoreCase = true) ||
                it.creator.contains(cleaned, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                label = { Text("Buscar en tu biblioteca") },
                singleLine = true
            )
        }
        if (results.isEmpty()) {
            item { EmptyState("No hay resultados para esa busqueda.") }
        } else {
            items(results, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    isFavorite = video.id in favoriteIds,
                    onPlay = { onPlay(video) },
                    onFavorite = { onFavorite(video.id) }
                )
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    videos: List<VideoItem>,
    customVideoIds: Set<String>,
    favoriteIds: Set<String>,
    historyIds: List<String>,
    onPlay: (VideoItem) -> Unit,
    onFavorite: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    val favorites = videos.filter { it.id in favoriteIds }
    val history = historyIds.mapNotNull { id -> videos.firstOrNull { it.id == id } }
    val added = videos.filter { it.id in customVideoIds }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Favoritos") }
        if (favorites.isEmpty()) item { EmptyState("Todavia no marcaste videos como favoritos.") }
        else items(favorites, key = { "fav-${it.id}" }) { video ->
            VideoCard(video, true, { onPlay(video) }, { onFavorite(video.id) })
        }

        item { SectionTitle("Historial") }
        if (history.isEmpty()) item { EmptyState("Los videos que reproduzcas apareceran aqui.") }
        else items(history.take(12), key = { "history-${it.id}" }) { video ->
            VideoCard(video, video.id in favoriteIds, { onPlay(video) }, { onFavorite(video.id) })
        }

        item { SectionTitle("Agregados por ti") }
        if (added.isEmpty()) item { EmptyState("Usa Agregar video para elegir un archivo o pegar un enlace directo.") }
        else items(added, key = { "added-${it.id}" }) { video ->
            VideoCard(
                video = video,
                isFavorite = video.id in favoriteIds,
                onPlay = { onPlay(video) },
                onFavorite = { onFavorite(video.id) },
                onDelete = { onRemove(video.id) }
            )
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun ProfileScreen(
    modifier: Modifier,
    name: String,
    email: String,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(22.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        }
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Cuenta local", fontWeight = FontWeight.Bold)
                Text(
                    "Tus datos, favoritos e historial se guardan en este dispositivo. Esta version no usa servidores ni Google.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Cerrar sesion")
        }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Icon(Icons.Default.Delete, null)
            Spacer(Modifier.width(8.dp))
            Text("Eliminar cuenta y datos")
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar todo") },
            text = { Text("Se borraran la cuenta local, favoritos, historial y videos agregados.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteAccount()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun VideoCard(
    video: VideoItem,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(videoGradient(video.id))
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(62.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir", tint = Color.White, modifier = Modifier.size(38.dp))
                }
                Text(
                    text = if (video.source.startsWith("content://")) "LOCAL" else if (video.isBuiltIn) "MUESTRA" else "ENLACE",
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(video.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(video.creator, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                onDelete?.let {
                    IconButton(onClick = it) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
                }
            }
        }
    }
}

@Composable
private fun CompactVideoCard(video: VideoItem, onPlay: () -> Unit) {
    Card(
        modifier = Modifier.width(220.dp),
        onClick = onPlay,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(videoGradient(video.id)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Text(video.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun EmptyState(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(18.dp))
    }
}

@Composable
private fun AddVideoDialog(
    onDismiss: () -> Unit,
    onAddDirect: (String, String, String) -> Unit,
    onPickLocal: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var creator by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titulo") }, singleLine = true)
                OutlinedTextField(value = creator, onValueChange = { creator = it }, label = { Text("Autor o categoria") }, singleLine = true)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Enlace directo del video") },
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                    singleLine = true
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                OutlinedButton(onClick = onPickLocal, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Elegir video del telefono")
                }
                Text(
                    "No acepta paginas de YouTube: solo archivos locales o enlaces directos autorizados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAddDirect(title, creator, url) }) { Text("Agregar enlace") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(
    video: VideoItem,
    isFavorite: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val player = remember(video.source) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(video.source)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                factory = { playerContext ->
                    PlayerView(playerContext).apply {
                        this.player = player
                        useController = true
                        keepScreenOn = true
                    }
                },
                update = { it.player = player }
            )
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(video.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(video.creator, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Este video ya fue agregado a tu historial local.")
                    }
                }
            }
        }
    }
}

private fun videoGradient(seed: String): Brush {
    val variants = listOf(
        listOf(Color(0xFF2E1065), Color(0xFF7C3AED)),
        listOf(Color(0xFF0F3D3E), Color(0xFF2A9D8F)),
        listOf(Color(0xFF532E1C), Color(0xFFE76F51)),
        listOf(Color(0xFF1E3A5F), Color(0xFF3B82F6))
    )
    return Brush.linearGradient(variants[Math.floorMod(seed.hashCode(), variants.size)])
}

private fun getDisplayName(context: Context, uri: Uri): String {
    var result = "Video local"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) result = cursor.getString(index).orEmpty().ifBlank { result }
        }
    }
    return result
}
