package com.lagradost.cloudstream4.compose

import androidx.compose.foundation.gestures.BringIntoViewSpec

data class LeftAlignedFocusFollower(val paddingPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        return offset - paddingPx
    }
}

object NoFocusFollower : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        return 0.0f
    }
}