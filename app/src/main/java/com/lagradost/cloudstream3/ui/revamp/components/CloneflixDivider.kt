package com.lagradost.cloudstream3.ui.revamp.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixDimens

class CloneflixDivider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.cloneflix_border_subtle))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = resources.getDimensionPixelSize(R.dimen.cloneflix_divider_height)
        val finalHeightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, finalHeightMeasureSpec)
    }
}
