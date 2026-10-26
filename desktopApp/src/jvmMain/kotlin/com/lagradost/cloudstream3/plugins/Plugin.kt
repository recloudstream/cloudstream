package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/**
 * Desktop copy of the Android app's Plugin class, with the same binary signature, so extensions
 * that extend Plugin (rather than the cross-platform BasePlugin) can be loaded on desktop.
 */
abstract class Plugin : BasePlugin() {
    /**
     * Called when your Plugin is loaded
     * @param context Context
     */
    @Throws(Throwable::class)
    open fun load(context: Context) {
        // If not overridden by an extension then try the cross-platform load()
        load()
    }

    /** Extension resources are not supported on desktop, this is always null. */
    var resources: Resources? = null

    /** Settings screens are Android views, so this is never invoked on desktop. */
    var openSettings: ((context: Context) -> Unit)? = null
}
