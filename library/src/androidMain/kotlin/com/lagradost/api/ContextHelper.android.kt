package com.lagradost.api

import android.content.Context
import java.lang.ref.WeakReference

var ctx: WeakReference<Context>? = null

/**
 * Helper function for Android specific context. Not usable in JVM.
 * Do not use this unless absolutely necessary.
 */
actual fun getContext(): Any? {
    return ctx?.get()
}

actual fun setContext(context: WeakReference<Any>) {
    val actualContext = context.get() as? Context
    if (actualContext != null) {
        ctx = WeakReference(actualContext)
    }
}