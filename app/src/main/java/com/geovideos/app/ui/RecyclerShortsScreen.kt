package com.geovideos.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geovideos.app.R
import com.geovideos.app.data.VideoItem
import com.geovideos.app.playback.DownloadStreamOption
import com.geovideos.app.playback.GeoPlayerConnection
import com.geovideos.app.playback.StreamOptions
import com.geovideos.app.playback.enqueueResolvedMediaDownload
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecyclerShortsScreen(
    modifier: Modifier,
    videos: List<VideoItem>,
    selectedVideoId: String,
    localLikedIds: Set<String>,
    localDislikedIds: Set<String>,
    loading: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    dataSaver: Boolean,
    playerConnection: GeoPlayerConnection,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onPreview: (VideoItem) -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onLike: (VideoItem) -> Unit,
    onDislike: (VideoItem) -> Unit,
    onRegisterDownload: (String, String, Long) -> Unit,
    onMessage: (String) -> Unit
) {
    val controller by playerConnection.controller.collectAsStateWithLifecycle()
    val playback by playerConnection.coreState.collectAsStateWithLifecycle()
    val preferredQuality by playerConnection.preferredQualityHeight.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var qualityVideo by remember { mutableStateOf<VideoItem?>(null) }
    var qualityOptions by remember { mutableStateOf<StreamOptions?>(null) }
    var qualityLoading by remember { mutableStateOf(false) }
    var qualityError by remember { mutableStateOf<String?>(null) }
    var downloadVideo by remember { mutableStateOf<VideoItem?>(null) }
    var downloadOptions by remember { mutableStateOf<StreamOptions?>(null) }
    var downloadLoading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var selectedDownloadHeight by remember { mutableStateOf<Int?>(null) }
    var pendingDownload by remember { mutableStateOf<Pair<VideoItem, DownloadStreamOption>?>(null) }

    fun openQuality(video: VideoItem) {
        qualityVideo = video
        qualityOptions = null
        qualityError = null
        qualityLoading = true
        scope.launch {
            runCatching { playerConnection.streamOptions(video, includeDownloadSizes = false) }
                .onSuccess { qualityOptions = it }
                .onFailure { qualityError = "No se pudieron obtener las calidades de este Short." }
            qualityLoading = false
        }
    }

    fun openDownload(video: VideoItem) {
        downloadVideo = video
        downloadOptions = null
        downloadError = null
        selectedDownloadHeight = null
        downloadLoading = true
        scope.launch {
            runCatching { playerConnection.streamOptions(video) }
                .onSuccess { options ->
                    downloadOptions = options
                    selectedDownloadHeight = options.downloads
                        .firstOrNull { it.height == preferredQuality }
                        ?.height
                        ?: options.downloads.maxByOrNull { it.height }?.height
                }
                .onFailure {
                    downloadError = "No se pudieron obtener las calidades descargables de este Short."
                }
            downloadLoading = false
        }
    }

    fun enqueueShortDownload(video: VideoItem, option: DownloadStreamOption) {
        val downloadId = enqueueResolvedMediaDownload(
            context = context,
            video = video,
            option = option,
            relativeFolder = "GeoVideos/Shorts"
        )
        if (downloadId < -1L) {
            onRegisterDownload(
                "${video.title} (${option.label})",
                "geo-download://$downloadId",
                downloadId
            )
            downloadVideo = null
            onMessage(
                if (option.requiresMux) {
                    "Descarga del Short en ${option.height}p iniciada. Se guardará unido con audio."
                } else {
                    "Descarga del Short en ${option.height}p iniciada."
                }
            )
        } else {
            onMessage("No se pudo iniciar la descarga del Short.")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingDownload
        pendingDownload = null
        if (granted && request != null) {
            enqueueShortDownload(request.first, request.second)
        } else if (!granted) {
            onMessage("Android necesita permiso de almacenamiento para descargar en esta versión.")
        }
    }

    fun startShortDownload(video: VideoItem, option: DownloadStreamOption) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = video to option
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            enqueueShortDownload(video, option)
        }
    }
    // The ViewModel already supplies a dedicated Shorts feed. Do not filter it again here:
    // a second strict filter previously removed every item when duration metadata was delayed.
    val shortVideos = remember(videos) { videos.distinctBy { it.id } }

    if (shortVideos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No se pudieron cargar Shorts.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onRetry) { Text("Reintentar") }
                }
            }
        }
        return
    }

    val adapter = remember {
        NativeShortsAdapter(
            onPreview = onPreview,
            onOpenComments = onOpenVideo,
            onWatchLater = onWatchLater,
            onLike = onLike,
            onDislike = onDislike,
            onQuality = ::openQuality,
            onDownload = ::openDownload,
            onTogglePlayback = { video ->
                if (playback.currentVideoId == video.id && playback.error != null) {
                    playerConnection.retryShort(video, dataSaver)
                } else if (playback.currentVideoId == video.id) {
                    if (playback.isPlaying) playerConnection.pause() else playerConnection.play()
                } else onPreview(video)
            }
        )
    }

    DisposableEffect(Unit) {
        playerConnection.setRepeat(true)
        onDispose { playerConnection.setRepeat(false) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                val manager = LinearLayoutManager(context, RecyclerView.VERTICAL, false).apply {
                    initialPrefetchItemCount = 2
                }
                layoutManager = manager
                this.adapter = adapter
                itemAnimator = null
                setHasFixedSize(false)
                setItemViewCacheSize(3)
                clipToPadding = false
                overScrollMode = View.OVER_SCROLL_NEVER
                val snap = PagerSnapHelper()
                snap.attachToRecyclerView(this)
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    val newHeight = height
                    if (newHeight > 0 && adapter.pageHeight != newHeight) {
                        adapter.pageHeight = newHeight
                        adapter.notifyItemRangeChanged(0, adapter.itemCount, NativeShortsAdapter.PAYLOAD_SIZE)
                    }
                }
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                        val snapView = snap.findSnapView(manager) ?: return
                        val position = manager.getPosition(snapView)
                        adapter.currentList.getOrNull(position)?.let { item ->
                            if (item.id != adapter.activeId) adapter.onPreviewCurrent(item)
                            adapter.preloadAround(position)
                            playerConnection.preload(
                                adapter.currentList.drop(position + 1).take(2),
                                dataSaver
                            )
                            if (position >= adapter.itemCount - 3 && adapter.canLoadMore && !adapter.loadingMore) {
                                adapter.loadingMore = true
                                adapter.onLoadMore()
                            }
                        }
                    }
                })
            }
        },
        update = { recyclerView ->
            adapter.updateCallbacks(onPreview, onOpenVideo, onWatchLater, onLike, onDislike, ::openQuality, ::openDownload) { video ->
                if (playback.currentVideoId == video.id && playback.error != null) {
                    playerConnection.retryShort(video, dataSaver)
                } else if (playback.currentVideoId == video.id) {
                    if (playback.isPlaying) playerConnection.pause() else playerConnection.play()
                } else onPreview(video)
            }
            adapter.canLoadMore = canLoadMore
            adapter.loadingMore = loadingMore
            adapter.onLoadMore = onLoadMore
            adapter.submitList(shortVideos)
            if (selectedVideoId.isBlank() && shortVideos.isNotEmpty() && adapter.activeId.isBlank()) {
                recyclerView.post { adapter.onPreviewCurrent(shortVideos.first()) }
            }
            adapter.setState(
                selectedVideoId = selectedVideoId,
                liked = localLikedIds,
                disliked = localDislikedIds,
                controller = controller,
                connecting = loading || playback.connecting || playback.resolving,
                error = playback.error
            )
            if (selectedVideoId.isNotBlank()) {
                val index = shortVideos.indexOfFirst { it.id == selectedVideoId }
                val manager = recyclerView.layoutManager as? LinearLayoutManager
                if (index >= 0 && manager?.findFirstCompletelyVisibleItemPosition() != index) {
                    recyclerView.post { manager?.scrollToPositionWithOffset(index, 0) }
                }
            }
        }
    )

    qualityVideo?.let { video ->
        ModalBottomSheet(onDismissRequest = { qualityVideo = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("Calidad del Short", style = MaterialTheme.typography.titleLarge)
                Text(
                    "La calidad elegida se aplicará también a los siguientes Shorts.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                when {
                    qualityLoading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    qualityError != null -> Text(
                        qualityError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    else -> {
                        ShortQualityRow(
                            label = "Automática",
                            selected = preferredQuality == null,
                            onClick = {
                                playerConnection.selectQuality(video, null, dataSaver)
                                qualityVideo = null
                            }
                        )
                        qualityOptions?.qualities.orEmpty().forEach { option ->
                            HorizontalDivider()
                            ShortQualityRow(
                                label = option.label,
                                selected = preferredQuality == option.height,
                                onClick = {
                                    playerConnection.selectQuality(video, option.height, dataSaver)
                                    qualityVideo = null
                                }
                            )
                        }
                        if (qualityOptions?.qualities.isNullOrEmpty()) {
                            Text(
                                "Este Short solo ofrece calidad automática.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    downloadVideo?.let { video ->
        val options = downloadOptions?.downloads.orEmpty().sortedByDescending { it.height }
        val selected = options.firstOrNull { it.height == selectedDownloadHeight }
            ?: options.firstOrNull()

        ModalBottomSheet(onDismissRequest = { downloadVideo = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("Descargar Short", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Escoge la calidad de descarga. La calidad que estás viendo no cambia.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                when {
                    downloadLoading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    downloadError != null -> Text(
                        downloadError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    downloadOptions?.isLive == true -> Text(
                        "Los Shorts en directo no se pueden descargar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                    options.isEmpty() -> Text(
                        "Este Short no ofrece una calidad descargable compatible.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                    else -> {
                        options.forEachIndexed { index, option ->
                            if (index > 0) HorizontalDivider()
                            ShortDownloadRow(
                                option = option,
                                selected = option.height == selected?.height,
                                onClick = { selectedDownloadHeight = option.height }
                            )
                        }
                        Button(
                            onClick = { selected?.let { startShortDownload(video, it) } },
                            enabled = selected != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp)
                        ) {
                            Text(
                                selected?.let { "Descargar en ${it.height}p" }
                                    ?: "Selecciona una calidad"
                            )
                        }
                        Text(
                            "Se guardará en Películas/GeoVideos/Shorts y aparecerá en Colección > Descargas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ShortDownloadRow(
    option: DownloadStreamOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val estimatedSize = option.estimatedSizeBytes
        .takeIf { it > 0L }
        ?.let { Formatter.formatShortFileSize(context, it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text("${option.height}p")
            Text(
                buildString {
                    append(if (option.requiresMux) "Video + audio · se unirán" else "Video con audio")
                    estimatedSize?.let { append(" · aprox. $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShortQualityRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 10.dp))
    }
}

private fun isStrictShort(video: VideoItem): Boolean {
    if (video.durationMs > 0L) return video.durationMs <= 180_000L
    val text = "${video.title} ${video.description} ${video.source}".lowercase()
    return "/shorts/" in text || "#shorts" in text || "#short " in text
}

private object ShortVideoDiff : DiffUtil.ItemCallback<VideoItem>() {
    override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem) = oldItem == newItem
}

private class NativeShortsAdapter(
    private var onPreview: (VideoItem) -> Unit,
    private var onOpenComments: (VideoItem) -> Unit,
    private var onWatchLater: (VideoItem) -> Unit,
    private var onLike: (VideoItem) -> Unit,
    private var onDislike: (VideoItem) -> Unit,
    private var onQuality: (VideoItem) -> Unit,
    private var onDownload: (VideoItem) -> Unit,
    private var onTogglePlayback: (VideoItem) -> Unit
) : ListAdapter<VideoItem, NativeShortHolder>(ShortVideoDiff) {
    var activeId: String = ""
        private set
    var canLoadMore: Boolean = false
    var loadingMore: Boolean = false
    var onLoadMore: () -> Unit = {}
    private var likedIds: Set<String> = emptySet()
    private var dislikedIds: Set<String> = emptySet()
    private var controller: MediaController? = null
    private var connecting = false
    private var playbackError: String? = null
    var pageHeight: Int = 0

    init { setHasStableIds(true) }
    override fun getItemId(position: Int) = getItem(position).id.hashCode().toLong()
    fun onPreviewCurrent(video: VideoItem) = onPreview(video)

    fun updateCallbacks(
        preview: (VideoItem) -> Unit,
        comments: (VideoItem) -> Unit,
        later: (VideoItem) -> Unit,
        like: (VideoItem) -> Unit,
        dislike: (VideoItem) -> Unit,
        quality: (VideoItem) -> Unit,
        download: (VideoItem) -> Unit,
        toggle: (VideoItem) -> Unit
    ) {
        onPreview = preview
        onOpenComments = comments
        onWatchLater = later
        onLike = like
        onDislike = dislike
        onQuality = quality
        onDownload = download
        onTogglePlayback = toggle
    }

    fun setState(
        selectedVideoId: String,
        liked: Set<String>,
        disliked: Set<String>,
        controller: MediaController?,
        connecting: Boolean,
        error: String?
    ) {
        val previous = activeId
        activeId = selectedVideoId
        likedIds = liked
        dislikedIds = disliked
        this.controller = controller
        this.connecting = connecting
        this.playbackError = error
        listOf(previous, activeId).filter { it.isNotBlank() }.distinct().forEach { id ->
            currentList.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(::notifyItemChanged)
        }
    }

    fun preloadAround(position: Int) {
        val context = recyclerViewContext ?: return
        listOf(position - 1, position + 1).forEach { index ->
            currentList.getOrNull(index)?.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
                Glide.with(context).load(url).preload(540, 960)
            }
        }
    }

    private var recyclerViewContext: Context? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        recyclerViewContext = recyclerView.context
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerViewContext = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NativeShortHolder =
        NativeShortHolder(ShortPageView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                pageHeight.takeIf { it > 0 }
                    ?: parent.measuredHeight.takeIf { it > 0 }
                    ?: ViewGroup.LayoutParams.MATCH_PARENT
            )
        })

    override fun onBindViewHolder(holder: NativeShortHolder, position: Int) {
        if (pageHeight > 0 && holder.itemView.layoutParams.height != pageHeight) {
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                pageHeight
            )
        }
        val video = getItem(position)
        holder.bind(
            video = video,
            active = video.id == activeId,
            liked = video.id in likedIds,
            disliked = video.id in dislikedIds,
            controller = controller,
            connecting = connecting,
            error = playbackError,
            onToggle = onTogglePlayback,
            onComments = onOpenComments,
            onLater = onWatchLater,
            onLike = onLike,
            onDislike = onDislike,
            onQuality = onQuality,
            onDownload = onDownload
        )
    }

    override fun onBindViewHolder(holder: NativeShortHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SIZE)) {
            if (pageHeight > 0) {
                holder.itemView.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    pageHeight
                )
            }
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onViewRecycled(holder: NativeShortHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    companion object {
        const val PAYLOAD_SIZE = "short-page-size"
    }
}

private class NativeShortHolder(private val page: ShortPageView) : RecyclerView.ViewHolder(page) {
    fun bind(
        video: VideoItem,
        active: Boolean,
        liked: Boolean,
        disliked: Boolean,
        controller: MediaController?,
        connecting: Boolean,
        error: String?,
        onToggle: (VideoItem) -> Unit,
        onComments: (VideoItem) -> Unit,
        onLater: (VideoItem) -> Unit,
        onLike: (VideoItem) -> Unit,
        onDislike: (VideoItem) -> Unit,
        onQuality: (VideoItem) -> Unit,
        onDownload: (VideoItem) -> Unit
    ) {
        page.bindVideo(video.id)
        page.channel.text = video.channelTitle.ifBlank { "Canal" }
        page.title.text = video.title
        loadShortAvatar(page.channelAvatar, video.channelThumbnailUrl)
        page.like.setSelectedState(liked)
        page.dislike.setSelectedState(disliked)
        page.setOnClickListener { onToggle(video) }
        page.like.setOnClickListener { onLike(video) }
        page.dislike.setOnClickListener { onDislike(video) }
        page.comments.setOnClickListener { onComments(video) }
        page.save.setOnClickListener { onLater(video) }
        page.share.setOnClickListener { shareShort(page.context, video) }
        page.quality.setOnClickListener { onQuality(video) }
        page.download.setOnClickListener { onDownload(video) }
        loadShortImage(page.thumbnail, video.thumbnailUrl)
        if (active && controller != null && controller.currentMediaItem?.mediaId == video.id) {
            page.attach(controller, video.id)
        } else {
            page.detach()
        }
        page.setPlaybackStatus(
            loading = active && connecting,
            error = error.takeIf { active }
        )
    }

    fun recycle() {
        page.detach()
        Glide.with(page.thumbnail).clear(page.thumbnail)
        Glide.with(page.channelAvatar).clear(page.channelAvatar)
        page.setOnClickListener(null)
    }
}

private class ShortPageView(context: Context) : FrameLayout(context) {
    val thumbnail = ImageView(context)
    val playerContainer = FrameLayout(context)
    val progress = ProgressBar(context)
    val channelAvatar = ImageView(context)
    val channel = TextView(context)
    val title = TextView(context)
    val like = ShortActionView(context, R.drawable.ic_short_like, "Me gusta")
    val dislike = ShortActionView(context, R.drawable.ic_short_dislike, "No me gusta")
    val comments = ShortActionView(context, R.drawable.ic_short_comment, "Comentarios")
    val share = ShortActionView(context, R.drawable.ic_short_share, "Compartir")
    val save = ShortActionView(context, R.drawable.ic_short_save, "Guardar")
    val download = ShortActionView(context, R.drawable.ic_player_download, "Descargar")
    val quality = ShortActionView(context, R.drawable.ic_player_quality, "Calidad")
    private val errorText = TextView(context)
    private var boundVideoId: String = ""
    private var renderedVideoId: String = ""
    private var attachedVideoId: String = ""
    private var externalLoading: Boolean = false
    private var attachedController: MediaController? = null
    private var internalPlaybackState: Int = Player.STATE_IDLE
    private val playerView = (LayoutInflater.from(context).inflate(R.layout.geo_player_texture, null, false) as PlayerView).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        setShutterBackgroundColor(Color.TRANSPARENT)
        setKeepContentOnPlayerReset(true)
        setBackgroundColor(Color.TRANSPARENT)
    }
    private val playerListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            renderedVideoId = attachedVideoId
            hideThumbnailAfterFirstFrame()
            updateProgress()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            internalPlaybackState = playbackState
            updateProgress()
        }

        override fun onPlayerError(error: PlaybackException) {
            showThumbnail()
            errorText.text = "No se pudo reproducir. Toca para reintentar."
            errorText.visibility = View.VISIBLE
            updateProgress()
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true

        playerContainer.setBackgroundColor(Color.BLACK)
        playerContainer.addView(
            playerView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        addView(playerContainer, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
        thumbnail.setBackgroundColor(Color.BLACK)
        addView(thumbnail, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x22000000, 0x00000000, 0x00000000, 0xD9000000.toInt())
            )
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        addView(progress, LayoutParams(dpShort(context, 38), dpShort(context, 38), Gravity.CENTER))

        errorText.setTextColor(Color.WHITE)
        errorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        errorText.gravity = Gravity.CENTER
        errorText.setPadding(dpShort(context, 16), dpShort(context, 10), dpShort(context, 16), dpShort(context, 10))
        errorText.background = GradientDrawable().apply {
            cornerRadius = dpShort(context, 16).toFloat()
            setColor(0xB8000000.toInt())
        }
        errorText.visibility = View.GONE
        addView(errorText, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            channelAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
            channelAvatar.background = circleDrawable(0xFF3C2A55.toInt())
            addView(channelAvatar, LinearLayout.LayoutParams(dpShort(context, 38), dpShort(context, 38)).apply {
                marginEnd = dpShort(context, 10)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                channel.setTextColor(Color.WHITE)
                channel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                channel.setTypeface(channel.typeface, android.graphics.Typeface.BOLD)
                channel.maxLines = 1
                channel.ellipsize = TextUtils.TruncateAt.END
                title.setTextColor(Color.WHITE)
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                title.maxLines = 3
                title.ellipsize = TextUtils.TruncateAt.END
                addView(channel)
                addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpShort(context, 5)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(info, LayoutParams((context.resources.displayMetrics.widthPixels * 0.74f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dpShort(context, 14), 0, 0, dpShort(context, 18))
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            listOf(like, share, save, download, quality).forEach { action ->
                addView(action, LinearLayout.LayoutParams(dpShort(context, 50), dpShort(context, 52)).apply {
                    bottomMargin = dpShort(context, 3)
                })
            }
        }
        addView(actions, LayoutParams(dpShort(context, 58), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dpShort(context, 5), dpShort(context, 16))
        })
    }

    fun bindVideo(videoId: String) {
        if (boundVideoId == videoId) return
        boundVideoId = videoId
        renderedVideoId = ""
        attachedVideoId = ""
        errorText.visibility = View.GONE
        showThumbnail()
    }

    fun attach(controller: MediaController, videoId: String) {
        attachedVideoId = videoId
        if (attachedController !== controller) {
            attachedController?.removeListener(playerListener)
            playerView.player = null
            attachedController = controller
            controller.addListener(playerListener)
            playerView.player = controller
        }
        playerContainer.visibility = View.VISIBLE
        internalPlaybackState = controller.playbackState
        if (renderedVideoId == videoId) {
            thumbnail.visibility = View.GONE
            thumbnail.alpha = 0f
        } else {
            showThumbnail()
            // When a ready player is attached to a fresh TextureView, the first-frame callback
            // normally fires immediately. This fallback prevents a permanent black page on
            // devices that do not dispatch it after reattachment.
            postDelayed({
                if (
                    attachedController === controller &&
                    attachedVideoId == videoId &&
                    controller.playbackState == Player.STATE_READY &&
                    controller.videoSize.width > 0
                ) {
                    renderedVideoId = videoId
                    hideThumbnailAfterFirstFrame()
                }
            }, 900L)
        }
        updateProgress()
    }

    fun setPlaybackStatus(loading: Boolean, error: String?) {
        externalLoading = loading
        if (error.isNullOrBlank()) {
            errorText.visibility = View.GONE
        } else {
            showThumbnail()
            errorText.text = "No se pudo reproducir. Toca para reintentar."
            errorText.visibility = View.VISIBLE
        }
        updateProgress()
    }

    fun detach() {
        attachedController?.removeListener(playerListener)
        attachedController = null
        playerView.player = null
        renderedVideoId = ""
        attachedVideoId = ""
        playerContainer.visibility = View.GONE
        externalLoading = false
        internalPlaybackState = Player.STATE_IDLE
        errorText.visibility = View.GONE
        showThumbnail()
        updateProgress()
    }

    private fun hideThumbnailAfterFirstFrame() {
        if (thumbnail.visibility != View.VISIBLE) return
        thumbnail.animate().cancel()
        thumbnail.animate()
            .alpha(0f)
            .setDuration(120L)
            .withEndAction { thumbnail.visibility = View.GONE }
            .start()
    }

    private fun showThumbnail() {
        thumbnail.animate().cancel()
        thumbnail.alpha = 1f
        thumbnail.visibility = View.VISIBLE
    }

    private fun updateProgress() {
        val buffering = attachedController != null && internalPlaybackState == Player.STATE_BUFFERING
        progress.visibility = if ((externalLoading || buffering) && errorText.visibility != View.VISIBLE) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}

private class ShortActionView(context: Context, iconRes: Int, label: String) : LinearLayout(context) {
    private val icon = ImageView(context)
    private val text = TextView(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        icon.setImageResource(iconRes)
        icon.setColorFilter(Color.WHITE)
        icon.setPadding(dpShort(context, 8), dpShort(context, 8), dpShort(context, 8), dpShort(context, 8))
        icon.background = circleDrawable(0x72000000.toInt())
        addView(icon, LayoutParams(dpShort(context, 39), dpShort(context, 39)))
        text.text = label
        text.gravity = Gravity.CENTER
        text.setTextColor(Color.WHITE)
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.8f)
        text.maxLines = 2
        text.ellipsize = TextUtils.TruncateAt.END
        addView(text, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpShort(context, 3)
        })
    }

    fun setSelectedState(selected: Boolean) {
        val color = if (selected) 0xFF9D6CFF.toInt() else Color.WHITE
        icon.setColorFilter(color)
        text.setTextColor(color)
    }
}

private fun shareShort(context: Context, video: VideoItem) {
    val url = if (video.source.startsWith("http")) video.source else "https://www.youtube.com/watch?v=${video.id}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${video.title}\n$url")
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Compartir video")) }
}

private fun loadShortImage(view: ImageView, url: String) {
    val manager = Glide.with(view)
    val placeholder = ColorDrawable(0xFF17171B.toInt())
    if (!url.contains("ytimg.com", true) && !url.contains("youtube.com", true)) {
        manager.load(url)
            .override(540, 960)
            .dontAnimate()
            .placeholder(placeholder)
            .error(ColorDrawable(0xFF2B1B45.toInt()))
            .centerCrop()
            .into(view)
        return
    }
    fun candidate(name: String) = url.replace(
        Regex("(maxresdefault|sddefault|hqdefault|mqdefault|default)\\.jpg"),
        name
    )
    val low = manager.load(candidate("mqdefault.jpg")).override(320, 568).dontAnimate().centerCrop()
    val fallback = manager.load(candidate("hqdefault.jpg")).override(540, 960).dontAnimate().centerCrop().error(low)
    manager.load(candidate("sddefault.jpg"))
        .override(540, 960)
        .dontAnimate()
        .placeholder(placeholder)
        .thumbnail(low)
        .error(fallback)
        .centerCrop()
        .into(view)
}

private fun loadShortAvatar(view: ImageView, url: String) {
    if (url.isBlank()) {
        view.setImageDrawable(ColorDrawable(0xFF3C2A55.toInt()))
        return
    }
    Glide.with(view)
        .load(url)
        .override(96, 96)
        .dontAnimate()
        .circleCrop()
        .placeholder(ColorDrawable(0xFF3C2A55.toInt()))
        .error(ColorDrawable(0xFF3C2A55.toInt()))
        .into(view)
}

private fun circleDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
}

private fun dpShort(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
