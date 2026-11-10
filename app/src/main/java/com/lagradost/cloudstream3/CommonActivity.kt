package com.lagradost.cloudstream3

import android.app.Activity
import android.widget.Toast
import androidx.annotation.StringRes
import com.lagradost.cloudstream3.utils.UiText
import java.lang.ref.WeakReference

/** Host context used by extension dialogs; no player, account or cast lifecycle. */
object CommonActivity {
    private var currentActivity = WeakReference<Activity>(null)
    val activity: Activity? get() = currentActivity.get()
    fun setActivityInstance(activity: Activity?) { currentActivity = WeakReference(activity) }
    fun loadThemes(activity: Activity?) { activity?.setTheme(R.style.AppTheme) }
    fun showToast(@StringRes message: Int, duration: Int? = null) {
        showToast(activity, activity?.getString(message), duration)
    }
    fun showToast(message: String?, duration: Int? = null) { showToast(activity, message, duration) }
    fun showToast(message: UiText?, duration: Int? = null) {
        showToast(activity, message?.asStringNull(activity), duration)
    }
    fun showToast(activity: Activity?, message: UiText, duration: Int) {
        showToast(activity, message.asStringNull(activity), duration)
    }
    fun showToast(activity: Activity?, @StringRes message: Int, duration: Int? = null) {
        showToast(activity, activity?.getString(message), duration)
    }
    fun showToast(activity: Activity?, message: String?, duration: Int? = null) {
        if (activity == null || message == null) return
        activity.runOnUiThread { Toast.makeText(activity, message, duration ?: Toast.LENGTH_SHORT).show() }
    }
}
