package com.geovideos.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.GeoVideosRepository
import com.geovideos.app.data.GoogleProfile
import com.geovideos.app.data.NotificationItem
import com.geovideos.app.data.PlaylistItem
import com.geovideos.app.data.VideoItem
import com.geovideos.app.network.YouTubeApi
import com.geovideos.app.network.YouTubeApiException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope


enum class AuthStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class MainSection {
    HOME,
    SHORTS,
    SEARCH,
    LIBRARY,
    ACCOUNT
}

enum class HomeCategory {
    FOR_YOU,
    LIVE,
    GAMING,
    MUSIC
}

data class GeoVideosUiState(
    val authStatus: AuthStatus = AuthStatus.DISCONNECTED,
    val authError: String = "",
    val profile: GoogleProfile? = null,
    val section: MainSection = MainSection.HOME,
    val homeCategory: HomeCategory = HomeCategory.FOR_YOU,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val personalized: List<VideoItem> = emptyList(),
    val popular: List<VideoItem> = emptyList(),
    val live: List<VideoItem> = emptyList(),
    val gaming: List<VideoItem> = emptyList(),
    val music: List<VideoItem> = emptyList(),
    val shorts: List<VideoItem> = emptyList(),
    val searchResults: List<VideoItem> = emptyList(),
    val subscriptions: List<ChannelItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val liked: List<VideoItem> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val history: List<VideoItem> = emptyList(),
    val watchLater: List<VideoItem> = emptyList(),
    val downloads: List<VideoItem> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val selectedVideo: VideoItem? = null,
    val playerExpanded: Boolean = false,
    val selectedChannelTitle: String = "",
    val channelVideos: List<VideoItem> = emptyList(),
    val autoplay: Boolean = true,
    val dataSaver: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val lastSyncMs: Long = 0L,
    val message: String? = null
)

class GeoVideosViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GeoVideosRepository(application)
    private val api = YouTubeApi()
    private var accessToken: String? = null
    private var likesPlaylistId: String = ""

    private val cachedProfile = repository.loadProfile()
    private val _uiState = MutableStateFlow(
        GeoVideosUiState(
            authStatus = if (repository.hasConnectedAccount() && cachedProfile != null) {
                AuthStatus.CONNECTED
            } else {
                AuthStatus.DISCONNECTED
            },
            profile = cachedProfile,
            personalized = repository.loadPersonalized(),
            popular = repository.loadPopular(),
            live = repository.loadLive(),
            gaming = repository.loadGaming(),
            music = repository.loadMusic(),
            shorts = repository.loadShorts(),
            subscriptions = repository.loadSubscriptions(),
            playlists = repository.loadPlaylists(),
            liked = repository.loadLiked(),
            notifications = repository.loadNotifications(),
            history = repository.loadHistory(),
            watchLater = repository.loadWatchLater(),
            downloads = repository.loadDownloads(),
            searchHistory = repository.loadSearchHistory(),
            autoplay = repository.loadAutoplay(),
            dataSaver = repository.loadDataSaver(),
            notificationsEnabled = repository.loadNotificationsEnabled(),
            lastSyncMs = repository.loadLastSyncMs()
        )
    )
    val uiState: StateFlow<GeoVideosUiState> = _uiState.asStateFlow()

    fun beginAuthorization() {
        if (_uiState.value.profile == null) {
            _uiState.update { it.copy(authStatus = AuthStatus.CONNECTING, authError = "") }
        } else {
            _uiState.update { it.copy(authError = "", message = "Renovando acceso de Google…") }
        }
    }

    fun onSilentAuthorizationUnavailable() {
        if (_uiState.value.profile == null) {
            _uiState.update { it.copy(authStatus = AuthStatus.DISCONNECTED, loading = false) }
        } else {
            _uiState.update {
                it.copy(
                    authStatus = AuthStatus.CONNECTED,
                    loading = false,
                    message = null
                )
            }
        }
    }

    fun onSilentAuthorizationFailure(message: String) {
        if (_uiState.value.profile == null) {
            _uiState.update { it.copy(authStatus = AuthStatus.DISCONNECTED, loading = false, authError = message) }
        } else {
            _uiState.update {
                it.copy(
                    authStatus = AuthStatus.CONNECTED,
                    loading = false,
                    message = "Se muestran datos guardados. Pulsa actualizar para renovar el acceso."
                )
            }
        }
    }

    fun onAuthorizationSuccess(token: String?) {
        if (token.isNullOrBlank()) {
            onAuthorizationFailure("Google no devolvió un token de acceso.", false)
            return
        }
        accessToken = token
        repository.markConnected(true)
        _uiState.update {
            it.copy(
                authStatus = AuthStatus.CONNECTED,
                loading = it.profile == null && it.personalized.isEmpty(),
                authError = ""
            )
        }
        loadAll(initialLoad = _uiState.value.lastSyncMs == 0L)
    }

    fun onAuthorizationFailure(message: String, cloudSetupLikely: Boolean) {
        val profile = _uiState.value.profile
        if (profile != null) {
            _uiState.update {
                it.copy(
                    authStatus = AuthStatus.CONNECTED,
                    authError = "",
                    loading = false,
                    refreshing = false,
                    message = if (cloudSetupLikely) {
                        "Google rechazó la firma registrada. Se conservaron los datos guardados."
                    } else {
                        "No se renovó la sesión. Se conservaron los datos guardados."
                    }
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    authStatus = AuthStatus.ERROR,
                    authError = message,
                    loading = false,
                    refreshing = false
                )
            }
        }
    }

    fun disconnect() {
        accessToken = null
        likesPlaylistId = ""
        repository.clearAccountCache()
        _uiState.update {
            GeoVideosUiState(
                authStatus = AuthStatus.DISCONNECTED,
                history = repository.loadHistory(),
                watchLater = repository.loadWatchLater(),
                downloads = repository.loadDownloads(),
                searchHistory = repository.loadSearchHistory(),
                autoplay = repository.loadAutoplay(),
                dataSaver = repository.loadDataSaver(),
                notificationsEnabled = repository.loadNotificationsEnabled()
            )
        }
    }

    fun clearLocalData() {
        repository.clearLocalUserData()
        _uiState.update {
            it.copy(
                history = emptyList(),
                watchLater = emptyList(),
                downloads = emptyList(),
                searchHistory = emptyList(),
                message = "Historial y datos locales eliminados."
            )
        }
    }

    fun refresh() {
        if (_uiState.value.refreshing) return
        if (accessToken.isNullOrBlank()) {
            _uiState.update {
                it.copy(message = "La cuenta sigue guardada, pero debes pulsar Renovar acceso en Cuenta una vez.")
            }
            return
        }
        _uiState.update { it.copy(refreshing = true) }
        loadAll(initialLoad = false)
    }

    private fun loadAll(initialLoad: Boolean) {
        val token = accessToken ?: return
        viewModelScope.launch {
            val previous = _uiState.value
            try {
                supervisorScope {
                    val userDeferred = async { api.getUserInfo(token) }
                    val popularDeferred = async { runCatching { api.mostPopular(token) }.getOrDefault(previous.popular) }
                    val liveDeferred = async { runCatching { api.liveVideos(token) }.getOrDefault(previous.live) }
                    val gamingDeferred = async { runCatching { api.mostPopular(token, "20") }.getOrDefault(previous.gaming) }
                    val musicDeferred = async { runCatching { api.musicVideos(token) }.getOrDefault(previous.music) }
                    val shortsDeferred = async { runCatching { api.shorts(token) }.getOrDefault(previous.shorts) }
                    val subscriptionsDeferred = async { runCatching { api.subscriptions(token) }.getOrDefault(previous.subscriptions) }
                    val playlistsDeferred = async { runCatching { api.playlists(token) }.getOrDefault(previous.playlists) }
                    val activitiesDeferred = async {
                        if (previous.notificationsEnabled) {
                            runCatching { api.homeActivities(token) }.getOrDefault(previous.notifications)
                        } else {
                            emptyList()
                        }
                    }

                    val baseProfile = userDeferred.await()
                    val channelDetails = runCatching { api.getMyChannel(token, baseProfile) }.getOrNull()
                    likesPlaylistId = channelDetails?.likesPlaylistId.orEmpty()
                    val liked = runCatching { api.playlistVideos(token, likesPlaylistId, 30) }
                        .getOrDefault(previous.liked)
                    val subscriptions = subscriptionsDeferred.await()
                    val subscriptionFeed = subscriptions
                        .take(14)
                        .map { channel ->
                            async {
                                runCatching { api.channelActivities(token, channel.id, 4) }
                                    .getOrDefault(emptyList())
                            }
                        }
                        .awaitAll()
                        .flatten()
                        .sortedByDescending { it.publishedAt }

                    val popular = popularDeferred.await()
                    val notifications = activitiesDeferred.await()
                    val activityVideos = notifications.mapNotNull { it.video }
                    val personalized = mergeUniqueVideos(
                        subscriptionFeed,
                        activityVideos,
                        previous.history.take(10),
                        liked.take(12),
                        popular
                    ).take(50)

                    val profile = channelDetails?.profile ?: baseProfile
                    val syncTime = System.currentTimeMillis()
                    val live = liveDeferred.await()
                    val gaming = gamingDeferred.await()
                    val music = musicDeferred.await()
                    val shorts = shortsDeferred.await()
                    val playlists = playlistsDeferred.await()

                    repository.saveRemoteSnapshot(
                        profile = profile,
                        personalized = personalized,
                        popular = popular,
                        live = live,
                        gaming = gaming,
                        music = music,
                        shorts = shorts,
                        liked = liked,
                        subscriptions = subscriptions,
                        playlists = playlists,
                        notifications = notifications,
                        syncTimeMs = syncTime
                    )

                    _uiState.update {
                        it.copy(
                            authStatus = AuthStatus.CONNECTED,
                            loading = false,
                            refreshing = false,
                            profile = profile,
                            personalized = personalized,
                            popular = popular,
                            live = live,
                            gaming = gaming,
                            music = music,
                            shorts = shorts,
                            subscriptions = subscriptions,
                            playlists = playlists,
                            liked = liked,
                            notifications = notifications,
                            lastSyncMs = syncTime,
                            authError = "",
                            message = if (!initialLoad) "Contenido actualizado." else null
                        )
                    }
                }
            } catch (error: YouTubeApiException) {
                handleApiError(error)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        authStatus = if (it.profile != null) AuthStatus.CONNECTED else AuthStatus.ERROR,
                        authError = if (it.profile == null) error.message ?: "No se pudo cargar YouTube." else "",
                        message = if (it.profile != null) {
                            "No se pudo actualizar. Se conservaron los datos anteriores."
                        } else {
                            error.message ?: "No se pudo cargar YouTube."
                        }
                    )
                }
            }
        }
    }

    private fun mergeUniqueVideos(vararg groups: List<VideoItem>): List<VideoItem> {
        val seen = LinkedHashSet<String>()
        val result = ArrayList<VideoItem>()
        groups.forEach { group ->
            group.forEach { video ->
                if (video.id.isNotBlank() && seen.add(video.id)) result.add(video)
            }
        }
        return result
    }

    fun selectSection(section: MainSection) {
        _uiState.update { it.copy(section = section, selectedChannelTitle = "", channelVideos = emptyList()) }
    }

    fun selectHomeCategory(category: HomeCategory) {
        _uiState.update { it.copy(homeCategory = category) }
    }

    fun play(video: VideoItem) {
        val saved = repository.loadHistory().firstOrNull { it.id == video.id }
        val playable = video.copy(
            resumePositionMs = saved?.resumePositionMs ?: video.resumePositionMs,
            durationMs = saved?.durationMs ?: video.durationMs
        )
        val history = repository.addToHistory(playable)
        _uiState.update {
            it.copy(
                selectedVideo = playable,
                playerExpanded = true,
                history = history
            )
        }
    }

    fun expandPlayer() {
        if (_uiState.value.selectedVideo != null) {
            _uiState.update { it.copy(playerExpanded = true) }
        }
    }

    fun minimizePlayer() {
        if (_uiState.value.selectedVideo != null) {
            _uiState.update { it.copy(playerExpanded = false) }
        }
    }

    fun savePlayback(video: VideoItem, positionMs: Long, durationMs: Long) {
        if (positionMs < 0L) return
        val history = repository.updatePlayback(video, positionMs, durationMs)
        _uiState.update { state ->
            state.copy(
                history = history,
                selectedVideo = state.selectedVideo?.takeIf { it.id == video.id }?.copy(
                    resumePositionMs = positionMs,
                    durationMs = durationMs
                ) ?: state.selectedVideo
            )
        }
    }

    fun closePlayer() {
        _uiState.update { it.copy(selectedVideo = null, playerExpanded = false) }
    }

    fun toggleWatchLater(video: VideoItem) {
        val updated = repository.toggleWatchLater(video)
        val added = updated.any { it.id == video.id }
        _uiState.update {
            it.copy(
                watchLater = updated,
                message = if (added) "Guardado en Ver después." else "Quitado de Ver después."
            )
        }
    }

    fun search(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val token = accessToken
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(message = "Renueva el acceso de Google para buscar contenido nuevo.") }
            return
        }
        val history = repository.addSearch(clean)
        _uiState.update { it.copy(loading = true, searchHistory = history) }
        viewModelScope.launch {
            try {
                val results = api.searchVideos(token, clean)
                _uiState.update { it.copy(loading = false, searchResults = results, section = MainSection.SEARCH) }
            } catch (error: YouTubeApiException) {
                handleApiError(error)
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, message = error.message ?: "Error al buscar.") }
            }
        }
    }

    fun openChannel(channel: ChannelItem) {
        val token = accessToken
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(message = "Renueva el acceso de Google para abrir el canal.") }
            return
        }
        _uiState.update { it.copy(loading = true, selectedChannelTitle = channel.title, channelVideos = emptyList()) }
        viewModelScope.launch {
            try {
                val videos = api.channelVideos(token, channel.id)
                _uiState.update { it.copy(loading = false, channelVideos = videos) }
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, message = error.message ?: "No se pudo abrir el canal.") }
            }
        }
    }

    fun closeChannel() {
        _uiState.update { it.copy(selectedChannelTitle = "", channelVideos = emptyList()) }
    }

    fun registerDownload(title: String, url: String, downloadId: Long) {
        val downloads = repository.addDownload(title, url, downloadId)
        _uiState.update { it.copy(downloads = downloads, message = "Descarga enviada al teléfono.") }
    }

    fun removeDownload(downloadId: Long) {
        val downloads = repository.removeDownload(downloadId)
        _uiState.update { it.copy(downloads = downloads, message = "Descarga quitada de la lista.") }
    }

    fun setAutoplay(value: Boolean) {
        repository.setAutoplay(value)
        _uiState.update { it.copy(autoplay = value) }
    }

    fun setDataSaver(value: Boolean) {
        repository.setDataSaver(value)
        _uiState.update { it.copy(dataSaver = value) }
    }

    fun setNotificationsEnabled(value: Boolean) {
        repository.setNotificationsEnabled(value)
        _uiState.update {
            it.copy(
                notificationsEnabled = value,
                notifications = if (value) it.notifications else emptyList()
            )
        }
        if (value && accessToken != null) refresh()
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun handleApiError(error: YouTubeApiException) {
        if (error.statusCode == 401) accessToken = null
        _uiState.update {
            it.copy(
                loading = false,
                refreshing = false,
                authStatus = if (it.profile != null) AuthStatus.CONNECTED else AuthStatus.ERROR,
                authError = if (it.profile == null) error.message else "",
                message = if (error.statusCode == 401 && it.profile != null) {
                    "La sesión caducó. Tus datos siguen visibles; pulsa Renovar acceso en Cuenta."
                } else {
                    error.message
                }
            )
        }
    }
}
