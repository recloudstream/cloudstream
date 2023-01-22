package com.lagradost.cloudstream3.ui

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class HeaderViewDecoration(private val customView: View) : RecyclerView.ItemDecoration() {
    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)
        customView.layout(parent.left, 0, parent.right, customView.measuredHeight)
        for (i in 0 until parent.childCount) {
            val view = parent.getChildAt(i)
            if (parent.getChildAdapterPosition(view) == 0) {
                c.save()
                val height = customView.measuredHeight
                val top = view.top - height
                c.translate(0f, top.toFloat())
                customView.draw(c)
                c.restore()
                break
            }
        }
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        if (parent.getChildAdapterPosition(view) == 0) {
            customView.measure(
                View.MeasureSpec.makeMeasureSpec(parent.measuredWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(parent.measuredHeight, View.MeasureSpec.AT_MOST)
            )
            outRect.set(0, customView.measuredHeight, 0, 0)
        } else {
            outRect.setEmpty()
        }
    }
}