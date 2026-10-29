package com.lagradost.cloudstream3.ui

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigationrail.NavigationRailView
import com.lagradost.cloudstream3.R
import java.lang.ref.WeakReference

internal class AutoHideNavigationRail(private val root: View, private val rail: NavigationRailView, private val content: View) {
    private var available = rail.visibility == View.VISIBLE
    private var hidden = false
    private var lastContent = WeakReference<View>(null)
    private val enabled get() = PreferenceManager.getDefaultSharedPreferences(root.context).getBoolean("auto_hide_navigation_rail", true)
    init {
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, focus ->
            if (focus != null && !inside(focus, rail) && inside(focus, content)) {
                lastContent = WeakReference(focus)
                if (available && enabled && posterRow(focus) != null) show(false)
            }
        }
    }
    fun destinationChanged(visible: Boolean) { available = visible; if (!enabled) hidden = false; applyVisibility() }
    fun dispatch(event: KeyEvent, focus: View?): Boolean {
        if (!available || !enabled || event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (hidden || !rail.hasFocus()) reveal() else restoreContent()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK && rail.hasFocus() && lastContent.get()?.isAttachedToWindow == true) {
            restoreContent(); return true
        }
        val leadingKey = if (root.layoutDirection == View.LAYOUT_DIRECTION_RTL) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        if (hidden && event.keyCode == leadingKey && focus != null) {
            val row = posterRow(focus) ?: return false
            val item = row.findContainingItemView(focus) ?: return false
            val manager = row.layoutManager as? LinearLayoutManager ?: return false
            val horizontal = manager.orientation == RecyclerView.HORIZONTAL
            val span = (manager as? GridLayoutManager)?.spanCount ?: 1
            if (RailNavigationPolicy.isLeadingItem(row.getChildAdapterPosition(item), horizontal, span)) { reveal(); return true }
        }
        return false
    }
    private fun reveal() { show(true); (rail.findViewById<View>(rail.selectedItemId) ?: rail).requestFocus() }
    private fun restoreContent() { show(false); lastContent.get()?.takeIf { it.isAttachedToWindow }?.requestFocus() ?: content.requestFocus() }
    private fun show(visible: Boolean) { hidden = !visible; applyVisibility() }
    private fun applyVisibility() {
        rail.visibility = if (!available || hidden) View.INVISIBLE else View.VISIBLE
        val margin = if (available && !hidden) root.resources.getDimensionPixelSize(R.dimen.nav_rail_view_width) else 0
        (content.layoutParams as? ViewGroup.MarginLayoutParams)?.let { if (it.marginStart != margin) { it.marginStart = margin; content.layoutParams = it } }
    }
    private fun posterRow(focus: View): RecyclerView? {
        var node: View? = focus
        var poster = false
        while (node != null && node !== content) {
            if (node is RecyclerView) return if (poster) node else null
            poster = poster || ((node === focus || node.isFocusable || node.isClickable) && node.findViewById<View?>(R.id.imageView) != null)
            node = node.parent as? View
        }
        return null
    }
    private fun inside(child: View, parent: View): Boolean {
        var node: View? = child
        while (node != null) { if (node === parent) return true; node = node.parent as? View }
        return false
    }
}
