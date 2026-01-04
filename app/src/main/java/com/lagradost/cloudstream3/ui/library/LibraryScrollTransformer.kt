package com.lagradost.cloudstream3.ui.library

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.lagradost.cloudstream3.R
import kotlin.math.roundToInt

class LibraryScrollTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        val padding = (-position * page.width).roundToInt()
        page.findViewById<View>(R.id.page_recyclerview).setPadding(
            padding, 0,
            -padding, 0
        )
    }
}

