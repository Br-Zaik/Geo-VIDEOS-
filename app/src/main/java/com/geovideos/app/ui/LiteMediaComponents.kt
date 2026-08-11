package com.geovideos.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3UiR
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.geovideos.app.R
import com.geovideos.app.data.MediaKind
import com.geovideos.app.data.VideoItem
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
internal fun LitePlayerView(
    controller: MediaController,
    modifier: Modifier = Modifier,
    useController: Boolean,
    resizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
    useTextureView: Boolean = false,
    zoomScale: Float = 1f,
    onZoomScaleChange: ((Float) -> Unit)? = null,
    onSeekBy: ((Long) -> Unit)? = null,
    onZoomFeedback: ((Int) -> Unit)? = null,
    onSingleTap: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    gesturesEnabled: Boolean = true
) {
    val attachedView = remember { arrayOfNulls<PlayerView>(1) }
    val currentZoom by rememberUpdatedState(zoomScale)
    val currentOnZoomScaleChange by rememberUpdatedState(onZoomScaleChange)
    val currentOnSeekBy by rememberUpdatedState(onSeekBy)
    val currentOnZoomFeedback by rememberUpdatedState(onZoomFeedback)
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentGesturesEnabled by rememberUpdatedState(gesturesEnabled)
    DisposableEffect(controller) {
        onDispose {
            attachedView[0]?.let { view ->
                if (view.player === controller) view.player = null
            }
            attachedView[0] = null
        }
    }
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            val layout = if (useTextureView) R.layout.geo_player_texture else R.layout.geo_player_surface
            (LayoutInflater.from(context)
                .inflate(layout, null, false) as PlayerView).apply {
                attachedView[0] = this
                player = controller
                this.useController = useController
                controllerAutoShow = useController
                controllerHideOnTouch = true
                controllerShowTimeoutMs = 2_300
                keepScreenOn = true
                setKeepContentOnPlayerReset(true)
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShowPreviousButton(true)
                setShowNextButton(true)
                setShowRewindButton(true)
                setShowFastForwardButton(true)
                this.resizeMode = resizeMode
                findViewById<View>(Media3UiR.id.exo_settings)?.setOnClickListener {
                    onSettingsClick?.invoke()
                }
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        onControllerVisibilityChanged?.invoke(visibility == View.VISIBLE)
                    }
                )

                val playerView = this
                var localZoom = currentZoom.coerceIn(1f, 3f)
                val scaleDetector = ScaleGestureDetector(
                    context,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            localZoom = (localZoom * detector.scaleFactor).coerceIn(1f, 3f)
                            applyPlayerZoom(playerView, localZoom)
                            currentOnZoomScaleChange?.invoke(localZoom)
                            currentOnZoomFeedback?.invoke((localZoom * 100f).roundToInt())
                            return true
                        }

                        override fun onScaleEnd(detector: ScaleGestureDetector) {
                            currentOnZoomFeedback?.invoke((localZoom * 100f).roundToInt())
                        }
                    }
                )
                val tapDetector = GestureDetector(
                    context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDown(e: MotionEvent): Boolean = true

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            currentOnSingleTap?.invoke()
                            return true
                        }

                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            val delta = if (e.x >= width / 2f) 10_000L else -10_000L
                            val duration = controller.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                            controller.seekTo((controller.currentPosition + delta).coerceIn(0L, duration))
                            currentOnSeekBy?.invoke(delta)
                            return true
                        }
                    }
                )
                setOnTouchListener { _, event ->
                    if (!currentGesturesEnabled) return@setOnTouchListener true
                    scaleDetector.onTouchEvent(event)
                    tapDetector.onTouchEvent(event)
                    false
                }
                applyPlayerZoom(this, localZoom)
            }
        },
        update = { view ->
            attachedView[0] = view
            if (view.player !== controller) view.player = controller
            view.useController = useController
            view.controllerAutoShow = useController
            view.resizeMode = resizeMode
            applyPlayerZoom(view, zoomScale.coerceIn(1f, 3f))
            view.findViewById<View>(Media3UiR.id.exo_settings)?.setOnClickListener {
                onSettingsClick?.invoke()
            }
            view.setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    onControllerVisibilityChanged?.invoke(visibility == View.VISIBLE)
                }
            )
        }
    )
}

private fun applyPlayerZoom(view: PlayerView, zoom: Float) {
    view.videoSurfaceView?.apply {
        scaleX = zoom
        scaleY = zoom
    }
}

@Composable
internal fun LiteThumbnail(
    url: String,
    description: String?,
    modifier: Modifier,
    widthPx: Int,
    heightPx: Int,
    contentScale: ContentScale = ContentScale.Crop,
    deferWhileScrolling: Boolean = false
) {
    var loadedSuccessfully by remember(url) { mutableStateOf(false) }

    if (url.isBlank()) {
        LiteThumbnailFallback(modifier)
        return
    }

    // During a fast fling, newly appearing cells show a cheap solid placeholder.
    // Already-decoded images remain visible. Once scrolling stops, Coil starts the request.
    if (deferWhileScrolling && !loadedSuccessfully) {
        Box(modifier = modifier.background(Color(0xFF202024)))
        return
    }

    val context = LocalContext.current
    val request = remember(url, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(url)
            .size(widthPx, heightPx)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = description,
        modifier = modifier.background(Color.Black),
        contentScale = contentScale,
        onSuccess = { loadedSuccessfully = true },
        onError = { loadedSuccessfully = false }
    )
}

@Composable
private fun LiteThumbnailFallback(modifier: Modifier) {
    Box(
        modifier = modifier.background(Color(0xFF202024)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.30f),
            modifier = Modifier.size(36.dp)
        )
    }
}

internal fun shareVideoLite(context: Context, video: VideoItem) {
    val url = if (video.mediaKind == MediaKind.YOUTUBE) {
        "https://www.youtube.com/watch?v=${video.id}"
    } else {
        video.source
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${video.title}\n$url")
    }
    context.startActivity(Intent.createChooser(intent, "Compartir video"))
}

internal tailrec fun Context.findActivityLite(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityLite()
    else -> null
}
