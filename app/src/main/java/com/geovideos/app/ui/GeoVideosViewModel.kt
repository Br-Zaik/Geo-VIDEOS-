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
import com.geovideos.app.data.VideoDetails
import com.geovideos.app.network.VideoPage
import com.geovideos.app.network.YouTubeApi
import com.geovideos.app.network.YouTubeApiException
import com.geovideos.app.playback.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

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
    val loadingMoreCategory: HomeCategory? = null,
    val canLoadMoreForYou: Boolean = true,
    val canLoadMoreLive: Boolean = true,
    val canLoadMoreGaming: Boolean = true,
    val canLoadMoreMusic: Boolean = true,
    val shortsLoadingMore: Boolean = false,
    val shortsCanLoadMore: Boolean = true,
    val uploadsLoadingMore: Boolean = false,
    val uploadsCanLoadMore: Boolean = true,
    val likedLoadingMore: Boolean = false,
    val likedCanLoadMore: Boolean = true,
    val searchLoadingMore: Boolean = false,
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
    val uploads: List<VideoItem> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val history: List<VideoItem> = emptyList(),
    val watchLater: List<VideoItem> = emptyList(),
    val localLikedIds: Set<String> = emptySet(),
    val localDislikedIds: Set<String> = emptySet(),
    val localLikedVideos: List<VideoItem> = emptyList(),
    val downloads: List<VideoItem> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val selectedVideo: VideoItem? = null,
    val playerExpanded: Boolean = false,
    val playerDetails: VideoDetails? = null,
    val playerDetailsLoading: Boolean = false,
    val relatedVideos: List<VideoItem> = emptyList(),
    val relatedLoading: Boolean = false,
    val relatedLoadingMore: Boolean = false,
    val relatedCanLoadMore: Boolean = true,
    val selectedChannelTitle: String = "",
    val selectedChannel: ChannelItem? = null,
    val channelVideos: List<VideoItem> = emptyList(),
    val channelPlaylists: List<PlaylistItem> = emptyList(),
    val autoplay: Boolean = true,
    val dataSaver: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val youtubeSyncEnabled: Boolean = false,
    val youtubeSyncAuthorized: Boolean = false,
    val youtubeSyncBusy: Boolean = false,
    val lastSyncMs: Long = 0L,
    val message: String? = null
) {
    fun isLoadingMore(category: HomeCategory): Boolean = loadingMoreCategory == category

    fun canLoadMore(category: HomeCategory): Boolean = when (category) {
        HomeCategory.FOR_YOU -> canLoadMoreForYou
        HomeCategory.LIVE -> canLoadMoreLive
        HomeCategory.GAMING -> canLoadMoreGaming
        HomeCategory.MUSIC -> canLoadMoreMusic
    }
}

private class GoogleAccountMismatchException(message: String) : IllegalStateException(message)

class GeoVideosViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GeoVideosRepository(application)
    private val api = YouTubeApi()

    private var accessToken: String? = null
    private var youtubeWriteToken: String? = null
    private var likesPlaylistId: String = ""
    private var uploadsPlaylistId: String = ""
    private var popularNextToken: String = ""
    private var liveNextToken: String = ""
    private var gamingNextToken: String = ""
    private var musicNextToken: String = ""
    private var shortsNextToken: String = ""
    private var shortsQuery: String = "shorts virales humor gaming musica anime deportes animales curiosidades tecnologia español"
    private var uploadsNextToken: String = ""
    private var likedNextToken: String = ""
    private var searchNextToken: String = ""
    private var relatedNextToken: String = ""
    private var relatedVideoId: String = ""
    private var lastSearchQuery: String = ""
    private var subscriptionOffset: Int = 0
    private var subscriptionRefreshCursor: Int = 0
    private var pendingSubscriptionToggle: ChannelItem? = null
    private var authorizationAttemptId: Long = 0L

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
            shorts = repository.loadShorts().filter(::looksLikeShort),
            subscriptions = repository.loadSubscriptions(),
            playlists = repository.loadPlaylists(),
            liked = repository.loadLiked(),
            uploads = repository.loadUploads(),
            notifications = repository.loadNotifications(),
            history = repository.loadHistory(),
            watchLater = repository.loadWatchLater(),
            localLikedIds = repository.loadLocalLikedIds(),
            localDislikedIds = repository.loadLocalDislikedIds(),
            localLikedVideos = repository.loadLocalLikedVideos(),
            downloads = repository.loadDownloads(),
            searchHistory = repository.loadSearchHistory(),
            autoplay = repository.loadAutoplay(),
            dataSaver = repository.loadDataSaver(),
            notificationsEnabled = repository.loadNotificationsEnabled(),
            youtubeSyncEnabled = repository.loadYouTubeSyncEnabled(),
            youtubeSyncAuthorized = false,
            lastSyncMs = repository.loadLastSyncMs()
        )
    )
    val uiState: StateFlow<GeoVideosUiState> = _uiState.asStateFlow()

    init {
        val cachedShorts = _uiState.value.shorts
        if (cachedShorts.isNotEmpty()) {
            viewModelScope.launch {
                val verified = verifyRealShorts(cachedShorts)
                _uiState.update { state ->
                    state.copy(shorts = verified.ifEmpty { state.shorts })
                }
            }
        }
    }

    fun beginYouTubeSyncAuthorization() {
        _uiState.update { it.copy(youtubeSyncBusy = true, message = "Solicitando permiso de sincronización con YouTube…") }
    }

    fun onYouTubeSyncAuthorizationSuccess(token: String?) {
        if (token.isNullOrBlank()) {
            onYouTubeSyncAuthorizationFailure("Google no devolvió el permiso de YouTube.")
            return
        }
        youtubeWriteToken = token
        repository.setYouTubeSyncEnabled(true)
        _uiState.update {
            it.copy(
                youtubeSyncEnabled = true,
                youtubeSyncAuthorized = true,
                youtubeSyncBusy = false,
                message = "Sincronización con YouTube activada."
            )
        }
        refreshSynchronizedAccountData()
        syncPendingLocalAccountChanges()
    }

    fun onYouTubeSyncAuthorizationUnavailable() {
        _uiState.update {
            it.copy(
                youtubeSyncAuthorized = false,
                youtubeSyncBusy = false,
                message = if (it.youtubeSyncEnabled) "La sincronización de YouTube necesita autorización cuando vuelvas a usar una acción sincronizada." else it.message
            )
        }
    }

    fun onYouTubeSyncAuthorizationFailure(message: String) {
        youtubeWriteToken = null
        _uiState.update {
            it.copy(
                youtubeSyncAuthorized = false,
                youtubeSyncBusy = false,
                message = message.ifBlank { "No se pudo autorizar la sincronización con YouTube." }
            )
        }
    }

    fun queueSubscriptionToggle(channel: ChannelItem) {
        pendingSubscriptionToggle = channel
        _uiState.update {
            it.copy(message = "Autoriza YouTube una vez para sincronizar esta suscripción.")
        }
    }

    private fun syncPendingLocalAccountChanges() {
        val token = youtubeWriteToken ?: return
        val snapshot = _uiState.value
        val localLikes = snapshot.localLikedVideos
        val localDislikes = snapshot.localDislikedIds
        val localWatchLater = snapshot.watchLater
        val pendingSubscription = pendingSubscriptionToggle.also { pendingSubscriptionToggle = null }

        viewModelScope.launch {
            runCatching {
                localLikes.take(250).forEach { video ->
                    api.rateVideo(token, video.id, "like")
                }
                localDislikes.take(250).forEach { videoId ->
                    api.rateVideo(token, videoId, "dislike")
                }

                if (localWatchLater.isNotEmpty()) {
                    var playlistId = repository.loadGeoWatchLaterPlaylistId()
                    if (playlistId.isBlank()) {
                        playlistId = api.findPlaylistByTitle(token, GEO_WATCH_LATER_TITLE)
                            ?: api.createPrivatePlaylist(
                                token,
                                GEO_WATCH_LATER_TITLE,
                                "Lista privada sincronizada desde Geo Videos"
                            )
                        if (playlistId.isNotBlank()) repository.saveGeoWatchLaterPlaylistId(playlistId)
                    }
                    if (playlistId.isNotBlank()) {
                        localWatchLater.take(250).forEach { video ->
                            val existing = api.findPlaylistItemId(token, playlistId, video.id)
                            if (existing == null) api.addVideoToPlaylist(token, playlistId, video.id)
                        }
                    }
                }
            }.onFailure(::handleYouTubeWriteFailure)

            pendingSubscription?.let { channel ->
                toggleSubscription(channel)
            }
        }
    }

    fun disableYouTubeSync() {
        youtubeWriteToken = null
        repository.setYouTubeSyncEnabled(false)
        _uiState.update {
            it.copy(
                youtubeSyncEnabled = false,
                youtubeSyncAuthorized = false,
                youtubeSyncBusy = false,
                message = "Sincronización con YouTube desactivada. El historial local se conserva."
            )
        }
    }

    private fun refreshSynchronizedAccountData() {
        val token = youtubeWriteToken ?: return
        viewModelScope.launch {
            runCatching {
                val subscriptions = api.subscriptions(token)
                val playlists = api.playlists(token)
                subscriptions to playlists
            }.onSuccess { (subscriptions, playlists) ->
                repository.saveSubscriptions(subscriptions)
                _uiState.update {
                    it.copy(
                        subscriptions = subscriptions,
                        playlists = playlists,
                        youtubeSyncAuthorized = true,
                        youtubeSyncBusy = false
                    )
                }
            }.onFailure { error ->
                if (error is YouTubeApiException && error.statusCode == 401) youtubeWriteToken = null
                _uiState.update { it.copy(youtubeSyncBusy = false) }
            }
        }
    }

    fun connectedAccountEmail(): String = _uiState.value.profile?.email.orEmpty().trim()

    fun beginAuthorization() {
        _uiState.update {
            it.copy(
                authStatus = AuthStatus.CONNECTING,
                authError = "",
                loading = false,
                message = null
            )
        }
    }

    fun onSilentAuthorizationUnavailable() {
        if (_uiState.value.profile == null) {
            _uiState.update { it.copy(authStatus = AuthStatus.DISCONNECTED, loading = false) }
        } else {
            _uiState.update { it.copy(authStatus = AuthStatus.CONNECTED, loading = false, message = null) }
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

    fun onAuthorizationSuccess(
        token: String?,
        selectedEmail: String? = null,
        selectedName: String? = null,
        selectedPhotoUrl: String? = null,
        interactive: Boolean = true
    ) {
        if (token.isNullOrBlank()) {
            onAuthorizationFailure("Google no devolvió un token de acceso.", false)
            return
        }

        val attemptId = ++authorizationAttemptId
        val previousProfile = _uiState.value.profile
        val selected = selectedEmail.orEmpty().trim()

        // No se abre la aplicación solo por recibir un token. El token se valida
        // contra userinfo y, cuando Google informa la cuenta elegida, ambos correos
        // deben coincidir antes de guardar la sesión.
        _uiState.update {
            it.copy(
                authStatus = AuthStatus.CONNECTING,
                authError = "",
                loading = false,
                message = null
            )
        }

        viewModelScope.launch {
            try {
                val tokenProfile = api.getUserInfo(token)
                if (attemptId != authorizationAttemptId) return@launch

                val verifiedEmail = tokenProfile.email.trim()
                if (verifiedEmail.isBlank()) {
                    throw IllegalStateException(
                        "Google autorizó el acceso, pero no devolvió el correo de la cuenta."
                    )
                }

                if (selected.isNotBlank() && !selected.equals(verifiedEmail, ignoreCase = true)) {
                    throw IllegalStateException(
                        "La cuenta elegida no coincide con el token devuelto por Google. Vuelve a elegir la cuenta."
                    )
                }

                val previousEmail = previousProfile?.email.orEmpty().trim()
                if (!interactive && previousEmail.isNotBlank() &&
                    !previousEmail.equals(verifiedEmail, ignoreCase = true)
                ) {
                    throw IllegalStateException(
                        "Google intentó renovar otra cuenta distinta de la que Geo Videos tenía guardada."
                    )
                }

                val changedAccount = previousEmail.isNotBlank() &&
                    !previousEmail.equals(verifiedEmail, ignoreCase = true)
                val newAccountSession = previousProfile == null || changedAccount

                val verifiedProfile = tokenProfile.copy(
                    name = tokenProfile.name.trim().ifBlank {
                        selectedName.orEmpty().trim().ifBlank { verifiedEmail.substringBefore('@') }
                    },
                    email = verifiedEmail,
                    pictureUrl = tokenProfile.pictureUrl.trim().ifBlank { selectedPhotoUrl.orEmpty().trim() },
                    channelTitle = "",
                    channelId = ""
                )

                if (newAccountSession) {
                    repository.clearAccountCache()
                    repository.clearYouTubeSyncAccountBinding()
                    youtubeWriteToken = null
                    likesPlaylistId = ""
                    uploadsPlaylistId = ""
                }

                accessToken = token
                repository.saveConnectedProfile(verifiedProfile)
                resetPagination()

                _uiState.update { state ->
                    val base = if (newAccountSession) {
                        state.copy(
                            personalized = emptyList(),
                            popular = emptyList(),
                            live = emptyList(),
                            gaming = emptyList(),
                            music = emptyList(),
                            shorts = emptyList(),
                            subscriptions = emptyList(),
                            playlists = emptyList(),
                            liked = emptyList(),
                            uploads = emptyList(),
                            notifications = emptyList(),
                            searchResults = emptyList(),
                            selectedChannel = null,
                            channelVideos = emptyList(),
                            channelPlaylists = emptyList(),
                            lastSyncMs = 0L,
                            youtubeSyncEnabled = false,
                            youtubeSyncAuthorized = false,
                            youtubeSyncBusy = false
                        )
                    } else {
                        state
                    }
                    base.copy(
                        authStatus = AuthStatus.CONNECTED,
                        profile = verifiedProfile,
                        loading = newAccountSession || base.personalized.isEmpty(),
                        authError = "",
                        message = null
                    )
                }

                loadAll(initialLoad = newAccountSession || _uiState.value.lastSyncMs == 0L)
            } catch (error: Exception) {
                if (attemptId != authorizationAttemptId) return@launch
                accessToken = null
                onAuthorizationFailure(
                    error.message ?: "No se pudo comprobar la cuenta seleccionada en Google.",
                    false
                )
            }
        }
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
                    loadingMoreCategory = null,
                    shortsLoadingMore = false,
                    uploadsLoadingMore = false,
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
                    refreshing = false,
                    loadingMoreCategory = null,
                    shortsLoadingMore = false,
                    uploadsLoadingMore = false
                )
            }
        }
    }

    fun disconnect() {
        authorizationAttemptId += 1L
        accessToken = null
        youtubeWriteToken = null
        repository.clearYouTubeSyncAccountBinding()
        likesPlaylistId = ""
        uploadsPlaylistId = ""
        resetPagination()
        repository.clearAccountCache()
        _uiState.update {
            GeoVideosUiState(
                authStatus = AuthStatus.DISCONNECTED,
                youtubeSyncAuthorized = false,
                history = repository.loadHistory(),
                watchLater = repository.loadWatchLater(),
                localLikedIds = repository.loadLocalLikedIds(),
                localDislikedIds = repository.loadLocalDislikedIds(),
                localLikedVideos = repository.loadLocalLikedVideos(),
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
                localLikedIds = emptySet(),
                localDislikedIds = emptySet(),
                localLikedVideos = emptyList(),
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
        resetPagination(keepSearch = true)
        _uiState.update {
            it.copy(
                refreshing = true,
                loadingMoreCategory = null,
                shortsLoadingMore = false,
                uploadsLoadingMore = false,
                likedLoadingMore = false,
                canLoadMoreForYou = true,
                canLoadMoreLive = true,
                canLoadMoreGaming = true,
                canLoadMoreMusic = true,
                shortsCanLoadMore = true,
                uploadsCanLoadMore = true,
                likedCanLoadMore = true
            )
        }
        loadAll(initialLoad = false)
    }

    private fun loadAll(initialLoad: Boolean) {
        val token = accessToken ?: return
        viewModelScope.launch {
            val previous = _uiState.value
            try {
                supervisorScope {
                    val userDeferred = async { api.getUserInfo(token) }
                    val popularDeferred = async {
                        runCatching { api.mostPopularPage(token) }
                            .getOrDefault(VideoPage(previous.popular))
                    }
                    val liveDeferred = async {
                        runCatching { api.liveVideosPage(token) }
                            .getOrDefault(VideoPage(previous.live))
                    }
                    val gamingDeferred = async {
                        runCatching { api.mostPopularPage(token, "20") }
                            .getOrDefault(VideoPage(previous.gaming))
                    }
                    val musicDeferred = async {
                        runCatching { api.musicVideosPage(token) }
                            .getOrDefault(VideoPage(previous.music))
                    }
                    val shortsDeferred = async { VideoPage(previous.shorts) }
                    val subscriptionsDeferred = async {
                        runCatching { api.subscriptions(token) }.getOrDefault(previous.subscriptions)
                    }
                    val playlistsDeferred = async {
                        runCatching { api.playlists(token) }.getOrDefault(previous.playlists)
                    }
                    val activitiesDeferred = async {
                        if (previous.notificationsEnabled) {
                            runCatching { api.homeActivities(token) }.getOrDefault(previous.notifications)
                        } else {
                            emptyList()
                        }
                    }

                    // Publicar cada bloque en cuanto llegue. La pantalla deja de esperar a que
                    // terminen suscripciones, listas, Shorts y enriquecimiento para mostrar contenido.
                    launch {
                        val page = popularDeferred.await()
                        if (page.items.isNotEmpty()) _uiState.update { it.copy(popular = page.items.filterNot(::looksLikeShort)) }
                    }
                    launch {
                        val page = liveDeferred.await()
                        if (page.items.isNotEmpty()) _uiState.update { it.copy(live = page.items) }
                    }
                    launch {
                        val page = gamingDeferred.await()
                        if (page.items.isNotEmpty()) _uiState.update { it.copy(gaming = page.items.filterNot(::looksLikeShort)) }
                    }
                    launch {
                        val page = musicDeferred.await()
                        if (page.items.isNotEmpty()) _uiState.update { it.copy(music = page.items.filterNot(::looksLikeShort)) }
                    }

                    val baseProfile = userDeferred.await()
                    val expectedEmail = previous.profile?.email.orEmpty().trim()
                    val receivedEmail = baseProfile.email.trim()
                    if (receivedEmail.isBlank()) {
                        throw GoogleAccountMismatchException(
                            "Google dejó de identificar la cuenta autorizada. Vuelve a conectar tu cuenta."
                        )
                    }
                    if (expectedEmail.isNotBlank() &&
                        !expectedEmail.equals(receivedEmail, ignoreCase = true)
                    ) {
                        throw GoogleAccountMismatchException(
                            "El token de Google pertenece a $receivedEmail, pero Geo Videos estaba conectado con $expectedEmail."
                        )
                    }

                    val channelDetails = runCatching { api.getMyChannel(token, baseProfile) }.getOrNull()
                    likesPlaylistId = channelDetails?.likesPlaylistId.orEmpty()
                    uploadsPlaylistId = channelDetails?.uploadsPlaylistId.orEmpty()

                    val uploadsPage = runCatching {
                        api.playlistVideosPage(token, uploadsPlaylistId, maxResults = 25)
                    }.getOrDefault(VideoPage(previous.uploads))
                    val likedPage = runCatching {
                        api.playlistVideosPage(token, likesPlaylistId, maxResults = 50)
                    }.getOrDefault(VideoPage(previous.liked))
                    val likedRaw = likedPage.items
                    val subscriptions = subscriptionsDeferred.await()
                    subscriptionOffset = minOf(INITIAL_SUBSCRIPTION_BATCH, subscriptions.size)
                    val subscriptionWindow = if (subscriptions.isEmpty()) {
                        emptyList()
                    } else if (initialLoad || subscriptions.size <= INITIAL_SUBSCRIPTION_BATCH) {
                        subscriptions.take(INITIAL_SUBSCRIPTION_BATCH).also {
                            subscriptionRefreshCursor = it.size % subscriptions.size
                        }
                    } else {
                        val start = subscriptionRefreshCursor % subscriptions.size
                        List(minOf(INITIAL_SUBSCRIPTION_BATCH, subscriptions.size)) { index ->
                            subscriptions[(start + index) % subscriptions.size]
                        }.also {
                            subscriptionRefreshCursor = (start + it.size) % subscriptions.size
                        }
                    }

                    val subscriptionFeedRaw: List<VideoItem> = subscriptionWindow
                        .map { channel ->
                            async {
                                runCatching { api.channelActivities(token, channel.id, 6) }
                                    .getOrDefault(emptyList())
                            }
                        }
                        .awaitAll()
                        .flatten()
                        .sortedByDescending { it.publishedAt }

                    val popularPage = popularDeferred.await()
                    val livePage = liveDeferred.await()
                    val gamingPage = gamingDeferred.await()
                    val musicPage = musicDeferred.await()
                    shortsQuery = buildShortsQuery(
                        history = previous.history,
                        liked = likedRaw,
                        subscriptions = subscriptions,
                        searchHistory = previous.searchHistory
                    )
                    val (loadedShortsPage, loadedShortsQuery) = runCatching {
                        loadShortsSearchPage(token, shortsQuery, maxResults = 24)
                    }.getOrDefault(shortsDeferred.await() to shortsQuery)
                    val shortsPage = loadedShortsPage
                    shortsQuery = loadedShortsQuery
                    val shortsRaw = shortsPage.items.distinctBy { it.id }.take(50)
                    val notificationsRaw = activitiesDeferred.await()
                    val activityVideosRaw = notificationsRaw.mapNotNull { it.video }
                    // La sección Shorts no debe quedar limitada a suscripciones. La actividad
                    // personal sirve como una señal secundaria; el descubrimiento público manda.
                    val personalShortsRaw = mergeUniqueVideos(
                        previous.history.take(24),
                        likedRaw.take(18),
                        previous.localLikedVideos.take(18),
                        popularPage.items.take(18),
                        activityVideosRaw.take(12),
                        subscriptionFeedRaw.take(12)
                    ).take(80)

                    val allRaw = mergeUniqueVideos(
                        subscriptionFeedRaw,
                        activityVideosRaw,
                        uploadsPage.items,
                        likedRaw,
                        previous.history.take(35),
                        popularPage.items,
                        livePage.items,
                        gamingPage.items,
                        musicPage.items,
                        personalShortsRaw,
                        shortsRaw
                    )
                    val enrichedById = enrichVideosWithCache(token, allRaw)
                        .associateBy { it.id }
                    fun enriched(items: List<VideoItem>): List<VideoItem> =
                        items.map { enrichedById[it.id] ?: it }

                    val popular = enriched(popularPage.items).filterNot(::looksLikeShort)
                    val live = enriched(livePage.items)
                    val gaming = enriched(gamingPage.items).filterNot(::looksLikeShort)
                    val music = enriched(musicPage.items).filterNot(::looksLikeShort)
                    val personalShortCandidates = enriched(personalShortsRaw)
                        .filter(::looksLikeShort)
                    val personalizedShorts = verifyRealShorts(personalShortCandidates)
                    val discoveredShortCandidates = enriched(shortsRaw)
                    val discoveredShorts = verifyRealShorts(discoveredShortCandidates)
                    val shorts = prioritizeShortsForUser(
                        buildDiverseShortFeed(
                            discovered = discoveredShorts,
                            personalized = personalizedShorts,
                            fallback = discoveredShortCandidates.filter(::looksLikeStrongShort) +
                                personalShortCandidates.filter(::looksLikeStrongShort),
                            limit = 48
                        ),
                        previous,
                        40
                    )
                    val liked = enriched(likedRaw)
                    val subscriptionFeed = enriched(subscriptionFeedRaw)
                    val normalSubscriptionFeed = subscriptionFeed.filterNot(::looksLikeShort)
                    val uploads = enriched(uploadsPage.items)
                    val activityVideos = enriched(activityVideosRaw)
                    val normalActivityVideos = activityVideos.filterNot(::looksLikeShort)
                    val notifications = notificationsRaw.map { item ->
                        item.copy(video = item.video?.let { enrichedById[it.id] ?: it })
                    }

                    val personalizedBase = mergeUniqueVideos(
                        normalSubscriptionFeed,
                        normalActivityVideos,
                        if (initialLoad) emptyList() else previous.personalized,
                        liked.take(12),
                        previous.history.take(10),
                        popular
                    ).take(MAX_HOME_ITEMS)
                    val previousPersonalizedIds = previous.personalized.asSequence()
                        .map { it.id }
                        .filter { it.isNotBlank() }
                        .toHashSet()
                    val newPersonalized = personalizedBase.filter { it.id !in previousPersonalizedIds }
                    val personalized = if (initialLoad) {
                        personalizedBase
                    } else {
                        mergeUniqueVideos(newPersonalized, personalizedBase, previous.personalized)
                            .take(MAX_HOME_ITEMS)
                    }

                    popularNextToken = popularPage.nextPageToken
                    liveNextToken = livePage.nextPageToken
                    gamingNextToken = gamingPage.nextPageToken
                    musicNextToken = musicPage.nextPageToken
                    shortsNextToken = shortsPage.nextPageToken
                    uploadsNextToken = uploadsPage.nextPageToken
                    likedNextToken = likedPage.nextPageToken

                    val previousRemoteIds = mergeUniqueVideos(
                        previous.personalized,
                        previous.live,
                        previous.gaming,
                        previous.music,
                        previous.shorts
                    ).asSequence().map { it.id }.filter { it.isNotBlank() }.toHashSet()
                    val newContentCount = mergeUniqueVideos(
                        personalized,
                        live,
                        gaming,
                        music,
                        shorts
                    ).count { it.id !in previousRemoteIds }

                    val profile = channelDetails?.profile ?: baseProfile
                    val playlists = playlistsDeferred.await()
                    val syncTime = System.currentTimeMillis()

                    withContext(Dispatchers.IO) {
                        repository.saveRemoteSnapshot(
                            profile = profile,
                            personalized = personalized,
                            popular = popular,
                            live = live,
                            gaming = gaming,
                            music = music,
                            shorts = shorts,
                            liked = liked,
                            uploads = uploads,
                            subscriptions = subscriptions,
                            playlists = playlists,
                            notifications = notifications,
                            syncTimeMs = syncTime
                        )
                    }

                    _uiState.update {
                        it.copy(
                            authStatus = AuthStatus.CONNECTED,
                            loading = false,
                            refreshing = false,
                            loadingMoreCategory = null,
                            shortsLoadingMore = false,
                            uploadsLoadingMore = false,
                            canLoadMoreForYou = subscriptionOffset < subscriptions.size || popularNextToken.isNotBlank(),
                            canLoadMoreLive = liveNextToken.isNotBlank(),
                            canLoadMoreGaming = gamingNextToken.isNotBlank(),
                            canLoadMoreMusic = musicNextToken.isNotBlank(),
                            shortsCanLoadMore = shortsNextToken.isNotBlank(),
                            uploadsCanLoadMore = uploadsNextToken.isNotBlank(),
                            likedLoadingMore = false,
                            likedCanLoadMore = likedNextToken.isNotBlank(),
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
                            uploads = uploads,
                            notifications = notifications,
                            lastSyncMs = syncTime,
                            authError = "",
                            message = if (!initialLoad) {
                                if (newContentCount > 0) {
                                    "$newContentCount videos nuevos encontrados."
                                } else {
                                    "No se encontraron videos nuevos; se mantuvo el contenido anterior."
                                }
                            } else null
                        )
                    }
                }
            } catch (error: GoogleAccountMismatchException) {
                disconnect()
                _uiState.update {
                    it.copy(
                        authStatus = AuthStatus.ERROR,
                        authError = error.message ?: "La cuenta de Google no coincide con la sesión guardada.",
                        loading = false,
                        refreshing = false,
                        message = null
                    )
                }
            } catch (error: YouTubeApiException) {
                handleApiError(error)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadingMoreCategory = null,
                        shortsLoadingMore = false,
                        uploadsLoadingMore = false,
                        likedLoadingMore = false,
                        authStatus = if (it.profile != null) AuthStatus.CONNECTED else AuthStatus.ERROR,
                        authError = if (it.profile == null) error.message ?: "No se pudo cargar el servicio de video." else "",
                        message = if (it.profile != null) {
                            "No se pudo actualizar. Se conservaron los datos anteriores."
                        } else {
                            error.message ?: "No se pudo cargar el servicio de video."
                        }
                    )
                }
            }
        }
    }

    fun loadMoreHome(category: HomeCategory) {
        val token = accessToken ?: return
        val state = _uiState.value
        if (state.loadingMoreCategory != null || state.refreshing || !state.canLoadMore(category)) return
        _uiState.update { it.copy(loadingMoreCategory = category) }

        viewModelScope.launch {
            try {
                when (category) {
                    HomeCategory.FOR_YOU -> loadMorePersonalized(token)
                    HomeCategory.LIVE -> loadMorePagedCategory(
                        category = category,
                        current = _uiState.value.live,
                        loader = { api.liveVideosPage(token, liveNextToken) },
                        tokenSetter = { liveNextToken = it },
                        updater = { stateNow, items -> stateNow.copy(live = items) }
                    )
                    HomeCategory.GAMING -> loadMorePagedCategory(
                        category = category,
                        current = _uiState.value.gaming,
                        loader = { api.mostPopularPage(token, "20", gamingNextToken) },
                        tokenSetter = { gamingNextToken = it },
                        updater = { stateNow, items -> stateNow.copy(gaming = items) }
                    )
                    HomeCategory.MUSIC -> loadMorePagedCategory(
                        category = category,
                        current = _uiState.value.music,
                        loader = { api.musicVideosPage(token, musicNextToken) },
                        tokenSetter = { musicNextToken = it },
                        updater = { stateNow, items -> stateNow.copy(music = items) }
                    )
                }
                persistCurrentSnapshot()
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        loadingMoreCategory = null,
                        message = "No se pudieron cargar más videos. Desliza nuevamente para reintentar."
                    )
                }
            }
        }
    }

    fun refreshShorts() {
        reloadShortsForSection(force = true)
    }

    private fun reloadShortsForSection(force: Boolean = false) {
        val token = accessToken ?: return
        val state = _uiState.value
        if (!force && (state.shortsLoadingMore || state.refreshing)) return
        shortsQuery = buildShortsQuery(
            history = state.history,
            liked = mergeUniqueVideos(state.liked, state.localLikedVideos),
            subscriptions = state.subscriptions,
            searchHistory = state.searchHistory
        )
        _uiState.update { it.copy(shortsLoadingMore = true, shortsCanLoadMore = true) }
        viewModelScope.launch {
            try {
                val personalizedDeferred = async {
                    loadPersonalizedShorts(token, state)
                }
                val searchDeferred = async {
                    loadShortsSearchPage(
                        token = token,
                        preferredQuery = shortsQuery,
                        maxResults = 30
                    )
                }
                val personalized = personalizedDeferred.await()
                val (page, usedQuery) = searchDeferred.await()
                shortsQuery = usedQuery
                shortsNextToken = page.nextPageToken
                val candidates = enrichVideosWithCache(token, page.items)
                val verified = verifyRealShorts(candidates)
                val discovered = verified.ifEmpty {
                    candidates.filter(::looksLikeStrongShort).take(24)
                }
                val usable = prioritizeShortsForUser(
                    buildDiverseShortFeed(
                        discovered = discovered,
                        personalized = personalized,
                        fallback = candidates.filter(::looksLikeStrongShort),
                        limit = MAX_HOME_ITEMS
                    ),
                    state,
                    MAX_HOME_ITEMS
                )
                _uiState.update { current ->
                    val merged = if (usable.isEmpty()) current.shorts else usable
                    current.copy(
                        shorts = merged,
                        shortsLoadingMore = false,
                        shortsCanLoadMore = page.nextPageToken.isNotBlank(),
                        message = if (merged.isEmpty()) {
                            "No se pudieron cargar Shorts. Pulsa Reintentar."
                        } else {
                            current.message
                        }
                    )
                }
                persistCurrentSnapshot()
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        shortsLoadingMore = false,
                        message = if (it.shorts.isEmpty()) {
                            "No se pudieron cargar Shorts. Pulsa Reintentar."
                        } else {
                            it.message
                        }
                    )
                }
            }
        }
    }

    fun loadMoreShorts() {
        val token = accessToken ?: return
        val state = _uiState.value
        if (state.shortsLoadingMore || state.refreshing || !state.shortsCanLoadMore || shortsNextToken.isBlank()) return
        _uiState.update { it.copy(shortsLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = api.searchVideosPage(
                    token = token,
                    query = shortsQuery,
                    shortOnly = true,
                    pageToken = shortsNextToken,
                    maxResults = 18
                )
                shortsNextToken = page.nextPageToken
                val candidates = enrichVideosWithCache(token, page.items)
                val verified = verifyRealShorts(candidates)
                val enriched = verified.ifEmpty {
                    candidates.filter(::looksLikeStrongShort).take(18)
                }
                _uiState.update {
                    it.copy(
                        shorts = prioritizeShortsForUser(
                            buildDiverseShortFeed(
                                discovered = mergeUniqueVideos(it.shorts, enriched),
                                personalized = emptyList(),
                                fallback = enriched,
                                limit = MAX_HOME_ITEMS
                            ),
                            it,
                            MAX_HOME_ITEMS
                        ),
                        shortsLoadingMore = false,
                        shortsCanLoadMore = page.nextPageToken.isNotBlank()
                    )
                }
                persistCurrentSnapshot()
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        shortsLoadingMore = false,
                        message = "No se pudieron cargar más Shorts."
                    )
                }
            }
        }
    }

    fun loadMoreUploads() {
        val token = accessToken ?: return
        val state = _uiState.value
        if (state.uploadsLoadingMore || state.refreshing || !state.uploadsCanLoadMore || uploadsNextToken.isBlank()) return
        _uiState.update { it.copy(uploadsLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = api.playlistVideosPage(
                    token = token,
                    playlistId = uploadsPlaylistId,
                    pageToken = uploadsNextToken,
                    maxResults = 25
                )
                uploadsNextToken = page.nextPageToken
                val enriched = enrichVideosWithCache(token, page.items)
                _uiState.update {
                    it.copy(
                        uploads = mergeUniqueVideos(it.uploads, enriched).take(MAX_HOME_ITEMS),
                        uploadsLoadingMore = false,
                        uploadsCanLoadMore = page.nextPageToken.isNotBlank()
                    )
                }
                persistCurrentSnapshot()
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        uploadsLoadingMore = false,
                        message = "No se pudieron cargar más videos de tu canal."
                    )
                }
            }
        }
    }

    fun loadMoreLiked() {
        val state = _uiState.value
        val token = accessToken ?: return
        if (state.likedLoadingMore || !state.likedCanLoadMore || likedNextToken.isBlank()) return
        val requestedToken = likedNextToken
        _uiState.update { it.copy(likedLoadingMore = true) }
        viewModelScope.launch {
            val page = runCatching {
                api.playlistVideosPage(
                    token = token,
                    playlistId = likesPlaylistId,
                    pageToken = requestedToken,
                    maxResults = 50
                )
            }.getOrNull()
            if (page == null) {
                _uiState.update { it.copy(likedLoadingMore = false, likedCanLoadMore = false) }
                return@launch
            }
            likedNextToken = page.nextPageToken
            val enriched = enrichVideosWithCache(token, page.items)
            _uiState.update { current ->
                current.copy(
                    liked = (current.liked + enriched).distinctBy { it.id },
                    likedLoadingMore = false,
                    likedCanLoadMore = likedNextToken.isNotBlank()
                )
            }
            persistCurrentSnapshot()
        }
    }

    private suspend fun loadMorePersonalized(token: String) {
        val current = _uiState.value
        val subscriptions = current.subscriptions
        val nextChannels = subscriptions.drop(subscriptionOffset).take(SUBSCRIPTION_PAGE_SIZE)
        subscriptionOffset += nextChannels.size

        val subscriptionMore: List<VideoItem> = if (nextChannels.isEmpty()) {
            emptyList()
        } else {
            supervisorScope {
                nextChannels.map { channel ->
                    async {
                        runCatching { api.channelActivities(token, channel.id, 6) }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
            }
        }

        val popularMorePage = if (subscriptionMore.size < 8 && popularNextToken.isNotBlank()) {
            runCatching { api.mostPopularPage(token, pageToken = popularNextToken) }
                .getOrDefault(VideoPage(emptyList(), popularNextToken))
        } else {
            VideoPage(emptyList(), popularNextToken)
        }
        popularNextToken = popularMorePage.nextPageToken

        val raw = mergeUniqueVideos(subscriptionMore, popularMorePage.items)
        val enriched = enrichVideosWithCache(token, raw)
        val appended = mergeUniqueVideos(current.personalized, enriched).take(MAX_HOME_ITEMS)
        val canContinue = subscriptionOffset < subscriptions.size || popularNextToken.isNotBlank()
        val popularPageIds = popularMorePage.items.asSequence().map { it.id }.toHashSet()

        _uiState.update {
            it.copy(
                personalized = appended,
                popular = mergeUniqueVideos(
                    it.popular,
                    enriched.filter { video -> video.id in popularPageIds }
                ).take(MAX_HOME_ITEMS),
                loadingMoreCategory = null,
                canLoadMoreForYou = canContinue
            )
        }
    }

    private suspend fun loadMorePagedCategory(
        category: HomeCategory,
        current: List<VideoItem>,
        loader: suspend () -> VideoPage,
        tokenSetter: (String) -> Unit,
        updater: (GeoVideosUiState, List<VideoItem>) -> GeoVideosUiState
    ) {
        val token = accessToken ?: return
        val page = loader()
        tokenSetter(page.nextPageToken)
        val enriched = enrichVideosWithCache(token, page.items)
        val appended = mergeUniqueVideos(current, enriched).take(MAX_HOME_ITEMS)
        _uiState.update { state ->
            val updated = updater(state, appended).copy(loadingMoreCategory = null)
            when (category) {
                HomeCategory.FOR_YOU -> updated.copy(canLoadMoreForYou = page.nextPageToken.isNotBlank())
                HomeCategory.LIVE -> updated.copy(canLoadMoreLive = page.nextPageToken.isNotBlank())
                HomeCategory.GAMING -> updated.copy(canLoadMoreGaming = page.nextPageToken.isNotBlank())
                HomeCategory.MUSIC -> updated.copy(canLoadMoreMusic = page.nextPageToken.isNotBlank())
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

    private fun resetPagination(keepSearch: Boolean = false) {
        popularNextToken = ""
        liveNextToken = ""
        gamingNextToken = ""
        musicNextToken = ""
        shortsNextToken = ""
        uploadsNextToken = ""
        likedNextToken = ""
        subscriptionOffset = 0
        if (!keepSearch) {
            searchNextToken = ""
            lastSearchQuery = ""
        }
    }

    private suspend fun enrichVideosWithCache(
        token: String,
        videos: List<VideoItem>
    ): List<VideoItem> {
        if (videos.isEmpty()) return videos
        val withDurations = runCatching { api.enrichVideoDurations(token, videos) }
            .getOrDefault(videos)
        val cached = withContext(Dispatchers.IO) { repository.loadChannelAvatars() }
        val embedded = withDurations.asSequence()
            .filter { it.channelId.isNotBlank() && it.channelThumbnailUrl.isNotBlank() }
            .associate { it.channelId to it.channelThumbnailUrl }
        val known = cached + embedded
        val withKnownAvatars = withDurations.map { video ->
            val avatar = known[video.channelId].orEmpty()
            if (video.channelThumbnailUrl.isNotBlank() || avatar.isBlank()) video
            else video.copy(channelThumbnailUrl = avatar)
        }
        val unresolved = withKnownAvatars.filter {
            it.channelId.isNotBlank() && it.channelThumbnailUrl.isBlank()
        }
        if (unresolved.isEmpty()) {
            if (embedded.isNotEmpty()) withContext(Dispatchers.IO) { repository.saveChannelAvatars(embedded) }
            return withKnownAvatars
        }

        val fetched = runCatching { api.enrichVideos(token, unresolved) }
            .getOrDefault(unresolved)
        val fresh = fetched.asSequence()
            .filter { it.channelId.isNotBlank() && it.channelThumbnailUrl.isNotBlank() }
            .associate { it.channelId to it.channelThumbnailUrl }
        if (embedded.isNotEmpty() || fresh.isNotEmpty()) {
            withContext(Dispatchers.IO) { repository.saveChannelAvatars(embedded + fresh) }
        }
        val allKnown = known + fresh
        return withKnownAvatars.map { video ->
            val avatar = allKnown[video.channelId].orEmpty()
            if (video.channelThumbnailUrl.isNotBlank() || avatar.isBlank()) video
            else video.copy(channelThumbnailUrl = avatar)
        }
    }

    private suspend fun persistCurrentSnapshot() {
        val state = _uiState.value
        val profile = state.profile ?: return
        withContext(Dispatchers.IO) {
            repository.saveRemoteSnapshot(
                profile = profile,
                personalized = state.personalized,
                popular = state.popular,
                live = state.live,
                gaming = state.gaming,
                music = state.music,
                shorts = state.shorts,
                liked = state.liked.take(300),
                uploads = state.uploads,
                subscriptions = state.subscriptions,
                playlists = state.playlists,
                notifications = state.notifications,
                syncTimeMs = state.lastSyncMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        }
    }

    fun selectSection(section: MainSection) {
        val current = _uiState.value
        if (section == MainSection.SHORTS) {
            val firstShort = current.shorts.firstOrNull()
            if (firstShort != null) {
                previewShort(firstShort)
            } else {
                _uiState.update {
                    it.copy(
                        section = MainSection.SHORTS,
                        selectedVideo = null,
                        playerExpanded = false,
                        selectedChannelTitle = "",
                        selectedChannel = null,
                        channelVideos = emptyList(),
                        channelPlaylists = emptyList()
                    )
                }
                reloadShortsForSection()
            }
            return
        }

        val leavingShorts = current.section == MainSection.SHORTS
        _uiState.update {
            it.copy(
                section = section,
                selectedVideo = if (leavingShorts) null else it.selectedVideo,
                playerExpanded = if (leavingShorts) false else it.playerExpanded,
                selectedChannelTitle = "",
                selectedChannel = null,
                channelVideos = emptyList(),
                channelPlaylists = emptyList()
            )
        }
    }

    fun selectHomeCategory(category: HomeCategory) {
        _uiState.update { it.copy(homeCategory = category) }
    }

    fun play(video: VideoItem) {
        if (looksLikeShort(video)) {
            previewShort(video)
            return
        }
        openPlayer(video)
    }

    fun openPlayer(video: VideoItem) {
        val saved = repository.loadHistory().firstOrNull { it.id == video.id }
        val playable = video.copy(
            resumePositionMs = saved?.resumePositionMs ?: video.resumePositionMs,
            durationMs = saved?.durationMs ?: video.durationMs
        )
        val history = repository.addToHistory(playable)
        relatedNextToken = ""
        relatedVideoId = playable.id
        _uiState.update {
            it.copy(
                selectedVideo = playable,
                playerExpanded = true,
                history = history,
                playerDetails = null,
                playerDetailsLoading = true,
                relatedVideos = emptyList(),
                relatedLoading = true,
                relatedLoadingMore = false,
                relatedCanLoadMore = true
            )
        }
        loadPlayerContext(playable)
    }

    fun previewShort(video: VideoItem) {
        val playable = video.copy(resumePositionMs = 0L)
        val history = repository.addToHistory(playable)
        relatedNextToken = ""
        relatedVideoId = ""
        _uiState.update {
            it.copy(
                selectedVideo = playable,
                playerExpanded = false,
                section = MainSection.SHORTS,
                history = history,
                playerDetails = null,
                playerDetailsLoading = false,
                relatedVideos = emptyList(),
                relatedLoading = false,
                relatedLoadingMore = false,
                relatedCanLoadMore = false
            )
        }
    }

    fun openShortDetails(video: VideoItem) {
        _uiState.update { it.copy(section = MainSection.HOME) }
        openPlayer(video)
    }

    fun playNext() {
        val state = _uiState.value
        val current = state.selectedVideo ?: return
        val next = state.relatedVideos.firstOrNull { it.id != current.id }
            ?: localRelated(current).firstOrNull()
        if (next != null) {
            play(next)
        } else {
            _uiState.update { it.copy(message = "No hay un siguiente video disponible.") }
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
        relatedNextToken = ""
        relatedVideoId = ""
        _uiState.update {
            it.copy(
                selectedVideo = null,
                playerExpanded = false,
                playerDetails = null,
                playerDetailsLoading = false,
                relatedVideos = emptyList(),
                relatedLoading = false,
                relatedLoadingMore = false,
                relatedCanLoadMore = true
            )
        }
    }

    private fun loadPlayerContext(video: VideoItem) {
        val token = accessToken
        val fallback = localRelated(video)
        if (token.isNullOrBlank()) {
            _uiState.update {
                if (it.selectedVideo?.id != video.id) it else it.copy(
                    playerDetails = VideoDetails(
                        videoId = video.id,
                        channelThumbnailUrl = video.channelThumbnailUrl,
                        publishedAt = video.publishedAt,
                        description = video.description
                    ),
                    playerDetailsLoading = false,
                    relatedVideos = fallback,
                    relatedLoading = false,
                    relatedCanLoadMore = false
                )
            }
            return
        }

        relatedNextToken = ""
        _uiState.update {
            if (it.selectedVideo?.id != video.id) it else it.copy(
                relatedVideos = fallback.take(6),
                relatedLoading = true,
                relatedCanLoadMore = true
            )
        }
        viewModelScope.launch {
            supervisorScope {
                val detailsDeferred = async { runCatching { api.videoDetails(token, video) }.getOrNull() }
                val relatedDeferred = async {
                    runCatching { api.relatedVideosPage(token, video, pageToken = "") }.getOrNull()
                }
                val details = detailsDeferred.await()
                val relatedPage = relatedDeferred.await()
                if (_uiState.value.selectedVideo?.id != video.id || relatedVideoId != video.id) return@supervisorScope

                relatedNextToken = relatedPage?.nextPageToken.orEmpty()
                val related = filterRelatedCandidates(
                    current = video,
                    candidates = mergeUniqueVideos(
                        relatedPage?.items.orEmpty().filterNot { it.id == video.id },
                        fallback
                    )
                ).take(16)
                _uiState.update {
                    if (it.selectedVideo?.id != video.id) it else it.copy(
                        playerDetails = details ?: VideoDetails(
                            videoId = video.id,
                            channelThumbnailUrl = video.channelThumbnailUrl,
                            publishedAt = video.publishedAt,
                            description = video.description
                        ),
                        playerDetailsLoading = false,
                        relatedVideos = related,
                        relatedLoading = false,
                        relatedCanLoadMore = relatedNextToken.isNotBlank()
                    )
                }
            }
        }
    }

    fun loadMoreRelated() {
        val state = _uiState.value
        val video = state.selectedVideo ?: return
        val token = accessToken ?: return
        if (state.relatedLoading || state.relatedLoadingMore || !state.relatedCanLoadMore) return
        if (relatedVideoId != video.id || relatedNextToken.isBlank()) return
        val requestedToken = relatedNextToken
        _uiState.update { it.copy(relatedLoadingMore = true) }
        viewModelScope.launch {
            val page = runCatching {
                api.relatedVideosPage(
                    token,
                    video,
                    pageToken = requestedToken.takeUnless { it == FIRST_RELATED_PAGE }.orEmpty()
                )
            }.getOrNull()
            if (_uiState.value.selectedVideo?.id != video.id || relatedVideoId != video.id) return@launch
            if (page == null) {
                _uiState.update { it.copy(relatedLoadingMore = false, relatedCanLoadMore = false) }
                return@launch
            }
            relatedNextToken = page.nextPageToken
            _uiState.update {
                it.copy(
                    relatedVideos = filterRelatedCandidates(
                        current = video,
                        candidates = (it.relatedVideos + page.items)
                            .filterNot { candidate -> candidate.id == video.id }
                            .distinctBy { candidate -> candidate.id }
                    ).take(20),
                    relatedLoadingMore = false,
                    relatedCanLoadMore = relatedNextToken.isNotBlank()
                )
            }
        }
    }

    private fun localRelated(video: VideoItem): List<VideoItem> {
        val state = _uiState.value
        val titleWords = video.title.lowercase()
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.length >= 4 }
            .toSet()
        return filterRelatedCandidates(
            current = video,
            candidates = sequenceOf(
                state.personalized,
                state.popular,
                state.music,
                state.gaming,
                state.live,
                state.shorts,
                state.liked,
                state.history
            ).flatten()
                .filterNot { it.id == video.id }
                .distinctBy { it.id }
                .toList()
        ).sortedByDescending { candidate ->
                var score = 0
                if (candidate.channelId.isNotBlank() && candidate.channelId == video.channelId) score += 6
                if (candidate.channelTitle.equals(video.channelTitle, ignoreCase = true)) score += 4
                val candidateWords = candidate.title.lowercase()
                    .split(Regex("""[^\p{L}\p{N}]+"""))
                    .toSet()
                score + titleWords.count { it in candidateWords }
            }
            .take(16)
            .toList()
    }


    private enum class ScriptGroup { LATIN, CJK, CYRILLIC, OTHER }

    private fun filterRelatedCandidates(
        current: VideoItem,
        candidates: List<VideoItem>
    ): List<VideoItem> {
        val currentIsShort = looksLikeShort(current)
        val currentScript = dominantScript(current.title + " " + current.channelTitle)
        return candidates.asSequence()
            .filterNot { it.id == current.id }
            .filter { candidate -> currentIsShort || !looksLikeShort(candidate) }
            .filter { candidate ->
                if (candidate.channelId.isNotBlank() && candidate.channelId == current.channelId) {
                    true
                } else {
                    val candidateScript = dominantScript(candidate.title + " " + candidate.channelTitle)
                    currentScript == ScriptGroup.OTHER ||
                        candidateScript == ScriptGroup.OTHER ||
                        candidateScript == currentScript
                }
            }
            .distinctBy { it.id }
            .toList()
    }

    private fun dominantScript(text: String): ScriptGroup {
        var latin = 0
        var cjk = 0
        var cyrillic = 0
        text.forEach { char ->
            when {
                char in '\u0041'..'\u024F' -> latin++
                char in '\u3040'..'\u30FF' || char in '\u3400'..'\u9FFF' || char in '\uAC00'..'\uD7AF' -> cjk++
                char in '\u0400'..'\u052F' -> cyrillic++
            }
        }
        val max = maxOf(latin, cjk, cyrillic)
        return when {
            max == 0 -> ScriptGroup.OTHER
            max == latin -> ScriptGroup.LATIN
            max == cjk -> ScriptGroup.CJK
            else -> ScriptGroup.CYRILLIC
        }
    }

    fun toggleLocalLike(video: VideoItem) {
        val (likes, dislikes, localVideos) = repository.toggleLocalLike(video)
        val likedNow = video.id in likes
        _uiState.update {
            it.copy(
                localLikedIds = likes,
                localDislikedIds = dislikes,
                localLikedVideos = localVideos,
                message = if (likedNow) "Me gusta guardado en Geo Videos." else "Me gusta eliminado."
            )
        }
        syncVideoRating(video, if (likedNow) "like" else "none")
    }

    fun toggleLocalDislike(video: VideoItem) {
        val (likes, dislikes, localVideos) = repository.toggleLocalDislike(video.id)
        val dislikedNow = video.id in dislikes
        _uiState.update {
            it.copy(
                localLikedIds = likes,
                localDislikedIds = dislikes,
                localLikedVideos = localVideos,
                message = if (dislikedNow) "Marcado como no me gusta en Geo Videos." else "Reacción eliminada."
            )
        }
        syncVideoRating(video, if (dislikedNow) "dislike" else "none")
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
        syncGeoWatchLater(video, added)
    }

    private fun syncVideoRating(video: VideoItem, rating: String) {
        if (!_uiState.value.youtubeSyncEnabled) return
        val token = youtubeWriteToken
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(message = "El cambio quedó en Geo Videos. Renueva la sincronización para enviarlo a YouTube.") }
            return
        }
        viewModelScope.launch {
            runCatching { api.rateVideo(token, video.id, rating) }
                .onSuccess {
                    _uiState.update { current ->
                        val remoteLiked = when (rating) {
                            "like" -> mergeUniqueVideos(listOf(video), current.liked)
                            else -> current.liked.filterNot { it.id == video.id }
                        }
                        repository.saveLikedVideos(remoteLiked)
                        current.copy(liked = remoteLiked, message = "Sincronizado con YouTube.")
                    }
                }
                .onFailure(::handleYouTubeWriteFailure)
        }
    }

    private fun syncGeoWatchLater(video: VideoItem, added: Boolean) {
        if (!_uiState.value.youtubeSyncEnabled) return
        val token = youtubeWriteToken
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(message = "Ver después quedó guardado en Geo Videos. Renueva la sincronización para enviarlo a YouTube.") }
            return
        }
        viewModelScope.launch {
            runCatching {
                var playlistId = repository.loadGeoWatchLaterPlaylistId()
                if (playlistId.isBlank()) {
                    playlistId = api.findPlaylistByTitle(token, GEO_WATCH_LATER_TITLE)
                        ?: api.createPrivatePlaylist(
                            token,
                            GEO_WATCH_LATER_TITLE,
                            "Lista privada sincronizada desde Geo Videos"
                        )
                    if (playlistId.isNotBlank()) repository.saveGeoWatchLaterPlaylistId(playlistId)
                }
                if (playlistId.isBlank()) error("No se pudo preparar la lista de YouTube.")
                if (added) {
                    val existing = api.findPlaylistItemId(token, playlistId, video.id)
                    if (existing == null) api.addVideoToPlaylist(token, playlistId, video.id)
                } else {
                    api.findPlaylistItemId(token, playlistId, video.id)?.let {
                        api.removePlaylistItem(token, it)
                    }
                }
            }.onSuccess {
                _uiState.update { it.copy(message = "Ver después sincronizado con YouTube.") }
            }.onFailure(::handleYouTubeWriteFailure)
        }
    }

    fun toggleSubscription(channel: ChannelItem) {
        if (!_uiState.value.youtubeSyncEnabled || youtubeWriteToken.isNullOrBlank()) {
            pendingSubscriptionToggle = channel
            _uiState.update { it.copy(message = "Autoriza YouTube para sincronizar esta suscripción.") }
            return
        }
        val token = youtubeWriteToken ?: return
        val currentlySubscribed = _uiState.value.subscriptions.any { it.id == channel.id }
        viewModelScope.launch {
            runCatching {
                if (currentlySubscribed) {
                    api.findSubscriptionId(token, channel.id)?.let { api.unsubscribe(token, it) }
                } else {
                    api.subscribe(token, channel.id)
                }
            }.onSuccess {
                val updated = if (currentlySubscribed) {
                    _uiState.value.subscriptions.filterNot { it.id == channel.id }
                } else {
                    listOf(channel.copy(isSubscribed = true)) + _uiState.value.subscriptions.filterNot { it.id == channel.id }
                }
                repository.saveSubscriptions(updated)
                _uiState.update { state ->
                    state.copy(
                        subscriptions = updated,
                        selectedChannel = state.selectedChannel?.takeIf { it.id == channel.id }?.copy(isSubscribed = !currentlySubscribed)
                            ?: state.selectedChannel,
                        message = if (currentlySubscribed) "Suscripción eliminada en YouTube." else "Suscripción guardada en YouTube."
                    )
                }
            }.onFailure(::handleYouTubeWriteFailure)
        }
    }

    private fun handleYouTubeWriteFailure(error: Throwable) {
        if (error is YouTubeApiException && error.statusCode == 401) {
            youtubeWriteToken = null
            _uiState.update { it.copy(youtubeSyncAuthorized = false, message = "El permiso de sincronización caducó. Se solicitará de nuevo al usar una acción de YouTube.") }
        } else {
            _uiState.update { it.copy(message = error.message ?: "No se pudo sincronizar con YouTube.") }
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
        lastSearchQuery = clean
        searchNextToken = ""
        _uiState.update { it.copy(loading = true, searchHistory = history, searchResults = emptyList()) }
        viewModelScope.launch {
            try {
                val page = api.searchVideosPage(token, clean)
                searchNextToken = page.nextPageToken
                val results = enrichVideosWithCache(token, page.items)
                _uiState.update {
                    it.copy(
                        loading = false,
                        searchResults = results,
                        searchLoadingMore = false,
                        section = MainSection.SEARCH
                    )
                }
            } catch (error: YouTubeApiException) {
                handleApiError(error)
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, message = error.message ?: "Error al buscar.") }
            }
        }
    }

    fun loadMoreSearch() {
        val token = accessToken ?: return
        val state = _uiState.value
        if (state.searchLoadingMore || lastSearchQuery.isBlank() || searchNextToken.isBlank()) return
        _uiState.update { it.copy(searchLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = api.searchVideosPage(token, lastSearchQuery, pageToken = searchNextToken)
                searchNextToken = page.nextPageToken
                val enriched = enrichVideosWithCache(token, page.items)
                _uiState.update {
                    it.copy(
                        searchResults = mergeUniqueVideos(it.searchResults, enriched),
                        searchLoadingMore = false
                    )
                }
            } catch (error: Exception) {
                _uiState.update { it.copy(searchLoadingMore = false, message = "No se cargaron más resultados.") }
            }
        }
    }

    fun openChannel(channel: ChannelItem) {
        val token = accessToken
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(message = "Renueva el acceso de Google para abrir el canal.") }
            return
        }
        val subscribed = _uiState.value.subscriptions.any { it.id == channel.id }
        val initialChannel = channel.copy(isSubscribed = subscribed)
        _uiState.update {
            it.copy(
                loading = true,
                selectedChannelTitle = channel.title,
                selectedChannel = initialChannel,
                channelVideos = emptyList(),
                channelPlaylists = emptyList()
            )
        }
        viewModelScope.launch {
            try {
                supervisorScope {
                    val infoDeferred = async { runCatching { api.channelInfo(token, channel.id) }.getOrNull() }
                    val videosDeferred = async { runCatching { api.channelVideos(token, channel.id) }.getOrDefault(emptyList()) }
                    val playlistsDeferred = async { runCatching { api.channelPlaylists(token, channel.id) }.getOrDefault(emptyList()) }
                    val info = infoDeferred.await()?.copy(isSubscribed = subscribed) ?: initialChannel
                    val rawVideos = videosDeferred.await()
                    val videos = enrichVideosWithCache(token, rawVideos)
                    val playlists = playlistsDeferred.await()
                    _uiState.update { current ->
                        if (current.selectedChannel?.id != channel.id) current else current.copy(
                            loading = false,
                            selectedChannelTitle = info.title,
                            selectedChannel = info,
                            channelVideos = videos,
                            channelPlaylists = playlists
                        )
                    }
                }
            } catch (error: Exception) {
                _uiState.update { current ->
                    if (current.selectedChannel?.id != channel.id) current else current.copy(
                        loading = false,
                        message = error.message ?: "No se pudo abrir el canal."
                    )
                }
            }
        }
    }

    fun closeChannel() {
        _uiState.update {
            it.copy(
                selectedChannelTitle = "",
                selectedChannel = null,
                channelVideos = emptyList(),
                channelPlaylists = emptyList()
            )
        }
    }

    fun removeHistory(videoId: String) {
        val history = repository.removeFromHistory(videoId)
        _uiState.update { it.copy(history = history, message = "Video eliminado del historial.") }
    }

    fun onPlayerTransition(videoId: String) {
        if (videoId.isBlank() || _uiState.value.selectedVideo?.id == videoId) return
        val state = _uiState.value
        val video = sequenceOf(
            state.relatedVideos, state.personalized, state.popular, state.music, state.gaming,
            state.live, state.history, state.watchLater, state.liked, state.localLikedVideos
        ).flatten().firstOrNull { it.id == videoId } ?: return
        val playable = video.copy(resumePositionMs = 0L)
        val history = repository.addToHistory(playable)
        relatedNextToken = ""
        relatedVideoId = playable.id
        _uiState.update { current ->
            current.copy(
                selectedVideo = playable,
                playerExpanded = current.playerExpanded,
                history = history,
                playerDetails = null,
                playerDetailsLoading = true,
                relatedVideos = emptyList(),
                relatedLoading = true,
                relatedLoadingMore = false,
                relatedCanLoadMore = true
            )
        }
        loadPlayerContext(playable)
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

    private fun buildShortsQuery(
        history: List<VideoItem>,
        liked: List<VideoItem>,
        subscriptions: List<ChannelItem>,
        searchHistory: List<String>
    ): String {
        val stopWords = setOf(
            "para", "with", "from", "this", "that", "video", "official", "shorts",
            "the", "and", "los", "las", "una", "uno", "del", "por", "como", "music",
            "hace", "nuevo", "nueva", "parte", "capitulo", "episodio", "espanol", "latino",
            "2023", "2024", "2025", "2026"
        )
        val sources = buildList {
            addAll(searchHistory.take(10))
            addAll(history.take(24).map { it.title })
            addAll(liked.take(18).map { it.title })
            addAll(subscriptions.take(8).map { it.title })
        }
        val allText = sources.joinToString(" ").lowercase()
        val categoryTerms = listOf(
            "anime", "naruto", "manhwa", "manga", "donghua", "rap", "musica",
            "free fire", "minecraft", "gaming", "videojuegos", "futbol", "humor",
            "curiosidades", "tecnologia", "noticias"
        ).filter { it in allText }

        val frequentWords = sources
            .asSequence()
            .flatMap { value ->
                value.lowercase()
                    .split(Regex("""[^\p{L}\p{N}]+"""))
                    .asSequence()
            }
            .filter { it.length >= 4 && it !in stopWords && it.any(Char::isLetter) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .filterNot { word -> categoryTerms.any { category -> word in category || category in word } }
            .take(5)
            .toList()

        val interests = (categoryTerms + frequentWords)
            .distinct()
            .take(3)
        return if (interests.isEmpty()) {
            "shorts virales humor gaming musica anime deportes animales curiosidades tecnologia español"
        } else {
            "shorts ${interests.joinToString(" ")} virales humor tendencias español"
        }
    }

    private suspend fun loadPersonalizedShorts(
        token: String,
        state: GeoVideosUiState
    ): List<VideoItem> = supervisorScope {
        val subscriptionVideos = state.subscriptions
            .take(5)
            .map { channel ->
                async {
                    runCatching { api.channelActivities(token, channel.id, 8) }
                        .getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
        val raw = roundRobinVideos(
            state.history.take(35),
            state.localLikedVideos.take(20),
            state.liked.take(20),
            state.personalized.take(35),
            subscriptionVideos.take(20),
            state.popular.take(20)
        ).take(100)
        val enriched = enrichVideosWithCache(token, raw)
            .filter(::looksLikeShort)
            .filter(::isAcceptableShort)
        verifyRealShorts(enriched).take(30)
    }

    private suspend fun verifyRealShorts(videos: List<VideoItem>): List<VideoItem> = supervisorScope {
        val unique = videos.filter(::isAcceptableShort).distinctBy { it.id }.take(30)
        val verified = ArrayList<VideoItem>(unique.size)
        // Small batches avoid opening too many extractor connections at the same time.
        unique.chunked(4).forEach { batch ->
            val results = batch.map { video ->
                async { video to runCatching { StreamResolver.isVerifiedShort(video) }.getOrDefault(false) }
            }.awaitAll()
            results.filter { it.second || looksLikeStrongShort(it.first) }
                .mapTo(verified) { it.first }
        }
        verified
    }

    private fun looksLikeShort(video: VideoItem): Boolean {
        val text = (video.title + " " + video.description).lowercase()
        val taggedAsShort = listOf(
            "#shorts", " shorts", "short ", "tiktok", "reel", "vertical", "status video"
        ).any { it in text }
        return taggedAsShort || video.durationMs in 1..180_000L
    }

    private fun looksLikeStrongShort(video: VideoItem): Boolean {
        val text = (video.title + " " + video.description + " " + video.source).lowercase()
        val tagged = listOf(
            "/shorts/", "#shorts", "#short ", " youtube shorts", "tiktok", "reel", "vertical"
        ).any { it in text }
        return tagged || video.durationMs in 1L..60_000L
    }

    private suspend fun loadShortsSearchPage(
        token: String,
        preferredQuery: String,
        maxResults: Int
    ): Pair<VideoPage, String> {
        val normalizedPreferred = preferredQuery.trim().ifBlank {
            "shorts virales humor gaming musica anime deportes animales curiosidades tecnologia español"
        }
        val queries = listOf(
            "shorts virales humor animales curiosidades español",
            "shorts deportes futbol comida viajes tecnologia español",
            "shorts gaming musica baile retos español",
            normalizedPreferred
        )
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)

        val collected = ArrayList<VideoItem>(maxResults)
        var primaryQuery = queries.first()
        var primaryToken = ""
        val perQuery = (maxResults / queries.size.coerceAtLeast(1)).coerceIn(12, 25)
        queries.forEachIndexed { index, query ->
            val page = runCatching {
                api.searchVideosPage(
                    token = token,
                    query = query,
                    shortOnly = true,
                    maxResults = perQuery
                )
            }.getOrNull() ?: return@forEachIndexed
            if (index == 0) {
                primaryQuery = query
                primaryToken = page.nextPageToken
            }
            collected += page.items.filter(::isAcceptableShort)
        }
        return VideoPage(
            items = limitShortsPerChannel(collected.distinctBy { it.id }, maxResults),
            nextPageToken = primaryToken
        ) to primaryQuery
    }

    private fun buildDiverseShortFeed(
        discovered: List<VideoItem>,
        personalized: List<VideoItem>,
        fallback: List<VideoItem>,
        limit: Int
    ): List<VideoItem> {
        // DailyTube-style discovery: rotate themes before using personal signals,
        // so one interest or subscription cannot fill the whole shelf.
        val allDiscovered = mergeUniqueVideos(discovered, fallback)
        val buckets = allDiscovered.groupBy(::shortDiscoveryCategory)
        val themed = roundRobinVideos(
            buckets["humor"].orEmpty(),
            buckets["animals"].orEmpty(),
            buckets["sports"].orEmpty(),
            buckets["gaming"].orEmpty(),
            buckets["music"].orEmpty(),
            buckets["tech"].orEmpty(),
            buckets["anime"].orEmpty(),
            buckets["other"].orEmpty()
        )
        val weighted = roundRobinVideos(
            themed,
            allDiscovered.drop(1),
            personalized.take((limit / 4).coerceAtLeast(4)),
            allDiscovered.drop(2)
        )
        return limitShortsPerChannel(weighted, limit)
    }

    private fun shortDiscoveryCategory(video: VideoItem): String {
        val text = (video.title + " " + video.description + " " + video.channelTitle).lowercase()
        return when {
            listOf("humor", "risa", "gracioso", "comedia", "meme", "broma").any { it in text } -> "humor"
            listOf("animal", "perro", "gato", "mascota", "wildlife").any { it in text } -> "animals"
            listOf("futbol", "fútbol", "deporte", "gol", "basket", "fitness").any { it in text } -> "sports"
            listOf("gaming", "juego", "free fire", "minecraft", "roblox", "gameplay").any { it in text } -> "gaming"
            listOf("musica", "música", "song", "rap", "baile", "dance", "lyrics").any { it in text } -> "music"
            listOf("tecnologia", "tecnología", "celular", "android", "ciencia", "curiosidad").any { it in text } -> "tech"
            listOf("anime", "manga", "manhwa", "naruto", "donghua").any { it in text } -> "anime"
            else -> "other"
        }
    }

    private fun prioritizeShortsForUser(
        videos: List<VideoItem>,
        state: GeoVideosUiState,
        limit: Int
    ): List<VideoItem> {
        if (videos.isEmpty()) return videos
        val stopWords = setOf(
            "video", "shorts", "short", "para", "como", "este", "esta", "with", "from",
            "that", "your", "the", "and", "official", "2026", "2025"
        )
        val interestTerms = sequenceOf(
            state.searchHistory.asSequence(),
            state.history.asSequence().take(30).map { it.title + " " + it.channelTitle },
            state.localLikedVideos.asSequence().take(20).map { it.title + " " + it.channelTitle },
            state.liked.asSequence().take(20).map { it.title + " " + it.channelTitle },
            state.subscriptions.asSequence().take(20).map { it.title }
        ).flatten()
            .flatMap { text -> text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).asSequence() }
            .filter { it.length >= 4 && it !in stopWords }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(18)
            .toSet()

        fun score(video: VideoItem): Int {
            val text = (video.title + " " + video.description + " " + video.channelTitle).lowercase()
            var score = 0
            val interestHits = interestTerms.count { it in text }
            score += interestHits * 5
            if (video.channelId.isNotBlank() && state.subscriptions.any { it.id == video.channelId }) score += 3
            if (state.localLikedVideos.any { it.channelId.isNotBlank() && it.channelId == video.channelId }) score += 2
            if (listOf(
                    " el ", " la ", " los ", " las ", " que ", " como ", " para ", " con ", " de ",
                    "español", "latino", "hoy", "nuevo", "nueva", "música", "musica", "juego", "humor"
                ).any { it in " $text " }) score += 4
            if (listOf("anime", "donghua", "manhwa", "naruto", "minecraft", "free fire", "rap", "fútbol", "futbol", "perro", "gato").any { it in text }) score += 2
            val genericEnglish = listOf(
                "watch till the end", "viral shorts", "funny video", "subscribe now",
                "best moments", "daily vlog", "prank video", "random facts"
            ).any { it in text }
            if (genericEnglish && interestHits == 0) score -= 6
            return score
        }

        val ranked = videos.distinctBy { it.id }
            .mapIndexed { index, video -> Triple(video, score(video), index) }
            .sortedWith(compareByDescending<Triple<VideoItem, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
        return limitShortsPerChannel(ranked, limit)
    }

    private fun isAcceptableShort(video: VideoItem): Boolean {
        val text = (video.title + " " + video.description + " " + video.channelTitle).lowercase()
        val blocked = listOf(
            "onlyfans", "porn", "xxx", "nsfw", "18+", "desnuda", "desnudo",
            "bikini transparente", "contenido adulto", "hot girl", "sexy body"
        )
        return blocked.none { it in text }
    }

    private fun limitShortsPerChannel(videos: List<VideoItem>, limit: Int): List<VideoItem> {
        val result = ArrayList<VideoItem>(limit)
        val channelCounts = HashMap<String, Int>()
        videos.forEach { video ->
            if (result.size >= limit || video.id.isBlank()) return@forEach
            val channelKey = video.channelId.ifBlank { video.channelTitle.ifBlank { video.id } }
            val maxForChannel = if (result.size < 16) 1 else 2
            val count = channelCounts[channelKey] ?: 0
            if (count >= maxForChannel) return@forEach
            result += video
            channelCounts[channelKey] = count + 1
        }
        if (result.size < limit) {
            videos.forEach { video ->
                if (result.size >= limit) return@forEach
                if (result.none { it.id == video.id }) result += video
            }
        }
        return result
    }

    private fun roundRobinVideos(vararg groups: List<VideoItem>): List<VideoItem> {
        val result = ArrayList<VideoItem>()
        val seen = HashSet<String>()
        val positions = IntArray(groups.size)
        var added: Boolean
        do {
            added = false
            groups.forEachIndexed { index, group ->
                while (positions[index] < group.size) {
                    val item = group[positions[index]++]
                    if (item.id.isNotBlank() && seen.add(item.id)) {
                        result += item
                        added = true
                        break
                    }
                }
            }
        } while (added)
        return result
    }

    private fun handleApiError(error: YouTubeApiException) {
        if (error.statusCode == 401) accessToken = null
        _uiState.update {
            it.copy(
                loading = false,
                refreshing = false,
                loadingMoreCategory = null,
                shortsLoadingMore = false,
                uploadsLoadingMore = false,
                searchLoadingMore = false,
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

    private companion object {
        const val FIRST_RELATED_PAGE = "__first__"
        const val INITIAL_SUBSCRIPTION_BATCH = 4
        const val SUBSCRIPTION_PAGE_SIZE = 4
        const val GEO_WATCH_LATER_TITLE = "Geo Videos - Ver después"
        const val MAX_HOME_ITEMS = 60
    }
}
