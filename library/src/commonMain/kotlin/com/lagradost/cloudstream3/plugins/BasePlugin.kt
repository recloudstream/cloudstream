package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.extractorApis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PLUGIN_TAG = "PluginInstance"

abstract class BasePlugin {
    /**
     * Used to register providers instances of MainAPI
     * @param element MainAPI provider you want to register
     */
    fun registerMainAPI(element: MainAPI) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} (${element.mainUrl}) MainAPI")
        element.sourcePlugin = this.filename
        APIHolder.allProviders.add(element)
        APIHolder.addPluginMapping(element)
    }

    /**
     * Used to register extractor instances of ExtractorApi
     * @param element ExtractorApi provider you want to register
     */
    fun registerExtractorAPI(element: ExtractorApi) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} (${element.mainUrl}) ExtractorApi")
        element.sourcePlugin = this.filename
        extractorApis.add(element)
    }

    /**
     * Called when your Plugin is being unloaded
     */
    @Throws(Throwable::class)
    open fun beforeUnload() {
    }

    /**
     * Called when your Plugin is loaded
     */
    @Throws(Throwable::class)
    open fun load() {
    }

    /** Full file path to the plugin. */
    @Deprecated(
        "Renamed to `filename` to follow conventions",
        replaceWith = ReplaceWith("filename"),
        level = DeprecationLevel.ERROR
    )
    var __filename: String?
        get() = filename
        set(value) {
            filename = value
        }
    var filename: String? = null

    @Serializable
    class Manifest {
        @SerialName("name") var name: String? = null
        @SerialName("pluginClassName") var pluginClassName: String? = null
        @SerialName("version") var version: Int? = null
        @SerialName("requiresResources") var requiresResources: Boolean = false
    }
}
