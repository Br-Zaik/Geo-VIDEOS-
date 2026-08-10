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
 * La ventana tiene dos tamanos estables (mini y grande), se puede mover libremente
 * y alterna de tamano desde el control inferior derecho manteniendo la proporcion 16:9.
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
    private var qualityPanel: LinearLayout? = null
    private var speedPanel: LinearLayout? = null
    private var activeController: MediaController? = null
    private var currentVideo: VideoItem? = null
    private var dataSaver: Boolean = false
    private var zoomScale = 1f
    private var selectedSpeed = 1f
    private var qualityOptions: List<Int?> = listOf(null)
    private var qualityLoadingJob: Job? = null
    private var controlsVisible = true
    private var windowLarge = false

    private var qualityText: TextView? = null
    private var speedText: TextView? = null
    private var playButton: ImageButton? = null
    private var progressSeek: SeekBar? = null
    private var timeText: TextView? = null
    private var feedbackText: TextView? = null
    private var resizeHandle: View? = null
    private var openAppButton: View? = null
    private var previousButton: View? = null
    private var nextButton: View? = null
    private var controlsRow: LinearLayout? = null
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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowLarge = preferences.getBoolean(KEY_LARGE, false)
        createNotificationChannel()
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
        if (!::connection.isInitialized) {
            connection = GeoPlayerConnection.get(applicationContext)
            startForeground(NOTIFICATION_ID, buildNotification())
            observePlayer()
        }
        showOrRefreshOverlay()
        loadQualityOptions()
        return START_NOT_STICKY
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
        val (initialWidth, initialHeight) = initialWindowSize()
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
        qualityText = compactTextButton("Auto") { toggleQualityPanel() }
        speedText = compactTextButton("1x") { toggleSpeedPanel() }
        topRow.addView(qualityText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)))
        topRow.addView(speedText, LinearLayout.LayoutParams(dp(42), dp(28)).apply { marginStart = dp(4) })
        topRow.addView(
            textButton("×", 20f) { closeFloatingAndStopPlayback() },
            LinearLayout.LayoutParams(dp(34), dp(28)).apply { marginStart = dp(3) }
        )
        layer.addView(
            topRow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(36),
                Gravity.TOP
            )
        )

        qualityPanel = createChoicePanel().also { panel ->
            layer.addView(
                panel,
                FrameLayout.LayoutParams(dp(156), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(38)
                    marginEnd = dp(78)
                }
            )
        }
        speedPanel = createChoicePanel().also { panel ->
            layer.addView(
                panel,
                FrameLayout.LayoutParams(dp(128), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(38)
                    marginEnd = dp(42)
                }
            )
        }

        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(4), 0, dp(4), dp(2))
            background = verticalScrim(top = false)
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }.also { controlsRow = it }
        openAppButton = textButton("↩", 18f) { openPlayerInApp() }.also { button ->
            controls.addView(button, LinearLayout.LayoutParams(dp(34), dp(32)))
        }
        controls.addView(View(this), LinearLayout.LayoutParams(0, dp(30), 1f))
        previousButton = mediaButton(android.R.drawable.ic_media_previous, "Anterior") {
            connection.playPrevious()
            showControls(autoHide = true)
        }.also { controls.addView(it) }
        playButton = mediaButton(android.R.drawable.ic_media_play, "Reproducir") {
            if (connection.coreState.value.isPlaying) connection.pause() else connection.play()
            showControls(autoHide = true)
        }.also { controls.addView(it) }
        nextButton = mediaButton(android.R.drawable.ic_media_next, "Siguiente") {
            connection.playNext()
            showControls(autoHide = true)
        }.also { controls.addView(it) }
        controls.addView(View(this), LinearLayout.LayoutParams(0, dp(30), 1f))
        resizeHandle = ImageButton(this).apply {
            setImageResource(R.drawable.ic_player_resize)
            contentDescription = "Arrastra para cambiar el tamaño"
            setColorFilter(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(6), dp(6), dp(4), dp(4))
            installResizeGesture(this)
        }.also { handle ->
            controls.addView(handle, LinearLayout.LayoutParams(dp(40), dp(36)))
        }
        bottomPanel.addView(
            controls,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32))
        )

        timeText = TextView(this).apply {
            text = "0:00 / 0:00"
            setTextColor(Color.WHITE)
            textSize = 10.5f
            gravity = Gravity.CENTER
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
        }
        bottomPanel.addView(
            timeText,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16))
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
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14))
        )

        layer.addView(
            bottomPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64),
                Gravity.BOTTOM
            )
        )
        updateControlDensity()
        return layer
    }

    private fun createChoicePanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        background = roundedBackground(0xF21D1C22.toInt(), 10f)
        elevation = dp(10).toFloat()
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    private fun toggleQualityPanel() {
        if (qualityPanel?.visibility == View.VISIBLE) {
            hideChoicePanels()
            showControls(autoHide = true)
            return
        }
        if (qualityOptions.size <= 1) loadQualityOptions()
        rebuildQualityPanel()
        speedPanel?.visibility = View.GONE
        qualityPanel?.visibility = View.VISIBLE
        handler.removeCallbacks(hideControlsRunnable)
    }

    private fun rebuildQualityPanel() {
        val video = currentVideo ?: return
        qualityPanel?.apply {
            removeAllViews()
            addView(choiceRow("Automática", connection.preferredQualityHeight.value == null) {
                connection.selectQuality(
                    video.copy(resumePositionMs = connection.progressState.value.positionMs),
                    null,
                    dataSaver
                )
                hideChoicePanels()
                showFeedback("Calidad automática")
                showControls(autoHide = true)
            })
            qualityOptions.filterNotNull().sortedDescending().forEach { height ->
                addView(choiceRow("${height}p", connection.preferredQualityHeight.value == height) {
                    connection.selectQuality(
                        video.copy(resumePositionMs = connection.progressState.value.positionMs),
                        height,
                        dataSaver
                    )
                    hideChoicePanels()
                    showFeedback("Calidad ${height}p")
                    showControls(autoHide = true)
                })
            }
            if (qualityOptions.size <= 1) {
                addView(choiceRow("Buscando calidades…", false) { loadQualityOptions() })
            }
        }
    }

    private fun toggleSpeedPanel() {
        if (speedPanel?.visibility == View.VISIBLE) {
            hideChoicePanels()
            showControls(autoHide = true)
            return
        }
        qualityPanel?.visibility = View.GONE
        speedPanel?.apply {
            removeAllViews()
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                addView(choiceRow(if (speed == 1f) "Normal" else "${speed}x", abs(speed - selectedSpeed) < 0.01f) {
                    selectedSpeed = speed
                    connection.setSpeed(speed)
                    speedText?.text = speedLabel(speed)
                    hideChoicePanels()
                    showFeedback("Velocidad ${speedLabel(speed)}")
                    showControls(autoHide = true)
                })
            }
            visibility = View.VISIBLE
        }
        handler.removeCallbacks(hideControlsRunnable)
    }

    private fun choiceRow(label: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = if (selected) "✓  $label" else "   $label"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(8), dp(9), dp(8))
            background = if (selected) roundedBackground(0xFF4C356E.toInt(), 7f) else null
            setOnClickListener { onClick() }
        }

    private fun hideChoicePanels() {
        qualityPanel?.visibility = View.GONE
        speedPanel?.visibility = View.GONE
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
            if (qualityPanel?.visibility == View.VISIBLE) rebuildQualityPanel()
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
            actualHeight > 0 -> "Auto ${actualHeight}p"
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
        if (autoHide && !isChoicePanelOpen()) scheduleControlsHide()
    }

    private fun isChoicePanelOpen(): Boolean =
        qualityPanel?.visibility == View.VISIBLE || speedPanel?.visibility == View.VISIBLE

    private fun scheduleControlsHide() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 2_600L)
    }

    private fun hideControls() {
        hideChoicePanels()
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

    private fun installResizeGesture(handle: View) {
        var downRawX = 0f
        var startWidth = 0
        handle.setOnTouchListener { _, event ->
            val params = windowParams ?: return@setOnTouchListener false
            val root = overlayRoot ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    startWidth = params.width
                    handler.removeCallbacks(hideControlsRunnable)
                    hideChoicePanels()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val screenWidth = resources.displayMetrics.widthPixels
                    val minWidth = dp(180)
                    val maxWidth = (screenWidth * MAX_WIDTH_FRACTION).roundToInt()
                        .coerceAtLeast(minWidth)
                    val requested = (startWidth + (event.rawX - downRawX)).roundToInt()
                    params.width = requested.coerceIn(minWidth, maxWidth)
                    params.height = (params.width * 9f / 16f).roundToInt()
                    windowLarge = params.width >= (screenWidth * LARGE_CONTROL_THRESHOLD).roundToInt()
                    clampWindowPosition(params)
                    runCatching { windowManager.updateViewLayout(root, params) }
                    updateControlDensity()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    saveWindowState()
                    showControls(autoHide = true)
                    true
                }
                else -> false
            }
        }
    }

    private fun updateControlDensity() {
        val width = windowParams?.width ?: return
        val compact = width < dp(300)
        openAppButton?.visibility = if (compact) View.GONE else View.VISIBLE
        previousButton?.visibility = if (compact) View.GONE else View.VISIBLE
        timeText?.visibility = if (compact) View.GONE else View.VISIBLE
        // Pausa/reproducir, siguiente y el asa de tamaño se mantienen siempre accesibles.
        nextButton?.visibility = View.VISIBLE
    }

    private fun initialWindowSize(): Pair<Int, Int> {
        val screenWidth = resources.displayMetrics.widthPixels
        val minWidth = dp(180)
        val maxWidth = (screenWidth * MAX_WIDTH_FRACTION).roundToInt().coerceAtLeast(minWidth)
        val fallbackWidth = if (windowLarge) {
            (screenWidth * LARGE_WIDTH_FRACTION).roundToInt()
        } else {
            (screenWidth * MINI_WIDTH_FRACTION).roundToInt()
        }
        val width = preferences.getInt(KEY_WIDTH, 0)
            .takeIf { it > 0 }
            ?.coerceIn(minWidth, maxWidth)
            ?: fallbackWidth.coerceIn(minWidth, maxWidth)
        windowLarge = width >= (screenWidth * LARGE_CONTROL_THRESHOLD).roundToInt()
        return width to (width * 9f / 16f).roundToInt()
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
            .putInt(KEY_WIDTH, params.width)
            .putBoolean(KEY_LARGE, windowLarge)
            .apply()
    }

    private fun closeFloatingAndStopPlayback() {
        connection.stop()
        stopSelf()
    }

    private fun openPlayerInApp() {
        saveWindowState()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EXPAND_PLAYER, true)
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
        qualityPanel = null
        speedPanel = null
        feedbackText = null
        resizeHandle = null
        openAppButton = null
        previousButton = null
        nextButton = null
        controlsRow = null
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
        private const val KEY_LARGE = "window_large"
        private const val KEY_WIDTH = "window_width"
        private const val MINI_WIDTH_FRACTION = 0.48f
        private const val LARGE_WIDTH_FRACTION = 0.84f
        private const val MAX_WIDTH_FRACTION = 0.94f
        private const val LARGE_CONTROL_THRESHOLD = 0.64f

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
