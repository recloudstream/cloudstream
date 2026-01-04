package com.lagradost.cloudstream3.widget

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager

class LinearRecycleViewLayoutManager(
    val context: Context,
    val nextFocusUp: Int,
    val nextFocusDown: Int
) : LinearLayoutManager(context) {
    override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
        return try {
            val position = getPosition(focused)
            val count = itemCount
            //println("onInterceptFocusSearch position=$position count=$count focused=$focused direction=$direction")

            (if (position == count - 1 && direction == View.FOCUS_DOWN) {
                focused.rootView.findViewById(nextFocusDown)
            } else if (position == 0 && direction == View.FOCUS_UP) {
                focused.rootView.findViewById(nextFocusUp)
            } else {
                super.onInterceptFocusSearch(focused, direction)
            }) ?: super.onInterceptFocusSearch(focused, direction)
        } catch (t : Throwable)  {
            super.onInterceptFocusSearch(focused, direction)
        }
    }
}

