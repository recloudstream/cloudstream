package com.lagradost.cloudstream3.ui.player

import android.text.TextPaint
import android.text.style.CharacterStyle
import androidx.annotation.Px

// source: https://github.com/androidx/media/pull/1840
class OutlineSpan(@Px val outlineWidth : Float) : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint?) { tp?.strokeWidth = outlineWidth }
}