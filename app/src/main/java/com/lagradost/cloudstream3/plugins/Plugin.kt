package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import kotlin.Throws
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis
import android.util.Log

const val PLUGIN_TAG = "PluginInstance"

abstract class Plugin {
    /**
     * Called when your Plugin is loaded
     * @param context Context
     */
    @Throws(Throwable::class)
    open fun load(context: Context) {
    }

    /**
     * Called when your Plugin is being unloaded
     */
    @Throws(Throwable::class)
    open fun beforeUnload() {
    }

    /**
     * Used to register providers instances of MainAPI
     * @param element MainAPI provider you want to register
     */
    fun registerMainAPI(element: MainAPI) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} (${element.mainUrl}) MainAPI")
        element.sourcePlugin = this.`__filename`
        APIHolder.allProviders.add(element)
        APIHolder.addPluginMapping(element)
    }

    /**
     * Used to register extractor instances of ExtractorApi
     * @param element ExtractorApi provider you want to register
     */
    fun registerExtractorAPI(element: ExtractorApi) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} (${element.mainUrl}) ExtractorApi")
        element.sourcePlugin = this.`__filename`
        extractorApis.add(element)
    }

    class Manifest {
        var name: String? = null
        var pluginClassName: String? = null
        var pluginVersion: Int? = null
    }

    var resources: Resources? = null
    var needsResources = false
    var __filename: String? = null
}