package com.lagradost.cloudstream3.ui.download

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.ui.download.DownloadButtonSetup.handleDownloadClick
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.VideoDownloadHelper
import com.lagradost.cloudstream3.utils.VideoDownloadManager.downloadDeleteEvent

class DownloadSwipeDeleteCallback(
    private val adapter: DownloadAdapter,
    private val context: Context
) : ItemTouchHelper.Callback() {

    private var downloadDeleteEventListener: ((Int) -> Unit)? = null

    private val swipeOpenItems: MutableSet<Int> = mutableSetOf()
    private val deleteIcon: Drawable? by lazy {
        ContextCompat.getDrawable(context, R.drawable.ic_baseline_delete_outline_24)
    }
    private val background: ColorDrawable by lazy {
        ColorDrawable(Color.RED).apply { alpha = 160 }
    }
    private val scaleFactor = 1.25f
    private val maxSwipeDistance = 230f

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val swipeFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT

        val position = viewHolder.bindingAdapterPosition
        val item = adapter.cardList[position]
        if (item !is VisualDownloadHeaderCached) return makeMovementFlags(0, swipeFlags)
        return if (item.data.type.isEpisodeBased()) 0 else {
            makeMovementFlags(0, swipeFlags)
        }
    }

    override fun isLongPressDragEnabled(): Boolean = false

    override fun isItemViewSwipeEnabled(): Boolean = true

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(
        viewHolder: RecyclerView.ViewHolder,
        direction: Int
    ) {}

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (dX == 0f) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        val itemView = viewHolder.itemView

        val minSwipeDistance = itemView.width / 4.5f
        val swipeDistance = minOf(minSwipeDistance, maxSwipeDistance)
        val limitedDX = if (dX < -swipeDistance) -swipeDistance else if (dX >= 0) 0f else dX

        val position = viewHolder.bindingAdapterPosition

        if (swipeOpenItems.contains(position)) {
            // If the item is already swiped we need to restore that
            // state so that you can delete items without the state
            // resetting, making it easier to quickly delete multiple items.
            super.onChildDraw(c, recyclerView, viewHolder, limitedDX, dY, actionState, isCurrentlyActive)
        }

        if (limitedDX < 0) { // Swiping to the left
            val icon = deleteIcon ?: return

            val backgroundLeft = itemView.right + limitedDX.toInt()

            val iconWidth = (icon.intrinsicWidth * scaleFactor).toInt()
            val iconHeight = (icon.intrinsicHeight * scaleFactor).toInt()

            val iconTop = itemView.top + (itemView.height - iconHeight) / 2
            val iconBottom = iconTop + iconHeight

            val iconLeft = backgroundLeft + (itemView.right - backgroundLeft - iconWidth) / 2
            val iconRight = iconLeft + iconWidth

            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

            val path = Path().apply {
                addRoundRect(
                    RectF(
                        backgroundLeft.toFloat(),
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat()
                    ),
                    floatArrayOf(
                        0f, 0f,    // Top-left corner
                        20f, 20f,  // Top-right corner
                        20f, 20f,  // Bottom-right corner
                        0f, 0f     // Bottom-left corner
                    ),
                    Path.Direction.CW
                )
            }
            c.clipPath(path)
            background.setBounds(
                backgroundLeft,
                itemView.top,
                itemView.right,
                itemView.bottom
            )
            background.draw(c)
            icon.draw(c)
        } else background.setBounds(0, 0, 0, 0)

        if (dX <= -swipeDistance && !isCurrentlyActive && adapter.cardList.getOrNull(position) != null) {
            swipeOpenItems.add(position)
            setRecyclerViewTouchListener(recyclerView, swipeDistance)
        } else {
            swipeOpenItems.remove(position)
            if (swipeOpenItems.isEmpty()) removeRecyclerViewTouchListener(recyclerView)
            super.onChildDraw(c, recyclerView, viewHolder, limitedDX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ) {
        clearDownloadDeleteEvent()
        super.clearView(recyclerView, viewHolder)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setRecyclerViewTouchListener(
        recyclerView: RecyclerView,
        swipeDistance: Float
    ) {
        recyclerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.x.toInt()
                val y = event.y.toInt()
                swipeOpenItems.forEach { pos ->
                    val vh = recyclerView.findViewHolderForAdapterPosition(pos)
                    vh?.itemView?.let { swipeItemView ->
                        val backgroundLeft: Int = swipeItemView.right - swipeDistance.toInt()
                        val backgroundXRange: IntRange = backgroundLeft..swipeItemView.right
                        val backgroundYRange: IntRange = swipeItemView.top..swipeItemView.bottom
                        if (x in backgroundXRange && y in backgroundYRange) {
                            handleDeleteAction(pos)
                            addDownloadDeleteEvent(pos)
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            } else false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun removeRecyclerViewTouchListener(recyclerView: RecyclerView) {
        // We don't want to unnecessarily listen to unused touch events
        recyclerView.setOnTouchListener(null)

        /**
         * If we are not listening to touch events, then
         * we should clear the delete event as it will
         * not be used at the moment.
         */
        clearDownloadDeleteEvent()
    }

    private fun handleDeleteAction(position: Int) {
        val item = adapter.cardList[position]
        runOnMainThread {
            val data: VideoDownloadHelper.DownloadEpisodeCached? = if (item is VisualDownloadHeaderCached) item.child else {
                item.data as VideoDownloadHelper.DownloadEpisodeCached?
            }
            data?.let { clickEvent ->
                handleDownloadClick(
                    DownloadClickEvent(
                        DOWNLOAD_ACTION_DELETE_FILE,
                        clickEvent
                    )
                )
            }
        }
    }

    private fun addDownloadDeleteEvent(position: Int) {
        // Clear any old events as we don't want to get
        // concurrent modification errors
        clearDownloadDeleteEvent()
        downloadDeleteEventListener = { id: Int ->
            val list = adapter.cardList
            if (list.any { it.data.id == id }) {
                /**
                 * Seamlessly remove now-deleted item from adapter.
                 * We don't need to reload from the viewModel,
                 * that just causes more unnecessary actions and
                 * unreliable data to be returned oftentimes,
                 * as it would cause it to reload the entire
                 * view model (which is all items) we only want
                 * to target one item and this provides a more seamless
                 * and performant solution to it since we do have access to
                 * the position we need to target here.
                 */
                if (list.getOrNull(position) != null) {
                    adapter.cardList.removeAt(position)
                }
                adapter.notifyItemRemoved(position)
            }
        }

        // We use synchronized to ensure we are thread-safe and
        // to avoid potential race conditions that may cause
        // concurrent modification errors
        synchronized(this) {
            downloadDeleteEventListener?.let { downloadDeleteEvent += it }
        }
    }

    private fun clearDownloadDeleteEvent() {
        // We use synchronized to ensure we are thread-safe and
        // to avoid potential race conditions that may cause
        // concurrent modification errors
        synchronized(this) {
            if (downloadDeleteEventListener != null) {
                downloadDeleteEvent -= downloadDeleteEventListener!!
                downloadDeleteEventListener = null
            }
        }
    }
}