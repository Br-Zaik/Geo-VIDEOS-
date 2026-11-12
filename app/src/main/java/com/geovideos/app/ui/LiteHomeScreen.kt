package com.geovideos.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geovideos.app.data.MediaKind
import com.geovideos.app.data.HomeFeedEntry
import com.geovideos.app.data.VideoItem
import com.geovideos.app.network.HomeChip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
internal fun LiteHomeScreen(
    modifier: Modifier,
    category: HomeCategory,
    personalized: List<VideoItem>,
    popular: List<VideoItem>,
    live: List<VideoItem>,
    gaming: List<VideoItem>,
    music: List<VideoItem>,
    shorts: List<VideoItem>,
    mixes: List<VideoItem>,
    homeFeed: List<HomeFeedEntry>,
    loading: Boolean,
    refreshing: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    watchLater: List<VideoItem>,
    onRefresh: () -> Unit,
    onLoadMore: (HomeCategory) -> Unit,
    onCategory: (HomeCategory) -> Unit,
    onPlay: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    scrollToTopSignal: Long,
    onAtTopChanged: (Boolean) -> Unit,
    youtubeChips: List<HomeChip> = emptyList(),
    selectedYouTubeChip: String = "",
    chipLoading: Boolean = false,
    onYouTubeChip: (HomeChip?) -> Unit = {},
    onVideoMenu: (VideoItem, String) -> Unit = { _, _ -> }
) {
    val baseVideos = remember(category, personalized, popular, live, gaming, music) {
        when (category) {
            // Nunca sustituir "Para ti" por Tendencias. En una cuenta recién conectada eso
            // mostraba videos ajenos durante la sincronización y daba la impresión de que la
            // cuenta equivocada estaba activa. Si todavía no hay feed personal, se muestran
            // skeletons hasta que lleguen señales reales de la cuenta.
            HomeCategory.FOR_YOU -> personalized
            HomeCategory.LIVE -> live
            HomeCategory.GAMING -> gaming
            HomeCategory.MUSIC -> music
        }
    }
    val homeShorts = remember(category, baseVideos, shorts) {
        if (category != HomeCategory.FOR_YOU) emptyList()
        else (shorts + baseVideos.filter(::looksLikeHomeShort)).distinctBy { it.id }.take(40)
    }
    // Si hay una señal musical real dentro del feed personal, mostrar una sola mezcla
    // compacta como DailyTube. Nunca se crea desde Tendencias ni desde búsquedas genéricas.
    val mixVideo: VideoItem? = remember(category, baseVideos, mixes) {
        // Con Mixes reales de YouTube no se inventa uno a partir de un video.
        if (category == HomeCategory.FOR_YOU && mixes.isEmpty()) {
            baseVideos.firstOrNull { video ->
                isEligibleHomeVideoCard(video) && looksLikeMusicForMix(video)
            }?.copy(isMix = true)
        } else {
            null
        }
    }
    val videos = remember(baseVideos, homeShorts, mixVideo) {
        val hiddenIds = buildSet {
            homeShorts.forEach { add(it.id) }
            mixVideo?.id?.let { add(it) }
        }
        baseVideos.filter { video ->
            video.id !in hiddenIds && isEligibleHomeVideoCard(video)
        }
    }
    val watchLaterIds = remember(watchLater) {
        watchLater.asSequence().map { it.id }.toHashSet()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (youtubeChips.isNotEmpty()) {
                // Los mismos filtros que muestra YouTube para esta cuenta.
                YouTubeHomeChip("Todos", category == HomeCategory.FOR_YOU && selectedYouTubeChip.isBlank()) {
                    if (category != HomeCategory.FOR_YOU) onCategory(HomeCategory.FOR_YOU)
                    onYouTubeChip(null)
                }
                youtubeChips.forEach { chip ->
                    YouTubeHomeChip(chip.title, category == HomeCategory.FOR_YOU && selectedYouTubeChip == chip.token) {
                        if (category != HomeCategory.FOR_YOU) onCategory(HomeCategory.FOR_YOU)
                        onYouTubeChip(chip)
                    }
                }
            } else {
                LiteCategoryChip("Todos", Icons.Default.Explore, category == HomeCategory.FOR_YOU) {
                    onCategory(HomeCategory.FOR_YOU)
                }
                LiteCategoryChip("En vivo", Icons.Default.LiveTv, category == HomeCategory.LIVE) {
                    onCategory(HomeCategory.LIVE)
                }
                LiteCategoryChip("Juegos", Icons.Default.Games, category == HomeCategory.GAMING) {
                    onCategory(HomeCategory.GAMING)
                }
                LiteCategoryChip("Música", Icons.Default.PlaylistPlay, category == HomeCategory.MUSIC) {
                    onCategory(HomeCategory.MUSIC)
                }
            }
        }
        if (chipLoading) {
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color(0xFFFF0033),
                trackColor = Color.Transparent
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            RecyclerHomeFeed(
                modifier = Modifier.fillMaxSize(),
                videos = videos,
                shorts = homeShorts,
                showShortsShelf = category == HomeCategory.FOR_YOU,
                // "Todos" con sesión: exactamente el inicio de YouTube, en su orden.
                orderedFeed = if (category == HomeCategory.FOR_YOU) homeFeed else emptyList(),
                mixes = if (category == HomeCategory.FOR_YOU) {
                    mixes.ifEmpty { listOfNotNull(mixVideo) }
                } else {
                    emptyList()
                },
                loading = loading,
                refreshing = refreshing,
                loadingMore = loadingMore,
                canLoadMore = canLoadMore,
                watchLaterIds = watchLaterIds,
                onRefresh = onRefresh,
                onLoadMore = { onLoadMore(category) },
                onPlay = onPlay,
                onOpenShort = onOpenShort,
                onWatchLater = onWatchLater,
                scrollToTopSignal = scrollToTopSignal,
                onAtTopChanged = onAtTopChanged,
                onVideoMenu = onVideoMenu
            )
        }
    }
}

/** Píldora de filtro como YouTube: gris oscuro, la elegida en blanco con texto negro. */
@Composable
private fun YouTubeHomeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color(0xFF272727))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun LiteCategoryChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(17.dp)) }
    )
}

internal fun looksLikeHomeShort(video: VideoItem): Boolean {
    val text = (video.title + " " + video.description).lowercase()
    val taggedAsShort = listOf(
        "#shorts", " shorts", "short ", "tiktok", "reel", "vertical", "status video"
    ).any { it in text }
    return taggedAsShort || video.durationMs in 1..180_000L
}

private fun isEligibleHomeVideoCard(video: VideoItem): Boolean {
    if (video.isLive) return true
    if (video.mediaKind != MediaKind.YOUTUBE) return !looksLikeHomeShort(video)
    // Para Principal no mostrar elementos de YouTube sin duración confirmada: un Short con
    // metadatos incompletos era exactamente lo que terminaba apareciendo como tarjeta normal.
    return video.durationMs > 180_000L && !looksLikeHomeShort(video)
}

private fun looksLikeMusicForMix(video: VideoItem): Boolean {
    val text = (video.title + " " + video.channelTitle + " " + video.description).lowercase()
    return listOf(
        "music", "música", "musica", "song", "lyrics", "audio", "rap", "mix", "nightcore",
        "remix", "cover", "playlist", "álbum", "album"
    ).any { it in text }
}
