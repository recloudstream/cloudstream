package com.lagradost.cloudstream3.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.animation.ValueAnimator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 *This is a container that lets the user pinch to zoom its entire contents
 */
class ZoomLayout @JvmOverloads constructor( //Why not
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), ScaleGestureDetector.OnScaleGestureListener {

    private var scaleFactor = 1f //current scale factor

    //pivot points
    private var focusX = 0f
    private var focusY = 0f

    //the gesture detector
    private val detector = ScaleGestureDetector(context, this)

    //animator that resets scaleFactor/focus back to defaults
    private val resetAnimator = ValueAnimator.ofFloat(0f,1f).apply {
        interpolator = FastOutSlowInInterpolator()
        duration = 300L
        addUpdateListener { anim ->
            // anim.animatedValue will go from 1f → 0f
            val fraction = anim.animatedFraction
            // lerp scaleFactor from current → 1f
            scaleFactor = lerp(scaleFactorStart, 1f, fraction)
            // lerp focus back to center
            focusX = lerp(focusXStart, width / 2f, fraction)
            focusY = lerp(focusYStart, height / 2f, fraction)
            invalidate()
        }
    }


    private var scaleFactorStart = 1f
    private var focusXStart = 0f
    private var focusYStart = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        //lets the detector see all touches
        detector.onTouchEvent(ev)
        //if we're in a scaling gesture, intercept so children don't steal it
        return detector.isInProgress || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        //consume all touch events to keep the zooming based on the current view
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        //before drawing children, scale the canvas around the pivot point
        canvas.save()
        canvas.translate(focusX, focusY)
        canvas.scale(scaleFactor, scaleFactor)
        canvas.translate(-focusX, -focusY)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    //scaleGestureDetector callbacks
    override fun onScaleBegin(detector: ScaleGestureDetector) = true

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        // Update scale and pivot
        scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.5f, 3.0f)
        focusX = detector.focusX
        focusY = detector.focusY
        // Tell Android we need to redraw
        invalidate()
        return true
    }


    //we now use it for a cleaner approach since it already works
    override fun onScaleEnd(detector: ScaleGestureDetector) {
        scaleFactorStart = scaleFactor
        focusXStart = focusX
        focusYStart = focusY
        resetAnimator.start()
    }

    //simple helper
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
