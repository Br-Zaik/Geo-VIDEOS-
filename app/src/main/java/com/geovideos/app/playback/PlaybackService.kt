package com.geovideos.app.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.geovideos.app.MainActivity
import java.io.File

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var playbackCache: SimpleCache? = null
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null
    private var exoPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Accept-Language" to "es-PE,es;q=0.9,en;q=0.7",
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/"
                )
            )
        val upstreamFactory = DefaultDataSource.Factory(this, httpFactory)
        val cache = SimpleCache(
            File(cacheDir, "media3-playback"),
            LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES),
            StandaloneDatabaseProvider(this)
        ).also { playbackCache = it }
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .also { cacheDataSourceFactory = it }
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_AFTER_REBUFFER_MS
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(5_000L)
            .setSeekForwardIncrementMs(15_000L)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)
            }
            .also { exoPlayer = it }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_PLAY_RESOLVED) {
            playResolved(intent)
        }
        return result
    }

    private fun playResolved(intent: Intent) {
        val player = exoPlayer ?: return
        val dataSourceFactory = cacheDataSourceFactory ?: return
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL).orEmpty()
        if (videoUrl.isBlank()) return

        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
        val artwork = intent.getStringExtra(EXTRA_ARTWORK).orEmpty()
        val videoMime = intent.getStringExtra(EXTRA_VIDEO_MIME)
        val audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL).orEmpty()
        val audioMime = intent.getStringExtra(EXTRA_AUDIO_MIME)
        val startPositionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L).coerceAtLeast(0L)
        val autoplay = intent.getBooleanExtra(EXTRA_AUTOPLAY, true)
        val repeat = intent.getBooleanExtra(EXTRA_REPEAT, false)

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artwork.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .build()

        val videoItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(videoUrl)
            .setMimeType(videoMime)
            .setMediaMetadata(metadata)
            .build()
        val progressiveFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
        val videoSource = progressiveFactory.createMediaSource(videoItem)
        val finalSource = if (audioUrl.isNotBlank()) {
            val audioItem = MediaItem.Builder()
                .setMediaId("$mediaId-audio")
                .setUri(audioUrl)
                .setMimeType(audioMime)
                .build()
            val audioSource = progressiveFactory.createMediaSource(audioItem)
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }

        player.setTrackSelectionParameters(
            player.trackSelectionParameters
                .buildUpon()
                .clearVideoSizeConstraints()
                .build()
        )
        player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setMediaSource(finalSource, startPositionMs)
        player.prepare()
        player.playWhenReady = autoplay
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        cacheDataSourceFactory = null
        playbackCache?.release()
        playbackCache = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_PLAY_RESOLVED = "com.geovideos.app.action.PLAY_RESOLVED"
        private const val EXTRA_MEDIA_ID = "media_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_ARTWORK = "artwork"
        private const val EXTRA_VIDEO_URL = "video_url"
        private const val EXTRA_VIDEO_MIME = "video_mime"
        private const val EXTRA_AUDIO_URL = "audio_url"
        private const val EXTRA_AUDIO_MIME = "audio_mime"
        private const val EXTRA_POSITION_MS = "position_ms"
        private const val EXTRA_AUTOPLAY = "autoplay"
        private const val EXTRA_REPEAT = "repeat"

        internal fun playResolved(
            context: Context,
            videoId: String,
            title: String,
            artist: String,
            artwork: String,
            resolved: ResolvedMedia,
            positionMs: Long,
            autoplay: Boolean,
            repeat: Boolean
        ) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_PLAY_RESOLVED
                putExtra(EXTRA_MEDIA_ID, videoId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_ARTWORK, artwork)
                putExtra(EXTRA_VIDEO_URL, resolved.uri)
                putExtra(EXTRA_VIDEO_MIME, resolved.mimeType)
                putExtra(EXTRA_AUDIO_URL, resolved.audioUri)
                putExtra(EXTRA_AUDIO_MIME, resolved.audioMimeType)
                putExtra(EXTRA_POSITION_MS, positionMs.coerceAtLeast(0L))
                putExtra(EXTRA_AUTOPLAY, autoplay)
                putExtra(EXTRA_REPEAT, repeat)
            }
            runCatching { context.startService(intent) }
        }

        private const val CACHE_SIZE_BYTES = 128L * 1024L * 1024L
        private const val MIN_BUFFER_MS = 6_000
        private const val MAX_BUFFER_MS = 25_000
        private const val BUFFER_FOR_PLAYBACK_MS = 500
        private const val BUFFER_AFTER_REBUFFER_MS = 1_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
