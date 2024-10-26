package com.lagradost.cloudstream3.utils

import androidx.annotation.DrawableRes

/// Type safe any image, because THIS IS NOT PYTHON
sealed class UiImage {
    data class Image(
        val url: String,
        val headers: Map<String, String>? = null
    ) : UiImage()

    data class Drawable(@DrawableRes val resId: Int) : UiImage()
    data class Bitmap(val bitmap: android.graphics.Bitmap) : UiImage()
}