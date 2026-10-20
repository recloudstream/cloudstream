package com.lagradost.cloudstream3.ui

import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import java.lang.ref.WeakReference

/**
 * tvOS-like focus: the focused view is scaled up with a shadow instead of being outlined.
 * Large views would overflow the screen, so they keep their focus drawable.
 * The lifted view must fit in the padding of its parent (tv_lift_room), as unclipping
 * the parents doubles the GPU time of every frame.
 */
object TvLiftFocus {
    private const val CARD_SCALE = 1.10f
    private const val BUTTON_SCALE = 1.06f
    private const val DURATION = 180L

    /** Max size of a liftable view, relative to the screen */
    private const val MAX_WIDTH_FRACTION = 0.4f
    private const val MAX_HEIGHT_FRACTION = 0.6f

    private val interpolator = DecelerateInterpolator(1.5f)
    private var lifted: WeakReference<View> = WeakReference(null)
    private var liftedForeground: Drawable? = null

    /** Hides the title below [card] until it is focused, like the posters on tvOS */
    fun hideTitleUntilFocused(card: View) {
        val title = card.findViewById<View>(R.id.imageText) ?: return
        card.setTag(R.id.tv_hide_title, true)
        title.animate().cancel()
        title.alpha = if (card.hasFocus()) 1.0f else 0.0f
    }

    fun onFocusChanged(newFocus: View?) {
        lifted.get()?.let { old -> if (old != newFocus) lower(old) }
        lifted = WeakReference(null)
        if (newFocus == null || !canLift(newFocus)) return

        // RecyclerView items are cards (posters, episodes), everything else is a button
        animate(newFocus, if (newFocus.parent is RecyclerView) CARD_SCALE else BUTTON_SCALE, 12.toPx)
        lifted = WeakReference(newFocus)
        liftedForeground = newFocus.foreground
        newFocus.foreground = null
    }

    private fun lower(view: View) {
        if (view.foreground == null) view.foreground = liftedForeground
        animate(view, 1.0f, 0)
    }

    private fun animate(view: View, scale: Float, elevation: Int) {
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .translationZ(elevation.toFloat())
            .setDuration(DURATION)
            .setInterpolator(interpolator)
            .start()
        if (view.getTag(R.id.tv_hide_title) == true) {
            view.findViewById<View>(R.id.imageText)?.animate()
                ?.alpha(if (scale > 1.0f) 1.0f else 0.0f)
                ?.setDuration(DURATION)
                ?.start()
        }
    }

    private fun canLift(view: View): Boolean {
        if (view.tag == view.context.getString(R.string.tv_no_focus_tag)) return false
        val root = view.rootView ?: return false
        return view.width > 0 && view.height > 0 &&
                view.width <= root.width * MAX_WIDTH_FRACTION &&
                view.height <= root.height * MAX_HEIGHT_FRACTION
    }
}
