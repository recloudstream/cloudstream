package com.lagradost.api

import java.lang.ref.WeakReference

/**
 * Set context for android specific code such as webview.
 * Does nothing on JVM.
 */
expect fun setContext(context: WeakReference<Any>)
/**
 * Helper function for Android specific context.
 * Do not use this unless absolutely necessary.
 * setContext() must be called before this is called.
 * @return Context if on android, null if not.
 */
expect fun getContext(): Any?
