package com.lagradost.cloudstream3.utils
//Reference: https://stackoverflow.com/a/29055283
import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet

class PercentageCropImageView : androidx.appcompat.widget.AppCompatImageView {
    private var mCropYCenterOffsetPct: Float? = null
    private var mCropXCenterOffsetPct: Float? = null
    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs)
    constructor(
        context: Context?, attrs: AttributeSet?,
        defStyle: Int
    ) : super(context!!, attrs, defStyle)

    var cropYCenterOffsetPct: Float
        get() = mCropYCenterOffsetPct!!
        set(cropYCenterOffsetPct) {
            require(cropYCenterOffsetPct <= 1.0) { "Value too large: Must be <= 1.0" }
            mCropYCenterOffsetPct = cropYCenterOffsetPct
        }
    var cropXCenterOffsetPct: Float
        get() = mCropXCenterOffsetPct!!
        set(cropXCenterOffsetPct) {
            require(cropXCenterOffsetPct <= 1.0) { "Value too large: Must be <= 1.0" }
            mCropXCenterOffsetPct = cropXCenterOffsetPct
        }

    private fun myConfigureBounds() {
        if (this.scaleType == ScaleType.MATRIX) {

            val d = this.drawable
            if (d != null) {
                val dWidth = d.intrinsicWidth
                val dHeight = d.intrinsicHeight
                val m = Matrix()
                val vWidth = width - this.paddingLeft - this.paddingRight
                val vHeight = height - this.paddingTop - this.paddingBottom
                val scale: Float
                var dx = 0f
                var dy = 0f
                if (dWidth * vHeight > vWidth * dHeight) {
                    val cropXCenterOffsetPct =
                        if (mCropXCenterOffsetPct != null) mCropXCenterOffsetPct!!.toFloat() else 0.5f
                    scale = vHeight.toFloat() / dHeight.toFloat()
                    dx = (vWidth - dWidth * scale) * cropXCenterOffsetPct
                } else {
                    val cropYCenterOffsetPct =
                        if (mCropYCenterOffsetPct != null) mCropYCenterOffsetPct!!.toFloat() else 0f
                    scale = vWidth.toFloat() / dWidth.toFloat()
                    dy = (vHeight - dHeight * scale) * cropYCenterOffsetPct
                }
                m.setScale(scale, scale)
                m.postTranslate((dx + 0.5f).toInt().toFloat(), (dy + 0.5f).toInt().toFloat())
                this.imageMatrix = m
            }
        }
    }

    // These 3 methods call configureBounds in ImageView.java class, which
    // adjusts the matrix in a call to center_crop (android's built-in
    // scaling and centering crop method). We also want to trigger
    // in the same place, but using our own matrix, which is then set
    // directly at line 588 of ImageView.java and then copied over
    // as the draw matrix at line 942 of ImageView.java
    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val changed = super.setFrame(l, t, r, b)
        myConfigureBounds()
        return changed
    }

    override fun setImageDrawable(d: Drawable?) {
        super.setImageDrawable(d)
        myConfigureBounds()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        myConfigureBounds()
    }
    // In case you can change the ScaleType in code you have to call redraw()
    //fullsizeImageView.setScaleType(ScaleType.FIT_CENTER);
    //fullsizeImageView.redraw();
    fun redraw() {
        val d = this.drawable
        if (d != null) {
            // Force toggle to recalculate our bounds
            setImageDrawable(null)
            setImageDrawable(d)
        }
    }
}