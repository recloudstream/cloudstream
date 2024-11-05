package com.lagradost.cloudstream3.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import coil3.Image
import coil3.asImage
import coil3.request.ImageRequest

/// Type safe any image, because THIS IS NOT PYTHON
sealed class UiImage {
    data class Image(
        val url: String,
        val headers: Map<String, String>? = null
    ) : UiImage()

    data class Drawable(@DrawableRes val resId: Int) : UiImage()
    data class Bitmap(val bitmap: android.graphics.Bitmap) : UiImage()
}

fun getImageFromDrawable(context: Context, drawableRes: Int): Image? {
    return ContextCompat.getDrawable(context, drawableRes)?.asImage()
}

fun drawableToBitmap(drawable: Drawable): Bitmap? {
    return when (drawable) {
        is BitmapDrawable -> drawable.bitmap
        else -> {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }
}