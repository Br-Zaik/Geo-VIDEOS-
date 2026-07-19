package com.geovideos.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import com.geovideos.app.data.VideoItem
import com.geovideos.app.playback.GeoPlayerConnection

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiteShortsScreen(
    modifier: Modifier,
    videos: List<VideoItem>,
    selectedVideoId: String,
    localLikedIds: Set<String>,
    localDislikedIds: Set<String>,
    loading: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    playerConnection: GeoPlayerConnection,
    onLoadMore: () -> Unit,
    onPreview: (VideoItem) -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onLike: (VideoItem) -> Unit,
    onDislike: (VideoItem) -> Unit
) {
    if (loading && videos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (videos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontraron Shorts.")
        }
        return
    }

    val initialPage = videos.indexOfFirst { it.id == selectedVideoId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { videos.size })
    val controller by playerConnection.controller.collectAsStateWithLifecycle()
    val playback by playerConnection.coreState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(selectedVideoId, videos.size) {
        val index = videos.indexOfFirst { it.id == selectedVideoId }
        if (index >= 0 && index != pagerState.currentPage) pagerState.scrollToPage(index)
    }

    LaunchedEffect(pagerState.currentPage, videos.size) {
        videos.getOrNull(pagerState.currentPage)?.let(onPreview)
    }

    LaunchedEffect(pagerState.currentPage, videos.size, loadingMore, canLoadMore) {
        if (pagerState.currentPage >= videos.lastIndex - 2 && canLoadMore && !loadingMore) {
            onLoadMore()
        }
    }

    DisposableEffect(Unit) {
        playerConnection.setRepeat(true)
        onDispose { playerConnection.setRepeat(false) }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0
        ) { page ->
            val video = videos[page]
            val isCurrent = page == pagerState.currentPage
            val isActive = isCurrent && playback.currentVideoId == video.id

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        if (!isActive) onPreview(video)
                        else if (playback.isPlaying) playerConnection.pause()
                        else playerConnection.play()
                    }
            ) {
                LiteThumbnail(
                    url = video.thumbnailUrl,
                    description = video.title,
                    modifier = Modifier.fillMaxSize(),
                    widthPx = 540,
                    heightPx = 960,
                    contentScale = ContentScale.Fit
                )

                if (isActive && controller != null && !playback.connecting && !playback.resolving) {
                    LitePlayerView(
                        controller = controller!!,
                        modifier = Modifier.fillMaxSize(),
                        useController = false,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    )
                }

                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.18f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.80f)
                            )
                        )
                    )
                )

                if (isActive && (playback.connecting || playback.resolving)) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(38.dp),
                        strokeWidth = 3.dp
                    )
                } else if (isActive && !playback.isPlaying) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(72.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.78f)
                        .padding(start = 16.dp, end = 8.dp, bottom = 24.dp)
                ) {
                    Text(
                        video.channelTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        video.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiteShortAction(
                        icon = Icons.Default.ThumbUp,
                        label = "Me gusta",
                        selected = video.id in localLikedIds,
                        onClick = { onLike(video) }
                    )
                    LiteShortAction(
                        icon = Icons.Default.ThumbDown,
                        label = "No me gusta",
                        selected = video.id in localDislikedIds,
                        onClick = { onDislike(video) }
                    )
                    LiteShortAction(
                        icon = Icons.Default.Share,
                        label = "Compartir",
                        onClick = { shareVideoLite(context, video) }
                    )
                    LiteShortAction(
                        icon = Icons.Outlined.WatchLater,
                        label = "Guardar",
                        onClick = { onWatchLater(video) }
                    )
                    LiteShortAction(
                        icon = Icons.Default.Fullscreen,
                        label = "Abrir",
                        onClick = { onOpenVideo(video) }
                    )
                }
            }
        }

        if (loadingMore) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun LiteShortAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.background(Color.Black.copy(alpha = 0.42f), CircleShape)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White
            )
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
