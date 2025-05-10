package com.lagradost.cloudstream3.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

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

    override fun onScaleEnd(detector: ScaleGestureDetector) { /* no-op */ }
}
