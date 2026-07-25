package com.geovideos.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.geovideos.app.playback.GeoPlayerConnection

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
    playerConnection: GeoPlayerConnection,
    onLoadMore: () -> Unit,
    onPreview: (VideoItem) -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    onLike: (VideoItem) -> Unit,
    onDislike: (VideoItem) -> Unit
) {
    val controller by playerConnection.controller.collectAsStateWithLifecycle()
    val playback by playerConnection.coreState.collectAsStateWithLifecycle()
    val shortVideos = remember(videos) { videos.filter(::isStrictShort).distinctBy { it.id } }
    val adapter = remember {
        NativeShortsAdapter(
            onPreview = onPreview,
            onOpenComments = onOpenVideo,
            onWatchLater = onWatchLater,
            onLike = onLike,
            onDislike = onDislike,
            onTogglePlayback = { video ->
                if (playback.currentVideoId == video.id) {
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
                setHasFixedSize(true)
                setItemViewCacheSize(3)
                overScrollMode = View.OVER_SCROLL_NEVER
                val snap = PagerSnapHelper()
                snap.attachToRecyclerView(this)
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                        val snapView = snap.findSnapView(manager) ?: return
                        val position = manager.getPosition(snapView)
                        adapter.currentList.getOrNull(position)?.let { item ->
                            if (item.id != adapter.activeId) adapter.onPreviewCurrent(item)
                            adapter.preloadAround(position)
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
            adapter.updateCallbacks(onPreview, onOpenVideo, onWatchLater, onLike, onDislike) { video ->
                if (playback.currentVideoId == video.id) {
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
                connecting = loading || playback.connecting || playback.resolving
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
}

private fun isStrictShort(video: VideoItem): Boolean {
    if (video.durationMs in 1L..75_000L) return true
    val text = "${video.title} ${video.description}".lowercase()
    return listOf("#shorts", "#short", " tiktok", " reel", "video vertical").any { it in text }
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

    init { setHasStableIds(true) }
    override fun getItemId(position: Int) = getItem(position).id.hashCode().toLong()
    fun onPreviewCurrent(video: VideoItem) = onPreview(video)

    fun updateCallbacks(
        preview: (VideoItem) -> Unit,
        comments: (VideoItem) -> Unit,
        later: (VideoItem) -> Unit,
        like: (VideoItem) -> Unit,
        dislike: (VideoItem) -> Unit,
        toggle: (VideoItem) -> Unit
    ) {
        onPreview = preview
        onOpenComments = comments
        onWatchLater = later
        onLike = like
        onDislike = dislike
        onTogglePlayback = toggle
    }

    fun setState(
        selectedVideoId: String,
        liked: Set<String>,
        disliked: Set<String>,
        controller: MediaController?,
        connecting: Boolean
    ) {
        val previous = activeId
        activeId = selectedVideoId
        likedIds = liked
        dislikedIds = disliked
        this.controller = controller
        this.connecting = connecting
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
                parent.measuredHeight.takeIf { it > 0 } ?: ViewGroup.LayoutParams.MATCH_PARENT
            )
        })

    override fun onBindViewHolder(holder: NativeShortHolder, position: Int) {
        val video = getItem(position)
        holder.bind(
            video = video,
            active = video.id == activeId,
            liked = video.id in likedIds,
            disliked = video.id in dislikedIds,
            controller = controller,
            connecting = connecting,
            onToggle = onTogglePlayback,
            onComments = onOpenComments,
            onLater = onWatchLater,
            onLike = onLike,
            onDislike = onDislike
        )
    }

    override fun onViewRecycled(holder: NativeShortHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
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
        onToggle: (VideoItem) -> Unit,
        onComments: (VideoItem) -> Unit,
        onLater: (VideoItem) -> Unit,
        onLike: (VideoItem) -> Unit,
        onDislike: (VideoItem) -> Unit
    ) {
        page.channel.text = video.channelTitle.ifBlank { "Canal" }
        page.title.text = video.title
        page.like.setSelectedState(liked)
        page.dislike.setSelectedState(disliked)
        page.setOnClickListener { onToggle(video) }
        page.like.setOnClickListener { onLike(video) }
        page.dislike.setOnClickListener { onDislike(video) }
        page.comments.setOnClickListener { onComments(video) }
        page.save.setOnClickListener { onLater(video) }
        page.share.setOnClickListener { shareShort(page.context, video) }
        loadShortImage(page.thumbnail, video.thumbnailUrl)
        if (active && controller != null && controller.currentMediaItem?.mediaId == video.id && !connecting) {
            page.attach(controller)
        } else {
            page.detach()
        }
        page.progress.visibility = if (active && connecting) View.VISIBLE else View.GONE
    }

    fun recycle() {
        page.detach()
        Glide.with(page.thumbnail).clear(page.thumbnail)
        page.setOnClickListener(null)
    }
}

private class ShortPageView(context: Context) : FrameLayout(context) {
    val thumbnail = ImageView(context)
    val playerContainer = FrameLayout(context)
    val progress = ProgressBar(context)
    val channel = TextView(context)
    val title = TextView(context)
    val like = ShortActionView(context, R.drawable.ic_short_like, "Me gusta")
    val dislike = ShortActionView(context, R.drawable.ic_short_dislike, "No me gusta")
    val comments = ShortActionView(context, R.drawable.ic_short_comment, "Comentarios")
    val share = ShortActionView(context, R.drawable.ic_short_share, "Compartir")
    val save = ShortActionView(context, R.drawable.ic_short_save, "Guardar")
    private val playerView = (LayoutInflater.from(context).inflate(R.layout.geo_player_texture, null, false) as PlayerView).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        setShutterBackgroundColor(Color.TRANSPARENT)
        setKeepContentOnPlayerReset(true)
    }

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(thumbnail, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(playerContainer, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x22000000, 0x00000000, 0x00000000, 0xD9000000.toInt())
            )
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(progress, LayoutParams(dpShort(context, 38), dpShort(context, 38), Gravity.CENTER))

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            channel.setTextColor(Color.WHITE)
            channel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            channel.setTypeface(channel.typeface, android.graphics.Typeface.BOLD)
            channel.maxLines = 1
            channel.ellipsize = TextUtils.TruncateAt.END
            title.setTextColor(Color.WHITE)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            title.maxLines = 3
            title.ellipsize = TextUtils.TruncateAt.END
            addView(channel)
            addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpShort(context, 7)
            })
        }
        addView(info, LayoutParams((context.resources.displayMetrics.widthPixels * 0.72f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dpShort(context, 16), 0, 0, dpShort(context, 22))
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            listOf(like, dislike, comments, share, save).forEach { action ->
                addView(action, LinearLayout.LayoutParams(dpShort(context, 62), dpShort(context, 64)).apply {
                    bottomMargin = dpShort(context, 5)
                })
            }
        }
        addView(actions, LayoutParams(dpShort(context, 70), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dpShort(context, 7), dpShort(context, 15))
        })
    }

    fun attach(controller: MediaController) {
        if (playerView.parent !== playerContainer) {
            (playerView.parent as? ViewGroup)?.removeView(playerView)
            playerContainer.removeAllViews()
            playerContainer.addView(playerView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        playerView.player = controller
        playerContainer.visibility = View.VISIBLE
    }

    fun detach() {
        playerView.player = null
        playerContainer.removeAllViews()
        playerContainer.visibility = View.GONE
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
        addView(icon, LayoutParams(dpShort(context, 29), dpShort(context, 29)))
        text.text = label
        text.gravity = Gravity.CENTER
        text.setTextColor(Color.WHITE)
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
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

private fun dpShort(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
