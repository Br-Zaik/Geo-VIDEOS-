package com.geovideos.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.CommentItem
import com.geovideos.app.data.GeoVideosRepository
import com.geovideos.app.data.GoogleProfile
import com.geovideos.app.data.HomeFeedEntry
import com.geovideos.app.data.MediaKind
import com.geovideos.app.data.NotificationItem
import com.geovideos.app.data.PlaylistItem
import com.geovideos.app.data.VideoItem
import com.geovideos.app.data.VideoDetails
import com.geovideos.app.data.YouTubeSessionStore
import com.geovideos.app.network.HomeChip
import com.geovideos.app.network.SearchEntry
import com.geovideos.app.network.ChannelPost
import com.geovideos.app.network.ChannelShelf
import com.geovideos.app.network.ChannelTabInfo
import com.geovideos.app.network.InnerTubeClient
import com.geovideos.app.network.YouTubeSessionExpiredException
import com.geovideos.app.network.VideoPage
import com.geovideos.app.network.YouTubeApi
import com.geovideos.app.network.YouTubeApiException
import com.geovideos.app.playback.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
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
    SUBSCRIPTIONS,
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
    val searchChannels: List<ChannelItem> = emptyList(),
    val searchPlaylists: List<PlaylistItem> = emptyList(),
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
    /** Búsqueda como YouTube: resultados mezclados en su orden, filtro, sugerencias y "Mostrando resultados de". */
    val searchEntries: List<SearchEntry> = emptyList(),
    val searchFilter: Int = 0,
    val searchSuggestions: List<String> = emptyList(),
    val searchCorrected: String = "",
    val searchQuery: String = "",
    val searchCanLoadMore: Boolean = false,
    /** Número rojo de la campana y estado de la bandeja. */
    val notificationsUnseen: Int = 0,
    val notificationsLoading: Boolean = false,
    val notificationsLoadingMore: Boolean = false,
    val notificationsCanLoadMore: Boolean = false,
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
    val selectedPlaylist: PlaylistItem? = null,
    val selectedPlaylistVideos: List<VideoItem> = emptyList(),
    val playlistLoading: Boolean = false,
    val autoplay: Boolean = true,
    val dataSaver: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val youtubeSyncEnabled: Boolean = false,
    val youtubeSyncAuthorized: Boolean = false,
    val youtubeSyncBusy: Boolean = false,
    val lastSyncMs: Long = 0L,
    val youtubeSessionConnected: Boolean = false,
    val youtubeSessionBusy: Boolean = false,
    val youtubeHistory: List<VideoItem> = emptyList(),
    val youtubeHistoryFetchedAtMs: Long = 0L,
    val youtubeWatchLater: List<VideoItem> = emptyList(),
    val historyLoadingMore: Boolean = false,
    val historyCanLoadMore: Boolean = false,
    val watchLaterLoadingMore: Boolean = false,
    val watchLaterCanLoadMore: Boolean = false,
    val homeMixes: List<VideoItem> = emptyList(),
    /** Inicio de YouTube en su orden exacto (videos, Shorts y Mixes). */
    val homeFeed: List<HomeFeedEntry> = emptyList(),
    /** Cola / Mix activo (panel como el de YouTube). */
    val queue: List<VideoItem> = emptyList(),
    val queueTitle: String = "",
    val queueSubtitle: String = "",
    val queuePlaylistId: String = "",
    /** 0 = no repetir, 1 = repetir lista, 2 = repetir este video. */
    val queueRepeat: Int = 0,
    val queueShuffle: Boolean = false,
    val queueOrder: List<String> = emptyList(),
    val queueLoadingMore: Boolean = false,
    val queueOpen: Boolean = false,
    /** Filtros del inicio que manda YouTube ("Todos" = token vacío). */
    val homeChips: List<HomeChip> = emptyList(),
    val selectedHomeChip: String = "",
    val homeChipLoading: Boolean = false,
    /** Filtros de los relacionados en la página del video. */
    val relatedChips: List<HomeChip> = emptyList(),
    val selectedRelatedChip: String = "",
    /** Hoja de comentarios. */
    val commentsOpen: Boolean = false,
    val commentsVideoId: String = "",
    val commentsList: List<CommentItem> = emptyList(),
    val commentsLoading: Boolean = false,
    val commentsLoadingMore: Boolean = false,
    val commentsCanLoadMore: Boolean = false,
    val commentsTotalText: String = "",
    val commentsSort: Int = 0,
    val commentsSortAvailable: Boolean = false,
    val commentReplies: Map<String, List<CommentItem>> = emptyMap(),
    val commentRepliesLoading: Set<String> = emptySet(),
    val commentRepliesCanLoadMore: Set<String> = emptySet(),
    val subsFeedVideos: List<VideoItem> = emptyList(),
    val subsFeedShorts: List<VideoItem> = emptyList(),
    val subsFeedLoading: Boolean = false,
    val subsFeedLoadingMore: Boolean = false,
    val subsFeedCanLoadMore: Boolean = false,
    /** Barra de canales de Suscripciones (orden, punto azul y en vivo, de la guía de YouTube). */
    val subsGuide: List<ChannelItem> = emptyList(),
    /** Canal elegido en la barra: el feed muestra solo ese canal. */
    val subsChannelFilter: ChannelItem? = null,
    val subsChannelVideos: List<VideoItem> = emptyList(),
    val subsChannelShorts: List<VideoItem> = emptyList(),
    val subsChannelLoading: Boolean = false,
    /** Página del canal como YouTube: pestañas reales, estanterías y orden. */
    val channelTabs: List<ChannelTabInfo> = emptyList(),
    val channelShelves: List<ChannelShelf> = emptyList(),
    val channelFeatured: VideoItem? = null,
    val channelTabIndex: Int = 0,
    val channelTabVideos: List<VideoItem> = emptyList(),
    val channelTabShorts: List<VideoItem> = emptyList(),
    val channelTabPlaylists: List<PlaylistItem> = emptyList(),
    val channelTabPosts: List<ChannelPost> = emptyList(),
    val channelTabChips: List<HomeChip> = emptyList(),
    val channelTabSelectedChip: String = "",
    val channelTabLoading: Boolean = false,
    val channelTabLoadingMore: Boolean = false,
    val channelTabCanLoadMore: Boolean = false,
    /** Hoja de la campana (Todas / Personalizadas / Ninguna). */
    val bellSheetChannel: ChannelItem? = null,
    val bellSheetLoading: Boolean = false,
    val message: String? = null
) {
    /** Historial visible: el real de YouTube + lo visto recién en Geo Videos. */
    val libraryHistory: List<VideoItem> by lazy {
        if (!youtubeSessionConnected || youtubeHistory.isEmpty()) return@lazy history
        val local = history.associateBy { it.id }
        val remote = youtubeHistory.map { yt ->
            val saved = local[yt.id]
            if (saved != null && saved.resumePositionMs > 0L) {
                yt.copy(
                    resumePositionMs = saved.resumePositionMs,
                    durationMs = maxOf(yt.durationMs, saved.durationMs)
                )
            } else {
                yt
            }
        }
        val recent = history.filter { it.watchedAtMs >= youtubeHistoryFetchedAtMs }
        val recentIds = recent.mapTo(HashSet()) { it.id }
        recent + remote.filterNot { it.id in recentIds }
    }

    /** Ver más tarde visible: la lista real de YouTube + lo guardado solo en el teléfono. */
    val libraryWatchLater: List<VideoItem> by lazy {
        if (!youtubeSessionConnected) return@lazy watchLater
        val remoteIds = youtubeWatchLater.mapTo(HashSet()) { it.id }
        youtubeWatchLater + watchLater.filterNot { it.id in remoteIds }
    }

    fun isLoadingMore(category: HomeCategory): Boolean = loadingMoreCategory == category

    val queueActive: Boolean get() = queue.size > 1

    val queueIndex: Int get() = selectedVideo?.let { current -> queue.indexOfFirst { it.id == current.id } } ?: -1

    /** Orden real de reproducción (aleatorio si está activado). */
    private fun playOrder(): List<String> =
        if (queueShuffle && queueOrder.size == queue.size) queueOrder else queue.map { it.id }

    /** Los siguientes videos de la cola, en el orden en que sonarán. */
    fun upcomingQueue(limit: Int = 4): List<VideoItem> {
        if (!queueActive) return emptyList()
        val current = selectedVideo?.id ?: return emptyList()
        val order = playOrder()
        val start = order.indexOf(current)
        if (start < 0) return emptyList()
        val byId = queue.associateBy { it.id }
        val ids = buildList {
            var i = start + 1
            while (size < limit) {
                if (i >= order.size) {
                    if (queueRepeat != 1) break
                    i = 0
                }
                if (order[i] == current) break
                add(order[i])
                i++
            }
        }
        return ids.mapNotNull { byId[it] }
    }

    fun previousInQueue(): VideoItem? {
        if (!queueActive) return null
        val current = selectedVideo?.id ?: return null
        val order = playOrder()
        val index = order.indexOf(current)
        if (index < 0) return null
        val id = order.getOrNull(index - 1) ?: if (queueRepeat == 1) order.lastOrNull() else null
        return id?.let { target -> queue.firstOrNull { it.id == target } }
    }

    fun canLoadMore(category: HomeCategory): Boolean = when (category) {
        HomeCategory.FOR_YOU -> canLoadMoreForYou
        HomeCategory.LIVE -> canLoadMoreLive
        HomeCategory.GAMING -> canLoadMoreGaming
        HomeCategory.MUSIC -> canLoadMoreMusic
    }
}

private class GoogleAccountMismatchException(message: String) : IllegalStateException(message)

private data class AccountSyncResult(
    val profile: GoogleProfile,
    val subscriptions: List<ChannelItem>,
    val playlists: List<PlaylistItem>,
    val liked: List<VideoItem>,
    val uploads: List<VideoItem>,
    val likesPlaylistId: String,
    val uploadsPlaylistId: String
)

/** Filtros de búsqueda de YouTube y sus parámetros. */
internal val SEARCH_FILTERS: List<Pair<String, String>> = listOf(
    "Todos" to "",
    "Videos" to "EgIQAQ%3D%3D",
    "Shorts" to "EgIQCQ%3D%3D",
    "En vivo" to "EgJAAQ%3D%3D",
    "Canales" to "EgIQAg%3D%3D",
    "Listas" to "EgIQAw%3D%3D",
    "Subidos hoy" to "EgIIAg%3D%3D",
    "Esta semana" to "EgIIAw%3D%3D",
    "Más vistos" to "CAM%3D"
)

class GeoVideosViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GeoVideosRepository(application)
    private val youtubeSession = YouTubeSessionStore(application)
    private val innerTube = InnerTubeClient { youtubeSession.load() }
    private val api = YouTubeApi(innerTube)
    private var subsFeedContinuation: String = ""
    private var commentsNextToken: String = ""
    private var channelTabContinuation: String = ""
    private var channelTabJob: Job? = null
    private var commentsSortTokens: List<String> = emptyList()
    private var commentsJob: Job? = null
    private val repliesNextTokens = HashMap<String, String>()
    private var homeChipJob: Job? = null
    private var savedAllHome: SavedHome? = null

    /** Copia del inicio "Todos" para volver a él sin recargar. */
    private data class SavedHome(
        val feed: List<HomeFeedEntry>,
        val personalized: List<VideoItem>,
        val mixes: List<VideoItem>,
        val continuation: String
    )
    private var shortsSequenceContinuation: String = ""
    private var shortsSequenceActive: Boolean = false
    private var youtubeHomeActive: Boolean = false
    private var youtubeHomeContinuation: String = ""
    private var youtubeHistoryContinuation: String = ""
    private var youtubeWatchLaterContinuation: String = ""
    private var youtubeSessionJob: Job? = null

    private var accessToken: String? = null
    private var oauthWriteToken: String? = null

    // Con la sesión de YouTube, las acciones (me gusta, suscribirse, listas) usan la misma sesión.
    private var youtubeWriteToken: String?
        get() = if (youtubeSession.hasStoredSession()) YouTubeApi.SESSION_TOKEN else oauthWriteToken
        set(value) {
            oauthWriteToken = value
        }
    private var likesPlaylistId: String = ""
    private var uploadsPlaylistId: String = ""
    private var popularNextToken: String = ""
    private var liveNextToken: String = ""
    private var gamingNextToken: String = ""
    private var musicNextToken: String = ""
    private var shortsNextToken: String = ""
    private var shortsQuery: String = ""
    private var uploadsNextToken: String = ""
    private var likedNextToken: String = ""
    private var searchNextToken: String = ""
    private var relatedNextToken: String = ""
    private var relatedVideoId: String = ""
    private var lastSearchQuery: String = ""
    private var suggestionsJob: Job? = null
    private var searchJob: Job? = null
    private var notificationsContinuation: String = ""
    private var subscriptionOffset: Int = 0
    private var subscriptionRefreshCursor: Int = 0
    private var pendingSubscriptionToggle: ChannelItem? = null
    private var authorizationAttemptId: Long = 0L
    private var fullSyncJob: Job? = null

    private val cachedProfile = repository.loadProfile()
    private val cachedAccountConnected = repository.hasConnectedAccount() && cachedProfile != null &&
        youtubeSession.hasStoredSession()
    private val _uiState = MutableStateFlow(
        GeoVideosUiState(
            authStatus = if (cachedAccountConnected) AuthStatus.CONNECTED else AuthStatus.DISCONNECTED,
            profile = cachedProfile,
            // Heavy JSON lists are hydrated off the main thread. This lets Android draw the
            // first frame immediately instead of keeping the splash screen visible while
            // history, feeds, playlists and Shorts are decoded from SharedPreferences.
            loading = cachedAccountConnected,
            autoplay = repository.loadAutoplay(),
            dataSaver = repository.loadDataSaver(),
            notificationsEnabled = repository.loadNotificationsEnabled(),
            youtubeSyncEnabled = youtubeSession.hasStoredSession(),
            youtubeSyncAuthorized = youtubeSession.hasStoredSession(),
            lastSyncMs = repository.loadLastSyncMs(),
            youtubeSessionConnected = youtubeSession.hasStoredSession()
        )
    )
    val uiState: StateFlow<GeoVideosUiState> = _uiState.asStateFlow()

    init {
        // Reproducción con tu sesión cuando YouTube bloquea la vía anónima.
        com.geovideos.app.playback.StreamResolver.sessionPlayerProvider = { id, sts ->
            if (!youtubeSession.hasStoredSession()) null else innerTube.sessionPlayer(id, sts)
        }
        com.geovideos.app.playback.StreamResolver.sessionChaptersProvider = { id ->
            if (!youtubeSession.hasStoredSession()) emptyList() else innerTube.chapters(id).map { (title, startMs, thumb) ->
                com.geovideos.app.playback.VideoChapter(title, startMs, thumb)
            }
        }
        com.geovideos.app.playback.StreamResolver.sessionTvProvider = { id, sts ->
            if (!youtubeSession.hasStoredSession()) null else innerTube.sessionTvStreams(id, sts)
        }
        com.geovideos.app.playback.StreamResolver.sessionEmbeddedProvider = { id, sts ->
            if (!youtubeSession.hasStoredSession()) null else innerTube.sessionEmbeddedStreams(id, sts)
        }
        // App de YouTube para Android sin cuenta: lo que usaba el ZIP original (va primero).
        com.geovideos.app.playback.StreamResolver.androidProvider = { id -> innerTube.androidReelStreams(id) }
        // TV Samsung sin cuenta: última opción, funciona aunque no hayas iniciado sesión.
        com.geovideos.app.playback.StreamResolver.anonymousTvProvider = { id, sts ->
            innerTube.anonymousTvStreams(id, sts)
        }
        // Versión web actual de YouTube: con una vieja YouTube responde "volver a cargar la página".
        viewModelScope.launch(Dispatchers.IO) {
            innerTube.updateWebClientVersion(com.geovideos.app.playback.StreamResolver.webClientVersion())
        }
        hydrateCachedState()
        // Un solo inicio de sesión: si la sesión de YouTube está guardada, entrar directo
        // y pedir el inicio de YouTube YA (antes era al final de toda la sincronización).
        if (youtubeSession.hasStoredSession()) {
            connectWithYouTubeSession(interactive = false)
            refreshYouTubeSession()
        }
    }

    private fun hydrateCachedState() {
        // Capture the account that owns the remote cache before moving the heavy JSON work
        // to IO. If the user changes account while hydration is still running, never inject
        // the previous account's YouTube feed into the new session.
        val hydratedAccountEmail = cachedProfile?.email.orEmpty().trim()
        viewModelScope.launch(Dispatchers.IO) {
            // Principal starts on "Para ti". Decode only the two lists needed for that first
            // useful screen, in parallel, instead of blocking on every cached category.
            val personalizedDeferred = async { repository.loadPersonalized().filter(::isNormalHomeVideo) }
            val popularDeferred = async { repository.loadPopular() }
            val lastSyncDeferred = async { repository.loadLastSyncMs() }
            val personalized = personalizedDeferred.await()
            val popular = popularDeferred.await()
            val lastSyncMs = lastSyncDeferred.await()

            _uiState.update { state ->
                val currentAccountEmail = state.profile?.email.orEmpty().trim()
                if (currentAccountEmail.equals(hydratedAccountEmail, ignoreCase = true)) {
                    state.copy(
                        personalized = if (youtubeHomeActive) state.personalized else personalized,
                        popular = popular,
                        lastSyncMs = lastSyncMs,
                        // Si la migración eliminó un feed genérico antiguo, mantener skeleton
                        // mientras se hidratan las señales reales de la cuenta; no mostrar un
                        // estado vacío falso entre ambas lecturas de disco.
                        loading = if (youtubeHomeActive) state.loading else personalized.isEmpty() && state.profile != null
                    )
                } else {
                    // An account switch completed while disk hydration was running. Do not
                    // disturb that new account's loading/refresh state.
                    state
                }
            }

            // Hydrate the remaining Home categories only after Principal is already usable.
            val liveDeferred = async { repository.loadLive() }
            val gamingDeferred = async { repository.loadGaming() }
            val musicDeferred = async { repository.loadMusic() }
            val shortsDeferred = async { repository.loadShorts().filter(::looksLikeShort) }
            val live = liveDeferred.await()
            val gaming = gamingDeferred.await()
            val music = musicDeferred.await()
            val shorts = shortsDeferred.await()

            _uiState.update { state ->
                val currentAccountEmail = state.profile?.email.orEmpty().trim()
                if (currentAccountEmail.equals(hydratedAccountEmail, ignoreCase = true)) {
                    state.copy(live = live, gaming = gaming, music = music, shorts = shorts)
                } else {
                    state
                }
            }

            // Library/account caches are independent JSON blobs; parse them concurrently on
            // IO so they cannot extend the splash/Principal critical path.
            val subscriptionsDeferred = async { repository.loadSubscriptions() }
            val playlistsDeferred = async { repository.loadPlaylists() }
            val likedDeferred = async { repository.loadLiked() }
            val uploadsDeferred = async { repository.loadUploads() }
            val notificationsDeferred = async { repository.loadNotifications() }
            val historyDeferred = async { repository.loadHistory() }
            val watchLaterDeferred = async { repository.loadWatchLater() }
            val localLikedIdsDeferred = async { repository.loadLocalLikedIds() }
            val localDislikedIdsDeferred = async { repository.loadLocalDislikedIds() }
            val localLikedVideosDeferred = async { repository.loadLocalLikedVideos() }
            val downloadsDeferred = async { repository.loadDownloads() }
            val searchHistoryDeferred = async { repository.loadSearchHistory() }

            val subscriptions = subscriptionsDeferred.await()
            val playlists = playlistsDeferred.await()
            val liked = likedDeferred.await()
            val uploads = uploadsDeferred.await()
            val notifications = notificationsDeferred.await()
            val history = historyDeferred.await()
            val watchLater = watchLaterDeferred.await()
            val localLikedIds = localLikedIdsDeferred.await()
            val localDislikedIds = localDislikedIdsDeferred.await()
            val localLikedVideos = localLikedVideosDeferred.await()
            val downloads = downloadsDeferred.await()
            val searchHistory = searchHistoryDeferred.await()

            _uiState.update { state ->
                val currentAccountEmail = state.profile?.email.orEmpty().trim()
                val sameAccount = currentAccountEmail.equals(hydratedAccountEmail, ignoreCase = true)
                if (sameAccount) {
                    // Principal no se reconstruye con Me gusta/historial: esas listas son
                    // señales de interés, no un sustituto del feed de suscripciones. Si el cache
                    // remoto fue invalidado, mantener skeleton hasta recibir publicaciones reales
                    // de los canales seguidos. Los Shorts guardados sí pueden mostrarse arriba.
                    val cachedAccountFeed = state.personalized.filter(::isNormalHomeVideo)
                    val cachedAccountShorts = shorts.filter(::looksLikeShort).take(40)
                    state.copy(
                        personalized = if (youtubeHomeActive) state.personalized else cachedAccountFeed,
                        shorts = state.shorts.ifEmpty { cachedAccountShorts },
                        subscriptions = subscriptions,
                        playlists = playlists,
                        liked = liked,
                        uploads = uploads,
                        notifications = notifications,
                        history = history,
                        watchLater = watchLater,
                        localLikedIds = localLikedIds,
                        localDislikedIds = localDislikedIds,
                        localLikedVideos = localLikedVideos,
                        downloads = downloads,
                        searchHistory = searchHistory,
                        loading = if (youtubeHomeActive) state.loading else cachedAccountFeed.isEmpty() && cachedAccountShorts.isEmpty()
                    )
                } else {
                    // Local-only data is account-independent in Geo Videos and is safe to
                    // restore even if a Google account switch completed during hydration.
                    state.copy(
                        history = history,
                        watchLater = watchLater,
                        localLikedIds = localLikedIds,
                        localDislikedIds = localDislikedIds,
                        localLikedVideos = localLikedVideos,
                        downloads = downloads,
                        searchHistory = searchHistory
                    )
                }
            }

            // Con la sesión de YouTube conectada, traer historial, Ver más tarde e inicio reales.
            if (youtubeSession.hasStoredSession()) refreshYouTubeSession()
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
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            runCatching {
                supervisorScope {
                    val subscriptionsDeferred = async { api.subscriptions(token) }
                    val playlistsDeferred = async { api.playlists(token) }
                    val channel = api.getMyChannel(token, profile)
                    val likedDeferred = async {
                        api.playlistVideosPage(token, channel.likesPlaylistId, maxResults = 50)
                    }
                    val uploadsDeferred = async {
                        api.playlistVideosPage(token, channel.uploadsPlaylistId, maxResults = 25)
                    }
                    AccountSyncResult(
                        profile = channel.profile,
                        subscriptions = subscriptionsDeferred.await(),
                        playlists = playlistsDeferred.await(),
                        liked = likedDeferred.await().items,
                        uploads = uploadsDeferred.await().items,
                        likesPlaylistId = channel.likesPlaylistId,
                        uploadsPlaylistId = channel.uploadsPlaylistId
                    )
                }
            }.onSuccess { result ->
                likesPlaylistId = result.likesPlaylistId
                uploadsPlaylistId = result.uploadsPlaylistId
                repository.saveConnectedProfile(result.profile)
                repository.saveSubscriptions(result.subscriptions)
                repository.saveLikedVideos(result.liked)
                _uiState.update {
                    it.copy(
                        profile = result.profile,
                        subscriptions = result.subscriptions,
                        playlists = result.playlists,
                        liked = result.liked,
                        uploads = result.uploads,
                        youtubeSyncAuthorized = true,
                        youtubeSyncBusy = false,
                        message = "YouTube sincronizado: suscripciones, Me gusta, listas y subidos actualizados."
                    )
                }
                // Reordenar Principal con las señales recién sincronizadas sin bloquear la UI.
                if (!accessToken.isNullOrBlank()) refresh()
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

        // La autorización interactiva muestra la pantalla de conexión. La renovación
        // silenciosa de una cuenta ya verificada NO cambia de pantalla: Principal se
        // mantiene visible mientras el token se comprueba en segundo plano.
        _uiState.update { state ->
            state.copy(
                authStatus = if (interactive || previousProfile == null) AuthStatus.CONNECTING else AuthStatus.CONNECTED,
                authError = "",
                loading = if (interactive && previousProfile == null) state.loading else false,
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
                if (!interactive && token != YouTubeApi.SESSION_TOKEN && previousEmail.isNotBlank() &&
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
                val accountHistory = if (newAccountSession) {
                    withContext(Dispatchers.IO) { repository.loadHistory() }
                } else {
                    null
                }
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
                            history = accountHistory.orEmpty(),
                            searchResults = emptyList(),
                            selectedChannel = null,
                            channelVideos = emptyList(),
                            channelPlaylists = emptyList(),
                            lastSyncMs = 0L,
                            youtubeSyncEnabled = youtubeSession.hasStoredSession(),
                            youtubeSyncAuthorized = youtubeSession.hasStoredSession(),
                            youtubeSyncBusy = false
                        )
                    } else {
                        state
                    }
                    base.copy(
                        authStatus = AuthStatus.CONNECTED,
                        profile = verifiedProfile,
                        // Cache-first: una renovación silenciosa no vuelve a poner toda la
                        // pantalla en carga. Solo una cuenta nueva sin contenido muestra skeleton.
                        loading = newAccountSession && base.personalized.isEmpty(),
                        authError = "",
                        message = null
                    )
                }

                val lastSync = _uiState.value.lastSyncMs
                val shouldRefresh = newAccountSession ||
                    lastSync <= 0L ||
                    System.currentTimeMillis() - lastSync >= AUTO_REFRESH_INTERVAL_MS
                if (shouldRefresh) {
                    loadAll(initialLoad = newAccountSession || lastSync <= 0L)
                }
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
        youtubeSessionJob?.cancel()
        youtubeSession.clear()
        innerTube.clearWatch()
        runCatching { android.webkit.CookieManager.getInstance().removeAllCookies(null) }
        youtubeHistoryContinuation = ""
        youtubeWatchLaterContinuation = ""
        subsFeedContinuation = ""
        repository.clearYouTubeSyncAccountBinding()
        likesPlaylistId = ""
        uploadsPlaylistId = ""
        resetPagination()
        fullSyncJob?.cancel()
        fullSyncJob = null
        youtubeHomeActive = false
        youtubeHomeContinuation = ""
        repository.clearAccountCache()
        _uiState.update {
            GeoVideosUiState(
                authStatus = AuthStatus.DISCONNECTED,
                youtubeSyncAuthorized = false,
                youtubeSessionConnected = youtubeSession.hasStoredSession(),
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
        if (accessToken.isNullOrBlank() && youtubeSession.hasStoredSession()) {
            connectWithYouTubeSession(interactive = false)
            return
        }
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

    private suspend fun refreshVisibleCategoryFast(
        token: String,
        snapshot: GeoVideosUiState
    ): Boolean {
        return try {
            when (snapshot.homeCategory) {
                HomeCategory.FOR_YOU -> {
                    // Actualizar "Para ti" significa renovar datos REALES de la cuenta. La
                    // versión anterior pedía Tendencias Perú y por eso el círculo terminaba
                    // mostrando videos ajenos. Aquí se refrescan suscripciones recientes y se
                    // conservan Likes/historial como señales secundarias.
                    val subscriptions = snapshot.subscriptions.ifEmpty {
                        runCatching { api.subscriptions(token, maxPages = 2) }.getOrDefault(emptyList())
                    }
                    val rankedSubscriptions = rankSubscriptionsForHome(subscriptions, snapshot)
                    val batchSize = minOf(FAST_REFRESH_SUBSCRIPTION_BATCH, rankedSubscriptions.size)
                    val start = if (rankedSubscriptions.isEmpty()) 0 else subscriptionRefreshCursor % rankedSubscriptions.size
                    val window = List(batchSize) { index ->
                        rankedSubscriptions[(start + index) % rankedSubscriptions.size]
                    }
                    if (subscriptions.isNotEmpty()) {
                        subscriptionRefreshCursor = (start + window.size) % subscriptions.size
                    }
                    // Renovar videos y Shorts en paralelo. Así el único círculo no espera una
                    // segunda fase después de actualizar los canales suscritos. La consulta de
                    // Shorts usa el cursor siguiente y excluye visualmente la tanda ya mostrada.
                    val computedShortsQuery = buildShortsQuery(
                        history = snapshot.history,
                        liked = mergeUniqueVideos(snapshot.liked, snapshot.localLikedVideos),
                        subscriptions = subscriptions,
                        searchHistory = snapshot.searchHistory
                    )
                    val refreshShortsQuery = computedShortsQuery.ifBlank { shortsQuery }
                    // Si los intereses de la cuenta cambiaron, iniciar esa búsqueda desde su
                    // primera página. Si la consulta es la misma, avanzar con nextPageToken.
                    val requestedShortsToken = if (
                        shortsQuery.isNotBlank() && refreshShortsQuery == shortsQuery
                    ) shortsNextToken else ""
                    val (rawRecent, searchedShortsPage) = supervisorScope {
                        val recentDeferred = async {
                            withTimeoutOrNull(4_800L) {
                                supervisorScope {
                                    window.map { channel ->
                                        async {
                                            runCatching { api.channelActivities(token, channel.id, 7) }
                                                .getOrDefault(emptyList())
                                        }
                                    }.awaitAll().flatten()
                                }
                            }.orEmpty()
                        }
                        val shortsDeferred = async {
                            if (refreshShortsQuery.isBlank()) {
                                VideoPage(emptyList())
                            } else {
                                withTimeoutOrNull(4_800L) {
                                    loadShortsSearchPage(
                                        token = token,
                                        preferredQuery = refreshShortsQuery,
                                        maxResults = 24,
                                        pageToken = requestedShortsToken
                                    ).first
                                } ?: VideoPage(emptyList())
                            }
                        }
                        recentDeferred.await().sortedByDescending { it.publishedAt } to shortsDeferred.await()
                    }
                    shortsQuery = refreshShortsQuery
                    if (searchedShortsPage.nextPageToken.isNotBlank()) {
                        shortsNextToken = searchedShortsPage.nextPageToken
                    } else if (requestedShortsToken.isNotBlank()) {
                        // El cursor llegó al final. El siguiente gesto vuelve a la primera página,
                        // pero rotateShortsForRefresh seguirá priorizando IDs no visibles.
                        shortsNextToken = ""
                    }

                    // Una sola consulta de duraciones permite separar antes de publicar: ningún
                    // Short debe colarse debajo de la fila superior. Si YouTube no devuelve la
                    // duración, ese elemento se conserva fuera del feed normal hasta la pasada
                    // completa, en vez de mostrarlo erróneamente como video horizontal.
                    val durationCandidates = mergeUniqueVideos(rawRecent, searchedShortsPage.items)
                    val classifiedCandidates = withTimeoutOrNull(3_000L) {
                        runCatching { api.enrichVideoDurations(token, durationCandidates) }
                            .getOrDefault(durationCandidates)
                    } ?: durationCandidates
                    val classifiedById = classifiedCandidates.associateBy { it.id }
                    val classifiedRecent = rawRecent.map { classifiedById[it.id] ?: it }
                    // Los resultados de esta consulta vienen específicamente del endpoint
                    // de búsqueda corta; no hacemos una segunda ronda de avatares/extractor
                    // mientras el usuario está esperando el refresh. La pasada completa los
                    // enriquece después sin mantener el círculo visible.
                    val searchedShorts = searchedShortsPage.items
                        .map { classifiedById[it.id] ?: it }
                        .filter(::isAcceptableShort)
                    val freshShorts = mergeUniqueVideos(
                        classifiedRecent.filter(::looksLikeShort),
                        searchedShorts
                    )
                    val freshVideos = classifiedRecent.filter(::isNormalHomeVideo)
                    val personalized = mergeUniqueVideos(
                        freshVideos,
                        snapshot.personalized.filter(::isNormalHomeVideo)
                    ).take(MAX_HOME_ITEMS)
                    val shorts = rotateShortsForRefresh(
                        fresh = freshShorts,
                        existing = snapshot.shorts,
                        state = snapshot.copy(subscriptions = subscriptions),
                        limit = 40
                    )
                    subscriptionOffset = minOf(INITIAL_SUBSCRIPTION_BATCH, subscriptions.size)
                    _uiState.update { current ->
                        current.copy(
                            refreshing = false,
                            subscriptions = subscriptions,
                            personalized = if (youtubeHomeActive && current.personalized.isNotEmpty()) {
                                current.personalized
                            } else if (personalized.isNotEmpty()) {
                                personalized
                            } else {
                                current.personalized.filter(::isNormalHomeVideo)
                            },
                            shorts = if (shorts.isNotEmpty()) shorts else current.shorts,
                            canLoadMoreForYou = if (youtubeHomeActive && current.personalized.isNotEmpty()) {
                                youtubeHomeContinuation.isNotBlank()
                            } else {
                                subscriptionOffset < subscriptions.size
                            },
                            message = null
                        )
                    }
                }

                HomeCategory.LIVE -> {
                    val page = api.liveVideosPage(token)
                    liveNextToken = page.nextPageToken
                    _uiState.update { current ->
                        current.copy(
                            refreshing = false,
                            live = page.items.ifEmpty { current.live },
                            canLoadMoreLive = page.nextPageToken.isNotBlank(),
                            message = null
                        )
                    }
                }

                HomeCategory.GAMING -> {
                    val page = api.mostPopularPage(token, "20")
                    val videos = page.items.filterNot(::looksLikeShort)
                    gamingNextToken = page.nextPageToken
                    _uiState.update { current ->
                        current.copy(
                            refreshing = false,
                            gaming = videos.ifEmpty { current.gaming },
                            canLoadMoreGaming = page.nextPageToken.isNotBlank(),
                            message = null
                        )
                    }
                }

                HomeCategory.MUSIC -> {
                    val page = api.musicVideosPage(token)
                    val videos = page.items.filterNot(::looksLikeShort)
                    musicNextToken = page.nextPageToken
                    _uiState.update { current ->
                        current.copy(
                            refreshing = false,
                            music = videos.ifEmpty { current.music },
                            canLoadMoreMusic = page.nextPageToken.isNotBlank(),
                            message = null
                        )
                    }
                }
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // El gesto de actualizar nunca debe dejar el circulo girando mientras se ejecuta
            // la sincronizacion completa. Si falla esta pasada rapida, conservar el cache.
            _uiState.update { current ->
                current.copy(
                    refreshing = false,
                    message = "No se pudo actualizar rapido. Se conservaron los datos guardados."
                )
            }
            false
        }
    }

    private fun loadAll(initialLoad: Boolean) {
        val token = accessToken ?: return
        fullSyncJob?.cancel()
        fullSyncJob = viewModelScope.launch {
            var previous = _uiState.value
            try {
                // Un refresh manual debe sentirse inmediato: primero actualiza solo la categoria
                // visible y apaga el unico indicador. La sincronizacion pesada continua despues
                // sin bloquear Principal ni mantener el circulo durante decenas de segundos.
                val fastRefreshedCategory = if (!initialLoad) {
                    val visibleCategory = previous.homeCategory
                    val refreshed = refreshVisibleCategoryFast(token, previous)
                    previous = _uiState.value
                    visibleCategory.takeIf { refreshed }
                } else {
                    null
                }

                supervisorScope {
                    val userDeferred = async { api.getUserInfo(token) }
                    // "Todos" ya no usa Tendencias como sustituto del feed personal.
                    // Conservar el cache anterior evita una llamada de red que no aporta al
                    // Principal y deja ancho de banda para cuenta, Shorts y reproducción.
                    val popularDeferred = async(start = CoroutineStart.LAZY) {
                        VideoPage(previous.popular, popularNextToken)
                    }
                    val liveDeferred = async(start = CoroutineStart.LAZY) {
                        if (fastRefreshedCategory == HomeCategory.LIVE) {
                            VideoPage(previous.live, liveNextToken)
                        } else {
                            runCatching { api.liveVideosPage(token) }
                                .getOrDefault(VideoPage(previous.live))
                        }
                    }
                    val gamingDeferred = async(start = CoroutineStart.LAZY) {
                        if (fastRefreshedCategory == HomeCategory.GAMING) {
                            VideoPage(previous.gaming, gamingNextToken)
                        } else {
                            runCatching { api.mostPopularPage(token, "20") }
                                .getOrDefault(VideoPage(previous.gaming))
                        }
                    }
                    val musicDeferred = async(start = CoroutineStart.LAZY) {
                        if (fastRefreshedCategory == HomeCategory.MUSIC) {
                            VideoPage(previous.music, musicNextToken)
                        } else {
                            runCatching { api.musicVideosPage(token) }
                                .getOrDefault(VideoPage(previous.music))
                        }
                    }
                    val shortsDeferred = async { VideoPage(previous.shorts) }
                    val subscriptionsDeferred = async {
                        val pages = if (initialLoad) 1 else 2
                        runCatching { api.subscriptions(token, maxPages = pages) }
                            .getOrDefault(previous.subscriptions)
                    }
                    val playlistsDeferred = async(start = CoroutineStart.LAZY) {
                        runCatching { api.playlists(token) }.getOrDefault(previous.playlists)
                    }
                    val activitiesDeferred = async(start = CoroutineStart.LAZY) {
                        if (previous.notificationsEnabled) {
                            runCatching { api.homeActivities(token) }.getOrDefault(previous.notifications)
                        } else {
                            emptyList()
                        }
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

                    val uploadsPageDeferred = async {
                        runCatching {
                            api.playlistVideosPage(token, uploadsPlaylistId, maxResults = 25)
                        }.getOrDefault(VideoPage(previous.uploads))
                    }
                    val likedPageDeferred = async {
                        runCatching {
                            api.playlistVideosPage(token, likesPlaylistId, maxResults = 50)
                        }.getOrDefault(VideoPage(previous.liked))
                    }
                    var subscriptions = subscriptionsDeferred.await()
                    val uploadsPage = uploadsPageDeferred.await()
                    val likedPage = likedPageDeferred.await()
                    val likedRaw = likedPage.items
                    val rankedSubscriptions = rankSubscriptionsForHome(
                        subscriptions,
                        previous.copy(subscriptions = subscriptions, liked = likedRaw)
                    )
                    subscriptionOffset = minOf(INITIAL_SUBSCRIPTION_BATCH, rankedSubscriptions.size)
                    val subscriptionWindow = if (rankedSubscriptions.isEmpty()) {
                        emptyList()
                    } else if (initialLoad || rankedSubscriptions.size <= INITIAL_SUBSCRIPTION_BATCH) {
                        rankedSubscriptions.take(INITIAL_SUBSCRIPTION_BATCH).also {
                            subscriptionRefreshCursor = it.size % rankedSubscriptions.size
                        }
                    } else {
                        val start = subscriptionRefreshCursor % rankedSubscriptions.size
                        List(minOf(INITIAL_SUBSCRIPTION_BATCH, rankedSubscriptions.size)) { index ->
                            rankedSubscriptions[(start + index) % rankedSubscriptions.size]
                        }.also {
                            subscriptionRefreshCursor = (start + it.size) % rankedSubscriptions.size
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

                    // Principal se alimenta únicamente con publicaciones de canales
                    // suscritos. Me gusta e historial sirven para ordenar intereses, pero nunca
                    // se insertan directamente como sustituto del feed de la cuenta.
                    val earlyAccountRaw = subscriptionFeedRaw.take(110)
                    val accountProfile = channelDetails?.profile ?: baseProfile
                    // Perfil, suscripciones, Me gusta y subidos ya están sincronizados. Publicarlos
                    // antes de enriquecer tarjetas para que Cuenta/Colección no esperen al feed.
                    _uiState.update { current ->
                        current.copy(
                            profile = accountProfile,
                            subscriptions = subscriptions,
                            liked = likedRaw,
                            uploads = uploadsPage.items
                        )
                    }
                    val earlyAccountEnriched = enrichVideosWithCache(token, earlyAccountRaw)
                    val earlyShortsRaw = earlyAccountEnriched.filter(::looksLikeShort)
                    val earlyVideos = earlyAccountEnriched.filter(::isNormalHomeVideo)
                    val earlyPersonalized = mergeUniqueVideos(
                        earlyVideos,
                        if (initialLoad) emptyList() else previous.personalized.filter(::isNormalHomeVideo)
                    ).take(MAX_HOME_ITEMS)
                    val earlyShorts = if (initialLoad) {
                        prioritizeShortsForUser(
                            mergeUniqueVideos(earlyShortsRaw, previous.shorts),
                            previous.copy(subscriptions = subscriptions, liked = likedRaw),
                            40
                        )
                    } else {
                        // El refresh rápido ya rotó la fila visible. La sincronización completa
                        // solo agrega candidatos nuevos detrás; no vuelve a colocar los mismos
                        // Shorts al inicio segundos después del gesto.
                        mergeUniqueVideos(previous.shorts, earlyShortsRaw).take(40)
                    }
                    _uiState.update { current ->
                        current.copy(
                            loading = initialLoad && earlyShorts.isEmpty(),
                            profile = accountProfile,
                            subscriptions = subscriptions,
                            liked = likedRaw,
                            uploads = uploadsPage.items,
                            personalized = if (youtubeHomeActive && current.personalized.isNotEmpty()) {
                                current.personalized
                            } else if (earlyPersonalized.isNotEmpty()) {
                                earlyPersonalized
                            } else {
                                current.personalized.filter(::isNormalHomeVideo)
                            },
                            shorts = if (earlyShorts.isNotEmpty()) earlyShorts else current.shorts,
                            canLoadMoreForYou = if (youtubeHomeActive && current.personalized.isNotEmpty()) {
                                youtubeHomeContinuation.isNotBlank()
                            } else {
                                subscriptionOffset < subscriptions.size
                            },
                            message = null
                        )
                    }

                    // Si los canales recientes no aportaron suficientes Shorts, completar la fila
                    // con UNA sola búsqueda basada en señales de esta cuenta. La pantalla ya está
                    // visible, así que esta búsqueda nunca bloquea el primer feed.
                    shortsQuery = buildShortsQuery(
                        history = previous.history,
                        liked = likedRaw,
                        subscriptions = subscriptions,
                        searchHistory = previous.searchHistory
                    )
                    if (_uiState.value.shorts.size < 10 && shortsQuery.isNotBlank()) {
                        val quickPage = runCatching {
                            loadShortsSearchPage(token, shortsQuery, maxResults = 18).first
                        }.getOrDefault(VideoPage(emptyList()))
                        val quickCandidates = enrichVideosWithCache(token, quickPage.items)
                            .filter(::looksLikeShort)
                            .filter(::isAcceptableShort)
                        val quickShorts = prioritizeShortsForUser(
                            mergeUniqueVideos(earlyShorts, quickCandidates),
                            previous.copy(subscriptions = subscriptions, liked = likedRaw),
                            40
                        )
                        if (quickShorts.isNotEmpty()) {
                            _uiState.update { current -> current.copy(shorts = quickShorts) }
                        }
                        shortsNextToken = quickPage.nextPageToken
                    }
                    // Con Shorts cargados o con el intento rápido ya completado, retirar el
                    // skeleton superior. Los datos secundarios siguen sincronizando aparte.
                    _uiState.update { current -> current.copy(loading = false) }

                    // Principal ya está visible. Completar primero los datos de la CUENTA
                    // (listas) y publicarlos enseguida; Tendencias/En vivo/Juegos/Música quedan
                    // realmente en segundo plano y no retrasan la sincronización visible.
                    playlistsDeferred.start()
                    activitiesDeferred.start()
                    val accountPlaylists = playlistsDeferred.await()
                    _uiState.update { current ->
                        current.copy(playlists = accountPlaylists)
                    }

                    // Ya están visibles Principal y los datos básicos de Cuenta. Recién ahora
                    // ampliar la lista de suscripciones completa, sin retrasar ninguna pantalla.
                    if (subscriptions.size >= 50) {
                        val expandedSubscriptions = runCatching {
                            api.subscriptions(token, maxPages = 8)
                        }.getOrDefault(subscriptions)
                        if (expandedSubscriptions.size > subscriptions.size) {
                            subscriptions = expandedSubscriptions
                            _uiState.update { current ->
                                current.copy(
                                    subscriptions = expandedSubscriptions,
                                    canLoadMoreForYou = subscriptionOffset < expandedSubscriptions.size
                                )
                            }
                        }
                    }

                    popularDeferred.start()
                    liveDeferred.start()
                    gamingDeferred.start()
                    musicDeferred.start()

                    val popularPage = popularDeferred.await()
                    val livePage = liveDeferred.await()
                    val gamingPage = gamingDeferred.await()
                    val musicPage = musicDeferred.await()
                    val (loadedShortsPage, loadedShortsQuery) = if (_uiState.value.shorts.size >= 10) {
                        VideoPage(emptyList(), shortsNextToken) to shortsQuery
                    } else {
                        runCatching {
                            loadShortsSearchPage(token, shortsQuery, maxResults = 24)
                        }.getOrDefault(shortsDeferred.await() to shortsQuery)
                    }
                    val shortsPage = loadedShortsPage
                    shortsQuery = loadedShortsQuery
                    val shortsRaw = shortsPage.items.distinctBy { it.id }.take(50)
                    val notificationsRaw = activitiesDeferred.await()
                    val activityVideosRaw = notificationsRaw.mapNotNull { it.video }
                    // Shorts de Principal se construye primero con señales reales de la cuenta.
                    // Nada de tendencias genéricas ni búsquedas por temas aleatorios delante de
                    // los canales que el usuario realmente sigue.
                    val personalShortsRaw = mergeUniqueVideos(
                        subscriptionFeedRaw.take(60),
                        likedRaw.take(24),
                        previous.localLikedVideos.take(20),
                        previous.history.take(24),
                        _uiState.value.shorts.take(30)
                    ).take(120)

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
                    val computedShorts = prioritizeShortsForUser(
                        buildDiverseShortFeed(
                            discovered = discoveredShorts,
                            personalized = personalizedShorts,
                            fallback = discoveredShortCandidates.filter(::looksLikeStrongShort) +
                                personalShortCandidates.filter(::looksLikeStrongShort),
                            limit = 48
                        ),
                        previous.copy(subscriptions = subscriptions, liked = likedRaw),
                        40
                    )
                    val shorts = if (initialLoad) {
                        computedShorts
                    } else {
                        mergeUniqueVideos(previous.shorts, computedShorts).take(40)
                    }
                    val liked = enriched(likedRaw)
                    val subscriptionFeed = enriched(subscriptionFeedRaw)
                    val normalSubscriptionFeed = subscriptionFeed.filter(::isNormalHomeVideo)
                    val uploads = enriched(uploadsPage.items)
                    val notifications = notificationsRaw.map { item ->
                        item.copy(video = item.video?.let { enrichedById[it.id] ?: it })
                    }

                    val personalizedBase = mergeUniqueVideos(
                        normalSubscriptionFeed,
                        if (initialLoad) emptyList() else previous.personalized.filter(::isNormalHomeVideo)
                    ).take(MAX_HOME_ITEMS)
                    val previousPersonalizedIds = previous.personalized.asSequence()
                        .map { it.id }
                        .filter { it.isNotBlank() }
                        .toHashSet()
                    val newPersonalized = personalizedBase.filter { it.id !in previousPersonalizedIds }
                    val personalized = if (initialLoad) {
                        personalizedBase
                    } else {
                        mergeUniqueVideos(newPersonalized, personalizedBase, previous.personalized.filter(::isNormalHomeVideo))
                            .take(MAX_HOME_ITEMS)
                    }

                    popularNextToken = popularPage.nextPageToken
                    liveNextToken = livePage.nextPageToken
                    gamingNextToken = gamingPage.nextPageToken
                    musicNextToken = musicPage.nextPageToken
                    shortsNextToken = shortsPage.nextPageToken
                    uploadsNextToken = uploadsPage.nextPageToken
                    likedNextToken = likedPage.nextPageToken

                    // El mensaje de refresh cuenta solamente la categoría que el usuario ve.
                    // Antes sumaba En vivo/Juegos/Música/Shorts aunque Principal no cambiara.
                    val visibleBefore = when (previous.homeCategory) {
                        HomeCategory.FOR_YOU -> mergeUniqueVideos(previous.shorts, previous.personalized)
                        HomeCategory.LIVE -> previous.live
                        HomeCategory.GAMING -> previous.gaming
                        HomeCategory.MUSIC -> previous.music
                    }.asSequence().map { it.id }.filter { it.isNotBlank() }.toHashSet()
                    val visibleAfter = when (previous.homeCategory) {
                        HomeCategory.FOR_YOU -> mergeUniqueVideos(shorts, personalized)
                        HomeCategory.LIVE -> live
                        HomeCategory.GAMING -> gaming
                        HomeCategory.MUSIC -> music
                    }
                    val newContentCount = visibleAfter.count { it.id !in visibleBefore }

                    val profile = accountProfile
                    val playlists = accountPlaylists
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
                            canLoadMoreForYou = if (youtubeHomeActive && it.personalized.isNotEmpty()) {
                                youtubeHomeContinuation.isNotBlank()
                            } else {
                                subscriptionOffset < subscriptions.size
                            },
                            canLoadMoreLive = liveNextToken.isNotBlank(),
                            canLoadMoreGaming = gamingNextToken.isNotBlank(),
                            canLoadMoreMusic = musicNextToken.isNotBlank(),
                            shortsCanLoadMore = shortsNextToken.isNotBlank(),
                            uploadsCanLoadMore = uploadsNextToken.isNotBlank(),
                            likedLoadingMore = false,
                            likedCanLoadMore = likedNextToken.isNotBlank(),
                            profile = profile,
                            personalized = if (youtubeHomeActive && it.personalized.isNotEmpty()) {
                                it.personalized
                            } else {
                                withChannelAvatars(personalized, subscriptions)
                            },
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
                if (youtubeSession.hasStoredSession() && (!initialLoad || !youtubeHomeActive)) {
                    refreshYouTubeSession(force = !initialLoad)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
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
                    HomeCategory.FOR_YOU -> if (!loadMoreYouTubeHome()) loadMorePersonalized(token)
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expired: YouTubeSessionExpiredException) {
                handleYouTubeSessionExpired(expired)
                _uiState.update { it.copy(loadingMoreCategory = null) }
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
        // Con sesión: el feed real de Shorts de YouTube (como la app oficial).
        if (youtubeSession.hasStoredSession() && _uiState.value.shorts.isNotEmpty()) {
            loadYouTubeShortsSequence(reset = true)
            return
        }
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
                val requestedPageToken = if (force) shortsNextToken else ""
                val searchDeferred = async {
                    loadShortsSearchPage(
                        token = token,
                        preferredQuery = shortsQuery,
                        maxResults = 30,
                        pageToken = requestedPageToken
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
                    val merged = when {
                        usable.isEmpty() -> current.shorts
                        force -> rotateShortsForRefresh(
                            fresh = usable,
                            existing = current.shorts,
                            state = current,
                            limit = MAX_HOME_ITEMS
                        )
                        else -> usable
                    }
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
        if (shortsSequenceActive && youtubeSession.hasStoredSession()) {
            loadYouTubeShortsSequence(reset = false)
            return
        }
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
        val subscriptions = rankSubscriptionsForHome(current.subscriptions, current)
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

        // "Para ti" pagina únicamente sobre las suscripciones de la cuenta. No rellenar
        // el final con Tendencias porque eso vuelve a introducir videos que el usuario no sigue.
        val enriched = enrichVideosWithCache(token, subscriptionMore)
        val normalVideos = enriched.filter(::isNormalHomeVideo)
        val moreShorts = enriched.filter(::looksLikeShort)
        val appended = mergeUniqueVideos(current.personalized, normalVideos).take(MAX_HOME_ITEMS)
        val shorts = prioritizeShortsForUser(
            mergeUniqueVideos(current.shorts, moreShorts),
            current,
            40
        )
        val canContinue = subscriptionOffset < subscriptions.size

        _uiState.update {
            it.copy(
                personalized = appended,
                shorts = shorts,
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

        if (section == MainSection.SUBSCRIPTIONS) loadSubscriptionFeed()
        val leavingShorts = current.section == MainSection.SHORTS
        _uiState.update {
            it.copy(
                section = section,
                selectedVideo = if (leavingShorts) null else it.selectedVideo,
                playerExpanded = if (leavingShorts) false else it.playerExpanded,
                selectedChannelTitle = "",
                selectedChannel = null,
                channelVideos = emptyList(),
                channelPlaylists = emptyList(),
                selectedPlaylist = null,
                selectedPlaylistVideos = emptyList(),
                playlistLoading = false
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
        // Never decode/write the full history on the UI thread when the user taps a video.
        // Use the already-hydrated state immediately so the player overlay opens in the same
        // frame; PlaybackProgressSaver persists it asynchronously after selection.
        val stateNow = _uiState.value
        val saved = stateNow.history.firstOrNull { it.id == video.id }
        val playable = video.copy(
            resumePositionMs = saved?.resumePositionMs ?: video.resumePositionMs,
            durationMs = saved?.durationMs ?: video.durationMs
        )
        val watched = playable.copy(watchedAtMs = System.currentTimeMillis())
        val optimisticHistory = (listOf(watched) + stateNow.history.filterNot { it.id == playable.id })
            .take(MAX_HISTORY_ITEMS_IN_MEMORY)
        relatedNextToken = ""
        relatedVideoId = playable.id
        _uiState.update {
            val keepsQueue = it.queue.any { item -> item.id == playable.id } ||
                (playable.isMix && playable.mixPlaylistId.isNotBlank() && playable.mixPlaylistId == it.queuePlaylistId)
            it.copy(
                queue = if (keepsQueue) it.queue else emptyList(),
                queueTitle = if (keepsQueue) it.queueTitle else "",
                queueSubtitle = if (keepsQueue) it.queueSubtitle else "",
                queuePlaylistId = if (keepsQueue) it.queuePlaylistId else "",
                queueOrder = if (keepsQueue) it.queueOrder else emptyList(),
                queueShuffle = if (keepsQueue) it.queueShuffle else false,
                queueOpen = if (keepsQueue) it.queueOpen else false,
                selectedVideo = playable,
                playerExpanded = true,
                history = optimisticHistory,
                playerDetails = null,
                playerDetailsLoading = true,
                relatedVideos = emptyList(),
                relatedLoading = true,
                relatedLoadingMore = false,
                relatedCanLoadMore = true
            )
        }
        // Guardar la entrada de historial desde el primer toque. Así Colección no depende
        // de que el reproductor alcance un checkpoint posterior para mostrar la reproducción.
        viewModelScope.launch(Dispatchers.IO) { repository.addToHistory(watched) }
        trackYouTubeWatch(playable)
        loadPlayerContext(playable)
    }

    fun previewShort(video: VideoItem) {
        enrichShortsAround(video.id)
        val playable = video.copy(resumePositionMs = 0L)
        val stateNow = _uiState.value
        val watched = playable.copy(watchedAtMs = System.currentTimeMillis())
        val optimisticHistory = (listOf(watched) + stateNow.history.filterNot { it.id == playable.id })
            .take(MAX_HISTORY_ITEMS_IN_MEMORY)
        relatedNextToken = ""
        relatedVideoId = ""
        _uiState.update {
            it.copy(
                selectedVideo = playable,
                playerExpanded = false,
                section = MainSection.SHORTS,
                history = optimisticHistory,
                playerDetails = null,
                playerDetailsLoading = false,
                relatedVideos = emptyList(),
                relatedLoading = false,
                relatedLoadingMore = false,
                relatedCanLoadMore = false
            )
        }
        viewModelScope.launch(Dispatchers.IO) { repository.addToHistory(watched) }
        trackYouTubeWatch(playable)
    }

    fun openShortDetails(video: VideoItem) {
        _uiState.update { it.copy(section = MainSection.HOME) }
        openPlayer(video)
    }

    fun playNext() {
        val state = _uiState.value
        val current = state.selectedVideo ?: return
        if (state.queueActive && state.queueIndex >= 0) {
            val queued = state.upcomingQueue(1).firstOrNull()
            if (queued != null) {
                openPlayer(queued)
                maybeLoadMoreQueue()
                return
            }
        }
        // En un Mix se sigue el orden de la lista; en un video normal, el primer relacionado.
        val index = state.relatedVideos.indexOfFirst { it.id == current.id }
        val next = (if (index >= 0) state.relatedVideos.getOrNull(index + 1) else null)
            ?: state.relatedVideos.firstOrNull { it.id != current.id }
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
        val safePosition = positionMs.coerceAtLeast(0L)
        _uiState.update { state ->
            val previous = state.history.firstOrNull { it.id == video.id }
            val safeDuration = when {
                durationMs > 0L -> durationMs
                video.durationMs > 0L -> video.durationMs
                else -> previous?.durationMs ?: 0L
            }
            val normalizedPosition = if (safeDuration > 0L && safePosition >= safeDuration - 8_000L) 0L else safePosition
            val updated = video.copy(
                resumePositionMs = normalizedPosition,
                durationMs = safeDuration,
                watchedAtMs = System.currentTimeMillis()
            )
            state.copy(
                history = (listOf(updated) + state.history.filterNot { it.id == video.id })
                    .take(MAX_HISTORY_ITEMS_IN_MEMORY),
                selectedVideo = state.selectedVideo?.takeIf { it.id == video.id }?.copy(
                    resumePositionMs = normalizedPosition,
                    durationMs = safeDuration
                ) ?: state.selectedVideo
            )
        }
        // JSON serialization and SharedPreferences I/O stay off the main thread so progress
        // checkpoints cannot stall playback or scrolling.
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePlayback(video, safePosition, durationMs)
        }
        reportYouTubeProgress(video, safePosition, durationMs)
    }

    fun closePlayer() {
        relatedNextToken = ""
        relatedVideoId = ""
        _uiState.update {
            it.copy(
                queue = emptyList(),
                queueTitle = "",
                queueSubtitle = "",
                queuePlaylistId = "",
                queueOrder = emptyList(),
                queueShuffle = false,
                queueOpen = false,
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
                relatedCanLoadMore = true,
                relatedChips = emptyList(),
                selectedRelatedChip = ""
            )
        }
        viewModelScope.launch {
            // Give stream extraction a short head start. Details/comments/related content are
            // useful below the player but must never compete with the first playable frame.
            delay(450L)
            if (_uiState.value.selectedVideo?.id != video.id || relatedVideoId != video.id) return@launch
            supervisorScope {
                val detailsDeferred = async { runCatching { api.videoDetails(token, video) }.getOrNull() }
                val isRealMix = video.isMix && video.mixPlaylistId.isNotBlank()
                // Mix real de YouTube: su lista va al panel de cola; abajo siguen los relacionados.
                val mixDeferred = async {
                    if (isRealMix) runCatching { api.mixPanel(token, video) }.getOrNull() else null
                }
                val relatedDeferred = async {
                    runCatching { api.relatedVideosPage(token, video, pageToken = "") }.getOrNull()
                }
                val details = detailsDeferred.await()
                val mixPanel = mixDeferred.await()
                val relatedPage = relatedDeferred.await()
                if (_uiState.value.selectedVideo?.id != video.id || relatedVideoId != video.id) return@supervisorScope

                relatedNextToken = relatedPage?.nextPageToken.orEmpty()
                val mixItems = mixPanel?.videos.orEmpty()
                if (isRealMix && mixItems.isNotEmpty()) mergeIntoQueue(video.mixPlaylistId, mixPanel!!)
                val related = run {
                    filterRelatedCandidates(
                        current = video,
                        candidates = mergeUniqueVideos(
                            relatedPage?.items.orEmpty().filterNot { it.id == video.id },
                            fallback
                        )
                    ).take(24)
                }
                _uiState.update {
                    if (it.selectedVideo?.id != video.id) it else it.copy(
                        playerDetails = details ?: VideoDetails(
                            videoId = video.id,
                            channelThumbnailUrl = video.channelThumbnailUrl,
                            publishedAt = video.publishedAt,
                            description = video.description
                        ),
                        playerDetailsLoading = false,
                        // La tarjeta de un Mix trae el título del Mix; mostrar el del video real.
                        selectedVideo = if (isRealMix) {
                            val item = mixItems.firstOrNull { candidate -> candidate.id == video.id }
                            val selected = it.selectedVideo
                            if (item != null && selected != null) {
                                selected.copy(
                                    title = item.title,
                                    channelTitle = item.channelTitle,
                                    channelId = item.channelId.ifBlank { selected.channelId },
                                    durationMs = maxOf(item.durationMs, selected.durationMs)
                                )
                            } else {
                                selected
                            }
                        } else {
                            it.selectedVideo
                        },
                        relatedVideos = related,
                        relatedLoading = false,
                        relatedCanLoadMore = relatedNextToken.isNotBlank(),
                        relatedChips = relatedPage?.chips.orEmpty(),
                        selectedRelatedChip = relatedPage?.chips?.firstOrNull()?.token.orEmpty()
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
                    ).take(MAX_YOUTUBE_ITEMS),
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
        if (_uiState.value.youtubeSessionConnected && video.mediaKind == MediaKind.YOUTUBE) {
            toggleYouTubeWatchLater(video)
            return
        }
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
                if (!currentlySubscribed) {
                    val bell = runCatching { api.channelBell(token, channel.id) }.getOrNull()
                    if (bell != null) {
                        _uiState.update { state ->
                            fun ChannelItem.withBell() = if (id == channel.id) copy(bellState = bell.first, bellParams = bell.second) else this
                            state.copy(
                                subscriptions = state.subscriptions.map { it.withBell() },
                                selectedChannel = state.selectedChannel?.withBell()
                            )
                        }
                    }
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

    fun search(query: String) = runSearch(query, filterIndex = 0)

    /** Cambia el filtro (Videos, Shorts, En vivo, Canales, Listas, Hoy, Esta semana, Más vistos). */
    fun setSearchFilter(index: Int) {
        if (lastSearchQuery.isBlank() || index == _uiState.value.searchFilter) return
        runSearch(lastSearchQuery, index)
    }

    private fun runSearch(query: String, filterIndex: Int) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val token = accessToken ?: if (_uiState.value.youtubeSessionConnected) YouTubeApi.SESSION_TOKEN else null
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(message = "Inicia sesión con YouTube para buscar.") }
            return
        }
        val history = repository.addSearch(clean)
        lastSearchQuery = clean
        searchNextToken = ""
        suggestionsJob?.cancel()
        searchJob?.cancel()
        val params = SEARCH_FILTERS.getOrNull(filterIndex)?.second.orEmpty()
        _uiState.update {
            it.copy(
                loading = true,
                searchHistory = history,
                searchQuery = clean,
                searchFilter = filterIndex,
                searchSuggestions = emptyList(),
                searchEntries = emptyList(),
                searchCorrected = "",
                searchResults = emptyList(),
                searchChannels = emptyList(),
                searchPlaylists = emptyList(),
                searchLoadingMore = false,
                searchCanLoadMore = false
            )
        }
        searchJob = viewModelScope.launch {
            try {
                val results = api.searchResults(token, clean, params)
                searchNextToken = results.continuation
                _uiState.update {
                    if (it.searchQuery != clean || it.searchFilter != filterIndex) it else it.copy(
                        loading = false,
                        searchEntries = results.entries,
                        searchCorrected = results.correctedQuery,
                        searchResults = results.page.videos + results.page.shorts,
                        searchChannels = results.page.channels,
                        searchPlaylists = results.page.playlists,
                        searchLoadingMore = false,
                        searchCanLoadMore = results.continuation.isNotBlank(),
                        section = MainSection.SEARCH
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: YouTubeApiException) {
                handleApiError(error)
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, message = "No se pudo buscar en YouTube.") }
            }
        }
    }

    fun loadMoreSearch() {
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        val state = _uiState.value
        if (state.searchLoadingMore || state.loading || lastSearchQuery.isBlank() || searchNextToken.isBlank()) return
        val query = lastSearchQuery
        val filter = state.searchFilter
        val pageToken = searchNextToken
        _uiState.update { it.copy(searchLoadingMore = true) }
        viewModelScope.launch {
            try {
                val results = api.searchResults(token, query, "", continuation = pageToken)
                searchNextToken = results.continuation
                _uiState.update {
                    if (it.searchQuery != query || it.searchFilter != filter) it else {
                        val known = it.searchEntries.mapTo(HashSet()) { entry -> searchEntryKey(entry) }
                        it.copy(
                            searchEntries = it.searchEntries + results.entries.filter { entry -> searchEntryKey(entry) !in known },
                            searchResults = mergeUniqueVideos(it.searchResults, results.page.videos + results.page.shorts),
                            searchChannels = (it.searchChannels + results.page.channels).distinctBy { channel -> channel.id },
                            searchPlaylists = (it.searchPlaylists + results.page.playlists).distinctBy { playlist -> playlist.id },
                            searchLoadingMore = false,
                            searchCanLoadMore = results.continuation.isNotBlank()
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(searchLoadingMore = false, message = "No se cargaron más resultados de YouTube.") }
            }
        }
    }

    private fun searchEntryKey(entry: SearchEntry): String = when (entry) {
        is SearchEntry.Video -> "v:" + entry.video.id
        is SearchEntry.Channel -> "c:" + entry.channel.id
        is SearchEntry.Playlist -> "p:" + entry.playlist.id
        is SearchEntry.Shorts -> "s:" + entry.shorts.firstOrNull()?.id.orEmpty()
        is SearchEntry.Shelf -> "h:" + entry.title + entry.videos.firstOrNull()?.id.orEmpty()
    }

    /** Sugerencias mientras se escribe (como el cuadro de búsqueda de YouTube). */
    fun updateSearchSuggestions(query: String) {
        suggestionsJob?.cancel()
        val clean = query.trim()
        if (clean.isBlank()) {
            _uiState.update { it.copy(searchSuggestions = emptyList()) }
            return
        }
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        suggestionsJob = viewModelScope.launch {
            delay(180L)
            val list = runCatching { api.searchSuggestions(token, clean) }.getOrNull() ?: return@launch
            _uiState.update { it.copy(searchSuggestions = list) }
        }
    }

    fun removeSearchHistory(query: String) {
        val history = repository.removeSearch(query)
        _uiState.update { it.copy(searchHistory = history) }
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        lastSearchQuery = ""
        searchNextToken = ""
        _uiState.update {
            it.copy(
                loading = false,
                searchQuery = "",
                searchEntries = emptyList(),
                searchResults = emptyList(),
                searchChannels = emptyList(),
                searchPlaylists = emptyList(),
                searchCorrected = "",
                searchFilter = 0,
                searchCanLoadMore = false
            )
        }
    }

    // ------------------------------------------------------------ Notificaciones

    fun refreshUnseenNotifications() {
        if (!youtubeSession.hasStoredSession()) return
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        viewModelScope.launch {
            val count = runCatching { api.unseenNotifications(token) }.getOrNull() ?: return@launch
            _uiState.update { it.copy(notificationsUnseen = count) }
        }
    }

    /** Al abrir la campana: bandeja nueva y el número rojo vuelve a cero. */
    fun refreshNotifications() {
        if (!youtubeSession.hasStoredSession() || _uiState.value.notificationsLoading) return
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        _uiState.update { it.copy(notificationsLoading = true, notificationsUnseen = 0) }
        viewModelScope.launch {
            val page = runCatching { api.notificationsPage(token) }.getOrNull()
            notificationsContinuation = page?.continuation.orEmpty()
            _uiState.update {
                it.copy(
                    notifications = page?.items ?: it.notifications,
                    notificationsLoading = false,
                    notificationsCanLoadMore = notificationsContinuation.isNotBlank()
                )
            }
        }
    }

    fun loadMoreNotifications() {
        val state = _uiState.value
        if (state.notificationsLoading || state.notificationsLoadingMore || notificationsContinuation.isBlank()) return
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        val pageToken = notificationsContinuation
        _uiState.update { it.copy(notificationsLoadingMore = true) }
        viewModelScope.launch {
            val page = runCatching { api.notificationsPage(token, pageToken) }.getOrNull()
            notificationsContinuation = page?.continuation?.takeIf { it != pageToken }.orEmpty()
            _uiState.update {
                it.copy(
                    notifications = (it.notifications + page?.items.orEmpty()).distinctBy { item -> item.id },
                    notificationsLoadingMore = false,
                    notificationsCanLoadMore = notificationsContinuation.isNotBlank()
                )
            }
        }
    }

    fun openPlaylist(playlist: PlaylistItem) {
        val token = accessToken
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(message = "Renueva el acceso de Google para abrir esta lista de YouTube.") }
            return
        }
        _uiState.update {
            it.copy(
                selectedPlaylist = playlist,
                selectedPlaylistVideos = emptyList(),
                playlistLoading = true
            )
        }
        viewModelScope.launch {
            try {
                val page = api.playlistVideosPage(token, playlist.id, maxResults = 50)
                val videos = enrichVideosWithCache(token, page.items)
                _uiState.update {
                    it.copy(
                        selectedPlaylist = playlist,
                        selectedPlaylistVideos = videos,
                        playlistLoading = false
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        selectedPlaylist = null,
                        selectedPlaylistVideos = emptyList(),
                        playlistLoading = false,
                        message = "No se pudo abrir la lista de YouTube."
                    )
                }
            }
        }
    }

    fun closePlaylist() {
        _uiState.update {
            it.copy(
                selectedPlaylist = null,
                selectedPlaylistVideos = emptyList(),
                playlistLoading = false
            )
        }
    }

    fun openChannel(channel: ChannelItem) {
        val token = accessToken ?: if (_uiState.value.youtubeSessionConnected) YouTubeApi.SESSION_TOKEN else null
        if (token.isNullOrBlank()) {
            _uiState.update { it.copy(message = "Inicia sesión con YouTube para abrir el canal.") }
            return
        }
        val known = _uiState.value.subscriptions.firstOrNull { it.id == channel.id }
        val subscribed = known != null
        val initialChannel = channel.copy(
            isSubscribed = subscribed,
            bellState = if (channel.bellState >= 0) channel.bellState else known?.bellState ?: -1,
            bellParams = channel.bellParams.ifEmpty { known?.bellParams.orEmpty() }
        )
        channelTabJob?.cancel()
        channelTabContinuation = ""
        _uiState.update {
            it.copy(
                loading = true,
                selectedChannelTitle = channel.title,
                selectedChannel = initialChannel,
                channelVideos = emptyList(),
                channelPlaylists = emptyList(),
                channelTabs = emptyList(),
                channelShelves = emptyList(),
                channelFeatured = null,
                channelTabIndex = 0,
                channelTabVideos = emptyList(),
                channelTabShorts = emptyList(),
                channelTabPlaylists = emptyList(),
                channelTabPosts = emptyList(),
                channelTabChips = emptyList(),
                channelTabSelectedChip = "",
                channelTabLoading = false,
                channelTabCanLoadMore = false
            )
        }
        viewModelScope.launch {
            try {
                val page = api.channelPage(token, channel.id)
                val info = page.channel.let { loaded ->
                    loaded.copy(
                        // La página dice si está suscrito; si no lo dice, usar la lista local.
                        isSubscribed = loaded.isSubscribed || subscribed,
                        thumbnailUrl = loaded.thumbnailUrl.ifBlank { initialChannel.thumbnailUrl },
                        bellState = if (loaded.bellState >= 0) loaded.bellState else initialChannel.bellState,
                        bellParams = loaded.bellParams.ifEmpty { initialChannel.bellParams }
                    )
                }
                val tabs = page.tabs.ifEmpty { defaultChannelTabs() }
                val homeIndex = tabs.indexOfFirst { it.selected }.coerceAtLeast(0)
                val shelfVideos = page.shelves.flatMap { it.videos }.distinctBy { it.id }
                val shelfPlaylists = page.shelves.flatMap { it.playlists }.distinctBy { it.id }
                _uiState.update { current ->
                    if (current.selectedChannel?.id != channel.id) current else current.copy(
                        loading = false,
                        selectedChannelTitle = info.title,
                        selectedChannel = info,
                        channelTabs = tabs,
                        channelShelves = page.shelves,
                        channelFeatured = page.featured,
                        channelTabIndex = homeIndex,
                        channelVideos = shelfVideos,
                        channelPlaylists = shelfPlaylists
                    )
                }
                // Sin estanterías (canal sin Inicio): abrir directamente la pestaña Videos.
                if (page.shelves.isEmpty() && page.featured == null) {
                    val videosIndex = tabs.indexOfFirst { it.title.contains("Video", true) }
                    if (videosIndex >= 0) selectChannelTab(videosIndex)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expired: YouTubeSessionExpiredException) {
                handleYouTubeSessionExpired(expired)
            } catch (error: Exception) {
                _uiState.update { current ->
                    if (current.selectedChannel?.id != channel.id) current else current.copy(
                        loading = false,
                        channelTabs = defaultChannelTabs(),
                        message = "No se pudo abrir el canal completo."
                    )
                }
                selectChannelTab(1)
            }
        }
    }

    private fun defaultChannelTabs(): List<ChannelTabInfo> = listOf(
        ChannelTabInfo("Inicio", "", true),
        ChannelTabInfo("Videos", CHANNEL_VIDEOS_PARAMS, false),
        ChannelTabInfo("Shorts", CHANNEL_SHORTS_PARAMS, false),
        ChannelTabInfo("Listas", CHANNEL_PLAYLISTS_PARAMS, false)
    )

    /** Cambia de pestaña del canal (Inicio usa las estanterías ya cargadas). */
    fun selectChannelTab(index: Int) {
        val state = _uiState.value
        val channel = state.selectedChannel ?: return
        val tab = state.channelTabs.getOrNull(index) ?: return
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        channelTabJob?.cancel()
        channelTabContinuation = ""
        val isHome = tab.selected && (state.channelShelves.isNotEmpty() || state.channelFeatured != null)
        _uiState.update {
            it.copy(
                channelTabIndex = index,
                channelTabVideos = emptyList(),
                channelTabShorts = emptyList(),
                channelTabPlaylists = emptyList(),
                channelTabPosts = emptyList(),
                channelTabChips = emptyList(),
                channelTabSelectedChip = "",
                channelTabLoading = !isHome,
                channelTabCanLoadMore = false
            )
        }
        if (isHome) return
        channelTabJob = viewModelScope.launch {
            val page = runCatching { api.channelTab(token, channel.id, tab.params) }.getOrNull()
            applyChannelTabPage(channel.id, index, page, replace = true)
        }
    }

    /** Orden de la pestaña: "Más recientes", "Populares", "Más antiguos". */
    fun selectChannelChip(chip: HomeChip) {
        val state = _uiState.value
        val channel = state.selectedChannel ?: return
        if (chip.token == state.channelTabSelectedChip) return
        val index = state.channelTabIndex
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        channelTabJob?.cancel()
        _uiState.update { it.copy(channelTabSelectedChip = chip.token, channelTabLoading = true) }
        channelTabJob = viewModelScope.launch {
            val page = runCatching { api.channelTab(token, channel.id, "", continuation = chip.token) }.getOrNull()
            applyChannelTabPage(channel.id, index, page, replace = true, keepChips = true)
        }
    }

    fun loadMoreChannelTab() {
        val state = _uiState.value
        val channel = state.selectedChannel ?: return
        if (state.channelTabLoading || state.channelTabLoadingMore || channelTabContinuation.isBlank()) return
        val index = state.channelTabIndex
        val pageToken = channelTabContinuation
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        _uiState.update { it.copy(channelTabLoadingMore = true) }
        channelTabJob = viewModelScope.launch {
            val page = runCatching { api.channelTab(token, channel.id, "", continuation = pageToken) }.getOrNull()
            applyChannelTabPage(channel.id, index, page, replace = false, keepChips = true)
        }
    }

    private fun applyChannelTabPage(
        channelId: String,
        index: Int,
        page: com.geovideos.app.network.ChannelTabPage?,
        replace: Boolean,
        keepChips: Boolean = false
    ) {
        val state = _uiState.value
        if (state.selectedChannel?.id != channelId || state.channelTabIndex != index) return
        if (page == null) {
            _uiState.update {
                it.copy(
                    channelTabLoading = false,
                    channelTabLoadingMore = false,
                    channelTabCanLoadMore = false,
                    message = if (replace) "No se pudo cargar esta pestaña." else it.message
                )
            }
            return
        }
        channelTabContinuation = page.continuation
        _uiState.update {
            it.copy(
                channelTabVideos = if (replace) page.videos else mergeUniqueVideos(it.channelTabVideos, page.videos).take(MAX_YOUTUBE_ITEMS),
                channelTabShorts = if (replace) page.shorts else mergeUniqueVideos(it.channelTabShorts, page.shorts).take(MAX_YOUTUBE_ITEMS),
                channelTabPlaylists = if (replace) page.playlists else (it.channelTabPlaylists + page.playlists).distinctBy { p -> p.id },
                channelTabPosts = if (replace) page.posts else (it.channelTabPosts + page.posts).distinctBy { p -> p.id },
                channelTabChips = if (keepChips && it.channelTabChips.isNotEmpty()) it.channelTabChips else page.chips,
                channelTabSelectedChip = if (keepChips) it.channelTabSelectedChip else page.chips.firstOrNull()?.token.orEmpty(),
                channelTabLoading = false,
                channelTabLoadingMore = false,
                channelTabCanLoadMore = page.continuation.isNotBlank()
            )
        }
    }

    fun closeChannel() {
        channelTabJob?.cancel()
        _uiState.update {
            it.copy(
                selectedChannelTitle = "",
                selectedChannel = null,
                channelVideos = emptyList(),
                channelPlaylists = emptyList(),
                channelTabs = emptyList(),
                channelShelves = emptyList(),
                channelFeatured = null,
                channelTabVideos = emptyList(),
                channelTabShorts = emptyList(),
                channelTabPlaylists = emptyList(),
                channelTabPosts = emptyList(),
                channelTabChips = emptyList()
            )
        }
    }

    // ------------------------------------------------------------ Campana

    fun openBellSheet(channel: ChannelItem) {
        val state = _uiState.value
        val known = listOfNotNull(
            channel.takeIf { it.bellParams.isNotEmpty() },
            state.selectedChannel?.takeIf { it.id == channel.id && it.bellParams.isNotEmpty() },
            state.subscriptions.firstOrNull { it.id == channel.id && it.bellParams.isNotEmpty() }
        ).firstOrNull()
        _uiState.update { it.copy(bellSheetChannel = known ?: channel, bellSheetLoading = known == null) }
        if (known != null) return
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        viewModelScope.launch {
            val bell = runCatching { api.channelBell(token, channel.id) }.getOrNull()
            _uiState.update {
                val sheet = it.bellSheetChannel
                if (sheet?.id != channel.id) it else it.copy(
                    bellSheetChannel = if (bell == null) sheet else sheet.copy(bellState = bell.first, bellParams = bell.second),
                    bellSheetLoading = false
                )
            }
        }
    }

    fun closeBellSheet() {
        _uiState.update { it.copy(bellSheetChannel = null, bellSheetLoading = false) }
    }

    /** 0 = Todas, 1 = Personalizadas, 2 = Ninguna. */
    fun setBell(index: Int) {
        val channel = _uiState.value.bellSheetChannel ?: return
        val params = channel.bellParams.getOrNull(index).orEmpty()
        if (params.isBlank()) {
            _uiState.update { it.copy(message = "YouTube no dejó cambiar la campana de este canal ahora.") }
            return
        }
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        _uiState.update { it.copy(bellSheetChannel = null) }
        viewModelScope.launch {
            runCatching { api.setBell(token, params) }
                .onSuccess {
                    fun ChannelItem.withBell() = if (id == channel.id) copy(bellState = index) else this
                    _uiState.update { state ->
                        state.copy(
                            subscriptions = state.subscriptions.map { it.withBell() },
                            subsGuide = state.subsGuide.map { it.withBell() },
                            selectedChannel = state.selectedChannel?.withBell(),
                            message = when (index) {
                                0 -> "Recibirás todas las notificaciones de ${channel.title}"
                                1 -> "Notificaciones personalizadas de ${channel.title}"
                                else -> "No recibirás notificaciones de ${channel.title}"
                            }
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(message = "No se pudo cambiar la campana.") } }
        }
    }

    // ------------------------------------------------------------ Suscripciones: barra y filtro por canal

    private fun loadSubscriptionGuide() {
        if (!youtubeSession.hasStoredSession()) return
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        viewModelScope.launch {
            val guide = runCatching { api.subscriptionGuide(token) }.getOrNull().orEmpty()
            if (guide.isEmpty()) return@launch
            _uiState.update { state ->
                val byId = state.subscriptions.associateBy { it.id }
                state.copy(
                    subsGuide = guide.map { entry ->
                        val saved = byId[entry.id]
                        if (saved == null) entry else entry.copy(
                            thumbnailUrl = entry.thumbnailUrl.ifBlank { saved.thumbnailUrl },
                            bellState = saved.bellState,
                            bellParams = saved.bellParams,
                            subscriberCount = saved.subscriberCount,
                            handle = saved.handle
                        )
                    }
                )
            }
        }
    }

    /** Toca un canal de la barra: muestra solo ese canal; tocarlo otra vez vuelve a Todos. */
    fun selectSubsChannel(channel: ChannelItem?) {
        val state = _uiState.value
        if (channel == null || state.subsChannelFilter?.id == channel.id) {
            _uiState.update { it.copy(subsChannelFilter = null, subsChannelVideos = emptyList(), subsChannelShorts = emptyList(), subsChannelLoading = false) }
            return
        }
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        _uiState.update {
            it.copy(
                subsChannelFilter = channel,
                subsChannelLoading = true,
                subsChannelVideos = it.subsFeedVideos.filter { v -> v.channelId == channel.id },
                subsChannelShorts = emptyList(),
                // Ya lo viste: se apaga el punto azul.
                subsGuide = it.subsGuide.map { c -> if (c.id == channel.id) c.copy(hasNewContent = false) else c }
            )
        }
        viewModelScope.launch {
            supervisorScope {
                val videos = async { runCatching { api.channelTab(token, channel.id, CHANNEL_VIDEOS_PARAMS) }.getOrNull() }
                val shorts = async { runCatching { api.channelTab(token, channel.id, CHANNEL_SHORTS_PARAMS) }.getOrNull() }
                val videoPage = videos.await()
                val shortPage = shorts.await()
                _uiState.update {
                    if (it.subsChannelFilter?.id != channel.id) it else it.copy(
                        subsChannelVideos = videoPage?.videos?.map { v ->
                            v.copy(
                                channelTitle = v.channelTitle.ifBlank { channel.title },
                                channelThumbnailUrl = v.channelThumbnailUrl.ifBlank { channel.thumbnailUrl }
                            )
                        } ?: it.subsChannelVideos,
                        subsChannelShorts = shortPage?.shorts.orEmpty().ifEmpty { shortPage?.videos.orEmpty() },
                        subsChannelLoading = false
                    )
                }
            }
        }
    }

    fun removeHistory(videoId: String) {
        val remoteToken = _uiState.value.youtubeHistory.firstOrNull { it.id == videoId }?.notInterestedToken.orEmpty()
        val history = repository.removeFromHistory(videoId)
        _uiState.update {
            it.copy(
                history = history,
                youtubeHistory = it.youtubeHistory.filterNot { video -> video.id == videoId },
                message = if (remoteToken.isBlank()) "Video eliminado del historial." else "Quitando del historial de YouTube…"
            )
        }
        if (remoteToken.isNotBlank() && _uiState.value.youtubeSessionConnected) {
            val token = accessToken ?: YouTubeApi.SESSION_TOKEN
            viewModelScope.launch {
                val ok = runCatching { api.sendFeedback(token, remoteToken) }.isSuccess
                _uiState.update {
                    it.copy(message = if (ok) "Quitado del historial de YouTube." else "Se quitó aquí, pero YouTube no respondió.")
                }
            }
        }
    }

    fun onPlayerTransition(videoId: String) {
        if (videoId.isBlank() || _uiState.value.selectedVideo?.id == videoId) return
        val state = _uiState.value
        val video = sequenceOf(
            state.queue, state.relatedVideos, state.personalized, state.popular, state.music, state.gaming,
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
        trackYouTubeWatch(playable)
        loadPlayerContext(playable)
        maybeLoadMoreQueue()
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
            .take(4)
        return if (interests.isEmpty()) {
            // Sin señales reales de la cuenta no hacer una búsqueda genérica que llene
            // Principal con Shorts aleatorios.
            ""
        } else {
            "shorts ${interests.joinToString(" ")}"
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
            subscriptionVideos.take(30)
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

    private fun rankSubscriptionsForHome(
        subscriptions: List<ChannelItem>,
        state: GeoVideosUiState
    ): List<ChannelItem> {
        if (subscriptions.size <= 1) return subscriptions
        val scoreByChannel = HashMap<String, Int>()
        fun addSignals(videos: List<VideoItem>, weight: Int, limit: Int) {
            videos.asSequence().take(limit).forEach { video ->
                val id = video.channelId
                if (id.isNotBlank()) scoreByChannel[id] = (scoreByChannel[id] ?: 0) + weight
            }
        }
        addSignals(state.localLikedVideos, 7, 80)
        addSignals(state.liked, 5, 120)
        addSignals(state.history, 3, 120)

        val searchTerms = state.searchHistory.asSequence()
            .take(10)
            .flatMap { it.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).asSequence() }
            .filter { it.length >= 4 }
            .toSet()

        return subscriptions.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<ChannelItem>> { indexed ->
                    val channel = indexed.value
                    val title = channel.title.lowercase()
                    (scoreByChannel[channel.id] ?: 0) + searchTerms.count { it in title } * 2
                }.thenBy { it.index }
            )
            .map { it.value }
    }

    private fun isNormalHomeVideo(video: VideoItem): Boolean {
        if (video.title.isBlank() || video.thumbnailUrl.isBlank()) return false
        if (video.isLive) return true
        if (video.mediaKind != MediaKind.YOUTUBE) return !looksLikeShort(video)
        // La API de actividades no incluye duración. Principal solo publica un video de YouTube
        // cuando la duración ya fue comprobada y supera el máximo de Shorts. Es preferible
        // esperar una pasada de metadatos antes que volver a mostrar un Short vertical abajo.
        return video.durationMs > SHORT_MAX_DURATION_MS && !looksLikeShort(video)
    }

    private fun rotateShortsForRefresh(
        fresh: List<VideoItem>,
        existing: List<VideoItem>,
        state: GeoVideosUiState,
        limit: Int
    ): List<VideoItem> {
        val ranked = prioritizeShortsForUser(
            mergeUniqueVideos(fresh, existing).filter(::looksLikeShort),
            state,
            limit
        )
        if (ranked.size <= 1) return ranked

        val visibleIds = existing.take(HOME_SHORTS_SHELF_SIZE)
            .asSequence()
            .map { it.id }
            .filter { it.isNotBlank() }
            .toHashSet()
        val unseenFirst = ranked.filter { it.id !in visibleIds }
        if (unseenFirst.isNotEmpty()) {
            return mergeUniqueVideos(
                unseenFirst,
                ranked.filter { it.id in visibleIds }
            ).take(limit)
        }

        // Si YouTube devolvió exactamente el mismo pool, rotarlo de todos modos para que el
        // gesto de actualizar no deje visualmente la misma tanda en la fila superior.
        val shift = HOME_SHORTS_ROTATE_STEP.coerceAtMost(ranked.size - 1).coerceAtLeast(1)
        return (ranked.drop(shift) + ranked.take(shift)).take(limit)
    }

    private fun looksLikeShort(video: VideoItem): Boolean {
        val text = (video.title + " " + video.description).lowercase()
        val taggedAsShort = listOf(
            "#shorts", " shorts", "short ", "tiktok", "reel", "vertical", "status video"
        ).any { it in text }
        return taggedAsShort || video.durationMs in 1..SHORT_MAX_DURATION_MS
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
        maxResults: Int,
        pageToken: String = ""
    ): Pair<VideoPage, String> {
        val normalizedPreferred = preferredQuery
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalizedPreferred.isBlank()) {
            return VideoPage(emptyList()) to ""
        }

        // Una sola búsqueda derivada de intereses reales. La versión anterior lanzaba cuatro
        // búsquedas temáticas genéricas y terminaba metiendo humor/animales/deportes aunque la
        // cuenta no mostrara esas señales.
        val page = runCatching {
            api.searchVideosPage(
                token = token,
                query = normalizedPreferred,
                shortOnly = true,
                pageToken = pageToken,
                maxResults = maxResults.coerceIn(12, 30)
            )
        }.getOrDefault(VideoPage(emptyList()))
        return VideoPage(
            items = limitShortsPerChannel(page.items.filter(::isAcceptableShort), maxResults),
            nextPageToken = page.nextPageToken
        ) to normalizedPreferred
    }

    private fun buildDiverseShortFeed(
        discovered: List<VideoItem>,
        personalized: List<VideoItem>,
        fallback: List<VideoItem>,
        limit: Int
    ): List<VideoItem> {
        // Señales personales primero. El descubrimiento solo completa huecos y nunca ocupa
        // la primera posición por encima de Shorts de suscripciones/Me gusta/historial.
        val allDiscovered = mergeUniqueVideos(discovered, fallback)
        val weighted = roundRobinVideos(
            personalized,
            personalized.drop(1),
            allDiscovered,
            personalized.drop(2),
            allDiscovered.drop(1)
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
            if (video.channelId.isNotBlank() && state.subscriptions.any { it.id == video.channelId }) score += 9
            if (state.localLikedVideos.any { it.channelId.isNotBlank() && it.channelId == video.channelId }) score += 5
            if (state.liked.any { it.channelId.isNotBlank() && it.channelId == video.channelId }) score += 4
            if (state.history.any { it.channelId.isNotBlank() && it.channelId == video.channelId }) score += 3
            // No sumar gustos prefabricados (anime, humor, fútbol, etc.). Si un tema interesa,
            // ya aparece en interestTerms porque proviene de esta cuenta. Esto evita que Geo
            // Videos vuelva a imponer categorías genéricas por encima de las señales reales.
            val genericSpam = listOf(
                "watch till the end", "viral shorts", "funny video", "subscribe now",
                "best moments", "daily vlog", "prank video", "random facts"
            ).any { it in text }
            if (genericSpam && interestHits == 0) score -= 6
            return score
        }

        val ranked = videos.distinctBy { it.id }
            .mapIndexed { index, video -> Triple(video, score(video), index) }
            .sortedWith(compareByDescending<Triple<VideoItem, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
        return limitShortsPerChannel(ranked, limit)
    }

    private fun isAcceptableShort(video: VideoItem): Boolean {
        if (video.title.isBlank() || video.thumbnailUrl.isBlank()) return false
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
        if (error.statusCode == 401) {
            handleYouTubeSessionExpired(error)
            return
        }
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

    // ------------------------------------------------------------------
    // Sesión de YouTube (inicio de sesión dentro de la app, como Daylitube)
    // ------------------------------------------------------------------

    fun onYouTubeSessionConnected() {
        connectWithYouTubeSession(interactive = true)
    }

    /** Único inicio de sesión: usa la sesión de YouTube guardada por YouTubeLoginActivity. */
    fun connectWithYouTubeSession(interactive: Boolean) {
        if (!youtubeSession.isConnected()) {
            youtubeSession.clear()
            onAuthorizationFailure("No se encontró la sesión de YouTube. Inicia sesión otra vez.", false)
            return
        }
        _uiState.update {
            it.copy(
                youtubeSessionConnected = true,
                youtubeSyncEnabled = true,
                youtubeSyncAuthorized = true,
                youtubeSyncBusy = false
            )
        }
        onAuthorizationSuccess(token = YouTubeApi.SESSION_TOKEN, interactive = interactive)
    }

    /** Las acciones ya usan la sesión; no hace falta pedir otro permiso a Google. */
    fun onYouTubeSessionSyncRequested() {
        _uiState.update { it.copy(youtubeSyncEnabled = true, youtubeSyncAuthorized = true, youtubeSyncBusy = false) }
        pendingSubscriptionToggle?.let { channel ->
            pendingSubscriptionToggle = null
            toggleSubscription(channel)
        }
    }

    fun disconnectYouTubeSession() {
        disconnect()
    }

    fun loadSubscriptionFeed(force: Boolean = false) {
        val state = _uiState.value
        if (!youtubeSession.hasStoredSession() || state.subsFeedLoading) return
        if (!force && state.subsFeedVideos.isNotEmpty()) return
        _uiState.update { it.copy(subsFeedLoading = true) }
        loadSubscriptionGuide()
        viewModelScope.launch {
            try {
                val page = innerTube.subscriptionsFeed()
                subsFeedContinuation = page.continuation
                _uiState.update {
                    it.copy(
                        subsFeedVideos = page.videos,
                        subsFeedShorts = page.shorts,
                        subsFeedLoading = false,
                        subsFeedCanLoadMore = page.continuation.isNotBlank()
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expired: YouTubeSessionExpiredException) {
                handleYouTubeSessionExpired(expired)
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(subsFeedLoading = false, message = "No se pudieron cargar tus suscripciones.")
                }
            }
        }
    }

    fun loadMoreSubscriptionFeed() {
        val state = _uiState.value
        if (state.subsFeedLoadingMore || state.subsFeedLoading || subsFeedContinuation.isBlank()) return
        _uiState.update { it.copy(subsFeedLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = innerTube.subscriptionsFeed(subsFeedContinuation)
                subsFeedContinuation = page.continuation
                _uiState.update {
                    it.copy(
                        subsFeedVideos = mergeUniqueVideos(it.subsFeedVideos, page.videos).take(MAX_YOUTUBE_ITEMS),
                        subsFeedShorts = mergeUniqueVideos(it.subsFeedShorts, page.shorts).take(60),
                        subsFeedLoadingMore = false,
                        subsFeedCanLoadMore = page.continuation.isNotBlank()
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expired: YouTubeSessionExpiredException) {
                handleYouTubeSessionExpired(expired)
            } catch (error: Exception) {
                _uiState.update { it.copy(subsFeedLoadingMore = false) }
            }
        }
    }

    // ------------------------------------------------------------ Cola / Mix

    /** Une la lista que manda YouTube con la cola actual (sin repetir y en orden). */
    private fun mergeIntoQueue(playlistId: String, panel: com.geovideos.app.network.MixPanel) {
        _uiState.update { state ->
            val samePlaylist = state.queuePlaylistId == playlistId && state.queue.isNotEmpty()
            val merged = if (samePlaylist) {
                val known = state.queue.mapTo(HashSet()) { it.id }
                state.queue + panel.videos.filterNot { it.id in known }
            } else {
                panel.videos
            }.distinctBy { it.id }.take(MAX_QUEUE_ITEMS)
            val newIds = merged.map { it.id }
            val order = when {
                !state.queueShuffle -> emptyList()
                samePlaylist -> state.queueOrder + newIds.filterNot { it in state.queueOrder }.shuffled()
                else -> newIds.shuffled()
            }
            state.copy(
                queue = merged,
                queuePlaylistId = playlistId,
                queueTitle = panel.title.ifBlank { state.queueTitle.ifBlank { "Mix" } },
                queueSubtitle = panel.owner.ifBlank { state.queueSubtitle },
                queueOrder = order,
                queueLoadingMore = false
            )
        }
    }

    fun openQueue() {
        if (_uiState.value.queueActive) _uiState.update { it.copy(queueOpen = true) }
    }

    fun closeQueue() {
        _uiState.update { it.copy(queueOpen = false) }
    }

    fun playFromQueue(video: VideoItem) {
        val state = _uiState.value
        if (state.queue.none { it.id == video.id }) return
        openPlayer(video)
        maybeLoadMoreQueue()
    }

    /** Repetir: no → lista → este video → no (como el botón de YouTube). */
    fun cycleQueueRepeat() {
        _uiState.update {
            val next = (it.queueRepeat + 1) % 3
            it.copy(
                queueRepeat = next,
                message = when (next) {
                    1 -> "Se repetirá la lista"
                    2 -> "Se repetirá este video"
                    else -> "Repetición desactivada"
                }
            )
        }
    }

    fun setRepeatOne(enabled: Boolean) {
        _uiState.update { it.copy(queueRepeat = if (enabled) 2 else if (it.queueRepeat == 2) 0 else it.queueRepeat) }
    }

    fun toggleQueueShuffle() {
        _uiState.update { state ->
            val enable = !state.queueShuffle
            val current = state.selectedVideo?.id
            val order = if (enable) {
                val rest = state.queue.map { it.id }.filterNot { it == current }.shuffled()
                listOfNotNull(current?.takeIf { id -> state.queue.any { it.id == id } }) + rest
            } else {
                emptyList()
            }
            state.copy(
                queueShuffle = enable,
                queueOrder = order,
                message = if (enable) "Orden aleatorio activado" else "Orden aleatorio desactivado"
            )
        }
    }

    /** Carga continua: cerca del final del Mix, pedir más videos a YouTube. */
    fun maybeLoadMoreQueue(force: Boolean = false) {
        val state = _uiState.value
        if (!state.queueActive || state.queueLoadingMore || state.queuePlaylistId.isBlank()) return
        if (!state.queuePlaylistId.startsWith("RD")) return
        val index = state.queueIndex
        if (state.queue.size >= MAX_QUEUE_ITEMS) return
        if (!force && (index < 0 || index < state.queue.size - 4)) return
        val last = state.queue.last()
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        val playlistId = state.queuePlaylistId
        _uiState.update { it.copy(queueLoadingMore = true) }
        viewModelScope.launch {
            val panel = runCatching { api.mixPanel(token, last.copy(mixPlaylistId = playlistId)) }.getOrNull()
            if (_uiState.value.queuePlaylistId != playlistId) return@launch
            if (panel == null || panel.videos.isEmpty()) {
                _uiState.update { it.copy(queueLoadingMore = false) }
            } else {
                mergeIntoQueue(playlistId, panel)
            }
        }
    }

    fun playPreviousInQueue(): Boolean {
        val previous = _uiState.value.previousInQueue() ?: return false
        openPlayer(previous)
        return true
    }

    // ------------------------------------------------------------ Filtros de relacionados

    fun selectRelatedChip(chip: HomeChip) {
        val state = _uiState.value
        val video = state.selectedVideo ?: return
        if (chip.token == state.selectedRelatedChip || chip.token.isBlank()) return
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        _uiState.update { it.copy(selectedRelatedChip = chip.token, relatedLoading = true) }
        viewModelScope.launch {
            val page = runCatching { api.relatedChipPage(token, video, chip.token) }.getOrNull()
            if (_uiState.value.selectedVideo?.id != video.id || _uiState.value.selectedRelatedChip != chip.token) return@launch
            if (page == null || page.items.isEmpty()) {
                _uiState.update { it.copy(relatedLoading = false, message = "No se pudo cargar ese filtro.") }
                return@launch
            }
            relatedNextToken = page.nextPageToken
            _uiState.update {
                it.copy(
                    relatedVideos = page.items.filterNot { candidate -> candidate.id == video.id }
                        .distinctBy { candidate -> candidate.id },
                    relatedLoading = false,
                    relatedCanLoadMore = page.nextPageToken.isNotBlank()
                )
            }
        }
    }

    // ------------------------------------------------------------ Comentarios

    fun openComments(video: VideoItem) {
        if (video.mediaKind != MediaKind.YOUTUBE) return
        val state = _uiState.value
        if (state.commentsVideoId == video.id && state.commentsList.isNotEmpty()) {
            _uiState.update { it.copy(commentsOpen = true) }
            return
        }
        commentsNextToken = ""
        commentsSortTokens = emptyList()
        repliesNextTokens.clear()
        _uiState.update {
            it.copy(
                commentsOpen = true,
                commentsVideoId = video.id,
                commentsList = emptyList(),
                commentsLoading = true,
                commentsCanLoadMore = false,
                commentsTotalText = "",
                commentsSort = 0,
                commentsSortAvailable = false,
                commentReplies = emptyMap(),
                commentRepliesLoading = emptySet(),
                commentRepliesCanLoadMore = emptySet()
            )
        }
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            val page = runCatching { api.commentsFirstPage(token, video.id) }.getOrNull()
            if (_uiState.value.commentsVideoId != video.id) return@launch
            applyCommentsPage(page, replace = true, sortIndex = 0)
        }
    }

    fun closeComments() {
        _uiState.update { it.copy(commentsOpen = false) }
    }

    fun setCommentsSort(index: Int) {
        val state = _uiState.value
        val sortToken = commentsSortTokens.getOrNull(index) ?: return
        if (index == state.commentsSort || state.commentsLoading) return
        val videoId = state.commentsVideoId
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        repliesNextTokens.clear()
        _uiState.update {
            it.copy(
                commentsSort = index,
                commentsLoading = true,
                commentsList = emptyList(),
                commentReplies = emptyMap(),
                commentRepliesCanLoadMore = emptySet()
            )
        }
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            val page = runCatching { api.commentsPage(token, sortToken) }.getOrNull()
            if (_uiState.value.commentsVideoId != videoId) return@launch
            applyCommentsPage(page, replace = true, sortIndex = index)
        }
    }

    fun loadMoreComments() {
        val state = _uiState.value
        if (state.commentsLoading || state.commentsLoadingMore || commentsNextToken.isBlank()) return
        val videoId = state.commentsVideoId
        val pageToken = commentsNextToken
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        _uiState.update { it.copy(commentsLoadingMore = true) }
        commentsJob = viewModelScope.launch {
            val page = runCatching { api.commentsPage(token, pageToken) }.getOrNull()
            if (_uiState.value.commentsVideoId != videoId) return@launch
            applyCommentsPage(page, replace = false, sortIndex = _uiState.value.commentsSort)
        }
    }

    private fun applyCommentsPage(page: com.geovideos.app.data.CommentsPage?, replace: Boolean, sortIndex: Int) {
        if (page == null) {
            _uiState.update {
                it.copy(
                    commentsLoading = false,
                    commentsLoadingMore = false,
                    commentsCanLoadMore = false,
                    message = if (it.commentsList.isEmpty()) "No se pudieron cargar los comentarios." else it.message
                )
            }
            return
        }
        commentsNextToken = page.nextToken
        if (page.sortTokens.size >= 2) commentsSortTokens = page.sortTokens
        _uiState.update {
            it.copy(
                commentsList = if (replace) page.comments else (it.commentsList + page.comments).distinctBy { c -> c.id },
                commentsLoading = false,
                commentsLoadingMore = false,
                commentsCanLoadMore = page.nextToken.isNotBlank(),
                commentsTotalText = page.totalText.ifBlank { it.commentsTotalText },
                commentsSort = sortIndex,
                commentsSortAvailable = commentsSortTokens.size >= 2
            )
        }
    }

    fun loadReplies(comment: CommentItem) {
        val state = _uiState.value
        if (comment.id in state.commentRepliesLoading) return
        val pageToken = repliesNextTokens[comment.id] ?: comment.repliesToken
        if (pageToken.isBlank()) return
        val videoId = state.commentsVideoId
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        _uiState.update { it.copy(commentRepliesLoading = it.commentRepliesLoading + comment.id) }
        viewModelScope.launch {
            val page = runCatching { api.commentsPage(token, pageToken) }.getOrNull()
            if (_uiState.value.commentsVideoId != videoId) return@launch
            val nextToken = page?.nextToken.orEmpty()
            if (nextToken.isNotBlank()) repliesNextTokens[comment.id] = nextToken else repliesNextTokens.remove(comment.id)
            _uiState.update {
                val old = it.commentReplies[comment.id].orEmpty()
                it.copy(
                    commentReplies = it.commentReplies + (comment.id to (old + page?.comments.orEmpty()).distinctBy { c -> c.id }),
                    commentRepliesLoading = it.commentRepliesLoading - comment.id,
                    commentRepliesCanLoadMore = if (nextToken.isNotBlank()) {
                        it.commentRepliesCanLoadMore + comment.id
                    } else {
                        it.commentRepliesCanLoadMore - comment.id
                    },
                    message = if (page == null) "No se pudieron cargar las respuestas." else it.message
                )
            }
        }
    }

    // ------------------------------------------------------------ Filtros del inicio

    /** null o token vacío = "Todos". */
    fun selectHomeChip(chip: HomeChip?) {
        val state = _uiState.value
        val chipToken = chip?.token.orEmpty()
        if (chipToken == state.selectedHomeChip) return
        homeChipJob?.cancel()
        if (chipToken.isBlank()) {
            val saved = savedAllHome
            savedAllHome = null
            if (saved != null) {
                youtubeHomeContinuation = saved.continuation
                _uiState.update {
                    it.copy(
                        selectedHomeChip = "",
                        homeChipLoading = false,
                        homeFeed = saved.feed,
                        personalized = saved.personalized,
                        homeMixes = saved.mixes,
                        canLoadMoreForYou = saved.continuation.isNotBlank()
                    )
                }
            } else {
                _uiState.update { it.copy(selectedHomeChip = "", homeChipLoading = false) }
                refreshYouTubeSession(force = true)
            }
            return
        }
        if (state.selectedHomeChip.isBlank()) {
            savedAllHome = SavedHome(state.homeFeed, state.personalized, state.homeMixes, youtubeHomeContinuation)
        }
        _uiState.update { it.copy(selectedHomeChip = chipToken, homeChipLoading = true) }
        homeChipJob = viewModelScope.launch {
            try {
                val page = innerTube.browsePage("", continuation = chipToken)
                if (_uiState.value.selectedHomeChip != chipToken) return@launch
                youtubeHomeContinuation = page.continuation
                _uiState.update {
                    it.copy(
                        homeChipLoading = false,
                        homeFeed = page.feed,
                        personalized = page.videos.filter(::isNormalHomeVideo).take(MAX_YOUTUBE_ITEMS),
                        homeMixes = page.mixes,
                        canLoadMoreForYou = page.continuation.isNotBlank()
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expired: YouTubeSessionExpiredException) {
                handleYouTubeSessionExpired(expired)
            } catch (error: Exception) {
                _uiState.update { it.copy(homeChipLoading = false, message = "No se pudo cargar ese filtro.") }
            }
        }
    }

    // ------------------------------------------------------------ Menú ⋮ (No me interesa)

    fun notInterested(video: VideoItem) {
        removeFromHome { it.id == video.id }
        sendFeedbackToken(video.notInterestedToken, "Video quitado. YouTube lo tendrá en cuenta.")
    }

    fun dontRecommendChannel(video: VideoItem) {
        val channelId = video.channelId
        val channelTitle = video.channelTitle
        removeFromHome { candidate ->
            candidate.id == video.id ||
                (channelId.isNotBlank() && candidate.channelId == channelId) ||
                (channelId.isBlank() && channelTitle.isNotBlank() && candidate.channelTitle == channelTitle)
        }
        sendFeedbackToken(video.dontRecommendToken, "No te recomendaremos este canal.")
    }

    private fun removeFromHome(matches: (VideoItem) -> Boolean) {
        _uiState.update { state ->
            state.copy(
                homeFeed = state.homeFeed.mapNotNull { entry ->
                    when (entry) {
                        is HomeFeedEntry.Video -> entry.takeUnless { matches(it.video) }
                        is HomeFeedEntry.Mix -> entry.takeUnless { matches(it.mix) }
                        is HomeFeedEntry.Shorts -> entry.shorts.filterNot(matches)
                            .takeIf { it.isNotEmpty() }
                            ?.let { HomeFeedEntry.Shorts(it) }
                    }
                },
                personalized = state.personalized.filterNot(matches),
                homeMixes = state.homeMixes.filterNot(matches),
                relatedVideos = state.relatedVideos.filterNot(matches)
            )
        }
        savedAllHome = savedAllHome?.let { saved ->
            saved.copy(personalized = saved.personalized.filterNot(matches))
        }
    }

    private fun sendFeedbackToken(feedbackToken: String, okMessage: String) {
        val token = accessToken ?: YouTubeApi.SESSION_TOKEN
        if (feedbackToken.isBlank() || !_uiState.value.youtubeSessionConnected) {
            _uiState.update { it.copy(message = "Quitado de esta lista.") }
            return
        }
        viewModelScope.launch {
            val ok = runCatching { api.sendFeedback(token, feedbackToken) }.isSuccess
            _uiState.update { it.copy(message = if (ok) okMessage else "Quitado aquí, pero YouTube no respondió.") }
        }
    }

    private var lastSessionRefreshAt = 0L

    private fun refreshYouTubeSession(force: Boolean = false) {
        if (!youtubeSession.hasStoredSession()) return
        val now = System.currentTimeMillis()
        if (!force) {
            // No reiniciar una carga que ya va en camino (antes se cancelaba y volvía a empezar).
            if (youtubeSessionJob?.isActive == true) return
            if (now - lastSessionRefreshAt < 45_000L && _uiState.value.homeFeed.isNotEmpty()) return
        }
        lastSessionRefreshAt = now
        refreshUnseenNotifications()
        youtubeSessionJob?.cancel()
        youtubeSessionJob = viewModelScope.launch {
            _uiState.update { it.copy(youtubeSessionBusy = true) }
            try {
                val fetchedAt = System.currentTimeMillis()
                supervisorScope {
                    val homeDeferred = async { runCatching { innerTube.home() } }
                    val historyDeferred = async { runCatching { innerTube.history() } }
                    val laterDeferred = async { runCatching { innerTube.watchLater() } }
                    val home = homeDeferred.await()
                    // Mostrar el inicio apenas llega, sin esperar historial ni Ver más tarde.
                    home.getOrNull()?.let { firstPage ->
                        val firstVideos = firstPage.videos.filter(::isNormalHomeVideo)
                        if (firstVideos.isNotEmpty() || firstPage.feed.isNotEmpty()) {
                            youtubeHomeActive = true
                            youtubeHomeContinuation = firstPage.continuation
                            savedAllHome = null
                            _uiState.update { state ->
                                state.copy(
                                    homeFeed = firstPage.feed.ifEmpty { state.homeFeed },
                                    homeMixes = firstPage.mixes.ifEmpty { state.homeMixes },
                                    personalized = firstVideos.ifEmpty { state.personalized }.take(MAX_YOUTUBE_ITEMS),
                                    homeChips = firstPage.chips
                                        .filterNot { chip -> chip.title.equals("Todos", true) || chip.title.equals("All", true) }
                                        .ifEmpty { state.homeChips },
                                    selectedHomeChip = "",
                                    canLoadMoreForYou = firstPage.continuation.isNotBlank(),
                                    loading = false
                                )
                            }
                        }
                    }
                    val history = historyDeferred.await()
                    val later = laterDeferred.await()
                    ensureActive()

                    val failures = listOfNotNull(
                        home.exceptionOrNull(),
                        history.exceptionOrNull(),
                        later.exceptionOrNull()
                    )
                    failures.firstOrNull { it is YouTubeSessionExpiredException }?.let { throw it }

                    val homePage = home.getOrNull()
                    val historyPage = history.getOrNull()
                    val laterPage = later.getOrNull()
                    if (homePage != null) {
                        youtubeHomeContinuation = homePage.continuation
                        savedAllHome = null
                    }
                    if (historyPage != null) youtubeHistoryContinuation = historyPage.continuation
                    if (laterPage != null) youtubeWatchLaterContinuation = laterPage.continuation

                    val homeVideos = homePage?.videos.orEmpty().filter(::isNormalHomeVideo)
                    val homeShorts = homePage?.shorts.orEmpty()
                    if (homeVideos.isNotEmpty()) youtubeHomeActive = true

                    _uiState.update { state ->
                        state.copy(
                            youtubeSessionConnected = true,
                            youtubeSessionBusy = false,
                            youtubeHistory = historyPage?.let { it.videos + it.shorts } ?: state.youtubeHistory,
                            youtubeHistoryFetchedAtMs = if (historyPage != null) fetchedAt else state.youtubeHistoryFetchedAtMs,
                            historyCanLoadMore = youtubeHistoryContinuation.isNotBlank(),
                            youtubeWatchLater = laterPage?.videos ?: state.youtubeWatchLater,
                            homeMixes = if (homePage != null && homePage.mixes.isNotEmpty()) homePage.mixes else state.homeMixes,
                            homeFeed = if (homePage != null && homePage.feed.isNotEmpty()) homePage.feed else state.homeFeed,
                            homeChips = homePage?.chips
                                ?.filterNot { chip -> chip.title.equals("Todos", true) || chip.title.equals("All", true) }
                                ?.takeIf { it.isNotEmpty() }
                                ?: state.homeChips,
                            selectedHomeChip = if (homePage != null) "" else state.selectedHomeChip,
                            homeChipLoading = false,
                            watchLaterCanLoadMore = youtubeWatchLaterContinuation.isNotBlank(),
                            personalized = if (homeVideos.isNotEmpty()) {
                                homeVideos.take(MAX_YOUTUBE_ITEMS)
                            } else {
                                state.personalized
                            },
                            canLoadMoreForYou = if (homeVideos.isNotEmpty()) {
                                youtubeHomeContinuation.isNotBlank()
                            } else {
                                state.canLoadMoreForYou
                            },
                            // No cambiar la lista de Shorts mientras se está viendo uno.
                            shorts = if (homeShorts.isEmpty() || state.section == MainSection.SHORTS) {
                                state.shorts
                            } else {
                                mergeUniqueVideos(homeShorts, state.shorts).take(60)
                            },
                            loading = if (homeVideos.isNotEmpty()) false else state.loading,
                            message = if (homePage == null && historyPage == null && laterPage == null) {
                                "No se pudo leer YouTube ahora. Se mantienen tus listas."
                            } else {
                                state.message
                            }
                        )
                    }
                    if (homeVideos.isNotEmpty()) persistCurrentSnapshot()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expired: YouTubeSessionExpiredException) {
                handleYouTubeSessionExpired(expired)
            } catch (error: Exception) {
                _uiState.update { it.copy(youtubeSessionBusy = false) }
            }
        }
    }

    private fun handleYouTubeSessionExpired(error: Exception) {
        disconnect()
        _uiState.update {
            it.copy(
                authStatus = AuthStatus.ERROR,
                authError = error.message ?: "La sesión de YouTube venció. Vuelve a iniciar sesión.",
                message = null
            )
        }
    }

    /** Devuelve true si el inicio de YouTube se encargó de cargar más. */
    private suspend fun loadMoreYouTubeHome(): Boolean {
        if (!youtubeHomeActive || !_uiState.value.youtubeSessionConnected) return false
        val token = youtubeHomeContinuation
        if (token.isBlank()) {
            _uiState.update { it.copy(loadingMoreCategory = null, canLoadMoreForYou = false) }
            return true
        }
        val page = innerTube.home(token)
        youtubeHomeContinuation = page.continuation
        _uiState.update { state ->
            state.copy(
                personalized = mergeUniqueVideos(
                    state.personalized,
                    page.videos.filter(::isNormalHomeVideo)
                ).take(MAX_YOUTUBE_ITEMS),
                shorts = if (page.shorts.isEmpty() || state.section == MainSection.SHORTS) {
                    state.shorts
                } else {
                    mergeUniqueVideos(state.shorts, page.shorts).take(MAX_YOUTUBE_ITEMS)
                },
                homeMixes = mergeUniqueVideos(state.homeMixes, page.mixes).take(30),
                homeFeed = appendHomeFeed(state.homeFeed, page.feed),
                loadingMoreCategory = null,
                canLoadMoreForYou = page.continuation.isNotBlank()
            )
        }
        return true
    }

    fun loadMoreHistory() {
        val state = _uiState.value
        if (!state.youtubeSessionConnected || state.historyLoadingMore || youtubeHistoryContinuation.isBlank()) return
        _uiState.update { it.copy(historyLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = innerTube.history(youtubeHistoryContinuation)
                youtubeHistoryContinuation = page.continuation
                _uiState.update {
                    it.copy(
                        youtubeHistory = mergeUniqueVideos(it.youtubeHistory, page.videos, page.shorts)
                            .take(MAX_YOUTUBE_ITEMS),
                        historyLoadingMore = false,
                        historyCanLoadMore = page.continuation.isNotBlank()
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expired: YouTubeSessionExpiredException) {
                handleYouTubeSessionExpired(expired)
            } catch (error: Exception) {
                _uiState.update { it.copy(historyLoadingMore = false, message = "No se pudo cargar más historial.") }
            }
        }
    }

    fun loadMoreWatchLater() {
        val state = _uiState.value
        if (!state.youtubeSessionConnected || state.watchLaterLoadingMore || youtubeWatchLaterContinuation.isBlank()) return
        _uiState.update { it.copy(watchLaterLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = innerTube.watchLater(youtubeWatchLaterContinuation)
                youtubeWatchLaterContinuation = page.continuation
                _uiState.update {
                    it.copy(
                        youtubeWatchLater = mergeUniqueVideos(it.youtubeWatchLater, page.videos)
                            .take(MAX_YOUTUBE_ITEMS * 3),
                        watchLaterLoadingMore = false,
                        watchLaterCanLoadMore = page.continuation.isNotBlank()
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expired: YouTubeSessionExpiredException) {
                handleYouTubeSessionExpired(expired)
            } catch (error: Exception) {
                _uiState.update { it.copy(watchLaterLoadingMore = false, message = "No se pudo cargar más de Ver más tarde.") }
            }
        }
    }

    private fun toggleYouTubeWatchLater(video: VideoItem) {
        val state = _uiState.value
        val inRemote = state.youtubeWatchLater.any { it.id == video.id }
        val inLocal = state.watchLater.any { it.id == video.id }
        val add = !inRemote && !inLocal
        val localUpdated = if (inLocal) repository.toggleWatchLater(video) else state.watchLater
        _uiState.update {
            it.copy(
                watchLater = localUpdated,
                youtubeWatchLater = if (add) {
                    listOf(video) + it.youtubeWatchLater
                } else {
                    it.youtubeWatchLater.filterNot { item -> item.id == video.id }
                },
                message = if (add) "Guardado en Ver más tarde de YouTube." else "Quitado de Ver más tarde."
            )
        }
        if (!add && !inRemote) return
        viewModelScope.launch {
            try {
                innerTube.editWatchLater(video.id, add)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expired: YouTubeSessionExpiredException) {
                handleYouTubeSessionExpired(expired)
            } catch (error: Exception) {
                // Revertir si YouTube no aceptó el cambio.
                _uiState.update {
                    it.copy(
                        youtubeWatchLater = if (add) {
                            it.youtubeWatchLater.filterNot { item -> item.id == video.id }
                        } else {
                            mergeUniqueVideos(listOf(video), it.youtubeWatchLater)
                        },
                        message = "YouTube no aceptó el cambio en Ver más tarde. Intenta otra vez."
                    )
                }
            }
        }
    }

    private fun trackYouTubeWatch(video: VideoItem) {
        if (video.mediaKind != MediaKind.YOUTUBE || !_uiState.value.youtubeSessionConnected) return
        viewModelScope.launch { runCatching { innerTube.startWatch(video.id) } }
    }

    private fun reportYouTubeProgress(video: VideoItem, positionMs: Long, durationMs: Long) {
        if (video.mediaKind != MediaKind.YOUTUBE || !_uiState.value.youtubeSessionConnected) return
        viewModelScope.launch { runCatching { innerTube.reportProgress(video.id, positionMs, durationMs) } }
    }

    private fun loadYouTubeShortsSequence(reset: Boolean) {
        val state = _uiState.value
        if (state.shortsLoadingMore) return
        val seed = state.shorts.firstOrNull()?.id ?: return
        if (!reset && shortsSequenceContinuation.isBlank()) return
        _uiState.update { it.copy(shortsLoadingMore = true) }
        viewModelScope.launch {
            try {
                val page = innerTube.shortsSequence(seed, if (reset) "" else shortsSequenceContinuation)
                shortsSequenceContinuation = page.continuation
                shortsSequenceActive = true
                val firstDetails = enrichShortList(page.shorts.take(4))
                val fresh = firstDetails + page.shorts.drop(4)
                _uiState.update { s ->
                    val merged = if (reset) {
                        // El Short que se está viendo queda primero; luego la secuencia de YouTube.
                        mergeUniqueVideos(s.shorts.take(1), fresh, s.shorts.drop(1))
                    } else {
                        mergeUniqueVideos(s.shorts, fresh)
                    }
                    s.copy(
                        shorts = merged.take(MAX_YOUTUBE_ITEMS),
                        shortsLoadingMore = false,
                        shortsCanLoadMore = shortsSequenceContinuation.isNotBlank()
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expired: YouTubeSessionExpiredException) {
                handleYouTubeSessionExpired(expired)
            } catch (error: Exception) {
                _uiState.update { it.copy(shortsLoadingMore = false) }
            }
        }
    }

    private suspend fun enrichShortList(list: List<VideoItem>): List<VideoItem> = supervisorScope {
        list.map { item ->
            async {
                val details = runCatching { innerTube.shortDetails(item.id) }.getOrNull()
                if (details == null) {
                    item
                } else {
                    item.copy(
                        title = details.title,
                        channelTitle = details.channelTitle.ifBlank { item.channelTitle },
                        channelId = details.channelId.ifBlank { item.channelId },
                        channelThumbnailUrl = details.channelThumbnailUrl.ifBlank { item.channelThumbnailUrl },
                        viewCountText = details.viewCountText.ifBlank { item.viewCountText },
                        likeCountText = details.likeCountText,
                        commentCountText = details.commentCountText,
                        durationMs = if (details.durationMs > 0L) details.durationMs else item.durationMs,
                        thumbnailUrl = item.thumbnailUrl.ifBlank { details.thumbnailUrl }
                    )
                }
            }
        }.awaitAll()
    }

    /** Completa título, canal y contadores del Short visible y de los 2 siguientes. */
    private fun enrichShortsAround(videoId: String) {
        if (!youtubeSession.hasStoredSession()) return
        val shorts = _uiState.value.shorts
        val index = shorts.indexOfFirst { it.id == videoId }
        if (index < 0) return
        val targets = shorts.drop(index).take(3).filter { it.likeCountText.isBlank() && it.commentCountText.isBlank() }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val enriched = enrichShortList(targets).associateBy { it.id }
            _uiState.update { s ->
                s.copy(
                    shorts = s.shorts.map { enriched[it.id] ?: it },
                    selectedVideo = s.selectedVideo?.let { selected ->
                        enriched[selected.id]?.let { e ->
                            selected.copy(title = e.title, channelTitle = e.channelTitle, channelThumbnailUrl = e.channelThumbnailUrl)
                        } ?: selected
                    }
                )
            }
        }
    }

    /** Agrega la página siguiente del inicio sin repetir videos, Shorts ni Mixes. */
    private fun appendHomeFeed(existing: List<HomeFeedEntry>, next: List<HomeFeedEntry>): List<HomeFeedEntry> {
        val seen = HashSet<String>()
        existing.forEach { entry ->
            when (entry) {
                is HomeFeedEntry.Video -> seen.add(entry.video.id)
                is HomeFeedEntry.Shorts -> entry.shorts.forEach { seen.add(it.id) }
                is HomeFeedEntry.Mix -> seen.add("m:" + entry.mix.mixPlaylistId)
            }
        }
        val added = next.mapNotNull { entry ->
            when (entry) {
                is HomeFeedEntry.Video -> entry.takeIf { seen.add(it.video.id) }
                is HomeFeedEntry.Shorts -> entry.shorts.filter { seen.add(it.id) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { HomeFeedEntry.Shorts(it) }
                is HomeFeedEntry.Mix -> entry.takeIf { seen.add("m:" + it.mix.mixPlaylistId) }
            }
        }
        return (existing + added).takeLast(MAX_YOUTUBE_ITEMS)
    }

    /** Pone la foto del canal usando las suscripciones cuando la API no la trae. */
    private fun withChannelAvatars(videos: List<VideoItem>, channels: List<ChannelItem>): List<VideoItem> {
        if (channels.isEmpty() || videos.none { it.channelThumbnailUrl.isBlank() }) return videos
        val avatars = channels
            .filter { it.thumbnailUrl.isNotBlank() }
            .associate { it.id to it.thumbnailUrl }
        return videos.map { video ->
            if (video.channelThumbnailUrl.isBlank()) {
                avatars[video.channelId]?.let { video.copy(channelThumbnailUrl = it) } ?: video
            } else {
                video
            }
        }
    }

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MS = 10L * 60L * 1000L
        const val MAX_HISTORY_ITEMS_IN_MEMORY = 300
        const val FIRST_RELATED_PAGE = "__first__"
        const val FAST_REFRESH_SUBSCRIPTION_BATCH = 8
        const val INITIAL_SUBSCRIPTION_BATCH = 12
        const val SUBSCRIPTION_PAGE_SIZE = 12
        const val GEO_WATCH_LATER_TITLE = "Geo Videos - Ver después"
        const val MAX_HOME_ITEMS = 60
        const val SHORT_MAX_DURATION_MS = 180_000L
        const val HOME_SHORTS_SHELF_SIZE = 14
        const val HOME_SHORTS_ROTATE_STEP = 7
        const val MAX_YOUTUBE_ITEMS = 400
        const val MAX_QUEUE_ITEMS = 200
        const val CHANNEL_VIDEOS_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"
        const val CHANNEL_SHORTS_PARAMS = "EgZzaG9ydHPyBgUKA5oBAA%3D%3D"
        const val CHANNEL_PLAYLISTS_PARAMS = "EglwbGF5bGlzdHPyBgQKAkIA"
    }
}
