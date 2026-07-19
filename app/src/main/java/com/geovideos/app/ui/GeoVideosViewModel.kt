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
    NEEDS_CLOUD_SETUP,
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
    DISCOVER,
    LIVE,
    GAMING
}

data class GeoVideosUiState(
    val authStatus: AuthStatus = AuthStatus.DISCONNECTED,
    val authError: String = "",
    val profile: GoogleProfile? = null,
    val section: MainSection = MainSection.HOME,
    val homeCategory: HomeCategory = HomeCategory.DISCOVER,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val popular: List<VideoItem> = emptyList(),
    val live: List<VideoItem> = emptyList(),
    val gaming: List<VideoItem> = emptyList(),
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
    val selectedChannelTitle: String = "",
    val channelVideos: List<VideoItem> = emptyList(),
    val autoplay: Boolean = true,
    val dataSaver: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val message: String? = null
)

class GeoVideosViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GeoVideosRepository(application)
    private val api = YouTubeApi()
    private var accessToken: String? = null
    private var likesPlaylistId: String = ""

    private val _uiState = MutableStateFlow(
        GeoVideosUiState(
            history = repository.loadHistory(),
            watchLater = repository.loadWatchLater(),
            downloads = repository.loadDownloads(),
            searchHistory = repository.loadSearchHistory(),
            autoplay = repository.loadAutoplay(),
            dataSaver = repository.loadDataSaver(),
            notificationsEnabled = repository.loadNotificationsEnabled()
        )
    )
    val uiState: StateFlow<GeoVideosUiState> = _uiState.asStateFlow()

    fun beginAuthorization() {
        _uiState.update { it.copy(authStatus = AuthStatus.CONNECTING, authError = "") }
    }

    fun onAuthorizationSuccess(token: String?) {
        if (token.isNullOrBlank()) {
            onAuthorizationFailure("Google no devolvió un token de acceso.", false)
            return
        }
        accessToken = token
        _uiState.update { it.copy(authStatus = AuthStatus.CONNECTED, loading = true, authError = "") }
        loadAll(initialLoad = true)
    }

    fun onAuthorizationFailure(message: String, cloudSetupLikely: Boolean) {
        _uiState.update {
            it.copy(
                authStatus = if (cloudSetupLikely) AuthStatus.NEEDS_CLOUD_SETUP else AuthStatus.ERROR,
                authError = message,
                loading = false,
                refreshing = false
            )
        }
    }

    fun disconnect() {
        accessToken = null
        likesPlaylistId = ""
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
        repository.clearAll()
        _uiState.update {
            it.copy(
                history = emptyList(),
                watchLater = emptyList(),
                downloads = emptyList(),
                searchHistory = emptyList(),
                message = "Datos locales eliminados."
            )
        }
    }

    fun refresh() {
        if (_uiState.value.refreshing) return
        if (accessToken.isNullOrBlank()) {
            _uiState.update { it.copy(message = "Vuelve a conectar tu cuenta de Google.") }
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
                    val liked = runCatching { api.playlistVideos(token, likesPlaylistId, 24) }.getOrDefault(previous.liked)

                    _uiState.update {
                        it.copy(
                            authStatus = AuthStatus.CONNECTED,
                            loading = false,
                            refreshing = false,
                            profile = channelDetails?.profile ?: baseProfile,
                            popular = popularDeferred.await(),
                            live = liveDeferred.await(),
                            gaming = gamingDeferred.await(),
                            shorts = shortsDeferred.await(),
                            subscriptions = subscriptionsDeferred.await(),
                            playlists = playlistsDeferred.await(),
                            liked = liked,
                            notifications = activitiesDeferred.await(),
                            authError = "",
                            message = if (!initialLoad) "Contenido actualizado." else it.message
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
        _uiState.update { it.copy(selectedVideo = playable, history = history) }
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
        _uiState.update { it.copy(selectedVideo = null) }
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
        val token = accessToken ?: return
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
        val token = accessToken ?: return
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
        _uiState.update { it.copy(notificationsEnabled = value, notifications = if (value) it.notifications else emptyList()) }
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
                authStatus = if (error.statusCode == 401) AuthStatus.ERROR else it.authStatus,
                authError = if (error.statusCode == 401) {
                    "La sesión de Google expiró. Pulsa Reconectar."
                } else {
                    it.authError
                },
                message = error.message
            )
        }
    }
}
