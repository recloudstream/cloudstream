package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import kotlin.Throws
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder

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
     * Used to register VideoClickAction instances
     * @param element VideoClickAction you want to register
     */
    fun registerVideoClickAction(element: VideoClickAction) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} VideoClickAction")
        element.sourcePlugin = this.filename
        synchronized(VideoClickActionHolder.allVideoClickActions) {
            VideoClickActionHolder.allVideoClickActions.add(element)
        }
    }

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

    /**
     * This will contain your resources if you specified requiresResources in gradle
     */
    var resources: Resources? = null
    /** Full file path to the plugin. */
    @Deprecated("Renamed to `filename` to follow conventions", replaceWith = ReplaceWith("filename"))
    var __filename: String?
        get() = filename
        set(value) {filename = value}
    var filename: String? = null

    /**
     * This will add a button in the settings allowing you to add custom settings
     */
    var openSettings: ((context: Context) -> Unit)? = null
}