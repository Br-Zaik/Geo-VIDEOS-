package com.geovideos.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import com.geovideos.app.data.ChannelItem
import com.geovideos.app.data.VideoDetails
import com.geovideos.app.data.VideoItem
import java.time.Instant
import kotlin.math.max

internal enum class NativeVideoListMode { FULL, COMPACT }

private object NativePools {
    val video = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(NativeVideoAdapter.TYPE_FULL, 10)
        setMaxRecycledViews(NativeVideoAdapter.TYPE_COMPACT, 16)
    }
    val channel = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(NativeChannelAdapter.TYPE_CHANNEL, 16)
    }
    val player = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(NativePlayerAdapter.TYPE_RELATED, 14)
    }
}

@Composable
internal fun NativeVideoList(
    modifier: Modifier,
    videos: List<VideoItem>,
    loading: Boolean = false,
    loadingMore: Boolean = false,
    canLoadMore: Boolean = false,
    mode: NativeVideoListMode = NativeVideoListMode.COMPACT,
    emptyMessage: String = "No hay videos disponibles.",
    onLoadMore: (() -> Unit)? = null,
    onPlay: (VideoItem) -> Unit,
    onSave: (VideoItem) -> Unit
) {
    val adapter = remember { NativeVideoAdapter(mode, onPlay, onSave) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context).apply { initialPrefetchItemCount = 3 }
                this.adapter = adapter
                itemAnimator = null
                setHasFixedSize(false)
                setItemViewCacheSize(if (mode == NativeVideoListMode.COMPACT) 8 else 5)
                setRecycledViewPool(NativePools.video)
                overScrollMode = View.OVER_SCROLL_NEVER
                isNestedScrollingEnabled = true
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if (dy <= 0 || onLoadMore == null) return
                        val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                        val last = manager.findLastVisibleItemPosition()
                        val currentAdapter = recyclerView.adapter as? NativeVideoAdapter ?: return
                        if (currentAdapter.canLoadMore && !currentAdapter.loadingMore && last >= currentAdapter.itemCount - 4) {
                            currentAdapter.loadingMore = true
                            currentAdapter.onLoadMore?.invoke()
                        }
                    }
                })
            }
        },
        update = { recyclerView ->
            val current = recyclerView.adapter as NativeVideoAdapter
            current.onPlay = onPlay
            current.onSave = onSave
            current.onLoadMore = onLoadMore
            current.canLoadMore = canLoadMore
            current.loadingMore = loadingMore
            current.submitContent(videos, loading, loadingMore, emptyMessage)
        }
    )
}

@Composable
internal fun NativeChannelList(
    modifier: Modifier,
    channels: List<ChannelItem>,
    emptyMessage: String = "No hay canales disponibles.",
    onOpenChannel: (ChannelItem) -> Unit
) {
    val adapter = remember { NativeChannelAdapter(onOpenChannel) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
                itemAnimator = null
                setHasFixedSize(true)
                setItemViewCacheSize(8)
                setRecycledViewPool(NativePools.channel)
                overScrollMode = View.OVER_SCROLL_NEVER
            }
        },
        update = { recyclerView ->
            val current = recyclerView.adapter as NativeChannelAdapter
            current.onOpen = onOpenChannel
            current.submitContent(channels, emptyMessage)
        }
    )
}

internal data class PlayerHeaderData(
    val video: VideoItem,
    val details: VideoDetails?,
    val isLiked: Boolean,
    val isDisliked: Boolean,
    val isWatchLater: Boolean,
    val description: String,
    val channelAvatar: String,
    val publishedAt: String
)

@Composable
internal fun NativePlayerDetailsList(
    modifier: Modifier,
    header: PlayerHeaderData,
    related: List<VideoItem>,
    relatedLoading: Boolean,
    relatedLoadingMore: Boolean,
    relatedCanLoadMore: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onWatchLater: () -> Unit,
    onShare: () -> Unit,
    onOpenChannel: (ChannelItem) -> Unit,
    onPlayRelated: (VideoItem) -> Unit,
    onSaveRelated: (VideoItem) -> Unit,
    onLoadMore: () -> Unit
) {
    val adapter = remember {
        NativePlayerAdapter(
            onLike = onLike,
            onDislike = onDislike,
            onWatchLater = onWatchLater,
            onShare = onShare,
            onOpenChannel = onOpenChannel,
            onPlayRelated = onPlayRelated,
            onSaveRelated = onSaveRelated,
            onLoadMore = onLoadMore
        )
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context).apply { initialPrefetchItemCount = 3 }
                this.adapter = adapter
                itemAnimator = null
                setHasFixedSize(false)
                setItemViewCacheSize(7)
                setRecycledViewPool(NativePools.player)
                overScrollMode = View.OVER_SCROLL_NEVER
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if (dy <= 0) return
                        val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                        val current = recyclerView.adapter as? NativePlayerAdapter ?: return
                        if (current.canLoadMore && !current.loadingMore && manager.findLastVisibleItemPosition() >= current.itemCount - 4) {
                            current.loadingMore = true
                            current.onLoadMore()
                        }
                    }
                })
            }
        },
        update = { recyclerView ->
            val current = recyclerView.adapter as NativePlayerAdapter
            current.updateCallbacks(
                onLike, onDislike, onWatchLater, onShare, onOpenChannel,
                onPlayRelated, onSaveRelated, onLoadMore
            )
            current.canLoadMore = relatedCanLoadMore
            current.loadingMore = relatedLoadingMore
            current.submitContent(header, related.distinctBy { it.id }, relatedLoading, relatedLoadingMore, relatedCanLoadMore)
        }
    )
}

private sealed interface VideoRow {
    val id: Long
    data class Video(val item: VideoItem) : VideoRow { override val id = item.id.hashCode().toLong() }
    data object Loading : VideoRow { override val id = Long.MIN_VALUE + 101 }
    data class Empty(val message: String) : VideoRow { override val id = Long.MIN_VALUE + 102 }
}

private object VideoRowDiff : DiffUtil.ItemCallback<VideoRow>() {
    override fun areItemsTheSame(oldItem: VideoRow, newItem: VideoRow) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: VideoRow, newItem: VideoRow) = oldItem == newItem
}

private class NativeVideoAdapter(
    private val mode: NativeVideoListMode,
    var onPlay: (VideoItem) -> Unit,
    var onSave: (VideoItem) -> Unit
) : ListAdapter<VideoRow, RecyclerView.ViewHolder>(VideoRowDiff) {
    var onLoadMore: (() -> Unit)? = null
    var canLoadMore = false
    var loadingMore = false

    init { setHasStableIds(true) }

    fun submitContent(videos: List<VideoItem>, loading: Boolean, loadingMore: Boolean, emptyMessage: String) {
        this.loadingMore = loadingMore
        submitList(buildList {
            videos.distinctBy { it.id }.forEach { add(VideoRow.Video(it)) }
            when {
                loadingMore || (loading && videos.isEmpty()) -> add(VideoRow.Loading)
                videos.isEmpty() -> add(VideoRow.Empty(emptyMessage))
            }
        })
    }

    override fun getItemId(position: Int) = getItem(position).id
    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is VideoRow.Video -> if (mode == NativeVideoListMode.FULL) TYPE_FULL else TYPE_COMPACT
        VideoRow.Loading -> TYPE_LOADING
        is VideoRow.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        TYPE_FULL -> FullVideoHolder(parent.context)
        TYPE_COMPACT -> CompactVideoHolder(parent.context)
        TYPE_LOADING -> LoadingRowHolder(parent.context)
        else -> EmptyRowHolder(parent.context)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is VideoRow.Video -> when (holder) {
                is FullVideoHolder -> holder.bind(row.item, onPlay, onSave)
                is CompactVideoHolder -> holder.bind(row.item, onPlay, onSave)
            }
            is VideoRow.Empty -> (holder as EmptyRowHolder).bind(row.message)
            VideoRow.Loading -> Unit
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is FullVideoHolder -> holder.recycle()
            is CompactVideoHolder -> holder.recycle()
        }
        super.onViewRecycled(holder)
    }

    companion object {
        const val TYPE_FULL = 20
        const val TYPE_COMPACT = 21
        const val TYPE_LOADING = 22
        const val TYPE_EMPTY = 23
    }
}

private class FullVideoHolder(context: Context) : RecyclerView.ViewHolder(FullVideoCardView(context)) {
    private val card = itemView as FullVideoCardView

    fun bind(video: VideoItem, onPlay: (VideoItem) -> Unit, onSave: (VideoItem) -> Unit) {
        card.title.text = video.title.ifBlank { "Video" }
        card.channel.text = buildString {
            append(video.channelTitle.ifBlank { "Canal" })
            formatRelativeTime(video.publishedAt).takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        card.duration.text = durationText(video.durationMs)
        card.duration.visibility = if (video.durationMs > 0L && !video.isLive) View.VISIBLE else View.GONE
        card.live.visibility = if (video.isLive) View.VISIBLE else View.GONE
        card.setOnClickListener { onPlay(video) }
        card.more.setOnClickListener { onSave(video) }
        loadThumbnail(card.thumbnail, video.thumbnailUrl, 640, 360)
        loadAvatar(card.avatar, video.channelThumbnailUrl)
    }

    fun recycle() {
        card.title.text = ""
        card.channel.text = ""
        Glide.with(card.thumbnail).clear(card.thumbnail)
        Glide.with(card.avatar).clear(card.avatar)
        card.setOnClickListener(null)
        card.more.setOnClickListener(null)
    }
}

private class CompactVideoHolder(context: Context) : RecyclerView.ViewHolder(CompactVideoCardView(context)) {
    private val card = itemView as CompactVideoCardView

    fun bind(video: VideoItem, onPlay: (VideoItem) -> Unit, onSave: (VideoItem) -> Unit) {
        card.title.text = video.title.ifBlank { "Video" }
        card.channel.text = video.channelTitle.ifBlank { "Canal" }
        card.meta.text = listOfNotNull(
            formatRelativeTime(video.publishedAt).takeIf { it.isNotBlank() },
            if (video.resumePositionMs > 0L) "Continuar" else null
        ).joinToString(" · ")
        card.duration.text = durationText(video.durationMs)
        card.duration.visibility = if (video.durationMs > 0L && !video.isLive) View.VISIBLE else View.GONE
        card.setOnClickListener { onPlay(video) }
        card.more.setOnClickListener { onSave(video) }
        loadThumbnail(card.thumbnail, video.thumbnailUrl, 480, 270)
    }

    fun recycle() {
        card.title.text = ""
        card.channel.text = ""
        card.meta.text = ""
        Glide.with(card.thumbnail).clear(card.thumbnail)
        card.setOnClickListener(null)
        card.more.setOnClickListener(null)
    }
}

private class FullVideoCardView(context: Context) : LinearLayout(context) {
    val thumbnail = ImageView(context)
    val avatar = ImageView(context)
    val title = TextView(context)
    val channel = TextView(context)
    val more = TextView(context)
    val duration = TextView(context)
    val live = TextView(context)

    init {
        orientation = VERTICAL
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true

        val media = NativeSixteenNineFrame(context).apply {
            setBackgroundColor(Color.BLACK)
            thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
            addView(thumbnail, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            live.text = "EN VIVO"
            live.setTextColor(Color.WHITE)
            live.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            live.setTypeface(live.typeface, android.graphics.Typeface.BOLD)
            live.background = roundedDrawable(0xFFD32F2F.toInt(), 5f, context)
            live.setPadding(dp(context, 7), dp(context, 3), dp(context, 7), dp(context, 3))
            addView(live, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                setMargins(dp(context, 10), 0, 0, dp(context, 9))
            })
            duration.setTextColor(Color.WHITE)
            duration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            duration.background = roundedDrawable(0xCC000000.toInt(), 4f, context)
            duration.setPadding(dp(context, 6), dp(context, 2), dp(context, 6), dp(context, 2))
            addView(duration, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(context, 10), dp(context, 9))
            })
        }
        addView(media)

        val info = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(context, 14), dp(context, 10), dp(context, 8), dp(context, 12))
            setBackgroundColor(Color.BLACK)
        }
        avatar.scaleType = ImageView.ScaleType.CENTER_CROP
        info.addView(avatar, LayoutParams(dp(context, 40), dp(context, 40)))
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            title.setTextColor(Color.WHITE)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
            title.maxLines = 2
            title.ellipsize = TextUtils.TruncateAt.END
            channel.setTextColor(0xFFB6B3BE.toInt())
            channel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            channel.maxLines = 1
            channel.ellipsize = TextUtils.TruncateAt.END
            channel.setPadding(0, dp(context, 4), 0, 0)
            addView(title, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(channel, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        info.addView(texts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 11) })
        more.text = "⋮"
        more.gravity = Gravity.CENTER
        more.setTextColor(Color.WHITE)
        more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        info.addView(more, LayoutParams(dp(context, 44), dp(context, 44)))
        addView(info)
    }
}

private class CompactVideoCardView(context: Context) : LinearLayout(context) {
    val thumbnail = ImageView(context)
    val title = TextView(context)
    val channel = TextView(context)
    val meta = TextView(context)
    val more = TextView(context)
    val duration = TextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 108))
        setPadding(dp(context, 12), dp(context, 8), dp(context, 8), dp(context, 8))
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true

        val media = FrameLayout(context)
        thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
        media.addView(thumbnail, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        duration.setTextColor(Color.WHITE)
        duration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        duration.background = roundedDrawable(0xCC000000.toInt(), 4f, context)
        duration.setPadding(dp(context, 5), dp(context, 2), dp(context, 5), dp(context, 2))
        media.addView(duration, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(context, 5), dp(context, 5))
        })
        addView(media, LayoutParams(dp(context, 150), dp(context, 84)))

        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            title.setTextColor(Color.WHITE)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
            title.maxLines = 2
            title.ellipsize = TextUtils.TruncateAt.END
            channel.setTextColor(0xFFB6B3BE.toInt())
            channel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            channel.maxLines = 1
            channel.ellipsize = TextUtils.TruncateAt.END
            meta.setTextColor(0xFF8E8A97.toInt())
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            meta.maxLines = 1
            addView(title, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(channel, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 4) })
            addView(meta, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 2) })
        }
        addView(texts, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(context, 12) })
        more.text = "⋮"
        more.gravity = Gravity.CENTER
        more.setTextColor(Color.WHITE)
        more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        addView(more, LayoutParams(dp(context, 42), dp(context, 48)))
    }
}

private sealed interface ChannelRow {
    val id: Long
    data class Channel(val item: ChannelItem) : ChannelRow { override val id = item.id.hashCode().toLong() }
    data class Empty(val message: String) : ChannelRow { override val id = Long.MIN_VALUE + 201 }
}

private object ChannelRowDiff : DiffUtil.ItemCallback<ChannelRow>() {
    override fun areItemsTheSame(oldItem: ChannelRow, newItem: ChannelRow) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: ChannelRow, newItem: ChannelRow) = oldItem == newItem
}

private class NativeChannelAdapter(var onOpen: (ChannelItem) -> Unit) : ListAdapter<ChannelRow, RecyclerView.ViewHolder>(ChannelRowDiff) {
    init { setHasStableIds(true) }
    fun submitContent(channels: List<ChannelItem>, emptyMessage: String) = submitList(
        if (channels.isEmpty()) listOf(ChannelRow.Empty(emptyMessage)) else channels.map { ChannelRow.Channel(it) }
    )
    override fun getItemId(position: Int) = getItem(position).id
    override fun getItemViewType(position: Int) = if (getItem(position) is ChannelRow.Channel) TYPE_CHANNEL else TYPE_EMPTY
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_CHANNEL) ChannelHolder(parent.context) else EmptyRowHolder(parent.context)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ChannelRow.Channel -> (holder as ChannelHolder).bind(row.item, onOpen)
            is ChannelRow.Empty -> (holder as EmptyRowHolder).bind(row.message)
        }
    }
    companion object { const val TYPE_CHANNEL = 30; const val TYPE_EMPTY = 31 }
}

private class ChannelHolder(context: Context) : RecyclerView.ViewHolder(ChannelCardView(context)) {
    private val card = itemView as ChannelCardView
    fun bind(channel: ChannelItem, onOpen: (ChannelItem) -> Unit) {
        card.title.text = channel.title.ifBlank { "Canal" }
        card.description.text = channel.description
        card.description.visibility = if (channel.description.isBlank()) View.GONE else View.VISIBLE
        card.setOnClickListener { onOpen(channel) }
        loadAvatar(card.avatar, channel.thumbnailUrl, 120)
    }
}

private class ChannelCardView(context: Context) : LinearLayout(context) {
    val avatar = ImageView(context)
    val title = TextView(context)
    val description = TextView(context)
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 78))
        setPadding(dp(context, 14), dp(context, 9), dp(context, 14), dp(context, 9))
        setBackgroundColor(Color.BLACK)
        avatar.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(avatar, LayoutParams(dp(context, 56), dp(context, 56)))
        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            title.setTextColor(Color.WHITE)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
            title.maxLines = 1
            title.ellipsize = TextUtils.TruncateAt.END
            description.setTextColor(0xFFAAA7B2.toInt())
            description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            description.maxLines = 2
            description.ellipsize = TextUtils.TruncateAt.END
            addView(title)
            addView(description)
        }
        addView(texts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 14) })
        addView(TextView(context).apply {
            text = "›"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
        }, LayoutParams(dp(context, 36), dp(context, 56)))
    }
}

private sealed interface PlayerRow {
    val id: Long
    data class Header(val data: PlayerHeaderData) : PlayerRow { override val id = Long.MIN_VALUE + 301 }
    data class Related(val item: VideoItem) : PlayerRow { override val id = item.id.hashCode().toLong() }
    data object Loading : PlayerRow { override val id = Long.MIN_VALUE + 302 }
    data object More : PlayerRow { override val id = Long.MIN_VALUE + 303 }
}

private object PlayerRowDiff : DiffUtil.ItemCallback<PlayerRow>() {
    override fun areItemsTheSame(oldItem: PlayerRow, newItem: PlayerRow) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: PlayerRow, newItem: PlayerRow) = oldItem == newItem
}

private class NativePlayerAdapter(
    private var onLike: () -> Unit,
    private var onDislike: () -> Unit,
    private var onWatchLater: () -> Unit,
    private var onShare: () -> Unit,
    private var onOpenChannel: (ChannelItem) -> Unit,
    private var onPlayRelated: (VideoItem) -> Unit,
    private var onSaveRelated: (VideoItem) -> Unit,
    var onLoadMore: () -> Unit
) : ListAdapter<PlayerRow, RecyclerView.ViewHolder>(PlayerRowDiff) {
    var canLoadMore = false
    var loadingMore = false
    init { setHasStableIds(true) }

    fun updateCallbacks(
        like: () -> Unit, dislike: () -> Unit, later: () -> Unit, share: () -> Unit,
        channel: (ChannelItem) -> Unit, play: (VideoItem) -> Unit,
        save: (VideoItem) -> Unit, load: () -> Unit
    ) {
        onLike = like; onDislike = dislike; onWatchLater = later; onShare = share
        onOpenChannel = channel; onPlayRelated = play; onSaveRelated = save; onLoadMore = load
    }

    fun submitContent(header: PlayerHeaderData, related: List<VideoItem>, loading: Boolean, loadingMore: Boolean, canLoadMore: Boolean) {
        this.loadingMore = loadingMore
        this.canLoadMore = canLoadMore
        submitList(buildList {
            add(PlayerRow.Header(header))
            related.forEach { add(PlayerRow.Related(it)) }
            if (loading || loadingMore) add(PlayerRow.Loading)
            else if (canLoadMore) add(PlayerRow.More)
        })
    }

    override fun getItemId(position: Int) = getItem(position).id
    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is PlayerRow.Header -> TYPE_HEADER
        is PlayerRow.Related -> TYPE_RELATED
        PlayerRow.Loading -> TYPE_LOADING
        PlayerRow.More -> TYPE_MORE
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        TYPE_HEADER -> PlayerHeaderHolder(parent.context)
        TYPE_RELATED -> CompactVideoHolder(parent.context)
        TYPE_LOADING -> LoadingRowHolder(parent.context)
        else -> MoreRowHolder(parent.context)
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is PlayerRow.Header -> (holder as PlayerHeaderHolder).bind(
                row.data, onLike, onDislike, onWatchLater, onShare, onOpenChannel
            )
            is PlayerRow.Related -> (holder as CompactVideoHolder).bind(row.item, onPlayRelated, onSaveRelated)
            PlayerRow.More -> (holder as MoreRowHolder).bind(onLoadMore)
            PlayerRow.Loading -> Unit
        }
    }
    companion object {
        const val TYPE_HEADER = 40
        const val TYPE_RELATED = 41
        const val TYPE_LOADING = 42
        const val TYPE_MORE = 43
    }
}

private class PlayerHeaderHolder(context: Context) : RecyclerView.ViewHolder(PlayerHeaderView(context)) {
    private val view = itemView as PlayerHeaderView
    fun bind(
        data: PlayerHeaderData,
        onLike: () -> Unit,
        onDislike: () -> Unit,
        onWatchLater: () -> Unit,
        onShare: () -> Unit,
        onOpenChannel: (ChannelItem) -> Unit
    ) {
        val video = data.video
        view.title.text = video.title
        view.meta.text = buildList {
            data.details?.viewCount?.takeIf { it > 0 }?.let { add("${compactNumber(it)} visualizaciones") }
            formatRelativeTime(data.publishedAt).takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString(" · ")
        view.channel.text = video.channelTitle.ifBlank { "Canal" }
        view.subscribers.text = data.details?.subscriberCount?.takeIf { it > 0 }?.let { "${compactNumber(it)} suscriptores" }.orEmpty()
        view.like.text = data.details?.likeCount?.takeIf { it > 0 }?.let(::compactNumber) ?: "Me gusta"
        view.like.isSelected = data.isLiked
        view.dislike.isSelected = data.isDisliked
        view.watchLater.text = if (data.isWatchLater) "Guardado" else "Ver después"
        view.description.text = data.description
        view.descriptionBox.visibility = if (data.description.isBlank()) View.GONE else View.VISIBLE
        view.like.setOnClickListener { onLike() }
        view.dislike.setOnClickListener { onDislike() }
        view.watchLater.setOnClickListener { onWatchLater() }
        view.share.setOnClickListener { onShare() }
        view.channelButton.visibility = if (video.channelId.isBlank()) View.GONE else View.VISIBLE
        view.channelButton.setOnClickListener {
            onOpenChannel(ChannelItem(video.channelId, video.channelTitle, data.channelAvatar))
        }
        loadAvatar(view.avatar, data.channelAvatar, 112)
    }
}

private class PlayerHeaderView(context: Context) : LinearLayout(context) {
    val title = TextView(context)
    val meta = TextView(context)
    val avatar = ImageView(context)
    val channel = TextView(context)
    val subscribers = TextView(context)
    val channelButton = TextView(context)
    val like = TextView(context)
    val dislike = TextView(context)
    val watchLater = TextView(context)
    val share = TextView(context)
    val descriptionBox = LinearLayout(context)
    val description = TextView(context)

    init {
        orientation = VERTICAL
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
        setBackgroundColor(Color.BLACK)

        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.maxLines = 3
        title.ellipsize = TextUtils.TruncateAt.END
        addView(title)

        meta.setTextColor(0xFFAAA7B2.toInt())
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        addView(meta, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 6) })

        val channelRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        avatar.scaleType = ImageView.ScaleType.CENTER_CROP
        channelRow.addView(avatar, LayoutParams(dp(context, 46), dp(context, 46)))
        val channelTexts = LinearLayout(context).apply {
            orientation = VERTICAL
            channel.setTextColor(Color.WHITE)
            channel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            channel.setTypeface(channel.typeface, android.graphics.Typeface.BOLD)
            channel.maxLines = 1
            subscribers.setTextColor(0xFFAAA7B2.toInt())
            subscribers.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            addView(channel)
            addView(subscribers)
        }
        channelRow.addView(channelTexts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 12) })
        styleAction(context, channelButton, "Canal")
        channelRow.addView(channelButton)
        addView(channelRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 14) })

        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        styleAction(context, like, "Me gusta")
        styleAction(context, dislike, "No me gusta")
        styleAction(context, watchLater, "Ver después")
        styleAction(context, share, "Compartir")
        listOf(like, dislike, watchLater, share).forEachIndexed { index, item ->
            actions.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 42)).apply {
                if (index > 0) marginStart = dp(context, 8)
            })
        }
        scroll.addView(actions)
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 52)).apply { topMargin = dp(context, 12) })

        descriptionBox.orientation = VERTICAL
        descriptionBox.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
        descriptionBox.background = roundedDrawable(0xFF1D1B22.toInt(), 12f, context)
        descriptionBox.addView(TextView(context).apply {
            text = "Descripción"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        description.setTextColor(0xFFC8C5CE.toInt())
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        description.maxLines = 5
        description.ellipsize = TextUtils.TruncateAt.END
        descriptionBox.addView(description, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 6) })
        addView(descriptionBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, 10) })

        addView(TextView(context).apply {
            text = "Videos similares"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(context, 20), 0, dp(context, 8))
        })
    }
}

private class LoadingRowHolder(context: Context) : RecyclerView.ViewHolder(FrameLayout(context).apply {
    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 88))
    setBackgroundColor(Color.BLACK)
    addView(ProgressBar(context), FrameLayout.LayoutParams(dp(context, 34), dp(context, 34), Gravity.CENTER))
})

private class EmptyRowHolder(context: Context) : RecyclerView.ViewHolder(TextView(context).apply {
    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 120))
    gravity = Gravity.CENTER
    setTextColor(0xFFB6B3BE.toInt())
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    setBackgroundColor(Color.BLACK)
    setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18))
}) {
    private val text = itemView as TextView
    fun bind(message: String) { text.text = message }
}

private class MoreRowHolder(context: Context) : RecyclerView.ViewHolder(TextView(context).apply {
    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 64))
    gravity = Gravity.CENTER
    text = "Cargar más"
    setTextColor(0xFF9D6CFF.toInt())
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    setTypeface(typeface, android.graphics.Typeface.BOLD)
    setBackgroundColor(Color.BLACK)
}) {
    private val button = itemView as TextView
    fun bind(onLoadMore: () -> Unit) { button.setOnClickListener { onLoadMore() } }
}

private class NativeSixteenNineFrame(context: Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((width * 9f / 16f).toInt(), MeasureSpec.EXACTLY)
        )
    }
}

private fun loadThumbnail(view: ImageView, url: String, width: Int, height: Int) {
    val manager = Glide.with(view)
    val placeholder = ColorDrawable(0xFF202024.toInt())
    if (!isYoutubeThumbnailList(url)) {
        manager.load(url)
            .override(width, height)
            .dontAnimate()
            .placeholder(placeholder)
            .error(ColorDrawable(0xFF2B1B45.toInt()))
            .centerCrop()
            .into(view)
        return
    }

    val low = manager.load(youtubeThumbnailList(url, "mqdefault.jpg"))
        .override(320, 180)
        .dontAnimate()
        .centerCrop()
    val highFallback = manager.load(youtubeThumbnailList(url, "hqdefault.jpg"))
        .override(width, height)
        .dontAnimate()
        .centerCrop()
        .error(low)
    manager.load(youtubeThumbnailList(url, "sddefault.jpg"))
        .override(width, height)
        .dontAnimate()
        .placeholder(placeholder)
        .thumbnail(low)
        .error(highFallback)
        .centerCrop()
        .into(view)
}

private fun loadAvatar(view: ImageView, url: String, size: Int = 96) {
    if (url.isBlank()) {
        Glide.with(view).clear(view)
        view.setImageDrawable(ColorDrawable(0xFF4A2A79.toInt()))
        return
    }
    Glide.with(view)
        .load(url)
        .override(size, size)
        .dontAnimate()
        .placeholder(ColorDrawable(0xFF303036.toInt()))
        .error(ColorDrawable(0xFF4A2A79.toInt()))
        .circleCrop()
        .into(view)
}

private fun isYoutubeThumbnailList(url: String): Boolean =
    url.contains("ytimg.com", ignoreCase = true) ||
        url.contains("youtube.com", ignoreCase = true)

private fun youtubeThumbnailList(url: String, fileName: String): String =
    url.replace(Regex("(maxresdefault|sddefault|hqdefault|mqdefault|default)\\.jpg"), fileName)

private fun durationText(ms: Long): String {
    if (ms <= 0L) return ""
    val total = ms / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatRelativeTime(value: String): String {
    if (value.isBlank()) return ""
    if (value.contains("hace", true)) return value
    return runCatching {
        val instant = Instant.parse(value)
        val elapsed = max(0L, Instant.now().epochSecond - instant.epochSecond)
        when {
            elapsed < 3_600 -> "hace ${max(1L, elapsed / 60)} min"
            elapsed < 86_400 -> "hace ${elapsed / 3_600} h"
            elapsed < 604_800 -> "hace ${elapsed / 86_400} días"
            elapsed < 2_592_000 -> "hace ${elapsed / 604_800} semanas"
            elapsed < 31_536_000 -> "hace ${elapsed / 2_592_000} meses"
            else -> "hace ${elapsed / 31_536_000} años"
        }
    }.getOrElse { value.take(10) }
}

private fun compactNumber(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1f mil M".format(value / 1_000_000_000.0).replace(".0", "")
    value >= 1_000_000 -> "%.1f M".format(value / 1_000_000.0).replace(".0", "")
    value >= 1_000 -> "%.1f K".format(value / 1_000.0).replace(".0", "")
    else -> value.toString()
}

private fun styleAction(context: Context, view: TextView, text: String) {
    view.text = text
    view.gravity = Gravity.CENTER
    view.setTextColor(Color.WHITE)
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    view.setPadding(dp(context, 14), 0, dp(context, 14), 0)
    view.background = roundedDrawable(0xFF242128.toInt(), 18f, context, 0xFF5B5663.toInt())
    view.isClickable = true
    view.isFocusable = true
}

private fun roundedDrawable(color: Int, radiusDp: Float, context: Context, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * context.resources.displayMetrics.density
        stroke?.let { setStroke(dp(context, 1), it) }
    }

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
