package com.geovideos.app.ui

import android.content.Context
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
    val adapter = remember {
        NativeShortsAdapter(
            onPreview = onPreview,
            onOpenVideo = onOpenVideo,
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
                val manager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
                layoutManager = manager
                this.adapter = adapter
                itemAnimator = null
                setHasFixedSize(true)
                setItemViewCacheSize(2)
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
            adapter.submitList(videos.distinctBy { it.id })
            if (selectedVideoId.isBlank() && videos.isNotEmpty() && adapter.activeId.isBlank()) {
                recyclerView.post { adapter.onPreviewCurrent(videos.first()) }
            }
            adapter.setState(
                selectedVideoId = selectedVideoId,
                liked = localLikedIds,
                disliked = localDislikedIds,
                controller = controller,
                connecting = playback.connecting || playback.resolving
            )
            if (selectedVideoId.isNotBlank()) {
                val index = videos.indexOfFirst { it.id == selectedVideoId }
                val manager = recyclerView.layoutManager as? LinearLayoutManager
                if (index >= 0 && manager?.findFirstCompletelyVisibleItemPosition() != index) {
                    recyclerView.post { manager?.scrollToPositionWithOffset(index, 0) }
                }
            }
        }
    )
}

private object ShortVideoDiff : DiffUtil.ItemCallback<VideoItem>() {
    override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem) = oldItem == newItem
}

private class NativeShortsAdapter(
    private var onPreview: (VideoItem) -> Unit,
    private var onOpenVideo: (VideoItem) -> Unit,
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
    fun onPreviewCurrent(video: VideoItem) = onPreview(video)
    private var likedIds: Set<String> = emptySet()
    private var dislikedIds: Set<String> = emptySet()
    private var controller: MediaController? = null
    private var connecting = false

    init { setHasStableIds(true) }
    override fun getItemId(position: Int) = getItem(position).id.hashCode().toLong()

    fun updateCallbacks(
        preview: (VideoItem) -> Unit,
        open: (VideoItem) -> Unit,
        later: (VideoItem) -> Unit,
        like: (VideoItem) -> Unit,
        dislike: (VideoItem) -> Unit,
        toggle: (VideoItem) -> Unit
    ) {
        onPreview = preview; onOpenVideo = open; onWatchLater = later
        onLike = like; onDislike = dislike; onTogglePlayback = toggle
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
        if (previous != activeId) {
            currentList.indexOfFirst { it.id == previous }.takeIf { it >= 0 }?.let(::notifyItemChanged)
            currentList.indexOfFirst { it.id == activeId }.takeIf { it >= 0 }?.let(::notifyItemChanged)
        } else {
            currentList.indexOfFirst { it.id == activeId }.takeIf { it >= 0 }?.let(::notifyItemChanged)
        }
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
            onOpen = onOpenVideo,
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
        onOpen: (VideoItem) -> Unit,
        onLater: (VideoItem) -> Unit,
        onLike: (VideoItem) -> Unit,
        onDislike: (VideoItem) -> Unit
    ) {
        page.channel.text = video.channelTitle.ifBlank { "Canal" }
        page.title.text = video.title
        page.like.text = if (liked) "♥\nMe gusta" else "♡\nMe gusta"
        page.dislike.text = if (disliked) "▼\nNo me gusta" else "▽\nNo me gusta"
        page.setOnClickListener { onToggle(video) }
        page.like.setOnClickListener { onLike(video) }
        page.dislike.setOnClickListener { onDislike(video) }
        page.save.setOnClickListener { onLater(video) }
        page.open.setOnClickListener { onOpen(video) }
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
    val like = TextView(context)
    val dislike = TextView(context)
    val save = TextView(context)
    val open = TextView(context)
    private val playerView = (LayoutInflater.from(context).inflate(R.layout.geo_player_surface, null, false) as PlayerView).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        setShutterBackgroundColor(Color.TRANSPARENT)
        setKeepContentOnPlayerReset(true)
    }

    init {
        setBackgroundColor(Color.BLACK)
        thumbnail.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(thumbnail, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(playerContainer, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x22000000, 0x00000000, 0xCC000000.toInt()))
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(progress, LayoutParams(dpShort(context, 42), dpShort(context, 42), Gravity.CENTER))

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            channel.setTextColor(Color.WHITE)
            channel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            channel.setTypeface(channel.typeface, android.graphics.Typeface.BOLD)
            title.setTextColor(Color.WHITE)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
            title.maxLines = 3
            title.ellipsize = TextUtils.TruncateAt.END
            addView(channel)
            addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpShort(context, 8) })
        }
        addView(info, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            width = (context.resources.displayMetrics.widthPixels * 0.76f).toInt()
            setMargins(dpShort(context, 16), 0, 0, dpShort(context, 24))
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            listOf(like, dislike, save, open).forEach { button ->
                button.gravity = Gravity.CENTER
                button.setTextColor(Color.WHITE)
                button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                button.background = roundedShort(0x66000000, 22f, context)
                addView(button, LinearLayout.LayoutParams(dpShort(context, 64), dpShort(context, 62)).apply { bottomMargin = dpShort(context, 7) })
            }
            save.text = "＋\nGuardar"
            open.text = "□\nAbrir"
        }
        addView(actions, LayoutParams(dpShort(context, 72), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dpShort(context, 7), dpShort(context, 16))
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

private fun loadShortImage(view: ImageView, url: String) {
    val manager = Glide.with(view)
    if (!url.contains("ytimg.com", true) && !url.contains("youtube.com", true)) {
        manager.load(url)
            .override(540, 960)
            .dontAnimate()
            .placeholder(ColorDrawable(0xFF202024.toInt()))
            .error(ColorDrawable(0xFF2B1B45.toInt()))
            .fitCenter()
            .into(view)
        return
    }
    fun candidate(name: String) = url.replace(
        Regex("(maxresdefault|sddefault|hqdefault|mqdefault|default)\\.jpg"),
        name
    )
    val low = manager.load(candidate("mqdefault.jpg"))
        .override(320, 568)
        .dontAnimate()
        .fitCenter()
    val fallback = manager.load(candidate("hqdefault.jpg"))
        .override(540, 960)
        .dontAnimate()
        .fitCenter()
        .error(low)
    manager.load(candidate("sddefault.jpg"))
        .override(540, 960)
        .dontAnimate()
        .placeholder(ColorDrawable(0xFF202024.toInt()))
        .thumbnail(low)
        .error(fallback)
        .fitCenter()
        .into(view)
}

private fun roundedShort(color: Int, radius: Float, context: Context) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius * context.resources.displayMetrics.density
}

private fun dpShort(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
