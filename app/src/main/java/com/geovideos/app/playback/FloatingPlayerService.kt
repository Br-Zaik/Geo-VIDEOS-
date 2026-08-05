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
import android.os.SystemClock
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Ventana flotante propia, independiente del minirreproductor interno.
 *
 * La ventana empieza compacta, se puede mover y alternar entre tres tamanos.
 * Los controles se superponen sobre el video y se ocultan automaticamente para
 * no convertir el reproductor en un panel grande dentro de la aplicacion.
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
    private var controlsLayer: View? = null
    private var activeController: MediaController? = null
    private var currentVideo: VideoItem? = null
    private var dataSaver: Boolean = false
    private var zoomScale = 1f
    private var selectedSpeed = 1f
    private var qualityOptions: List<Int?> = listOf(null)
    private var qualityLoadingJob: Job? = null
    private var controlsVisible = true
    private var sizeIndex = 0

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

    private val preferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

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
        sizeIndex = preferences.getInt(KEY_SIZE_INDEX, 0).coerceIn(0, 2)
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
        if (currentVideo == null || !canDrawOverlays()) {
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
        handler.removeCallbacksAndMessages(null)
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
                    if (state.isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
                updateQualityLabel(state.videoHeight)
            }
        }
        scope.launch {
            connection.progressState.collect { progress ->
                val duration = progress.durationMs.coerceAtLeast(0L)
                val position = progress.positionMs.coerceIn(
                    0L,
                    duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                )
                ignoreSeekCallback = true
                progressSeek?.progress = if (duration > 0L) {
                    ((position.toDouble() / duration.toDouble()) * SEEK_MAX)
                        .roundToInt()
                        .coerceIn(0, SEEK_MAX)
                } else {
                    0
                }
                ignoreSeekCallback = false
                timeText?.text = "${formatTime(position)} / ${formatTime(duration)}"
            }
        }
        scope.launch {
            connection.preferredQualityHeight.collect {
                updateQualityLabel(connection.coreState.value.videoHeight)
            }
        }
    }

    private fun showOrRefreshOverlay() {
        if (overlayRoot == null) createOverlay()
        activeController?.let(::attachController)
        showControls(autoHide = true)
    }

    private fun createOverlay() {
        val (initialWidth, initialHeight) = sizeForIndex(sizeIndex)
        val params = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt(KEY_X, defaultX(initialWidth))
            y = preferences.getInt(KEY_Y, defaultY(initialHeight))
            clampWindowPosition(this)
        }
        windowParams = params

        val root = FrameLayout(this).apply {
            background = roundedBackground(Color.BLACK, 10f)
            clipToOutline = true
            elevation = dp(12).toFloat()
        }
        overlayRoot = root

        val view = (LayoutInflater.from(this)
            .inflate(R.layout.geo_player_texture, root, false) as PlayerView).apply {
            useController = false
            controllerAutoShow = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setKeepContentOnPlayerReset(true)
            setShutterBackgroundColor(Color.TRANSPARENT)
        }
        playerView = view
        root.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        feedbackText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedBackground(0xC8000000.toInt(), 22f)
            visibility = View.GONE
        }
        root.addView(
            feedbackText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        controlsLayer = createControlsLayer().also { layer ->
            root.addView(
                layer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        installVideoGestures(view)

        runCatching { windowManager.addView(root, params) }
            .onSuccess { showControls(autoHide = true) }
            .onFailure { stopSelf() }
    }

    private fun createControlsLayer(): View {
        val layer = FrameLayout(this)

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(dp(6), dp(5), dp(5), 0)
            background = verticalScrim(top = true)
        }
        qualityText = compactTextButton("Auto") { cycleQuality() }
        speedText = compactTextButton("1x") { cycleSpeed() }
        topRow.addView(qualityText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(27)))
        topRow.addView(speedText, LinearLayout.LayoutParams(dp(40), dp(27)).apply { marginStart = dp(3) })
        topRow.addView(textButton("×", 19f) { closeFloatingAndStopPlayback() }, LinearLayout.LayoutParams(dp(32), dp(27)).apply { marginStart = dp(2) })
        layer.addView(
            topRow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34),
                Gravity.TOP
            )
        )

        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(5), 0, dp(5), dp(3))
            background = verticalScrim(top = false)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(mediaButton(android.R.drawable.ic_media_previous, "Anterior") {
            connection.playPrevious()
            showControls(autoHide = true)
        })
        playButton = mediaButton(android.R.drawable.ic_media_play, "Reproducir") {
            if (connection.coreState.value.isPlaying) connection.pause() else connection.play()
            showControls(autoHide = true)
        }.also { controls.addView(it) }
        controls.addView(mediaButton(android.R.drawable.ic_media_next, "Siguiente") {
            connection.playNext()
            showControls(autoHide = true)
        })
        controls.addView(View(this), LinearLayout.LayoutParams(0, dp(30), 1f))
        resizeButton = textButton(sizeLabel(), 17f) { toggleWindowSize() }.also {
            controls.addView(it, LinearLayout.LayoutParams(dp(31), dp(30)))
        }
        controls.addView(
            textButton("⛶", 17f) { openFullscreenInApp() },
            LinearLayout.LayoutParams(dp(31), dp(30))
        )
        bottomPanel.addView(
            controls,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30))
        )

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
                        timeText?.text = "${formatTime(target)} / ${formatTime(duration)}"
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    handler.removeCallbacks(hideControlsRunnable)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val duration = connection.progressState.value.durationMs
                    val progress = seekBar?.progress ?: return
                    if (duration > 0L) connection.seekTo(duration * progress / SEEK_MAX)
                    showControls(autoHide = true)
                }
            })
        }
        bottomPanel.addView(
            progressSeek,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12))
        )

        layer.addView(
            bottomPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44),
                Gravity.BOTTOM
            )
        )
        return layer
    }

    private fun attachController(controller: MediaController) {
        selectedSpeed = controller.playbackParameters.speed
        speedText?.text = speedLabel(selectedSpeed)
        playerView?.let { view ->
            if (view.player !== controller) view.player = controller
        }
    }

    private fun loadQualityOptions() {
        val video = currentVideo ?: return
        qualityLoadingJob?.cancel()
        qualityLoadingJob = scope.launch {
            qualityText?.text = "..."
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
            showFeedback("Buscando calidades")
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
        showFeedback(next?.let { "Calidad ${it}p" } ?: "Calidad automatica")
        showControls(autoHide = true)
    }

    private fun updateQualityLabel(actualHeight: Int) {
        val preferred = connection.preferredQualityHeight.value
        qualityText?.text = when {
            preferred != null -> "${preferred}p"
            actualHeight > 0 -> "A ${actualHeight}p"
            else -> "Auto"
        }
    }

    private fun cycleSpeed() {
        val speeds = floatArrayOf(1f, 1.25f, 1.5f, 2f, 0.75f)
        val index = speeds.indexOfFirst { abs(it - selectedSpeed) < 0.01f }
            .takeIf { it >= 0 } ?: 0
        selectedSpeed = speeds[(index + 1) % speeds.size]
        connection.setSpeed(selectedSpeed)
        speedText?.text = speedLabel(selectedSpeed)
        showFeedback("Velocidad ${speedLabel(selectedSpeed)}")
        showControls(autoHide = true)
    }

    private fun installVideoGestures(view: PlayerView) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        var downRawX = 0f
        var downRawY = 0f
        var startWindowX = 0
        var startWindowY = 0
        var draggingWindow = false

        val scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    zoomScale = (zoomScale * detector.scaleFactor).coerceIn(1f, 3f)
                    applyZoom()
                    showFeedback("Zoom ${(zoomScale * 100f).roundToInt()}%")
                    return true
                }
            }
        )

        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (controlsVisible) hideControls() else showControls(autoHide = true)
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val forward = e.x >= view.width / 2f
                    val delta = if (forward) 10_000L else -10_000L
                    val progress = connection.progressState.value
                    connection.seekTo(
                        (progress.positionMs + delta).coerceIn(
                            0L,
                            progress.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                        )
                    )
                    showAccumulatedSeek(if (forward) 1 else -1)
                    return true
                }
            }
        )

        view.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (!draggingWindow) gestureDetector.onTouchEvent(event)
            val params = windowParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startWindowX = params?.x ?: 0
                    startWindowY = params?.y ?: 0
                    draggingWindow = false
                    handler.removeCallbacks(hideControlsRunnable)
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 && !scaleDetector.isInProgress && params != null) {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!draggingWindow && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            draggingWindow = true
                            hideControls()
                        }
                        if (draggingWindow) {
                            params.x = startWindowX + dx.roundToInt()
                            params.y = startWindowY + dy.roundToInt()
                            clampWindowPosition(params)
                            overlayRoot?.let {
                                runCatching { windowManager.updateViewLayout(it, params) }
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (draggingWindow) saveWindowState()
                    draggingWindow = false
                    if (controlsVisible) scheduleControlsHide()
                }
            }
            true
        }
    }

    private fun showAccumulatedSeek(direction: Int) {
        val now = SystemClock.uptimeMillis()
        accumulatedSeekSeconds = if (
            direction == lastSeekDirection && now - lastSeekAtMs <= 1_100L
        ) {
            accumulatedSeekSeconds + 10
        } else {
            10
        }
        lastSeekDirection = direction
        lastSeekAtMs = now
        showFeedback(
            if (direction > 0) "+$accumulatedSeekSeconds segundos"
            else "-$accumulatedSeekSeconds segundos"
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
        handler.removeCallbacks(hideFeedbackRunnable)
        handler.postDelayed(hideFeedbackRunnable, 950L)
    }

    private fun showControls(autoHide: Boolean) {
        controlsVisible = true
        controlsLayer?.visibility = View.VISIBLE
        controlsLayer?.animate()?.alpha(1f)?.setDuration(120L)?.start()
        if (autoHide) scheduleControlsHide()
    }

    private fun scheduleControlsHide() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 2_600L)
    }

    private fun hideControls() {
        controlsVisible = false
        handler.removeCallbacks(hideControlsRunnable)
        controlsLayer?.animate()
            ?.alpha(0f)
            ?.setDuration(140L)
            ?.withEndAction {
                if (!controlsVisible) controlsLayer?.visibility = View.GONE
            }
            ?.start()
    }

    private val hideFeedbackRunnable = Runnable { feedbackText?.visibility = View.GONE }
    private val hideControlsRunnable = Runnable { hideControls() }

    private fun toggleWindowSize() {
        sizeIndex = (sizeIndex + 1) % 3
        val params = windowParams ?: return
        val oldCenterX = params.x + params.width / 2
        val oldCenterY = params.y + params.height / 2
        val (width, height) = sizeForIndex(sizeIndex)
        params.width = width
        params.height = height
        params.x = oldCenterX - width / 2
        params.y = oldCenterY - height / 2
        clampWindowPosition(params)
        resizeButton?.text = sizeLabel()
        overlayRoot?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        saveWindowState()
        showFeedback(
            when (sizeIndex) {
                0 -> "Ventana pequena"
                1 -> "Ventana mediana"
                else -> "Ventana grande"
            }
        )
        showControls(autoHide = true)
    }

    private fun sizeForIndex(index: Int): Pair<Int, Int> {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val fraction = when (index.coerceIn(0, 2)) {
            0 -> 0.46f
            1 -> 0.64f
            else -> 0.84f
        }
        val minimum = when (index.coerceIn(0, 2)) {
            0 -> dp(174)
            1 -> dp(230)
            else -> dp(286)
        }
        val width = (screenWidth * fraction).roundToInt()
            .coerceAtLeast(minimum)
            .coerceAtMost(screenWidth - dp(12))
        val height = (width * 9f / 16f).roundToInt()
            .coerceAtMost(screenHeight - dp(48))
        return width to height
    }

    private fun sizeLabel(): String = when (sizeIndex) {
        0 -> "+"
        1 -> "+"
        else -> "-"
    }

    private fun defaultX(width: Int): Int =
        (resources.displayMetrics.widthPixels - width - dp(10)).coerceAtLeast(0)

    private fun defaultY(height: Int): Int =
        (resources.displayMetrics.heightPixels - height - dp(110)).coerceAtLeast(dp(24))

    private fun clampWindowPosition(params: WindowManager.LayoutParams) {
        val maxX = (resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0)
        val maxY = (resources.displayMetrics.heightPixels - params.height - dp(16)).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun saveWindowState() {
        val params = windowParams ?: return
        preferences.edit()
            .putInt(KEY_X, params.x)
            .putInt(KEY_Y, params.y)
            .putInt(KEY_SIZE_INDEX, sizeIndex)
            .apply()
    }

    private fun closeFloatingAndStopPlayback() {
        connection.stop()
        stopSelf()
    }

    private fun openFullscreenInApp() {
        saveWindowState()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_FULLSCREEN_PLAYER, true)
        }
        startActivity(intent)
        stopSelf()
    }

    private fun removeOverlay() {
        val hadOverlay = overlayRoot != null || playerView != null
        saveWindowState()
        playerView?.player = null
        overlayRoot?.let { runCatching { windowManager.removeView(it) } }
        overlayRoot = null
        playerView = null
        controlsLayer = null
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
            NotificationChannel(
                CHANNEL_ID,
                "Ventana emergente",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene el reproductor flotante de Geo Videos"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun compactTextButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 10.5f
            gravity = Gravity.CENTER
            setPadding(dp(7), 0, dp(7), 0)
            background = roundedBackground(0xC826262C.toInt(), 7f)
            setOnClickListener {
                onClick()
                showControls(autoHide = true)
            }
        }

    private fun textButton(label: String, sizeSp: Float, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = sizeSp
            gravity = Gravity.CENTER
            background = roundedBackground(0x8A000000.toInt(), 6f)
            setOnClickListener {
                onClick()
                showControls(autoHide = true)
            }
        }

    private fun mediaButton(
        icon: Int,
        description: String,
        onClick: () -> Unit
    ): ImageButton = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = description
        setColorFilter(Color.WHITE)
        setBackgroundColor(Color.TRANSPARENT)
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
    }

    private fun roundedBackground(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.roundToInt()).toFloat()
    }

    private fun verticalScrim(top: Boolean) = GradientDrawable(
        if (top) GradientDrawable.Orientation.TOP_BOTTOM
        else GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(0xC9000000.toInt(), 0x00000000)
    )

    private fun speedLabel(speed: Float): String =
        if (abs(speed - 1f) < 0.01f) "1x" else "${speed}x"

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun formatTime(milliseconds: Long): String {
        val total = milliseconds.coerceAtLeast(0L) / 1000L
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
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
        private const val PREFS_NAME = "geo_floating_player"
        private const val KEY_X = "window_x"
        private const val KEY_Y = "window_y"
        private const val KEY_SIZE_INDEX = "window_size_index"

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
