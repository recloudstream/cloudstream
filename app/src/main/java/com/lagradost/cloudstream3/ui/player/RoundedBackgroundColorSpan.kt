package com.lagradost.cloudstream3.ui.player

/**
 * Inspired by https://medium.com/@Semper_Viventem/simple-implementation-of-rounded-background-for-text-in-android-60a7706c0419
 * however the connecting triangles cant be rendered on a transparent bg, also does not support alignment.
 *
 * This current implementation may be expanded to only draw the drawRoundRect with rounded corners iff
 * it is on an edge for a nice look:
 *
 * /----------\
 * |  large   |
 * \----------/
 *  |       |  <- this instead of / and \
 *  | small |
 *  \-------/
 *
 *  Also note that the background may be drawn wildly different from where exoplayer places it
 *  because exoplayer has their own custom drawing. This is only an attempt to correlate it.
 *
 *  Additionally, not tested on RTL
*/

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout.Alignment
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LineBackgroundSpan

class RoundedBackgroundColorSpan(
    private val backgroundColor: Int,
    private val alignment: Alignment,
    private val padding: Float,
    private val radius: Float
) : LineBackgroundSpan {
    private val paint = Paint().apply {
        color = backgroundColor
        isAntiAlias = true
    }

    override fun drawBackground(
        c: Canvas,
        p: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int
    ) {

        // https://github.com/androidx/media/blob/main/libraries/ui/src/main/java/androidx/media3/ui/SubtitlePainter.java
        if (Color.alpha(backgroundColor) <= 0) {
            return
        }

        // we cant use StaticLayout.Builder() due to API
        val width = p.measureText(text, start, end)
        val textLayout =
            StaticLayout(
                text,
                TextPaint(p),
                width.toInt(),
                alignment,
                1.0f,
                0.0f,
                true
            )

        val center = (left + right).toFloat() * 0.5f

        // I know this is not how you actually do it, but fuck it.
        // You have to override the subtitle painter to get all the correct value
        val textLeft = when (alignment) {
            Alignment.ALIGN_NORMAL -> {
                0.0f
            }

            Alignment.ALIGN_OPPOSITE -> {
                right - width
            }

            Alignment.ALIGN_CENTER -> {
                center - width * 0.5f
            }
        }

        val textTop = textLayout.getLineTop(lineNumber).toFloat()
        val textBottom = textLayout.getLineBottom(lineNumber).toFloat()

        c.drawRoundRect(
            textLeft - padding,
            textTop,
            textLeft + width + padding,
            textBottom,
            radius,
            radius,
            paint
        )
    }
}
