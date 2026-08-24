package com.lagradost.cloudstream3.ui.revamp.components

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.revamp.theme.CloneflixTypography

class CloneflixTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CloneflixTextView)
            val styleEnum = typedArray.getInt(R.styleable.CloneflixTextView_cloneflixTextStyle, -1)
            typedArray.recycle()

            if (styleEnum >= 0) {
                applyStyleEnum(styleEnum)
            }
        }
    }

    fun setCloneflixStyle(level: CloneflixTypography.StyleLevel) {
        TextViewCompat.setTextAppearance(this, level.styleResId)
        typeface = CloneflixTypography.getFontTypeface(context, level.weight)
    }

    private fun applyStyleEnum(index: Int) {
        val level = when (index) {
            0 -> CloneflixTypography.StyleLevel.REGULAR_CAPTION2
            1 -> CloneflixTypography.StyleLevel.REGULAR_CAPTION1
            2 -> CloneflixTypography.StyleLevel.REGULAR_SMALL_BODY
            3 -> CloneflixTypography.StyleLevel.REGULAR_BODY
            4 -> CloneflixTypography.StyleLevel.REGULAR_HEADLINE2
            5 -> CloneflixTypography.StyleLevel.REGULAR_HEADLINE1
            6 -> CloneflixTypography.StyleLevel.REGULAR_TITLE4
            7 -> CloneflixTypography.StyleLevel.REGULAR_TITLE3
            8 -> CloneflixTypography.StyleLevel.REGULAR_TITLE2
            9 -> CloneflixTypography.StyleLevel.REGULAR_TITLE1
            10 -> CloneflixTypography.StyleLevel.REGULAR_LARGE_TITLE

            11 -> CloneflixTypography.StyleLevel.MEDIUM_CAPTION2
            12 -> CloneflixTypography.StyleLevel.MEDIUM_CAPTION1
            13 -> CloneflixTypography.StyleLevel.MEDIUM_SMALL_BODY
            14 -> CloneflixTypography.StyleLevel.MEDIUM_BODY
            15 -> CloneflixTypography.StyleLevel.MEDIUM_HEADLINE2
            16 -> CloneflixTypography.StyleLevel.MEDIUM_HEADLINE1
            17 -> CloneflixTypography.StyleLevel.MEDIUM_TITLE4
            18 -> CloneflixTypography.StyleLevel.MEDIUM_TITLE3
            19 -> CloneflixTypography.StyleLevel.MEDIUM_TITLE2
            20 -> CloneflixTypography.StyleLevel.MEDIUM_TITLE1
            21 -> CloneflixTypography.StyleLevel.MEDIUM_LARGE_TITLE

            22 -> CloneflixTypography.StyleLevel.BOLD_TITLE2
            23 -> CloneflixTypography.StyleLevel.BOLD_TITLE1
            24 -> CloneflixTypography.StyleLevel.BOLD_LARGE_TITLE

            25 -> CloneflixTypography.StyleLevel.LOGO_BEBAS
            26 -> CloneflixTypography.StyleLevel.HEADER_DISPLAY
            else -> null
        }
        level?.let { setCloneflixStyle(it) }
    }
}
