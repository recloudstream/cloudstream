package com.lagradost.api

/**
 * Set context for Android-specific code such as webview.
 * Does nothing on non-Android platforms.
 */
expect fun setContext(context: Any?)

/**
 * Helper function for Android specific context.
 * Do not use this unless absolutely necessary.
 * setContext() must be called before this is called.
 * @return Context if on Android, null if not.
 */
expect fun getContext(): Any?
