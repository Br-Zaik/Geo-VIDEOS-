package com.geovideos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.geovideos.app.data.VideoItem

@Composable
internal fun HomeShortsShelf(
    videos: List<VideoItem>,
    onOpenShort: (VideoItem) -> Unit
) {
    if (videos.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(
            "Shorts",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(videos.take(8), key = { "home-short-${it.id}" }) { video ->
                Box(
                    modifier = Modifier
                        .width(154.dp)
                        .height(274.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .clickable { onOpenShort(video) }
                ) {
                    HomeModuleImage(video.thumbnailUrl, video.title, Modifier.fillMaxSize(), ContentScale.Crop, 320, 568)
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.86f))
                            )
                        )
                    )
                    Text(
                        video.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeModuleImage(
    url: String,
    description: String,
    modifier: Modifier,
    contentScale: ContentScale,
    requestWidth: Int = 640,
    requestHeight: Int = 360
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .size(requestWidth, requestHeight)
            .build(),
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier
    )
}
