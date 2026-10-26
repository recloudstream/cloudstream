@file:Suppress("unused")

package com.lagradost.cloudstream3

import android.app.Activity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Desktop copy of CommonActivity, toasts from extensions are shown as snackbars */
object CommonActivity {
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    /** There is no Android activity on desktop */
    val activity: Activity? get() = null

    fun showToast(message: String?, duration: Int? = null) {
        if (!message.isNullOrBlank()) _toasts.tryEmit(message)
    }

    fun showToast(act: Activity?, message: String?, duration: Int? = null) = showToast(message, duration)
}
