package com.lagradost.cloudstream3.ui.revamp.components

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.lagradost.cloudstream3.R

class CloneflixCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class ElevationLevel {
        FLAT,
        SURFACE,
        ELEVATED
    }

    init {
        var elevationLevel = ElevationLevel.SURFACE
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CloneflixCardView)
            val levelIndex = typedArray.getInt(R.styleable.CloneflixCardView_cardElevationLevel, 1)
            typedArray.recycle()

            elevationLevel = when (levelIndex) {
                0 -> ElevationLevel.FLAT
                2 -> ElevationLevel.ELEVATED
                else -> ElevationLevel.SURFACE
            }
        }

        applyElevationLevel(elevationLevel)
        clipToOutline = true
    }

    fun setElevationLevel(level: ElevationLevel) {
        applyElevationLevel(level)
    }

    private fun applyElevationLevel(level: ElevationLevel) {
        when (level) {
            ElevationLevel.FLAT -> {
                setBackgroundColor(ContextCompat.getColor(context, R.color.cloneflix_canvas_background))
            }
            ElevationLevel.SURFACE -> {
                setBackgroundResource(R.drawable.cloneflix_bg_card)
            }
            ElevationLevel.ELEVATED -> {
                setBackgroundResource(R.drawable.cloneflix_bg_card_elevated)
            }
        }
    }
}
