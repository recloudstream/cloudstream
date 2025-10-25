package com.lagradost.cloudstream3.utils
//Reference: https://stackoverflow.com/a/29055283
import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import com.lagradost.cloudstream3.R

/**
 * A custom [AppCompatImageView] that allows precise control over the visible crop area
 * of an image by adjusting its horizontal and vertical center offset percentages.
 *
 * ### Key Features:
 * - Allows **manual vertical or horizontal cropping** via percentage offsets.
 * - Works seamlessly with Coil, Glide, or any image loading library.
 *
 * ### Usage (XML):
 * You can set the crop offset directly in XML using custom attributes:
 * ```xml
 * <com.lagradost.cloudstream3.utils.PercentageCropImageView
 *     android:id="@+id/home_scroll_preview"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     android:scaleType="matrix"
 *     app:cropYCenterOffsetPct="0.2"
 *     app:cropXCenterOffsetPct="0.5"
 *     tools:src="@drawable/example_poster" />
 * ```
 * - `app:cropYCenterOffsetPct` → controls how far vertically the image shifts
 *   `0.0` = top-aligned, `0.5` = centered, `1.0` = bottom-aligned.
 * - `app:cropXCenterOffsetPct` → controls how far horizontally the image shifts
 *   `0.0` = left, `0.5` = center, `1.0` = right.
 *
 * ### Programmatic Example:
 * ```kotlin
 * imageView.cropYCenterOffsetPct = 0.15f   // Show slightly more (15%) of the top area
 * imageView.cropXCenterOffsetPct = 0.5f    // Keep image centered horizontally
 * imageView.redraw()    //Only needed if you changed cropYCenterOffsetPct/cropXCenterOffsetPct at runtime
 * ```
 *
 * ### Notes:
 * - Must use `android:scaleType="matrix"` to enable manual matrix transformations.
 * - Reference: https://stackoverflow.com/a/29055283
 *
 * @property cropYCenterOffsetPct the vertical crop percentage (0.0–1.0)
 * @property cropXCenterOffsetPct the horizontal crop percentage (0.0–1.0)
 *
 * @see ImageView.ScaleType.MATRIX
 */
class PercentageCropImageView : androidx.appcompat.widget.AppCompatImageView {
    private var mCropYCenterOffsetPct: Float? = null
    private var mCropXCenterOffsetPct: Float? = null

    constructor(context: Context?) : super(context!!)

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        initAttrs(context, attrs)
    }

    constructor(
        context: Context?, attrs: AttributeSet?,
        defStyle: Int
    ) : super(context!!, attrs, defStyle) {
        initAttrs(context, attrs)
    }

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

    private fun initAttrs(context: Context, attrs: AttributeSet?) {
        attrs ?: return
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.PercentageCropImageView)
        try {
            if (typedArray.hasValue(R.styleable.PercentageCropImageView_cropYCenterOffsetPct)) {
                mCropYCenterOffsetPct = typedArray.getFloat(
                    R.styleable.PercentageCropImageView_cropYCenterOffsetPct,
                    0.5f
                )
            }
            if (typedArray.hasValue(R.styleable.PercentageCropImageView_cropXCenterOffsetPct)) {
                mCropXCenterOffsetPct = typedArray.getFloat(
                    R.styleable.PercentageCropImageView_cropXCenterOffsetPct,
                    0.5f
                )
            }
        } finally {
            typedArray.recycle()
        }
    }
}