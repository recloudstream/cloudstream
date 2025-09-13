package com.lagradost.cloudstream3.utils

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlin.math.max

class ZoomablePlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    private val minScale = 1f
    private val maxScale = 4f

    private var scale = 1f
    private var translateX = 0f
    private var translateY = 0f

    private var focusX = 0f
    private var focusY = 0f

    private var isScaling = false
    private var isPanning = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scale = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
                focusX = detector.focusX
                focusY = detector.focusY

                if (scale <= 1f + 1e-4) {
                    resetTransforms()
                } else {
                    clampTranslations()
                    applyTransform()
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                parent?.requestDisallowInterceptTouchEvent(scale > 1f || isPanning)
            }
        })

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scale > 1f) {
                    isPanning = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    translateX -= distanceX
                    translateY -= distanceY
                    clampTranslations()
                    applyTransform()
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                performClick()
                return true
            }
        }
    )

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1 || isScaling || (scale > 1f && isPanning)) {
            parent?.requestDisallowInterceptTouchEvent(true)
            return onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!isScaling) {
            gestureDetector.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (event.pointerCount == 1) isPanning = false
            MotionEvent.ACTION_POINTER_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isScaling && !isPanning) performClick()
                isPanning = false
                parent?.requestDisallowInterceptTouchEvent(scale > 1f)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    @OptIn(UnstableApi::class)
    private fun applyTransform() {
        val tv = videoSurfaceView as? TextureView ?: return
        val matrix = Matrix()
        matrix.postScale(scale, scale, focusX, focusY)
        matrix.postTranslate(translateX, translateY)
        tv.setTransform(matrix)
        tv.invalidate()
    }

    @OptIn(UnstableApi::class)
    private fun resetTransforms() {
        scale = 1f
        translateX = 0f
        translateY = 0f
        focusX = width / 2f
        focusY = height / 2f
        val tv = videoSurfaceView as? TextureView ?: return
        tv.setTransform(Matrix())
        tv.invalidate()
    }

    @OptIn(UnstableApi::class)
    private fun clampTranslations() {
        val tv = videoSurfaceView as? TextureView ?: return
        val scaledW = tv.width * scale
        val scaledH = tv.height * scale
        val maxTx = max(0f, (scaledW - tv.width) / 2f)
        val maxTy = max(0f, (scaledH - tv.height) / 2f)
        translateX = translateX.coerceIn(-maxTx, maxTx)
        translateY = translateY.coerceIn(-maxTy, maxTy)
    }
}
