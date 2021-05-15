package com.lagradost.cloudstream3

import android.app.Activity
import android.content.res.Resources
import android.view.View
import androidx.preference.PreferenceManager

object UIHelper {
    val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
    val Float.toPx: Float get() = (this * Resources.getSystem().displayMetrics.density)
    val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()
    val Float.toDp: Float get() = (this / Resources.getSystem().displayMetrics.density)

    fun Activity.loadResult(url: String, apiName: String) {
        /*this.runOnUiThread {
            this.supportFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.enter_anim, R.anim.exit_anim, R.anim.pop_enter, R.anim.pop_exit)
                .add(R.id.homeRoot, ResultFragment().newInstance(url, apiName))
                .commit()
        }*/
    }

    private fun Activity.getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    fun Activity.fixPaddingStatusbar(v: View) {
        v.setPadding(v.paddingLeft, v.paddingTop + getStatusBarHeight(), v.paddingRight, v.paddingBottom)
    }

    private fun Activity.getGridFormat(): String {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return settingsManager.getString(getString(R.string.grid_format_key), "grid")!!
    }

    fun Activity.getGridFormatId(): Int {
        return when (getGridFormat()) {
            "list" -> R.layout.search_result_compact
            "compact_list" -> R.layout.search_result_super_compact
            else -> R.layout.search_result_grid
        }
    }

    fun Activity.getGridIsCompact(): Boolean {
        return getGridFormat() != "grid"
    }
}