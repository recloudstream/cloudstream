package com.lagradost.cloudstream3.plugins

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.extractorApis

const val PLUGIN_TAG = "PluginInstance"

abstract class BasePlugin {
    /**
     * Used to register providers instances of MainAPI
     * @param element MainAPI provider you want to register
     */
    fun registerMainAPI(element: MainAPI) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} (${element.mainUrl}) MainAPI")
        element.sourcePlugin = this.filename
        // Race condition causing which would case duplicates if not for distinctBy
        synchronized(APIHolder.allProviders) {
            APIHolder.allProviders.add(element)
        }
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
        replaceWith = ReplaceWith("filename")
    )
    var __filename: String?
        get() = filename
        set(value) {
            filename = value
        }
    var filename: String? = null


    class Manifest {
        @JsonProperty("name")
        var name: String? = null

        @JsonProperty("pluginClassName")
        var pluginClassName: String? = null

        @JsonProperty("version")
        var version: Int? = null

        @JsonProperty("requiresResources")
        var requiresResources: Boolean = false
    }
}