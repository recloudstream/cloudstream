package com.lagradost.cloudstream3.ui.result

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.UIHelper.setImage

sealed class UiText {
    data class DynamicString(val value: String) : UiText()
    class StringResource(
        @StringRes val resId: Int,
        vararg val args: Any
    ) : UiText()

    fun asStringNull(context: Context?): String? {
        try {
            return asString(context ?: return null)
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args)
        }
    }
}

sealed class UiImage {
    data class Image(
        val url: String,
        val headers: Map<String, String>? = null,
        @DrawableRes val errorDrawable: Int? = null
    ) : UiImage()

    data class Drawable(@DrawableRes val resId: Int) : UiImage()
}

fun ImageView?.setImage(value: UiImage?) {
    when (value) {
        is UiImage.Image -> setImageImage(value)
        is UiImage.Drawable -> setImageDrawable(value)
        null -> {
            this?.isVisible = false
        }
    }
}

fun ImageView?.setImageImage(value: UiImage.Image) {
    if (this == null) return
    this.isVisible = setImage(value.url, value.headers, value.errorDrawable)
}

fun ImageView?.setImageDrawable(value: UiImage.Drawable) {
    if (this == null) return
    this.isVisible = true
    setImageResource(value.resId)
}

@JvmName("imgNull")
fun img(
    url: String?,
    headers: Map<String, String>? = null,
    @DrawableRes errorDrawable: Int? = null
): UiImage? {
    if (url.isNullOrBlank()) return null
    return UiImage.Image(url, headers, errorDrawable)
}

fun img(
    url: String,
    headers: Map<String, String>? = null,
    @DrawableRes errorDrawable: Int? = null
): UiImage {
    return UiImage.Image(url, headers, errorDrawable)
}

fun img(@DrawableRes drawable: Int): UiImage {
    return UiImage.Drawable(drawable)
}

fun txt(value: String): UiText {
    return UiText.DynamicString(value)
}

@JvmName("txtNull")
fun txt(value: String?): UiText? {
    return UiText.DynamicString(value ?: return null)
}

fun txt(@StringRes resId: Int, vararg args: Any): UiText {
    return UiText.StringResource(resId, args)
}

@JvmName("txtNull")
fun txt(@StringRes resId: Int?, vararg args: Any?): UiText? {
    if (resId == null || args.any { it == null }) {
        return null
    }
    return UiText.StringResource(resId, args)
}

fun TextView?.setText(text: UiText?) {
    if (this == null) return
    if (text == null) {
        this.isVisible = false
    } else {
        val str = text.asStringNull(context)
        this.isGone = str.isNullOrBlank()
        this.text = str
    }
}

fun TextView?.setTextHtml(text: UiText?) {
    if (this == null) return
    if (text == null) {
        this.isVisible = false
    } else {
        val str = text.asStringNull(context)
        this.isGone = str.isNullOrBlank()
        this.text = str.html()
    }
}
