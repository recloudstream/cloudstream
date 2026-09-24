package com.lagradost.cloudstream3.ui.player

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlin.math.roundToInt

/** A focusable 16:9 channel-card list shared by touch devices and TV remotes. */
class ZappingChannelAdapter(
    private val onChannelClick: (Int, View) -> Unit,
) : RecyclerView.Adapter<ZappingChannelAdapter.ChannelViewHolder>() {
    private var channels: List<ZappingChannel> = emptyList()
    private var selectedIndex: Int = RecyclerView.NO_POSITION

    fun submit(channels: List<ZappingChannel>, selectedIndex: Int) {
        this.channels = channels
        this.selectedIndex = selectedIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val context = parent.context
        val item = CarouselItemContainer(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 1.toPx
                bottomMargin = 1.toPx
            }
            clipChildren = false
            clipToPadding = false
        }
        val card = AspectRatioCardView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END,
            )
            radius = 12.toPx.toFloat()
            cardElevation = 2.toPx.toFloat()
            useCompatPadding = true
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            contentDescription = context.getString(R.string.player_channel_list)
        }
        val content = FrameLayout(context)
        val poster = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(context.colorFromAttribute(R.attr.boxItemBackground))
        }
        val scrim = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                72.toPx,
                Gravity.BOTTOM,
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, 0xE6000000.toInt()),
            )
        }
        val title = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                marginStart = 8.toPx
                marginEnd = 8.toPx
                bottomMargin = 6.toPx
            }
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(context.colorFromAttribute(R.attr.textColor))
        }
        content.addView(poster)
        content.addView(scrim)
        content.addView(title)
        card.addView(content)
        item.addView(card)
        return ChannelViewHolder(item, card, poster, title)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        val isActive = position == selectedIndex
        holder.poster.loadImage(channel.posterUrl)
        holder.title.text = channel.name
        holder.title.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        holder.title.setTextColor(holder.title.context.colorFromAttribute(R.attr.textColor))
        holder.card.setCardBackgroundColor(
            holder.card.context.colorFromAttribute(R.attr.primaryBlackBackground)
        )
        holder.card.animate().cancel()
        applyVisualState(holder, isActive, holder.card.hasFocus(), animate = false)
        holder.card.contentDescription = channel.name
        holder.card.setOnClickListener { onChannelClick(position, holder.card) }
        holder.card.setOnFocusChangeListener { view, hasFocus ->
            applyVisualState(holder, position == selectedIndex, hasFocus, animate = true)
            if (hasFocus) {
                centerFocusedItem(holder.itemView)
            }
        }
    }

    private fun applyVisualState(
        holder: ChannelViewHolder,
        isActive: Boolean,
        isFocused: Boolean,
        animate: Boolean,
    ) {
        val card = holder.card
        val highlighted = isActive || isFocused
        val targetScale = if (highlighted) 1.15f else 1f
        card.foreground = null
        card.cardElevation = 2.toPx.toFloat()
        if (animate) {
            card.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(150L)
                .start()
        } else {
            card.scaleX = targetScale
            card.scaleY = targetScale
        }
    }

    private fun centerFocusedItem(view: View) {
        val recycler = view.parent?.parent as? RecyclerView ?: return
        recycler.post {
            val itemCenter = view.top + view.height / 2
            val viewportCenter = recycler.height / 2
            recycler.smoothScrollBy(0, itemCenter - viewportCenter)
        }
    }

    override fun getItemCount(): Int = channels.size

    class ChannelViewHolder(
        val itemViewContainer: CarouselItemContainer,
        val card: AspectRatioCardView,
        val poster: ImageView,
        val title: TextView,
    ) : RecyclerView.ViewHolder(itemViewContainer)

    class CarouselItemContainer(context: android.content.Context) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
            val cardWidth = (availableWidth * 0.70f).roundToInt()
            val cardHeight = (cardWidth * 9f / 16f).roundToInt()
            val card = getChildAt(0)
            card?.measure(
                MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY),
            )
            setMeasuredDimension(availableWidth, cardHeight)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val card = getChildAt(0) ?: return
            val cardLeft = width - card.measuredWidth
            card.layout(cardLeft, 0, width, card.measuredHeight)
        }
    }

    class AspectRatioCardView(context: android.content.Context) : CardView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = (width * 9f / 16f).roundToInt()
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
        }
    }
}
