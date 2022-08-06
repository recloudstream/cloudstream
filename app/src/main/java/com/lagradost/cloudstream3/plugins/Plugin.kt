package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import kotlin.Throws

abstract class Plugin {
    /**
     * Called when your Plugin is loaded
     * @param context Context
     */
    @Throws(Throwable::class)
    open fun load(context: Context?) {
    }

    class Manifest {
        var name: String? = null
        var pluginClassName: String? = null
    }

    var resources: Resources? = null
    var needsResources = false
    var __filename: String? = null
}