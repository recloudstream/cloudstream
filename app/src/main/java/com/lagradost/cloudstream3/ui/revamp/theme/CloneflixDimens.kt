package com.lagradost.cloudstream3.ui.revamp.theme

import android.content.Context
import android.util.TypedValue

object CloneflixDimens {
    const val SPACING_XXS = 2
    const val SPACING_XS = 4
    const val SPACING_S = 8
    const val SPACING_M = 12
    const val SPACING_L = 16
    const val SPACING_XL = 20
    const val SPACING_2XL = 24
    const val SPACING_3XL = 32
    const val SPACING_4XL = 40
    const val SPACING_5XL = 48
    const val SPACING_6XL = 64
    const val SPACING_7XL = 80

    const val RADIUS_XS = 4f
    const val RADIUS_S = 8f
    const val RADIUS_M = 12f
    const val RADIUS_L = 16f
    const val RADIUS_XL = 24f
    const val RADIUS_FULL = 999f

    const val HEADER_ICON_SIZE = 80
    const val HEADER_ICON_SIZE_COMPACT = 56
    const val HEADER_ICON_STROKE_WIDTH = 1.4f

    const val BUTTON_HEIGHT_LARGE = 48
    const val BUTTON_HEIGHT_MEDIUM = 40
    const val BUTTON_HEIGHT_SMALL = 32
    const val INPUT_HEIGHT = 52

    const val ICON_XS = 14
    const val ICON_S = 16
    const val ICON_M = 24
    const val ICON_L = 32
    const val ICON_XL = 40

    const val BORDER_SUBTLE = 0.5f
    const val BORDER_DEFAULT = 1f
    const val BORDER_FOCUS = 2f

    fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    fun spToPx(context: Context, sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }
}
