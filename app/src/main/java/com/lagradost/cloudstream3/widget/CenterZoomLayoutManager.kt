package com.lagradost.cloudstream3.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.mvvm.logError
import kotlin.math.abs
import kotlin.math.min

class CenterZoomLayoutManager : LinearLayoutManager {
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(
        context, attrs, defStyleAttr, defStyleRes
    )

    constructor(context: Context?) : super(context)
    constructor(context: Context?, orientation: Int, reverseLayout: Boolean) : super(
        context,
        orientation,
        reverseLayout
    )

    private var itemListener: ((Int) -> Unit)? = null

    // to not spam updates
    private var lastViewIndex: Int? = null

    private val mShrinkAmount = 0.15f
    private val mShrinkDistance = 0.9f

    fun updateSize(forceUpdate: Boolean = false) {
        val midpoint = width / 2f
        val d0 = 0f
        val d1 = mShrinkDistance * midpoint
        val s0 = 1f
        val s1 = 1f - mShrinkAmount

        var largestTag: Int? = null
        var largestSize = 0f
        for (i in 0 until childCount) {
            getChildAt(i)?.let { child ->
                try {
                    val childMidpoint = (getDecoratedRight(child) + getDecoratedLeft(child)) / 2f
                    val d = min(d1, abs(midpoint - childMidpoint))
                    val scale = s0 + (s1 - s0) * (d - d0) / (d1 - d0)
                    child.scaleX = scale
                    child.scaleY = scale

                    if (scale > largestSize) {
                        (child.tag as Int?)?.let { tag ->
                            largestSize = scale
                            largestTag = tag
                        }
                    }
                } catch (e : Exception) {
                    logError(e)
                }
            }
        }

        largestTag?.let { tag ->
            if (lastViewIndex != tag || forceUpdate) {
                lastViewIndex = tag
                itemListener?.invoke(tag)
            }
        }
    }

    fun setOnSizeListener(listener: (Int) -> Unit) {
        lastViewIndex = null
        itemListener = listener
    }

    fun removeOnSizeListener() {
        itemListener = null
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        if(waitForSnap != null) {
            this.getChildAt(snapChild ?: 1)?.let { view ->
                LinearSnapHelper().calculateDistanceToFinalSnap(this,view)?.get(0)?.let { dx ->
                    waitForSnap?.invoke(dx)
                    waitForSnap = null
                }
            }
        }
        updateSize()
    }

    private var waitForSnap : ((Int) -> Unit)? = null
    private var snapChild : Int? = null

    fun snap(snap : Int? = null, callback : (Int) -> Unit) {
        waitForSnap = callback
        snapChild = snap
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        val orientation = orientation
        return if (orientation == HORIZONTAL) {
            val scrolled = super.scrollHorizontallyBy(dx, recycler, state)
            updateSize()
            scrolled
        } else {
            0
        }
    }
}