package com.lagradost.cloudstream3.ui.account

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class AccountSelectLinearItemDecoration(private val size: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val layoutParams = view.layoutParams as RecyclerView.LayoutParams
        layoutParams.width = size
        layoutParams.height = size
        view.layoutParams = layoutParams
    }
}