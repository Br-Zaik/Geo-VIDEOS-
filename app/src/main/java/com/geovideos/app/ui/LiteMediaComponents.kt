package com.geovideos.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.geovideos.app.R
import com.geovideos.app.data.MediaKind
import com.geovideos.app.data.VideoItem

@OptIn(UnstableApi::class)
@Composable
internal fun LitePlayerView(
    controller: MediaController,
    modifier: Modifier = Modifier,
    useController: Boolean,
    resizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            (LayoutInflater.from(context)
                .inflate(R.layout.geo_player_surface, null, false) as PlayerView).apply {
                player = controller
                this.useController = useController
                controllerAutoShow = useController
                controllerHideOnTouch = true
                controllerShowTimeoutMs = 2_300
                keepScreenOn = true
                setKeepContentOnPlayerReset(true)
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShowPreviousButton(false)
                setShowNextButton(false)
                setShowRewindButton(true)
                setShowFastForwardButton(true)
                this.resizeMode = resizeMode
            }
        },
        update = { view ->
            if (view.player !== controller) view.player = controller
            view.useController = useController
            view.controllerAutoShow = useController
            view.resizeMode = resizeMode
        }
    )
}

@Composable
internal fun LiteThumbnail(
    url: String,
    description: String?,
    modifier: Modifier,
    widthPx: Int,
    heightPx: Int,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (url.isBlank()) {
        LiteThumbnailFallback(modifier)
        return
    }
    val context = LocalContext.current
    val request = remember(url, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(url)
            .size(widthPx, heightPx)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = description,
        modifier = modifier.background(Color.Black),
        contentScale = contentScale,
        onError = { }
    )
}

@Composable
private fun LiteThumbnailFallback(modifier: Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(Color(0xFF24163D), Color(0xFF4A2A79), Color.Black))
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.size(42.dp)
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
