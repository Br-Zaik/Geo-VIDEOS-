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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


data class PlaybackUiState(
    val connecting: Boolean = true,
    val resolving: Boolean = false,
    val currentVideoId: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercentage: Int = 0,
    val error: String? = null
)

class GeoPlayerConnection private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
    ).buildAsync()

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var currentVideo: VideoItem? = null
    private var resolveJob: Job? = null
    private var requestSerial: Long = 0L
    private val pendingControllerActions = ArrayList<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updateFrom(player)

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    resolving = false,
                    error = error.localizedMessage ?: "No se pudo reproducir el video."
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
                                error = error.message ?: "No se pudo iniciar el reproductor."
                            )
                        }
                    }
            },
            ContextCompat.getMainExecutor(appContext)
        )

        scope.launch {
            while (isActive) {
                _controller.value?.let(::updateFrom)
                delay(750L)
            }
        }
    }

    fun open(video: VideoItem, autoplay: Boolean, dataSaver: Boolean) {
        val controllerNow = _controller.value
        if (
            currentVideo?.id == video.id &&
            controllerNow?.currentMediaItem?.mediaId == video.id &&
            controllerNow.mediaItemCount > 0
        ) {
            if (autoplay && controllerNow.playbackState == Player.STATE_IDLE) controllerNow.prepare()
            return
        }

        currentVideo = video
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
                val resolved = StreamResolver.resolve(video, dataSaver)
                if (requestId != requestSerial || currentVideo?.id != video.id) return@launch
                withController { controller ->
                    if (requestId != requestSerial || currentVideo?.id != video.id) return@withController
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
                    controller.setMediaItem(item, video.resumePositionMs.coerceAtLeast(0L))
                    controller.prepare()
                    controller.playWhenReady = autoplay
                    _state.update { it.copy(resolving = false, error = null) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestId != requestSerial || currentVideo?.id != video.id) return@launch
                _state.update {
                    it.copy(
                        resolving = false,
                        error = error.message
                            ?.takeIf(String::isNotBlank)
                            ?: "No se pudo obtener la transmisión del video."
                    )
                }
            }
        }
    }

    fun play() = withController { it.play() }
    fun pause() = withController { it.pause() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }
    fun setMuted(muted: Boolean) = withController { it.volume = if (muted) 0f else 1f }
    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed.coerceIn(0.25f, 2f)) }
    fun setRepeat(enabled: Boolean) = withController {
        it.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun stop() {
        requestSerial += 1L
        resolveJob?.cancel()
        currentVideo = null
        withController {
            it.stop()
            it.clearMediaItems()
        }
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

    private fun updateFrom(player: Player) {
        _state.update {
            it.copy(
                connecting = false,
                currentVideoId = player.currentMediaItem?.mediaId.orEmpty().ifBlank { it.currentVideoId },
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { duration -> duration > 0L } ?: it.durationMs,
                bufferedPercentage = player.bufferedPercentage.coerceIn(0, 100),
                error = player.playerError?.localizedMessage ?: it.error
            )
        }
    }

    private fun release() {
        resolveJob?.cancel()
        _controller.value?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        scope.cancel()
    }

    companion object {
        @Volatile
        private var instance: GeoPlayerConnection? = null

        fun get(context: Context): GeoPlayerConnection =
            instance ?: synchronized(this) {
                instance ?: GeoPlayerConnection(context).also { instance = it }
            }
    }
}
