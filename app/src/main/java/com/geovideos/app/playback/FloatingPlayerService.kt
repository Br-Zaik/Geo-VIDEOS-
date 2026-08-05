package com.geovideos.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.geovideos.app.MainActivity
import com.geovideos.app.R
import com.geovideos.app.data.MediaKind
import com.geovideos.app.data.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Ventana flotante propia, separada del minirreproductor interno y del PiP del sistema.
 * Mantiene el mismo MediaSession, por lo que no reinicia el video ni pierde el segundo.
 */
@OptIn(UnstableApi::class)
class FloatingPlayerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var connection: GeoPlayerConnection
    private lateinit var windowManager: WindowManager

    private var overlayRoot: FrameLayout? = null
    private var playerView: PlayerView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var activeController: MediaController? = null
    private var currentVideo: VideoItem? = null
    private var dataSaver: Boolean = false
    private var expandedWindow = false
    private var zoomScale = 1f
    private var selectedSpeed = 1f
    private var qualityOptions: List<Int?> = listOf(null)
    private var qualityLoadingJob: Job? = null

    private var qualityText: TextView? = null
    private var speedText: TextView? = null
    private var playButton: ImageButton? = null
    private var progressSeek: SeekBar? = null
    private var timeText: TextView? = null
    private var feedbackText: TextView? = null
    private var resizeButton: TextView? = null
    private var ignoreSeekCallback = false
    private var lastSeekDirection = 0
    private var accumulatedSeekSeconds = 0
    private var lastSeekAtMs = 0L

    private val controllerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val item = mediaItem ?: return
            val metadata = item.mediaMetadata
            currentVideo = VideoItem(
                id = item.mediaId,
                title = metadata.title?.toString().orEmpty(),
                channelTitle = metadata.artist?.toString().orEmpty(),
                thumbnailUrl = metadata.artworkUri?.toString().orEmpty(),
                source = item.mediaId,
                resumePositionMs = connection.progressState.value.positionMs,
                durationMs = connection.progressState.value.durationMs
            )
            qualityOptions = listOf(null)
            loadQualityOptions()
        }
    }

    override fun onCreate() {
        super.onCreate()
        connection = GeoPlayerConnection.get(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        observePlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            stopSelf()
            return START_NOT_STICKY
        }
        currentVideo = intent?.toVideoItem() ?: currentVideo
        dataSaver = intent?.getBooleanExtra(EXTRA_DATA_SAVER, false) ?: dataSaver
        if (currentVideo == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!canDrawOverlays()) {
            stopSelf()
            return START_NOT_STICKY
        }
        showOrRefreshOverlay()
        loadQualityOptions()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        qualityLoadingJob?.cancel()
        activeController?.removeListener(controllerListener)
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun observePlayer() {
        scope.launch {
            connection.controller.filterNotNull().collect { controller ->
                if (activeController !== controller) {
                    activeController?.removeListener(controllerListener)
                    activeController = controller
                    controller.addListener(controllerListener)
                }
                attachController(controller)
            }
        }
        scope.launch {
            connection.coreState.collect { state ->
                playButton?.setImageResource(
                    if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
                updateQualityLabel(state.videoHeight)
            }
        }
        scope.launch {
            connection.progressState.collect { progress ->
                val duration = progress.durationMs.coerceAtLeast(0L)
                val position = progress.positionMs.coerceIn(0L, duration.takeIf { it > 0L } ?: Long.MAX_VALUE)
                ignoreSeekCallback = true
                progressSeek?.progress = if (duration > 0L) {
                    ((position.toDouble() / duration.toDouble()) * SEEK_MAX).roundToInt().coerceIn(0, SEEK_MAX)
                } else 0
                ignoreSeekCallback = false
                timeText?.text = "${formatTime(position)}  /  ${formatTime(duration)}"
            }
        }
        scope.launch {
            connection.preferredQualityHeight.collect { updateQualityLabel(connection.coreState.value.videoHeight) }
        }
    }

    private fun showOrRefreshOverlay() {
        if (overlayRoot == null) createOverlay()
        activeController?.let(::attachController)
    }

    private fun createOverlay() {
        val width = dp(350).coerceAtMost(resources.displayMetrics.widthPixels - dp(16))
        val height = dp(284).coerceAtMost(resources.displayMetrics.heightPixels - dp(96))
        val params = WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(84)
        }
        windowParams = params

        val root = FrameLayout(this).apply {
            background = roundedBackground(0xFF0C0C0F.toInt(), 12f)
            clipToOutline = true
            elevation = dp(10).toFloat()
        }
        overlayRoot = root

        val vertical = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(7))
        }
        root.addView(vertical, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dragTitle = TextView(this).apply {
            text = "Geo Videos"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        installDragHandle(dragTitle)
        topRow.addView(dragTitle, LinearLayout.LayoutParams(0, dp(32), 1f))

        qualityText = compactTextButton("Automático") { cycleQuality() }.also {
            topRow.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)))
        }
        speedText = compactTextButton("1x") { cycleSpeed() }.also {
            topRow.addView(it, LinearLayout.LayoutParams(dp(48), dp(32)))
        }
        topRow.addView(textButton("×", 24f) { closeFloatingAndStopPlayback() }, LinearLayout.LayoutParams(dp(40), dp(32)))
        vertical.addView(topRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)))

        val videoFrame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val view = (LayoutInflater.from(this)
            .inflate(R.layout.geo_player_texture, videoFrame, false) as PlayerView).apply {
            useController = false
            controllerAutoShow = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setKeepContentOnPlayerReset(true)
            setShutterBackgroundColor(Color.TRANSPARENT)
        }
        playerView = view
        videoFrame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        feedbackText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedBackground(0xB3000000.toInt(), 22f)
            visibility = View.GONE
        }
        videoFrame.addView(
            feedbackText,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        installVideoGestures(view)
        vertical.addView(videoFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(mediaButton(android.R.drawable.ic_media_previous, "Anterior") { connection.playPrevious() })
        playButton = mediaButton(android.R.drawable.ic_media_play, "Reproducir") {
            if (connection.coreState.value.isPlaying) connection.pause() else connection.play()
        }.also { controls.addView(it) }
        controls.addView(mediaButton(android.R.drawable.ic_media_next, "Siguiente") { connection.playNext() })
        timeText = TextView(this).apply {
            text = "0:00 / 0:00"
            setTextColor(0xFFD7D4DD.toInt())
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(4), 0)
        }
        controls.addView(timeText, LinearLayout.LayoutParams(0, dp(38), 1f))
        resizeButton = textButton(if (expandedWindow) "▣" else "□", 20f) { toggleWindowSize() }.also {
            controls.addView(it, LinearLayout.LayoutParams(dp(42), dp(38)))
        }
        controls.addView(textButton("⛶", 19f) { openFullscreenInApp() }, LinearLayout.LayoutParams(dp(42), dp(38)))
        vertical.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))

        progressSeek = SeekBar(this).apply {
            max = SEEK_MAX
            progress = 0
            setPadding(0, 0, 0, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser || ignoreSeekCallback) return
                    val duration = connection.progressState.value.durationMs
                    if (duration > 0L) {
                        val target = duration * progress / SEEK_MAX
                        timeText?.text = "${formatTime(target)}  /  ${formatTime(duration)}"
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val duration = connection.progressState.value.durationMs
                    val progress = seekBar?.progress ?: return
                    if (duration > 0L) connection.seekTo(duration * progress / SEEK_MAX)
                }
            })
        }
        vertical.addView(progressSeek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)))

        runCatching { windowManager.addView(root, params) }
            .onFailure { stopSelf() }
    }

    private fun attachController(controller: MediaController) {
        selectedSpeed = controller.playbackParameters.speed
        speedText?.text = if (abs(selectedSpeed - 1f) < 0.01f) "1x" else "${selectedSpeed}x"
        playerView?.let { view ->
            if (view.player !== controller) view.player = controller
        }
    }

    private fun loadQualityOptions() {
        val video = currentVideo ?: return
        qualityLoadingJob?.cancel()
        qualityLoadingJob = scope.launch {
            qualityText?.text = "Calidad…"
            val options = runCatching {
                connection.streamOptions(video, includeDownloadSizes = false)
            }.getOrNull()
            qualityOptions = buildList<Int?> {
                add(null)
                options?.qualities.orEmpty()
                    .map { it.height }
                    .filter { it > 0 }
                    .distinct()
                    .sorted()
                    .forEach(::add)
            }.distinct()
            updateQualityLabel(connection.coreState.value.videoHeight)
        }
    }

    private fun cycleQuality() {
        val video = currentVideo ?: return
        if (qualityOptions.size <= 1) {
            loadQualityOptions()
            showFeedback("Buscando calidades…")
            return
        }
        val current = connection.preferredQualityHeight.value
        val index = qualityOptions.indexOf(current).takeIf { it >= 0 } ?: 0
        val next = qualityOptions[(index + 1) % qualityOptions.size]
        connection.selectQuality(
            video = video.copy(resumePositionMs = connection.progressState.value.positionMs),
            height = next,
            dataSaver = dataSaver
        )
        showFeedback(next?.let { "Calidad ${it}p" } ?: "Calidad automática")
    }

    private fun updateQualityLabel(actualHeight: Int) {
        val preferred = connection.preferredQualityHeight.value
        qualityText?.text = when {
            preferred != null -> "${preferred}p"
            actualHeight > 0 -> "Auto (${actualHeight}p)"
            else -> "Automático"
        }
    }

    private fun cycleSpeed() {
        val speeds = floatArrayOf(1f, 1.25f, 1.5f, 2f, 0.75f)
        val index = speeds.indexOfFirst { abs(it - selectedSpeed) < 0.01f }.takeIf { it >= 0 } ?: 0
        selectedSpeed = speeds[(index + 1) % speeds.size]
        connection.setSpeed(selectedSpeed)
        speedText?.text = if (selectedSpeed == 1f) "1x" else "${selectedSpeed}x"
        showFeedback("Velocidad ${speedText?.text}")
    }

    private fun installVideoGestures(view: PlayerView) {
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomScale = (zoomScale * detector.scaleFactor).coerceIn(1f, 3f)
                applyZoom()
                showFeedback("Zoom ${(zoomScale * 100f).roundToInt()}%")
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                showFeedback("Zoom ${(zoomScale * 100f).roundToInt()}%")
            }
        })
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val forward = e.x >= view.width / 2f
                val delta = if (forward) 10_000L else -10_000L
                val progress = connection.progressState.value
                connection.seekTo((progress.positionMs + delta).coerceIn(0L, progress.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE))
                showAccumulatedSeek(if (forward) 1 else -1)
                return true
            }
        })
        view.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun showAccumulatedSeek(direction: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        if (direction == lastSeekDirection && now - lastSeekAtMs <= 1_100L) {
            accumulatedSeekSeconds += 10
        } else {
            accumulatedSeekSeconds = 10
        }
        lastSeekDirection = direction
        lastSeekAtMs = now
        showFeedback(
            if (direction > 0) "+$accumulatedSeekSeconds segundos" else "−$accumulatedSeekSeconds segundos"
        )
    }

    private fun applyZoom() {
        playerView?.videoSurfaceView?.apply {
            scaleX = zoomScale
            scaleY = zoomScale
        }
    }

    private fun showFeedback(message: String) {
        feedbackText?.apply {
            text = message
            visibility = View.VISIBLE
            alpha = 1f
        }
        handler.removeCallbacks(hideFeedback)
        handler.postDelayed(hideFeedback, 950L)
    }

    private val hideFeedback = Runnable { feedbackText?.visibility = View.GONE }

    private fun toggleWindowSize() {
        expandedWindow = !expandedWindow
        val params = windowParams ?: return
        params.width = dp(if (expandedWindow) 420 else 350)
            .coerceAtMost(resources.displayMetrics.widthPixels - dp(12))
        params.height = dp(if (expandedWindow) 340 else 284)
            .coerceAtMost(resources.displayMetrics.heightPixels - dp(48))
        resizeButton?.text = if (expandedWindow) "▣" else "□"
        overlayRoot?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    private fun closeFloatingAndStopPlayback() {
        connection.stop()
        stopSelf()
    }

    private fun openFullscreenInApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_FULLSCREEN_PLAYER, true)
        }
        startActivity(intent)
        stopSelf()
    }

    private fun installDragHandle(view: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            val params = windowParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).roundToInt()
                    params.y = startY + (event.rawY - touchY).roundToInt()
                    overlayRoot?.let { runCatching { windowManager.updateViewLayout(it, params) } }
                    true
                }
                else -> false
            }
        }
    }

    private fun removeOverlay() {
        val hadOverlay = overlayRoot != null || playerView != null
        playerView?.player = null
        overlayRoot?.let { runCatching { windowManager.removeView(it) } }
        overlayRoot = null
        playerView = null
        feedbackText = null
        resizeButton = null
        windowParams = null
        if (hadOverlay) _surfaceGeneration.value += 1
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Geo Videos")
        .setContentText("Ventana emergente activa")
        .setOngoing(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                22,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ventana emergente", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mantiene el reproductor flotante de Geo Videos"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun compactTextButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 11f
        gravity = Gravity.CENTER
        setPadding(dp(7), 0, dp(7), 0)
        background = roundedBackground(0xFF25252B.toInt(), 8f)
        setOnClickListener { onClick() }
    }

    private fun textButton(label: String, sizeSp: Float, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = sizeSp
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    private fun mediaButton(icon: Int, description: String, onClick: () -> Unit): ImageButton = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = description
        setColorFilter(Color.WHITE)
        setBackgroundColor(Color.TRANSPARENT)
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(38))
    }

    private fun roundedBackground(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.roundToInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun formatTime(milliseconds: Long): String {
        val total = milliseconds.coerceAtLeast(0L) / 1000L
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    private fun Intent.toVideoItem(): VideoItem? {
        val id = getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        if (id.isBlank()) return null
        return VideoItem(
            id = id,
            title = getStringExtra(EXTRA_TITLE).orEmpty(),
            channelTitle = getStringExtra(EXTRA_CHANNEL_TITLE).orEmpty(),
            thumbnailUrl = getStringExtra(EXTRA_THUMBNAIL).orEmpty(),
            channelId = getStringExtra(EXTRA_CHANNEL_ID).orEmpty(),
            channelThumbnailUrl = getStringExtra(EXTRA_CHANNEL_THUMBNAIL).orEmpty(),
            publishedAt = getStringExtra(EXTRA_PUBLISHED_AT).orEmpty(),
            description = getStringExtra(EXTRA_DESCRIPTION).orEmpty(),
            isLive = getBooleanExtra(EXTRA_IS_LIVE, false),
            mediaKind = runCatching {
                MediaKind.valueOf(getStringExtra(EXTRA_MEDIA_KIND).orEmpty())
            }.getOrDefault(MediaKind.YOUTUBE),
            source = getStringExtra(EXTRA_SOURCE).orEmpty().ifBlank { id },
            resumePositionMs = getLongExtra(EXTRA_RESUME_POSITION, 0L),
            durationMs = getLongExtra(EXTRA_DURATION, 0L)
        )
    }

    companion object {
        private const val ACTION_SHOW = "com.geovideos.app.action.SHOW_FLOATING_PLAYER"
        private const val ACTION_CLOSE = "com.geovideos.app.action.CLOSE_FLOATING_PLAYER"
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_CHANNEL_TITLE = "channel_title"
        private const val EXTRA_THUMBNAIL = "thumbnail"
        private const val EXTRA_CHANNEL_ID = "channel_id"
        private const val EXTRA_CHANNEL_THUMBNAIL = "channel_thumbnail"
        private const val EXTRA_PUBLISHED_AT = "published_at"
        private const val EXTRA_DESCRIPTION = "description"
        private const val EXTRA_IS_LIVE = "is_live"
        private const val EXTRA_MEDIA_KIND = "media_kind"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_RESUME_POSITION = "resume_position"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_DATA_SAVER = "data_saver"
        private const val CHANNEL_ID = "geo_floating_player"
        private const val NOTIFICATION_ID = 4217
        private const val SEEK_MAX = 1_000

        private val _surfaceGeneration = MutableStateFlow(0)
        val surfaceGeneration = _surfaceGeneration.asStateFlow()

        fun start(context: Context, video: VideoItem, dataSaver: Boolean) {
            val intent = Intent(context, FloatingPlayerService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_VIDEO_ID, video.id)
                putExtra(EXTRA_TITLE, video.title)
                putExtra(EXTRA_CHANNEL_TITLE, video.channelTitle)
                putExtra(EXTRA_THUMBNAIL, video.thumbnailUrl)
                putExtra(EXTRA_CHANNEL_ID, video.channelId)
                putExtra(EXTRA_CHANNEL_THUMBNAIL, video.channelThumbnailUrl)
                putExtra(EXTRA_PUBLISHED_AT, video.publishedAt)
                putExtra(EXTRA_DESCRIPTION, video.description)
                putExtra(EXTRA_IS_LIVE, video.isLive)
                putExtra(EXTRA_MEDIA_KIND, video.mediaKind.name)
                putExtra(EXTRA_SOURCE, video.source)
                putExtra(EXTRA_RESUME_POSITION, video.resumePositionMs)
                putExtra(EXTRA_DURATION, video.durationMs)
                putExtra(EXTRA_DATA_SAVER, dataSaver)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingPlayerService::class.java))
        }
    }
}
