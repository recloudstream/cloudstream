package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.view.View
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import com.lagradost.api.Log
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute

object SnackbarHelper {

    private const val TAG = "COMPACT"
    private var currentSnackbar: Snackbar? = null

    @MainThread
    fun showSnackbar(
        act: Activity?,
        message: UiText,
        duration: Int = Snackbar.LENGTH_SHORT,
        actionText: UiText? = null,
        actionCallback: (() -> Unit)? = null
    ) {
        if (act == null) return
        showSnackbar(act, message.asString(act), duration,
            actionText?.asString(act), actionCallback)
    }

    @MainThread
    fun showSnackbar(
        act: Activity?,
        @StringRes message: Int,
        duration: Int = Snackbar.LENGTH_SHORT,
        @StringRes actionText: Int? = null,
        actionCallback: (() -> Unit)? = null
    ) {
        if (act == null) return
        showSnackbar(act, act.getString(message), duration,
            actionText?.let { act.getString(it) }, actionCallback)
    }

    @MainThread
    fun showSnackbar(
        act: Activity?,
        message: String?,
        duration: Int = Snackbar.LENGTH_SHORT,
        actionText: String? = null,
        actionCallback: (() -> Unit)? = null
    ) {
        if (act == null || message == null) {
            Log.w(TAG, "Invalid showSnackbar: act = $act, message = $message")
            return
        }
        Log.i(TAG, "showSnackbar: $message")

        try {
            currentSnackbar?.dismiss()
        } catch (e: Exception) {
            logError(e)
        }

        try {
            val parentView = act.findViewById<View>(android.R.id.content)
            val snackbar = Snackbar.make(parentView, message, duration)

            actionCallback?.let {
                snackbar.setAction(actionText) { actionCallback.invoke() }
            }

            snackbar.show()
            currentSnackbar = snackbar

            snackbar.setBackgroundTint(act.colorFromAttribute(R.attr.primaryBlackBackground))
            snackbar.setTextColor(act.colorFromAttribute(R.attr.textColor))
            snackbar.setActionTextColor(act.colorFromAttribute(R.attr.colorPrimary))

        } catch (e: Exception) {
            logError(e)
        }
    }
}