package com.geovideos.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.geovideos.app.data.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch




data class PlaybackCoreState(
    val connecting: Boolean = true,
    val resolving: Boolean = false,
    val currentVideoId: String = "",
    val isPlaying: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val playbackState: Int = Player.STATE_IDLE,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null
)

data class PlaybackProgressState(
    val currentVideoId: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercentage: Int = 0
)

data class PlaybackUiState(
    val connecting: Boolean = true,
    val resolving: Boolean = false,
    val currentVideoId: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercentage: Int = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val playbackState: Int = Player.STATE_IDLE,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null
)

class GeoPlayerConnection private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val playerPreferences = appContext.getSharedPreferences("geo_player_preferences", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
    ).buildAsync()

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _preferredQualityHeight = MutableStateFlow<Int?>(
        playerPreferences.getInt(KEY_PREFERRED_QUALITY, 0).takeIf { it > 0 }
    )
    val preferredQualityHeight: StateFlow<Int?> = _preferredQualityHeight.asStateFlow()

    // El modo música pertenece únicamente a la reproducción actual. No se conserva
    // como preferencia global porque al abrir otro video o un Short debe volver el video.
    private val _audioOnlyMode = MutableStateFlow(false)
    val audioOnlyMode: StateFlow<Boolean> = _audioOnlyMode.asStateFlow()
    private var activeAudioOnly: Boolean = false

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    val coreState: StateFlow<PlaybackCoreState> = _state
        .map { playback ->
            PlaybackCoreState(
                connecting = playback.connecting,
                resolving = playback.resolving,
                currentVideoId = playback.currentVideoId,
                isPlaying = playback.isPlaying,
                videoWidth = playback.videoWidth,
                videoHeight = playback.videoHeight,
                playbackState = playback.playbackState,
                repeatMode = playback.repeatMode,
                error = playback.error
            )
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, PlaybackCoreState())

    val progressState: StateFlow<PlaybackProgressState> = _state
        .map { playback ->
            PlaybackProgressState(
                currentVideoId = playback.currentVideoId,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                bufferedPercentage = playback.bufferedPercentage
            )
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, PlaybackProgressState())

    private val _endedEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val endedEvents: SharedFlow<String> = _endedEvents.asSharedFlow()

    private val _mediaTransitionEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val mediaTransitionEvents: SharedFlow<String> = _mediaTransitionEvents.asSharedFlow()

    private var currentVideo: VideoItem? = null
    private var resolveJob: Job? = null
    private var queueJob: Job? = null
    private var preloadJob: Job? = null
    private var pendingQueue: List<VideoItem> = emptyList()
    private var pendingQueueDataSaver: Boolean = false
    private var requestSerial: Long = 0L
    private var activeRepeatPlayback = false
    private var activeDataSaver = false
    private var shortFallbackVideoId: String? = null
    private val pendingControllerActions = ArrayList<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updateFrom(player)

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.takeIf { it.isNotBlank() }?.let { _mediaTransitionEvents.tryEmit(it) }
            _controller.value?.let(::updateFrom)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = _controller.value ?: return
            if (playbackState == Player.STATE_ENDED && player.repeatMode != Player.REPEAT_MODE_ONE) {
                val mediaId = player.currentMediaItem?.mediaId.orEmpty()
                if (mediaId.isNotBlank()) _endedEvents.tryEmit(mediaId)
            }
            updateFrom(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            val video = currentVideo
            if (
                video != null &&
                activeRepeatPlayback &&
                shortFallbackVideoId != video.id
            ) {
                shortFallbackVideoId = video.id
                openInternal(
                    video = video.copy(resumePositionMs = 0L),
                    autoplay = true,
                    dataSaver = activeDataSaver,
                    repeat = true,
                    preferredHeight = null,
                    forceReload = true,
                    audioOnly = false,
                    forceProgressive = true,
                    updateQualityPreference = false
                )
                return
            }
            _state.update {
                it.copy(
                    resolving = false,
                    error = friendlyPlaybackError(error.localizedMessage)
                )
            }
        }
    }

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { mediaController ->
                        mediaController.addListener(listener)
                        _controller.value = mediaController
                        _state.update { it.copy(connecting = false) }
                        val queued = synchronized(pendingControllerActions) {
                            pendingControllerActions.toList().also { pendingControllerActions.clear() }
                        }
                        queued.forEach { it(mediaController) }
                        updateFrom(mediaController)
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                connecting = false,
                                resolving = false,
                                error = friendlyPlaybackError(error.message)
                            )
                        }
                    }
            },
            ContextCompat.getMainExecutor(appContext)
        )

        scope.launch {
            while (isActive) {
                _controller.value?.let(::updateFrom)
                // Keep the custom progress bar responsive while playing without waking the
                // UI every few milliseconds when playback is paused.
                delay(if (_controller.value?.isPlaying == true) 500L else 1_500L)
            }
        }
    }

    fun open(
        video: VideoItem,
        autoplay: Boolean,
        dataSaver: Boolean,
        repeat: Boolean = false
    ) {
        openInternal(
            video = video,
            autoplay = autoplay,
            dataSaver = dataSaver,
            repeat = repeat,
            preferredHeight = _preferredQualityHeight.value,
            forceReload = false,
            audioOnly = false
        )
    }

    private fun openInternal(
        video: VideoItem,
        autoplay: Boolean,
        dataSaver: Boolean,
        repeat: Boolean,
        preferredHeight: Int?,
        forceReload: Boolean,
        audioOnly: Boolean,
        forceProgressive: Boolean = false,
        updateQualityPreference: Boolean = true
    ) {
        val controllerNow = _controller.value
        if (
            !forceReload &&
            controllerNow?.currentMediaItem?.mediaId == video.id &&
            controllerNow.mediaItemCount > 0 &&
            activeAudioOnly == audioOnly
        ) {
            currentVideo = video
            controllerNow.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            if (autoplay && controllerNow.playbackState == Player.STATE_IDLE) controllerNow.prepare()
            if (autoplay && controllerNow.playbackState == Player.STATE_ENDED) {
                controllerNow.seekTo(0L)
                controllerNow.play()
            }
            return
        }

        controllerNow?.pause()
        currentVideo = video
        activeAudioOnly = audioOnly
        activeRepeatPlayback = repeat
        activeDataSaver = dataSaver
        if (!forceProgressive) shortFallbackVideoId = null
        _audioOnlyMode.value = audioOnly
        if (updateQualityPreference) {
            _preferredQualityHeight.value = preferredHeight
            playerPreferences.edit().putInt(KEY_PREFERRED_QUALITY, preferredHeight ?: 0).apply()
        }
        resolveJob?.cancel()
        requestSerial += 1L
        val requestId = requestSerial
        _state.value = PlaybackUiState(
            connecting = controllerNow == null,
            resolving = true,
            currentVideoId = video.id,
            positionMs = video.resumePositionMs,
            durationMs = video.durationMs
        )

        resolveJob = scope.launch {
            try {
                val resolved = if (audioOnly) {
                    StreamResolver.resolveAudio(video)
                } else {
                    StreamResolver.resolve(
                        video = video,
                        dataSaver = dataSaver,
                        preferredHeight = if (forceProgressive) null else preferredHeight,
                        preferProgressive = forceProgressive || repeat
                    )
                }
                if (requestId != requestSerial || currentVideo?.id != video.id) return@launch
                withController { controller ->
                    if (requestId != requestSerial || currentVideo?.id != video.id) return@withController
                    val metadata = MediaMetadata.Builder()
                        .setTitle(video.title)
                        .setArtist(video.channelTitle)
                        .setArtworkUri(video.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                        .build()
                    val trackBuilder = controller.trackSelectionParameters
                        .buildUpon()
                        .clearVideoSizeConstraints()
                    if (!audioOnly && !resolved.hasSeparateAudio) {
                        preferredHeight?.takeIf { it > 0 }?.let { exactHeight ->
                            trackBuilder
                                .setMinVideoSize(0, exactHeight)
                                .setMaxVideoSize(Int.MAX_VALUE, exactHeight)
                        }
                    }
                    controller.setTrackSelectionParameters(trackBuilder.build())
                    controller.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

                    if (resolved.hasSeparateAudio) {
                        PlaybackService.playResolved(
                            context = appContext,
                            videoId = video.id,
                            title = video.title,
                            artist = video.channelTitle,
                            artwork = video.thumbnailUrl,
                            resolved = resolved,
                            positionMs = video.resumePositionMs,
                            autoplay = autoplay,
                            repeat = repeat
                        )
                    } else {
                        val item = MediaItem.Builder()
                            .setMediaId(video.id)
                            .setUri(resolved.uri)
                            .setMimeType(resolved.mimeType)
                            .setMediaMetadata(metadata)
                            .build()
                        controller.setMediaItem(item, video.resumePositionMs.coerceAtLeast(0L))
                        controller.prepare()
                        controller.playWhenReady = autoplay
                    }
                    _state.update { it.copy(resolving = false, error = null) }
                    appendPendingQueue(video.id)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestId != requestSerial || currentVideo?.id != video.id) return@launch
                _state.update {
                    it.copy(
                        resolving = false,
                        error = friendlyPlaybackError(error.message)
                    )
                }
            }
        }
    }

    fun updateQueue(current: VideoItem, candidates: List<VideoItem>, dataSaver: Boolean) {
        pendingQueue = candidates
            .asSequence()
            .filterNot { it.id == current.id }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            // Resolving twenty YouTube streams up front wastes extractor/network time and can
            // compete with the video that is currently buffering. Four items are enough for
            // seamless Next/autoplay and the queue is rebuilt as playback advances.
            .take(4)
            .toList()
        pendingQueueDataSaver = dataSaver
        if (_controller.value?.currentMediaItem?.mediaId == current.id) {
            appendPendingQueue(current.id)
        }
    }

    private fun appendPendingQueue(currentId: String) {
        queueJob?.cancel()
        val upcoming = pendingQueue
        if (upcoming.isEmpty()) return
        queueJob = scope.launch {
            // Let the current item reach its first playable buffer before preparing the queue.
            delay(900L)
            for (video in upcoming) {
                val controllerNow = _controller.value ?: break
                val queuedIds = (0 until controllerNow.mediaItemCount)
                    .map { index -> controllerNow.getMediaItemAt(index).mediaId }
                    .toHashSet()
                if (video.id in queuedIds) continue
                val activeIds = queuedIds + controllerNow.currentMediaItem?.mediaId.orEmpty()
                if (currentId !in activeIds && controllerNow.currentMediaItem?.mediaId != currentId) break
                val resolved = runCatching {
                    if (_audioOnlyMode.value) {
                        StreamResolver.resolveAudio(video)
                    } else {
                        StreamResolver.resolve(
                            video = video,
                            dataSaver = pendingQueueDataSaver,
                            preferredHeight = _preferredQualityHeight.value,
                            preferProgressive = false
                        )
                    }
                }.getOrNull() ?: continue
                if (resolved.hasSeparateAudio) {
                    PlaybackService.appendResolved(
                        context = appContext,
                        videoId = video.id,
                        title = video.title,
                        artist = video.channelTitle,
                        artwork = video.thumbnailUrl,
                        resolved = resolved
                    )
                    delay(120L)
                } else {
                    val metadata = MediaMetadata.Builder()
                        .setTitle(video.title)
                        .setArtist(video.channelTitle)
                        .setArtworkUri(video.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                        .build()
                    val item = MediaItem.Builder()
                        .setMediaId(video.id)
                        .setUri(resolved.uri)
                        .setMimeType(resolved.mimeType)
                        .setMediaMetadata(metadata)
                        .build()
                    withController { controller ->
                        val exists = (0 until controller.mediaItemCount)
                            .any { index -> controller.getMediaItemAt(index).mediaId == video.id }
                        if (!exists) controller.addMediaItem(item)
                    }
                }
            }
        }
    }

    internal suspend fun streamOptions(
        video: VideoItem,
        includeDownloadSizes: Boolean = true
    ): StreamOptions = StreamResolver.options(video, includeDownloadSizes)

    suspend fun isVerifiedShort(video: VideoItem): Boolean = StreamResolver.isVerifiedShort(video)

    fun selectQuality(video: VideoItem, height: Int?, dataSaver: Boolean) {
        val controllerNow = _controller.value
        val position = controllerNow?.currentPosition
            ?.takeIf { it >= 0L }
            ?: video.resumePositionMs
        val shouldPlay = controllerNow?.playWhenReady ?: true
        val repeat = controllerNow?.repeatMode == Player.REPEAT_MODE_ONE
        openInternal(
            video = video.copy(resumePositionMs = position),
            autoplay = shouldPlay,
            dataSaver = dataSaver,
            repeat = repeat,
            preferredHeight = height,
            forceReload = true,
            audioOnly = false
        )
    }


    fun setAudioOnly(video: VideoItem, enabled: Boolean, autoplay: Boolean, dataSaver: Boolean) {
        val controllerNow = _controller.value
        val position = controllerNow?.currentPosition
            ?.takeIf { it >= 0L }
            ?: video.resumePositionMs
        val shouldPlay = controllerNow?.playWhenReady ?: autoplay
        val repeat = controllerNow?.repeatMode == Player.REPEAT_MODE_ONE
        openInternal(
            video = video.copy(resumePositionMs = position),
            autoplay = shouldPlay,
            dataSaver = dataSaver,
            repeat = repeat,
            preferredHeight = _preferredQualityHeight.value,
            forceReload = true,
            audioOnly = enabled
        )
    }

    fun playNext() = withController { controller ->
        if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
    }

    fun playPrevious() = withController { controller ->
        if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem()
        else controller.seekTo(0L)
    }

    fun retryShort(video: VideoItem, dataSaver: Boolean) {
        openInternal(
            video = video.copy(resumePositionMs = 0L),
            autoplay = true,
            dataSaver = dataSaver,
            repeat = true,
            preferredHeight = _preferredQualityHeight.value,
            forceReload = true,
            audioOnly = false,
            forceProgressive = true,
            updateQualityPreference = false
        )
    }

    fun play() = withController { it.play() }
    fun pause() = withController { it.pause() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }
    fun setMuted(muted: Boolean) = withController { it.volume = if (muted) 0f else 1f }
    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed.coerceIn(0.25f, 2f)) }
    fun setMaxVideoHeight(height: Int) = withController { controller ->
        val builder = controller.trackSelectionParameters
            .buildUpon()
            .clearVideoSizeConstraints()
        if (height > 0) {
            builder
                .setMinVideoSize(0, height)
                .setMaxVideoSize(Int.MAX_VALUE, height)
        }
        controller.setTrackSelectionParameters(builder.build())
    }
    fun preload(videos: List<VideoItem>, dataSaver: Boolean) {
        if (videos.isEmpty()) return
        // NewPipe extraction is blocking work internally, so cancelling and immediately
        // launching another speculative batch can still leave both requests competing. Keep
        // at most one small preload batch active at any moment.
        if (preloadJob?.isActive == true) return
        val preferredHeight = _preferredQualityHeight.value
        preloadJob = scope.launch {
            delay(120L)
            StreamResolver.preload(videos, dataSaver, preferredHeight)
        }
    }
    fun setRepeat(enabled: Boolean) = withController {
        it.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        PlaybackService.setContinuousAutoplay(appContext, enabled)
    }

    fun stop() {
        requestSerial += 1L
        resolveJob?.cancel()
        queueJob?.cancel()
        preloadJob?.cancel()
        pendingQueue = emptyList()
        currentVideo = null
        activeAudioOnly = false
        activeRepeatPlayback = false
        activeDataSaver = false
        shortFallbackVideoId = null
        _audioOnlyMode.value = false
        // Keep the user's quality preference when closing or leaving Shorts.
        // The next video reuses it, matching DayliTube/YouTube behavior.
        withController {
            it.stop()
            it.clearMediaItems()
        }
        PlaybackService.stopPlayback(appContext)
        _state.value = PlaybackUiState(connecting = _controller.value == null)
    }

    private fun withController(action: (MediaController) -> Unit) {
        val ready = _controller.value
        if (ready != null) {
            action(ready)
        } else {
            synchronized(pendingControllerActions) { pendingControllerActions.add(action) }
        }
    }

    private fun friendlyPlaybackError(rawMessage: String?): String {
        val message = rawMessage.orEmpty()
        return when {
            message.contains("unable to resolve host", ignoreCase = true) ||
                message.contains("no address associated with hostname", ignoreCase = true) ||
                message.contains("youtubei.googleapis.com", ignoreCase = true) ->
                "No se pudo conectar con el servicio de video. Revisa tu conexión y pulsa Reintentar."
            message.contains("timeout", ignoreCase = true) ->
                "La conexión tardó demasiado. Pulsa Reintentar."
            message.isBlank() -> "No se pudo reproducir el video. Pulsa Reintentar."
            else -> "No se pudo reproducir el video. Pulsa Reintentar."
        }
    }

    private fun updateFrom(player: Player) {
        _state.update {
            it.copy(
                connecting = false,
                currentVideoId = player.currentMediaItem?.mediaId.orEmpty().ifBlank { it.currentVideoId },
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { duration -> duration > 0L } ?: it.durationMs,
                bufferedPercentage = player.bufferedPercentage.coerceIn(0, 100),
                videoWidth = player.videoSize.width.coerceAtLeast(0),
                videoHeight = player.videoSize.height.coerceAtLeast(0),
                playbackState = player.playbackState,
                repeatMode = player.repeatMode,
                error = player.playerError?.localizedMessage ?: it.error
            )
        }
    }

    private fun release() {
        resolveJob?.cancel()
        queueJob?.cancel()
        preloadJob?.cancel()
        _controller.value?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        scope.cancel()
    }

    companion object {
        private const val KEY_PREFERRED_QUALITY = "preferred_quality_height"

        @Volatile
        private var instance: GeoPlayerConnection? = null

        fun get(context: Context): GeoPlayerConnection =
            instance ?: synchronized(this) {
                instance ?: GeoPlayerConnection(context).also { instance = it }
            }
    }
}
