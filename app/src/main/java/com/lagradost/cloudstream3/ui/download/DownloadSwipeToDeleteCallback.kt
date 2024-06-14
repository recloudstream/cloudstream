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

class DownloadSwipeToDeleteCallback(
    private val adapter: DownloadHeaderAdapter,
    private val context: Context
) : ItemTouchHelper.Callback() {

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
        val position = viewHolder.bindingAdapterPosition
        val item = adapter.cardList[position]
        return if (item.data.type.isEpisodeBased()) 0 else {
            makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
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
                    floatArrayOf(0f, 0f, 20f, 20f, 20f, 20f, 0f, 0f),
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

        if (dX <= -swipeDistance && !isCurrentlyActive) {
            swipeOpenItems.add(viewHolder.bindingAdapterPosition)
            setRecyclerViewTouchListener(recyclerView, swipeDistance)
        } else {
            swipeOpenItems.remove(viewHolder.bindingAdapterPosition)
            if (swipeOpenItems.isEmpty()) removeRecyclerViewTouchListener(recyclerView)
            super.onChildDraw(c, recyclerView, viewHolder, limitedDX, dY, actionState, isCurrentlyActive)
        }
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
                            handleDelete(pos)
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            } else false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun removeRecyclerViewTouchListener(
        recyclerView: RecyclerView
    ): Unit = recyclerView.setOnTouchListener(null)

    private fun handleDelete(position: Int) {
        val item = adapter.cardList[position]
        runOnMainThread {
            item.child?.let { clickEvent ->
                handleDownloadClick(
                    DownloadClickEvent(
                        DOWNLOAD_ACTION_DELETE_FILE,
                        clickEvent
                    )
                ) { adapter.notifyItemRemoved(position) }
            }
        }
    }
}