package com.lagradost.cloudstream3.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.ReplacementSpan
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey

/**
 * Remembers, per provider, whether the last real playback attempt worked, and renders that as a
 * small badge next to the provider name in the search results.
 */
object ProviderHealth {
    private const val FOLDER = "provider_health"

    private val BG_OK = 0xFF12301F.toInt()
    private val FG_OK = 0xFF6EE79A.toInt()
    private val BG_FAILED = 0xFF3A2C0B.toInt()
    private val FG_FAILED = 0xFFFFC857.toInt()
    private val BG_UNTESTED = 0xFF2B2C30.toInt()
    private val FG_UNTESTED = 0xFFB4B9BD.toInt()

    /** [ok] is true if a video from the provider really started playing, false if the player failed. */
    data class Entry(val ok: Boolean, val atMs: Long)

    fun record(context: Context, apiName: String, ok: Boolean) {
        context.setKey(FOLDER, apiName, Entry(ok, System.currentTimeMillis()))
    }

    fun get(context: Context, apiName: String): Entry? {
        return context.getKey<Entry>(FOLDER, apiName)
    }

    /** Returns [name] followed by a rounded status badge, e.g. "Provider  ✓ Working · 2 hr. ago". */
    fun withBadge(context: Context, name: String): CharSequence {
        val entry = get(context, name)
        val (text, bg, fg) = when {
            entry == null -> Triple(
                context.getString(R.string.provider_status_untested), BG_UNTESTED, FG_UNTESTED
            )

            entry.ok -> Triple(
                context.getString(R.string.provider_status_working, timeAgo(context, entry.atMs)),
                BG_OK, FG_OK
            )

            else -> Triple(
                context.getString(R.string.provider_status_failed), BG_FAILED, FG_FAILED
            )
        }

        val builder = SpannableStringBuilder(name).append("  ")
        val start = builder.length
        builder.append(text)
        builder.setSpan(
            PillSpan(bg, fg, context.resources.displayMetrics.density),
            start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    private fun timeAgo(context: Context, atMs: Long): CharSequence {
        val now = System.currentTimeMillis()
        if (now - atMs < DateUtils.MINUTE_IN_MILLIS) {
            return context.getString(R.string.provider_status_just_now)
        }
        return DateUtils.getRelativeTimeSpanString(
            atMs, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
        )
    }

    /** Draws the text smaller than its line, on a rounded colored background. */
    private class PillSpan(
        private val bg: Int,
        private val fg: Int,
        density: Float
    ) : ReplacementSpan() {
        private val padH = 8f * density
        private val padV = 2f * density

        private fun pillPaint(paint: Paint) = Paint(paint).apply { textSize = paint.textSize * 0.7f }

        override fun getSize(
            paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?
        ): Int {
            return (pillPaint(paint).measureText(text, start, end) + padH * 2).toInt()
        }

        override fun draw(
            canvas: Canvas, text: CharSequence, start: Int, end: Int,
            x: Float, top: Int, y: Int, bottom: Int, paint: Paint
        ) {
            val p = pillPaint(paint)
            val width = p.measureText(text, start, end)
            val metrics = p.fontMetrics
            val rect = RectF(
                x, y + metrics.ascent - padV,
                x + width + padH * 2, y + metrics.descent + padV
            )
            p.color = bg
            canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, p)
            p.color = fg
            canvas.drawText(text, start, end, x + padH, y.toFloat(), p)
        }
    }
}
