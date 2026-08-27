package com.lagradost.cloudstream3.ui.home

import android.view.View
import androidx.viewpager2.widget.ViewPager2

class HomeScrollTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        //page.translationX = -position * page.width / 2.0f

        //val params = RecyclerView.LayoutParams(
        //    RecyclerView.LayoutParams.MATCH_PARENT,
        //    0
        //)
        //page.layoutParams = params
        //progressBar?.layoutParams = params

        val padding = (-position * page.width / 2).toInt()
        page.setPadding(
            padding, 0,
            -padding, 0
        )
    }
}