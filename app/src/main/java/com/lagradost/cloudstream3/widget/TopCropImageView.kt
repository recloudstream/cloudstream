package com.lagradost.cloudstream3.widget

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet

// taken from https://gist.github.com/arriolac/3843346
class TopCropImageView : androidx.appcompat.widget.AppCompatImageView {
    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        recomputeImgMatrix()
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        recomputeImgMatrix()
        return super.setFrame(l, t, r, b)
    }

    private fun init() {
        scaleType = ScaleType.MATRIX
    }

    private fun recomputeImgMatrix() {
        val drawable: Drawable = drawable ?: return
        val matrix: Matrix = imageMatrix
        val scale: Float
        val viewWidth: Int = width - paddingLeft - paddingRight
        val viewHeight: Int = height - paddingTop - paddingBottom
        val drawableWidth: Int = drawable.intrinsicWidth
        val drawableHeight: Int = drawable.intrinsicHeight
        scale = if (drawableWidth * viewHeight > drawableHeight * viewWidth) {
            viewHeight.toFloat() / drawableHeight.toFloat()
        } else {
            viewWidth.toFloat() / drawableWidth.toFloat()
        }
        matrix.setScale(scale, scale)
        imageMatrix = matrix
    }
}