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
    val channelVideos: List<VideoItem> = emptyList(),
    val autoplay: Boolean = true,
    val dataSaver: Boolean = false,
    val notificationsEnabled: Boolean = true,
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

class GeoVideosViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GeoVideosRepository(application)
    private val api = YouTubeApi()

    private var accessToken: String? = null
    private var likesPlaylistId: String = ""
    private var uploadsPlaylistId: String = ""
    private var popularNextToken: String = ""
    private var liveNextToken: String = ""
    private var gamingNextToken: String = ""
    private var musicNextToken: String = ""
    private var shortsNextToken: String = ""
    private var shortsQuery: String = "shorts"
    private var uploadsNextToken: String = ""
    private var searchNextToken: String = ""
    private var relatedNextToken: String = ""
    private var relatedVideoId: String = ""
    private var lastSearchQuery: String = ""
    private var subscriptionOffset: Int = 0
    private var subscriptionRefreshCursor: Int = 0

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
            uploads = repository.loadUploads(),
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
        resetPagination()
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
        accessToken = null
        likesPlaylistId = ""
        uploadsPlaylistId = ""
        resetPagination()
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
        resetPagination(keepSearch = true)
        _uiState.update {
            it.copy(
                refreshing = true,
                loadingMoreCategory = null,
                shortsLoadingMore = false,
                uploadsLoadingMore = false,
                canLoadMoreForYou = true,
                canLoadMoreLive = true,
                canLoadMoreGaming = true,
                canLoadMoreMusic = true,
                shortsCanLoadMore = true,
                uploadsCanLoadMore = true
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

                    val baseProfile = userDeferred.await()
                    val channelDetails = runCatching { api.getMyChannel(token, baseProfile) }.getOrNull()
                    likesPlaylistId = channelDetails?.likesPlaylistId.orEmpty()
                    uploadsPlaylistId = channelDetails?.uploadsPlaylistId.orEmpty()

                    val uploadsPage = runCatching {
                        api.playlistVideosPage(token, uploadsPlaylistId, maxResults = 25)
                    }.getOrDefault(VideoPage(previous.uploads))
                    val likedRaw = runCatching { api.playlistVideos(token, likesPlaylistId, 40) }
                        .getOrDefault(previous.liked)
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
                                runCatching { api.channelActivities(token, channel.id, 5) }
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
                        subscriptions = subscriptions
                    )
                    val shortsPage = runCatching {
                        api.searchVideosPage(
                            token = token,
                            query = shortsQuery,
                            shortOnly = true,
                            maxResults = 30
                        )
                    }.getOrDefault(shortsDeferred.await())
                    val subscriptionShorts = subscriptionFeedRaw.filter(::looksLikeShort)
                    val familiarShorts = mergeUniqueVideos(likedRaw, previous.history)
                        .filter(::looksLikeShort)
                    val taggedSearchShorts = shortsPage.items.filter(::looksLikeShort)
                    val personalizedSearchShorts = if (taggedSearchShorts.size >= 6) {
                        taggedSearchShorts
                    } else {
                        shortsPage.items
                    }
                    val shortsRaw = mergeUniqueVideos(
                        subscriptionShorts,
                        familiarShorts,
                        personalizedSearchShorts
                    ).take(MAX_HOME_ITEMS)
                    val notificationsRaw = activitiesDeferred.await()
                    val activityVideosRaw = notificationsRaw.mapNotNull { it.video }

                    val allRaw = mergeUniqueVideos(
                        subscriptionFeedRaw,
                        activityVideosRaw,
                        uploadsPage.items,
                        likedRaw,
                        previous.history.take(20),
                        popularPage.items,
                        livePage.items,
                        gamingPage.items,
                        musicPage.items,
                        shortsRaw
                    )
                    val enrichedById = enrichVideosWithCache(token, allRaw)
                        .associateBy { it.id }
                    fun enriched(items: List<VideoItem>): List<VideoItem> =
                        items.map { enrichedById[it.id] ?: it }

                    val popular = enriched(popularPage.items)
                    val live = enriched(livePage.items)
                    val gaming = enriched(gamingPage.items)
                    val music = enriched(musicPage.items)
                    val shorts = enriched(shortsRaw)
                    val liked = enriched(likedRaw)
                    val subscriptionFeed = enriched(subscriptionFeedRaw)
                    val uploads = enriched(uploadsPage.items)
                    val activityVideos = enriched(activityVideosRaw)
                    val notifications = notificationsRaw.map { item ->
                        item.copy(video = item.video?.let { enrichedById[it.id] ?: it })
                    }

                    val personalizedBase = mergeUniqueVideos(
                        subscriptionFeed,
                        activityVideos,
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
                    maxResults = 30
                )
                shortsNextToken = page.nextPageToken
                val tagged = page.items.filter(::looksLikeShort)
                val candidates = if (tagged.size >= 5) tagged else page.items
                val enriched = enrichVideosWithCache(token, candidates)
                _uiState.update {
                    it.copy(
                        shorts = mergeUniqueVideos(it.shorts, enriched).take(MAX_HOME_ITEMS),
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
                        runCatching { api.channelActivities(token, channel.id, 5) }
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
        val cached = withContext(Dispatchers.IO) { repository.loadChannelAvatars() }
        val embedded = videos.asSequence()
            .filter { it.channelId.isNotBlank() && it.channelThumbnailUrl.isNotBlank() }
            .associate { it.channelId to it.channelThumbnailUrl }
        val known = cached + embedded
        val withKnownAvatars = videos.map { video ->
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
                liked = state.liked,
                uploads = state.uploads,
                subscriptions = state.subscriptions,
                playlists = state.playlists,
                notifications = state.notifications,
                syncTimeMs = state.lastSyncMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
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
        val history = _uiState.value.history
        relatedNextToken = ""
        relatedVideoId = ""
        _uiState.update {
            it.copy(
                selectedVideo = playable,
                playerExpanded = false,
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
                val related = mergeUniqueVideos(
                    relatedPage?.items.orEmpty().filterNot { it.id == video.id },
                    fallback
                ).take(40)
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
                    relatedVideos = (it.relatedVideos + page.items)
                        .filterNot { candidate -> candidate.id == video.id }
                        .distinctBy { candidate -> candidate.id },
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
        return sequenceOf(
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
            .sortedByDescending { candidate ->
                var score = 0
                if (candidate.channelId.isNotBlank() && candidate.channelId == video.channelId) score += 6
                if (candidate.channelTitle.equals(video.channelTitle, ignoreCase = true)) score += 4
                val candidateWords = candidate.title.lowercase()
                    .split(Regex("""[^\p{L}\p{N}]+"""))
                    .toSet()
                score + titleWords.count { it in candidateWords }
            }
            .take(24)
            .toList()
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
        _uiState.update { it.copy(loading = true, selectedChannelTitle = channel.title, channelVideos = emptyList()) }
        viewModelScope.launch {
            try {
                val raw = api.channelVideos(token, channel.id)
                val videos = enrichVideosWithCache(token, raw)
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

    private fun buildShortsQuery(
        history: List<VideoItem>,
        liked: List<VideoItem>,
        subscriptions: List<ChannelItem>
    ): String {
        val stopWords = setOf(
            "para", "with", "from", "this", "that", "video", "official", "shorts",
            "the", "and", "los", "las", "una", "uno", "del", "por", "como", "music"
        )
        val interestWords = (history.take(18) + liked.take(18))
            .asSequence()
            .flatMap { it.title.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).asSequence() }
            .filter { it.length >= 4 && it !in stopWords }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
        val channelHints = subscriptions.take(3)
            .map { it.title.split(' ').firstOrNull().orEmpty() }
            .filter { it.length >= 3 }
        return (interestWords + channelHints + listOf("shorts"))
            .distinct()
            .take(8)
            .joinToString(" ")
            .ifBlank { "shorts" }
    }

    private fun looksLikeShort(video: VideoItem): Boolean {
        val text = (video.title + " " + video.description).lowercase()
        return video.durationMs in 1..180_000L ||
            listOf("#shorts", " shorts", "short ", "tiktok", "reel", "clip").any { it in text }
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
        const val INITIAL_SUBSCRIPTION_BATCH = 14
        const val SUBSCRIPTION_PAGE_SIZE = 10
        const val MAX_HOME_ITEMS = 180
    }
}
