package com.lagradost.cloudstream3.ui.result

import android.content.Context
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.lagradost.cloudstream3.mvvm.Some
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.utils.UIHelper.setImage

sealed class UiText {
    companion object {
        const val TAG = "UiText"
    }

    data class DynamicString(val value: String) : UiText() {
        override fun toString(): String = value
    }

    class StringResource(
        @StringRes val resId: Int,
        val args: List<Any>
    ) : UiText() {
        override fun toString(): String =
            "resId = $resId\nargs = ${args.toList().map { "(${it::class} = $it)" }}"
    }

    fun asStringNull(context: Context?): String? {
        try {
            return asString(context ?: return null)
        } catch (e: Exception) {
            Log.e(TAG, "Got invalid data from $this")
            logError(e)
            return null
        }
    }

    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> {
                val str = context.getString(resId)
                if (args.isEmpty()) {
                    str
                } else {
                    str.format(*args.map {
                        when (it) {
                            is UiText -> it.asString(context)
                            else -> it
                        }
                    }.toTypedArray())
                }
            }
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

fun ImageView?.setImage(value: UiImage?, fadeIn: Boolean = true) {
    when (value) {
        is UiImage.Image -> setImageImage(value,fadeIn)
        is UiImage.Drawable -> setImageDrawable(value)
        null -> {
            this?.isVisible = false
        }
    }
}

fun ImageView?.setImageImage(value: UiImage.Image, fadeIn: Boolean = true) {
    if (this == null) return
    this.isVisible = setImage(value.url, value.headers, value.errorDrawable, fadeIn)
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
    return UiText.StringResource(resId, args.toList())
}

@JvmName("txtNull")
fun txt(@StringRes resId: Int?, vararg args: Any?): UiText? {
    if (resId == null || args.any { it == null }) {
        return null
    }
    return UiText.StringResource(resId, args.filterNotNull().toList())
}

fun TextView?.setText(text: UiText?) {
    if (this == null) return
    if (text == null) {
        this.isVisible = false
    } else {
        val str = text.asStringNull(context)?.let {
            if (this.maxLines == 1) {
                it.replace("\n", " ")
            } else {
                it
            }
        }

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

fun TextView?.setTextHtml(text: Some<UiText>?) {
    setTextHtml(if (text is Some.Success) text.value else null)
}

fun TextView?.setText(text: Some<UiText>?) {
    setText(if (text is Some.Success) text.value else null)
}