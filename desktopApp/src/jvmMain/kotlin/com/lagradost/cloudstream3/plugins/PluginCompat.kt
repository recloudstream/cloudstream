@file:Suppress("unused")

package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream4.desktop.data.AppData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.lagradost.cloudstream4.desktop.plugins.PluginManager as DesktopPluginManager

/** Same shape as the Android app's PluginData */
data class PluginData(
    val internalName: String,
    val url: String?,
    val isOnline: Boolean,
    val filePath: String,
    val version: Int,
)

/** The parts of the Android app's PluginManager extensions are known to call */
object PluginManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getPluginsOnline(): Array<PluginData> = AppData.plugins.value.map {
        PluginData(it.internalName, null, true, it.filePath, it.version)
    }.toTypedArray()

    fun getPluginsLocal(): Array<PluginData> = emptyArray()

    fun unloadPlugin(absolutePath: String) {
        scope.launch { DesktopPluginManager.unload(absolutePath) }
    }
}
