package com.geovideos.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geovideos.app.data.VideoItem
import kotlin.math.roundToInt

@Composable
internal fun RecyclerHomeFeed(
    modifier: Modifier,
    videos: List<VideoItem>,
    shorts: List<VideoItem>,
    showShortsShelf: Boolean,
    mixVideo: VideoItem?,
    loading: Boolean,
    refreshing: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    watchLaterIds: Set<String>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit,
    scrollToTopSignal: Long,
    onAtTopChanged: (Boolean) -> Unit
) {
    var recyclerViewRef by remember { mutableStateOf<RecyclerView?>(null) }
    val adapter = remember {
        HomeFeedAdapter(
            onPlay = onPlay,
            onOpenShort = onOpenShort,
            onWatchLater = onWatchLater
        )
    }

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0L) recyclerViewRef?.scrollHomeToTop()
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val root = FrameLayout(context)
            val recycler = RecyclerView(context).apply {
                recyclerViewRef = this
                layoutManager = LinearLayoutManager(context).apply {
                    initialPrefetchItemCount = 5
                }
                this.adapter = adapter
                itemAnimator = null
                setHasFixedSize(false)
                setItemViewCacheSize(6)
                recycledViewPool.setMaxRecycledViews(HomeFeedAdapter.TYPE_VIDEO, 8)
                overScrollMode = View.OVER_SCROLL_NEVER
                isNestedScrollingEnabled = true
            }
            root.addView(
                recycler,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            val indicator = ProgressBar(context).apply {
                visibility = View.GONE
                alpha = 0f
                scaleX = 0.70f
                scaleY = 0.70f
            }
            root.addView(
                indicator,
                FrameLayout.LayoutParams(dp(context, 34), dp(context, 34), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = dp(context, 5)
                }
            )

            val runtime = HomeFeedRuntime(
                onAtTopChanged = onAtTopChanged,
                onRefresh = onRefresh,
                refreshing = refreshing,
                recycler = recycler,
                indicator = indicator
            )
            root.tag = runtime

            recycler.setOnTouchListener { _, event ->
                val density = context.resources.displayMetrics.density
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        runtime.startY = event.y
                        runtime.pullEligible = !recycler.canScrollVertically(-1)
                        runtime.pullDistance = 0f
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!recycler.canScrollVertically(-1) && event.y >= runtime.startY) {
                            runtime.pullEligible = true
                            val raw = (event.y - runtime.startY).coerceAtLeast(0f)
                            runtime.pullDistance = (raw * 0.28f).coerceAtMost(48f * density)
                            // Mantener el feed fijo. Solo el indicador acompana levemente el gesto,
                            // evitando que toda la lista se desplace y se vea como una capa suelta.
                            val pullProgress = (runtime.pullDistance / (34f * density)).coerceIn(0f, 1f)
                            if (runtime.pullDistance >= 10f * density) {
                                indicator.visibility = View.VISIBLE
                                indicator.alpha = pullProgress
                                indicator.translationY = (8f * density * pullProgress)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val shouldRefresh = runtime.pullEligible &&
                            !runtime.refreshing &&
                            runtime.pullDistance >= 36f * density
                        runtime.pullDistance = 0f
                        runtime.pullEligible = false
                        if (shouldRefresh) {
                            indicator.visibility = View.VISIBLE
                            indicator.animate().translationY(0f).alpha(1f).setDuration(120L).start()
                            runtime.onRefresh()
                        } else if (!runtime.refreshing) {
                            indicator.animate().translationY(0f).alpha(0f).setDuration(120L).withEndAction {
                                if (!runtime.refreshing) indicator.visibility = View.GONE
                            }.start()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        runtime.pullDistance = 0f
                        runtime.pullEligible = false
                        if (!runtime.refreshing) {
                            indicator.animate().translationY(0f).alpha(0f).setDuration(100L).withEndAction {
                                if (!runtime.refreshing) indicator.visibility = View.GONE
                            }.start()
                        }
                    }
                }
                false
            }

            recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    runtime.onAtTopChanged(!recyclerView.canScrollVertically(-1))
                    if (dy <= 0) return
                    val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val lastVisible = manager.findLastVisibleItemPosition()
                    val total = recyclerView.adapter?.itemCount ?: 0
                    val feedAdapter = recyclerView.adapter as? HomeFeedAdapter ?: return
                    if (
                        feedAdapter.canLoadMore &&
                        !feedAdapter.loadingMore &&
                        total > 0 &&
                        lastVisible >= total - 4
                    ) {
                        feedAdapter.loadingMore = true
                        feedAdapter.onLoadMore()
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    runtime.onAtTopChanged(!recyclerView.canScrollVertically(-1))
                }
            })
            recycler.post { runtime.onAtTopChanged(!recycler.canScrollVertically(-1)) }
            root
        },
        update = { root ->
            val runtime = root.tag as HomeFeedRuntime
            val recyclerView = runtime.recycler
            recyclerViewRef = recyclerView
            runtime.onAtTopChanged = onAtTopChanged
            runtime.onRefresh = onRefresh
            runtime.refreshing = refreshing
            runtime.indicator.apply {
                if (refreshing) {
                    visibility = View.VISIBLE
                    alpha = 1f
                } else if (runtime.pullDistance <= 0f) {
                    animate().translationY(0f).alpha(0f).setDuration(120L).withEndAction {
                        if (!runtime.refreshing) visibility = View.GONE
                    }.start()
                }
            }
            onAtTopChanged(!recyclerView.canScrollVertically(-1))
            val homeAdapter = recyclerView.adapter as HomeFeedAdapter
            homeAdapter.onPlay = onPlay
            homeAdapter.onOpenShort = onOpenShort
            homeAdapter.onWatchLater = onWatchLater
            homeAdapter.onLoadMore = onLoadMore
            homeAdapter.canLoadMore = canLoadMore
            homeAdapter.loadingMore = loadingMore
            homeAdapter.submitFeed(
                videos = videos,
                shorts = shorts,
                showShortsShelf = showShortsShelf,
                mixVideo = mixVideo,
                watchLaterIds = watchLaterIds,
                loading = loading,
                refreshing = refreshing,
                loadingMore = loadingMore
            )
        }
    )
}

private class HomeFeedRuntime(
    var onAtTopChanged: (Boolean) -> Unit,
    var onRefresh: () -> Unit,
    var refreshing: Boolean,
    val recycler: RecyclerView,
    val indicator: ProgressBar
) {
    var startY: Float = 0f
    var pullEligible: Boolean = false
    var pullDistance: Float = 0f
}

private fun RecyclerView.scrollHomeToTop() {
    val manager = layoutManager as? LinearLayoutManager ?: return
    val first = manager.findFirstVisibleItemPosition().coerceAtLeast(0)
    if (first > 12) scrollToPosition(8)
    post { smoothScrollToPosition(0) }
}

private sealed interface HomeFeedRow {
    val stableId: Long

    data class Shorts(val videos: List<VideoItem>) : HomeFeedRow {
        override val stableId: Long = Long.MIN_VALUE + 10
    }

    data class Mix(val video: VideoItem) : HomeFeedRow {
        override val stableId: Long = Long.MIN_VALUE + 11
    }

    data class Video(val video: VideoItem, val saved: Boolean) : HomeFeedRow {
        override val stableId: Long = video.id.hashCode().toLong()
    }

    data class Skeleton(val index: Int, val shortsStyle: Boolean = false) : HomeFeedRow {
        override val stableId: Long = Long.MIN_VALUE + 100 + index
    }

    data object Loading : HomeFeedRow {
        override val stableId: Long = Long.MIN_VALUE + 20
    }

    data object Empty : HomeFeedRow {
        override val stableId: Long = Long.MIN_VALUE + 30
    }
}

private object HomeFeedDiff : DiffUtil.ItemCallback<HomeFeedRow>() {
    override fun areItemsTheSame(oldItem: HomeFeedRow, newItem: HomeFeedRow): Boolean =
        oldItem.stableId == newItem.stableId

    override fun areContentsTheSame(oldItem: HomeFeedRow, newItem: HomeFeedRow): Boolean =
        oldItem == newItem
}

private class HomeFeedAdapter(
    var onPlay: (VideoItem) -> Unit,
    var onOpenShort: (VideoItem) -> Unit,
    var onWatchLater: (VideoItem) -> Unit
) : ListAdapter<HomeFeedRow, RecyclerView.ViewHolder>(HomeFeedDiff) {

    var onLoadMore: () -> Unit = {}
    var canLoadMore: Boolean = false
    var loadingMore: Boolean = false

    init {
        setHasStableIds(true)
    }

    fun submitFeed(
        videos: List<VideoItem>,
        shorts: List<VideoItem>,
        showShortsShelf: Boolean,
        mixVideo: VideoItem?,
        watchLaterIds: Set<String>,
        loading: Boolean,
        refreshing: Boolean,
        loadingMore: Boolean
    ) {
        val hasContent = videos.isNotEmpty() || shorts.isNotEmpty() || mixVideo != null
        val rows = buildList {
            // Principal siempre reserva primero la zona de Shorts. En una cuenta recién
            // conectada se ve el skeleton de esa fila mientras llegan Shorts reales, y debajo
            // pueden aparecer ya los videos de suscripciones sin invertir el orden de la Home.
            if (showShortsShelf) {
                if (shorts.isNotEmpty()) add(HomeFeedRow.Shorts(shorts.take(14)))
                else if (loading) add(HomeFeedRow.Skeleton(index = 0, shortsStyle = true))
            }
            mixVideo?.let { add(HomeFeedRow.Mix(it)) }
            videos.forEach { video ->
                add(HomeFeedRow.Video(video, video.id in watchLaterIds))
            }
            when {
                loadingMore && !refreshing -> add(HomeFeedRow.Loading)
                loading && !hasContent -> {
                    // La fila de Shorts ya reservó su espacio arriba; este segundo skeleton es
                    // la primera tarjeta de video normal.
                    add(HomeFeedRow.Skeleton(index = 1))
                }
                !hasContent -> add(HomeFeedRow.Empty)
            }
        }
        this.loadingMore = loadingMore
        submitList(rows)
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is HomeFeedRow.Shorts -> TYPE_SHORTS
        is HomeFeedRow.Mix -> TYPE_MIX
        is HomeFeedRow.Video -> TYPE_VIDEO
        is HomeFeedRow.Skeleton -> TYPE_SKELETON
        HomeFeedRow.Loading -> TYPE_LOADING
        HomeFeedRow.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        TYPE_SHORTS -> ShortsShelfHolder(parent.context, onOpenShort)
        TYPE_MIX -> MixHolder(parent.context, onPlay)
        TYPE_VIDEO -> VideoHolder(parent.context, onPlay, onWatchLater)
        TYPE_SKELETON -> SkeletonHolder(parent.context)
        TYPE_LOADING -> LoadingHolder(parent.context)
        else -> EmptyHolder(parent.context)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is HomeFeedRow.Shorts -> (holder as ShortsShelfHolder).bind(row.videos, onOpenShort)
            is HomeFeedRow.Mix -> (holder as MixHolder).bind(row.video, onPlay)
            is HomeFeedRow.Video -> (holder as VideoHolder).bind(row.video, row.saved, onPlay, onWatchLater)
            is HomeFeedRow.Skeleton -> (holder as SkeletonHolder).bind(row.shortsStyle)
            HomeFeedRow.Loading -> Unit
            HomeFeedRow.Empty -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is VideoHolder -> holder.recycle()
            is MixHolder -> holder.recycle()
            is ShortsShelfHolder -> holder.recycle()
        }
    }

    companion object {
        const val TYPE_SHORTS = 1
        const val TYPE_MIX = 2
        const val TYPE_VIDEO = 3
        const val TYPE_LOADING = 4
        const val TYPE_EMPTY = 5
        const val TYPE_SKELETON = 6
    }
}


private class MixHolder(
    context: Context,
    onPlay: (VideoItem) -> Unit
) : RecyclerView.ViewHolder(MixCardView(context)) {
    private val card = itemView as MixCardView
    private var current: VideoItem? = null

    init { card.setOnClickListener { current?.let(onPlay) } }

    fun bind(video: VideoItem, onPlay: (VideoItem) -> Unit) {
        val mixVideo = video.copy(isMix = true)
        current = mixVideo
        card.setOnClickListener { onPlay(mixVideo) }
        card.title.text = "Mix: ${video.title}"
        card.subtitle.text = buildString {
            append(video.channelTitle.ifBlank { "Selección automática" })
            append(" y más")
        }
        loadClearThumbnail(card.image, video.thumbnailUrl, 720, 405)
    }

    fun recycle() {
        current = null
        Glide.with(card.image).clear(card.image)
        card.setOnClickListener(null)
    }
}

private class VideoHolder(
    context: Context,
    onPlay: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) : RecyclerView.ViewHolder(VideoCardView(context)) {
    private val card = itemView as VideoCardView
    private var current: VideoItem? = null

    init {
        card.setOnClickListener { current?.let(onPlay) }
        card.moreButton.setOnClickListener { current?.let(onWatchLater) }
    }

    fun bind(
        video: VideoItem,
        saved: Boolean,
        onPlay: (VideoItem) -> Unit,
        onWatchLater: (VideoItem) -> Unit
    ) {
        current = video
        card.setOnClickListener { onPlay(video) }
        card.moreButton.setOnClickListener { onWatchLater(video) }
        card.titleView.text = video.title
        card.channelView.text = buildString {
            append(video.channelTitle)
            val published = formatRecyclerPublished(video.publishedAt)
            if (published.isNotBlank()) {
                append(" · ")
                append(published)
            }
        }
        card.moreButton.text = if (saved) "✓" else "⋮"
        card.liveBadge.visibility = if (video.isLive) View.VISIBLE else View.GONE
        card.durationBadge.text = formatRecyclerDuration(video.durationMs)
        card.durationBadge.visibility = if (video.durationMs > 0L && !video.isLive) View.VISIBLE else View.GONE

        loadClearThumbnail(card.thumbnail, video.thumbnailUrl, 640, 360)

        if (video.channelThumbnailUrl.isBlank()) {
            Glide.with(card.avatar).clear(card.avatar)
            card.avatar.setImageDrawable(null)
            card.avatar.background = avatarPlaceholder()
        } else {
            card.avatar.background = avatarPlaceholder()
            Glide.with(card.avatar)
                .load(video.channelThumbnailUrl)
                .override(72, 72)
                .dontAnimate()
                .placeholder(avatarPlaceholder())
                .error(avatarPlaceholder())
                .circleCrop()
                .into(card.avatar)
        }
    }

    fun recycle() {
        current = null
        card.titleView.text = ""
        card.channelView.text = ""
        card.moreButton.text = "⋮"
        card.liveBadge.visibility = View.GONE
        card.durationBadge.visibility = View.GONE
        Glide.with(card.thumbnail).clear(card.thumbnail)
        Glide.with(card.avatar).clear(card.avatar)
        card.avatar.setImageDrawable(null)
        card.avatar.background = avatarPlaceholder()
        card.setOnClickListener(null)
        card.moreButton.setOnClickListener(null)
    }
}

private class ShortsShelfHolder(
    context: Context,
    onOpenShort: (VideoItem) -> Unit
) : RecyclerView.ViewHolder(ShortsShelfView(context, onOpenShort)) {
    private val shelf = itemView as ShortsShelfView

    fun bind(videos: List<VideoItem>, onOpenShort: (VideoItem) -> Unit) {
        shelf.adapter.onOpenShort = onOpenShort
        shelf.adapter.submitList(videos)
    }

    fun recycle() {
        // The nested RecyclerView owns its recycled image requests.
    }
}

private class SkeletonHolder(context: Context) : RecyclerView.ViewHolder(SkeletonFeedView(context)) {
    private val view = itemView as SkeletonFeedView
    fun bind(shortsStyle: Boolean) = view.bind(shortsStyle)
}

private class SkeletonFeedView(context: Context) : LinearLayout(context) {
    private val imageBlock = View(context)
    private val avatarBlock = View(context)
    private val titleBlock = View(context)
    private val subtitleBlock = View(context)
    private val shortsRow = LinearLayout(context)

    init {
        orientation = VERTICAL
        layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 14))
        setBackgroundColor(Color.BLACK)

        shortsRow.orientation = HORIZONTAL
        shortsRow.gravity = Gravity.CENTER_VERTICAL
        repeat(3) { index ->
            shortsRow.addView(View(context).apply {
                background = roundedSkeleton(context, 14f)
            }, LayoutParams(dp(context, 146), dp(context, 258)).apply {
                if (index > 0) marginStart = dp(context, 10)
            })
        }
        addView(shortsRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        imageBlock.background = roundedSkeleton(context, 0f)
        addView(imageBlock, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 205)))

        val info = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(context, 11), 0, 0)
        }
        avatarBlock.background = roundedSkeleton(context, 22f)
        info.addView(avatarBlock, LayoutParams(dp(context, 42), dp(context, 42)))
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            titleBlock.background = roundedSkeleton(context, 6f)
            subtitleBlock.background = roundedSkeleton(context, 6f)
            addView(titleBlock, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 15)))
            addView(subtitleBlock, LayoutParams(dp(context, 180), dp(context, 12)).apply {
                topMargin = dp(context, 8)
            })
        }
        info.addView(texts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(context, 12)
            marginEnd = dp(context, 38)
        })
        addView(info, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun bind(shortsStyle: Boolean) {
        shortsRow.visibility = if (shortsStyle) View.VISIBLE else View.GONE
        imageBlock.visibility = if (shortsStyle) View.GONE else View.VISIBLE
        avatarBlock.visibility = if (shortsStyle) View.GONE else View.VISIBLE
        titleBlock.visibility = if (shortsStyle) View.GONE else View.VISIBLE
        subtitleBlock.visibility = if (shortsStyle) View.GONE else View.VISIBLE
        layoutParams = (layoutParams as RecyclerView.LayoutParams).apply {
            height = if (shortsStyle) dp(context, 284) else ViewGroup.LayoutParams.WRAP_CONTENT
        }
        alpha = 0.86f
    }
}

private fun avatarPlaceholder(): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(0xFF2B2B31.toInt())
}

private fun roundedSkeleton(context: Context, radiusDp: Float): GradientDrawable =
    GradientDrawable().apply {
        setColor(0xFF1B1B20.toInt())
        cornerRadius = dp(context, radiusDp.roundToInt()).toFloat()
    }

private class LoadingHolder(context: Context) : RecyclerView.ViewHolder(
    FrameLayout(context).apply {
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 52))
        addView(
            ProgressBar(context).apply {
                scaleX = 0.72f
                scaleY = 0.72f
            },
            FrameLayout.LayoutParams(dp(context, 38), dp(context, 38), Gravity.CENTER)
        )
    }
)

private class EmptyHolder(context: Context) : RecyclerView.ViewHolder(
    TextView(context).apply {
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 120))
        text = "No hay videos disponibles. Desliza hacia abajo para actualizar."
        gravity = Gravity.CENTER
        setTextColor(0xFFB6B3BE.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20))
    }
)

private class ShortsShelfView(
    context: Context,
    onOpenShort: (VideoItem) -> Unit
) : LinearLayout(context) {
    val adapter = ShortsAdapter(onOpenShort)

    init {
        orientation = VERTICAL
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setPadding(0, dp(context, 8), 0, dp(context, 14))

        addView(TextView(context).apply {
            text = "⚡  Shorts"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 10))
        })

        addView(RecyclerView(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 304))
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false).apply {
                initialPrefetchItemCount = 3
            }
            this.adapter = this@ShortsShelfView.adapter
            itemAnimator = null
            setItemViewCacheSize(4)
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
            clipToPadding = false
        })
    }
}

private object ShortDiff : DiffUtil.ItemCallback<VideoItem>() {
    override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean = oldItem == newItem
}

private class ShortsAdapter(
    var onOpenShort: (VideoItem) -> Unit
) : ListAdapter<VideoItem, ShortHolder>(ShortDiff) {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortHolder = ShortHolder(parent.context)

    override fun onBindViewHolder(holder: ShortHolder, position: Int) {
        val video = getItem(position)
        holder.bind(video, onOpenShort)
    }

    override fun onViewRecycled(holder: ShortHolder) {
        holder.recycle()
    }
}

private class ShortHolder(context: Context) : RecyclerView.ViewHolder(ShortCardView(context)) {
    private val card = itemView as ShortCardView

    fun bind(video: VideoItem, onOpenShort: (VideoItem) -> Unit) {
        card.setOnClickListener { onOpenShort(video) }
        card.title.text = video.title
        card.meta.text = buildString {
            append("Recomendado")
            formatRecyclerPublished(video.publishedAt).takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        }
        loadClearThumbnail(card.image, video.thumbnailUrl, 360, 640, fitCenter = false)
    }

    fun recycle() {
        Glide.with(card.image).clear(card.image)
    }
}

private class VideoCardView(context: Context) : LinearLayout(context) {
    val thumbnail = ImageView(context)
    val avatar = ImageView(context)
    val titleView = TextView(context)
    val channelView = TextView(context)
    val moreButton = TextView(context)
    val liveBadge = TextView(context)
    val durationBadge = TextView(context)

    init {
        orientation = VERTICAL
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        isClickable = true
        isFocusable = true
        foreground = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).getDrawable(0)

        val mediaFrame = SixteenNineFrame(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(thumbnail, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(liveBadge.apply {
                text = "EN VIVO"
                setTextColor(Color.WHITE)
                setBackgroundColor(0xFFD32F2F.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(context, 7), dp(context, 3), dp(context, 7), dp(context, 3))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                setMargins(dp(context, 10), 0, 0, dp(context, 10))
            })
            addView(durationBadge.apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(0xCC000000.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(context, 6), dp(context, 2), dp(context, 6), dp(context, 2))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(context, 10), dp(context, 10))
            })
        }
        addView(mediaFrame)

        val info = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(context, 14), dp(context, 10), dp(context, 8), dp(context, 11))
        }
        info.addView(avatar.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = avatarPlaceholder()
            clipToOutline = true
        }, LayoutParams(dp(context, 40), dp(context, 40)))

        info.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(titleView.apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(channelView.apply {
                setTextColor(0xFFB6B3BE.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(context, 4), 0, 0)
            }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(context, 11)
        })

        info.addView(moreButton.apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            isClickable = true
            isFocusable = true
            foreground = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless)).getDrawable(0)
        }, LayoutParams(dp(context, 44), dp(context, 44)))
        addView(info)
    }
}

private class ShortCardView(context: Context) : FrameLayout(context) {
    val image = ImageView(context)
    val title = TextView(context)
    val meta = TextView(context)
    private val more = TextView(context)

    init {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth * 0.445f).roundToInt().coerceIn(dp(context, 148), dp(context, 184))
        layoutParams = RecyclerView.LayoutParams(cardWidth, dp(context, 292)).apply {
            marginEnd = dp(context, 10)
        }
        setBackgroundColor(Color.BLACK)
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(context, 12).toFloat())
            }
        }
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(image, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val gradient = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xE6000000.toInt(), 0x70000000, 0x00000000)
            )
        }
        addView(gradient, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 132), Gravity.BOTTOM))

        more.apply {
            text = "⋮"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.CENTER
            setShadowLayer(5f, 0f, 1f, Color.BLACK)
        }
        addView(more, LayoutParams(dp(context, 34), dp(context, 40), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(context, 4), dp(context, 3), 0)
        })

        val textBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 9), dp(context, 8), dp(context, 9), dp(context, 10))
            title.setTextColor(Color.WHITE)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
            title.maxLines = 3
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            meta.setTextColor(0xFFE1DDE7.toInt())
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            meta.maxLines = 1
            meta.ellipsize = android.text.TextUtils.TruncateAt.END
            meta.setPadding(0, dp(context, 5), 0, 0)
            addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(meta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        addView(textBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }
}

private class MixCardView(context: Context) : LinearLayout(context) {
    val image = ImageView(context)
    val title = TextView(context)
    val subtitle = TextView(context)

    init {
        orientation = VERTICAL
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        isClickable = true
        isFocusable = true
        foreground = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).getDrawable(0)

        val frame = SixteenNineFrame(context).apply {
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(context, 14), 0, dp(context, 14), 0)
            })
            addView(TextView(context).apply {
                text = "◉  Mix"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(context, 10).toFloat()
                    setColor(0x99000000.toInt())
                }
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(context, 22), dp(context, 10))
            })
        }
        addView(frame)

        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.maxLines = 2
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        addView(title, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(context, 16), dp(context, 9), dp(context, 16), 0)
        })

        subtitle.setTextColor(0xFFAAA7B2.toInt())
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        subtitle.maxLines = 1
        subtitle.ellipsize = android.text.TextUtils.TruncateAt.END
        addView(subtitle, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(context, 16), dp(context, 3), dp(context, 16), dp(context, 16))
        })
    }
}

private class SixteenNineFrame(context: Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 9f / 16f).toInt()
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
    }
}

private fun loadClearThumbnail(
    view: ImageView,
    url: String,
    width: Int,
    height: Int,
    fitCenter: Boolean = false
) {
    val manager = Glide.with(view)
    val placeholder = ColorDrawable(0xFF202024.toInt())
    if (!isYoutubeThumbnail(url)) {
        val request = manager.load(url)
            .override(width, height)
            .dontAnimate()
            .placeholder(placeholder)
            .error(ColorDrawable(0xFF242428.toInt()))
        if (fitCenter) request.fitCenter() else request.centerCrop()
        request.into(view)
        return
    }

    val low = manager.load(youtubeThumb(url, "mqdefault.jpg"))
        .override(320, 180)
        .dontAnimate()
        .centerCrop()
    val highFallback = manager.load(youtubeThumb(url, "hqdefault.jpg"))
        .override(width, height)
        .dontAnimate()
        .apply { if (fitCenter) fitCenter() else centerCrop() }
        .error(low)
    val clear = manager.load(youtubeThumb(url, "sddefault.jpg"))
        .override(width, height)
        .dontAnimate()
        .placeholder(placeholder)
        .thumbnail(low)
        .error(highFallback)
    if (fitCenter) clear.fitCenter() else clear.centerCrop()
    clear.into(view)
}

private fun isYoutubeThumbnail(url: String): Boolean =
    url.contains("ytimg.com", ignoreCase = true) ||
        url.contains("youtube.com", ignoreCase = true)

private fun youtubeThumb(url: String, fileName: String): String =
    url.replace(Regex("(maxresdefault|sddefault|hqdefault|mqdefault|default)\\.jpg"), fileName)

private fun formatRecyclerDuration(ms: Long): String {
    if (ms <= 0L) return ""
    val total = ms / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun formatRecyclerPublished(value: String): String {
    if (value.isBlank()) return ""
    if (value.contains("hace", ignoreCase = true)) return value
    return runCatching {
        val instant = runCatching { java.time.Instant.parse(value) }.getOrElse {
            java.time.LocalDate.parse(value.take(10))
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
        }
        val elapsed = (java.time.Instant.now().epochSecond - instant.epochSecond).coerceAtLeast(0L)
        when {
            elapsed < 3_600L -> "hace ${maxOf(1L, elapsed / 60L)} min"
            elapsed < 86_400L -> "hace ${elapsed / 3_600L} h"
            elapsed < 604_800L -> "hace ${elapsed / 86_400L} días"
            elapsed < 2_592_000L -> "hace ${elapsed / 604_800L} semanas"
            elapsed < 31_536_000L -> "hace ${elapsed / 2_592_000L} meses"
            else -> "hace ${elapsed / 31_536_000L} años"
        }
    }.getOrElse { value.take(10) }
}

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()

private fun resolveColor(context: Context, attr: Int, fallback: Int): Int {
    val value = TypedValue()
    return if (context.theme.resolveAttribute(attr, value, true)) value.data else fallback
}
