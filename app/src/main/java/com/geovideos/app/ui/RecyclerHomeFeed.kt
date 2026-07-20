package com.geovideos.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.geovideos.app.data.VideoItem

@Composable
internal fun RecyclerHomeFeed(
    modifier: Modifier,
    videos: List<VideoItem>,
    shorts: List<VideoItem>,
    loading: Boolean,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    watchLaterIds: Set<String>,
    onLoadMore: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onOpenShort: (VideoItem) -> Unit,
    onWatchLater: (VideoItem) -> Unit
) {
    val adapter = remember {
        HomeFeedAdapter(
            onPlay = onPlay,
            onOpenShort = onOpenShort,
            onWatchLater = onWatchLater
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context).apply {
                    initialPrefetchItemCount = 4
                }
                this.adapter = adapter
                itemAnimator = null
                setHasFixedSize(false)
                setItemViewCacheSize(4)
                recycledViewPool.setMaxRecycledViews(HomeFeedAdapter.TYPE_VIDEO, 8)
                overScrollMode = View.OVER_SCROLL_NEVER
                isNestedScrollingEnabled = true

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
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
                })
            }
        },
        update = { recyclerView ->
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
                watchLaterIds = watchLaterIds,
                loading = loading,
                loadingMore = loadingMore
            )
        }
    )
}

private sealed interface HomeFeedRow {
    val stableId: Long

    data class Shorts(val videos: List<VideoItem>) : HomeFeedRow {
        override val stableId: Long = Long.MIN_VALUE + 10
    }

    data class Video(val video: VideoItem, val saved: Boolean) : HomeFeedRow {
        override val stableId: Long = video.id.hashCode().toLong()
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
        watchLaterIds: Set<String>,
        loading: Boolean,
        loadingMore: Boolean
    ) {
        val rows = buildList {
            if (shorts.isNotEmpty()) add(HomeFeedRow.Shorts(shorts.take(10)))
            videos.forEach { video -> add(HomeFeedRow.Video(video, video.id in watchLaterIds)) }
            when {
                loadingMore -> add(HomeFeedRow.Loading)
                loading && videos.isEmpty() -> add(HomeFeedRow.Loading)
                videos.isEmpty() -> add(HomeFeedRow.Empty)
            }
        }
        this.loadingMore = loadingMore
        submitList(rows)
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is HomeFeedRow.Shorts -> TYPE_SHORTS
        is HomeFeedRow.Video -> TYPE_VIDEO
        HomeFeedRow.Loading -> TYPE_LOADING
        HomeFeedRow.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        TYPE_SHORTS -> ShortsShelfHolder(parent.context, onOpenShort)
        TYPE_VIDEO -> VideoHolder(parent.context, onPlay, onWatchLater)
        TYPE_LOADING -> LoadingHolder(parent.context)
        else -> EmptyHolder(parent.context)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is HomeFeedRow.Shorts -> (holder as ShortsShelfHolder).bind(row.videos, onOpenShort)
            is HomeFeedRow.Video -> (holder as VideoHolder).bind(row.video, row.saved, onPlay, onWatchLater)
            HomeFeedRow.Loading -> Unit
            HomeFeedRow.Empty -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is VideoHolder -> holder.recycle()
            is ShortsShelfHolder -> holder.recycle()
        }
    }

    companion object {
        const val TYPE_SHORTS = 1
        const val TYPE_VIDEO = 2
        const val TYPE_LOADING = 3
        const val TYPE_EMPTY = 4
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

        Glide.with(card.thumbnail)
            .load(feedImageUrl(video.thumbnailUrl))
            .override(480, 270)
            .dontAnimate()
            .placeholder(ColorDrawable(0xFF202024.toInt()))
            .error(ColorDrawable(0xFF2B1B45.toInt()))
            .centerCrop()
            .into(card.thumbnail)

        if (video.channelThumbnailUrl.isBlank()) {
            Glide.with(card.avatar).clear(card.avatar)
            card.avatar.setImageDrawable(ColorDrawable(0xFF4A2A79.toInt()))
        } else {
            Glide.with(card.avatar)
                .load(video.channelThumbnailUrl)
                .override(72, 72)
                .dontAnimate()
                .placeholder(ColorDrawable(0xFF303036.toInt()))
                .error(ColorDrawable(0xFF4A2A79.toInt()))
                .circleCrop()
                .into(card.avatar)
        }
    }

    fun recycle() {
        current = null
        Glide.with(card.thumbnail).clear(card.thumbnail)
        Glide.with(card.avatar).clear(card.avatar)
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

private class LoadingHolder(context: Context) : RecyclerView.ViewHolder(
    FrameLayout(context).apply {
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 96))
        addView(
            ProgressBar(context),
            FrameLayout.LayoutParams(dp(context, 38), dp(context, 38), Gravity.CENTER)
        )
    }
)

private class EmptyHolder(context: Context) : RecyclerView.ViewHolder(
    TextView(context).apply {
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 120))
        text = "No hay videos disponibles. Pulsa actualizar."
        gravity = Gravity.CENTER
        setTextColor(resolveColor(context, android.R.attr.textColorSecondary, Color.GRAY))
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
            text = "Shorts"
            setTextColor(resolveColor(context, android.R.attr.textColorPrimary, Color.WHITE))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 10))
        })

        addView(RecyclerView(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 270))
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
        Glide.with(card.image)
            .load(shortImageUrl(video.thumbnailUrl))
            .override(320, 568)
            .dontAnimate()
            .placeholder(ColorDrawable(0xFF202024.toInt()))
            .error(ColorDrawable(0xFF2B1B45.toInt()))
            .centerCrop()
            .into(card.image)
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
            setBackgroundColor(0xFF303036.toInt())
        }, LayoutParams(dp(context, 40), dp(context, 40)))

        info.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(titleView.apply {
                setTextColor(resolveColor(context, android.R.attr.textColorPrimary, Color.WHITE))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(channelView.apply {
                setTextColor(resolveColor(context, android.R.attr.textColorSecondary, Color.LTGRAY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(context, 4), 0, 0)
            })
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(context, 11)
        })

        info.addView(moreButton.apply {
            gravity = Gravity.CENTER
            setTextColor(resolveColor(context, android.R.attr.textColorPrimary, Color.WHITE))
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

    init {
        layoutParams = RecyclerView.LayoutParams(dp(context, 148), dp(context, 264)).apply {
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
        addView(title.apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(context, 9), dp(context, 8), dp(context, 9), dp(context, 9))
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
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

private fun feedImageUrl(url: String): String = url
    .replace("maxresdefault.jpg", "mqdefault.jpg")
    .replace("sddefault.jpg", "mqdefault.jpg")
    .replace("hqdefault.jpg", "mqdefault.jpg")

private fun shortImageUrl(url: String): String = url
    .replace("maxresdefault.jpg", "hqdefault.jpg")
    .replace("sddefault.jpg", "hqdefault.jpg")

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
    return value.take(10)
}

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()

private fun resolveColor(context: Context, attr: Int, fallback: Int): Int {
    val value = TypedValue()
    return if (context.theme.resolveAttribute(attr, value, true)) value.data else fallback
}
