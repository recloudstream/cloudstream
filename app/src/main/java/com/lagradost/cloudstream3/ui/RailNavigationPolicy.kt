package com.lagradost.cloudstream3.ui

internal object RailNavigationPolicy {
    fun isLeadingItem(position: Int, horizontal: Boolean, spanCount: Int): Boolean =
        position >= 0 && if (horizontal) position == 0 else position % spanCount.coerceAtLeast(1) == 0
}
