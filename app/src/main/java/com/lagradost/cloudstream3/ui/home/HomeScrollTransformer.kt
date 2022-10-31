package com.lagradost.cloudstream3.ui.home

import android.view.View
import androidx.viewpager.widget.ViewPager

class HomeScrollTransformer : ViewPager.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.setPadding(
            maxOf(0, (-position * page.width / 2).toInt()), 0,
            maxOf(0, (position * page.width / 2).toInt()), 0
        )
    }
}