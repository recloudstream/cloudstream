package com.lagradost.cloudstream3.plugins

import android.content.Context
import dalvik.system.PathClassLoader
import com.google.gson.Gson
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Environment
import android.widget.Toast
import android.app.Activity
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.RepositoryManager.ONLINE_PLUGINS_FOLDER
import com.lagradost.cloudstream3.plugins.RepositoryManager.downloadPluginToFile
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.R
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStreamReader
import java.util.*

// Different keys for local and not since local can be removed at any time without app knowing, hence the local are getting rebuilt on every app start
const val PLUGINS_KEY = "PLUGINS_KEY"
const val PLUGINS_KEY_LOCAL = "PLUGINS_KEY_LOCAL"

data class PluginData(
    @JsonProperty("name") val name: String,
    @JsonProperty("url") val url: String?,
    @JsonProperty("isOnline") val isOnline: Boolean,
    @JsonProperty("filePath") val filePath: String,
)

object PluginManager {
    // Prevent multiple writes at once
    val lock = Mutex()

    /**
     * Store data about the plugin for fetching later
     * */
    private fun setPluginData(data: PluginData) {
        ioSafe {
            lock.withLock {
                if (data.isOnline) {
                    val plugins = getPluginsOnline()
                    setKey(PLUGINS_KEY, plugins + data)
                } else {
                    val plugins = getPluginsLocal()
                    setKey(PLUGINS_KEY_LOCAL, plugins + data)
                }
            }
        }
    }

    private fun deletePluginData(data: PluginData) {
        ioSafe {
            lock.withLock {
                if (data.isOnline) {
                    val plugins = getPluginsOnline().filter { it.url != data.url }
                    setKey(PLUGINS_KEY, plugins)
                } else {
                    val plugins = getPluginsLocal().filter { it.filePath != data.filePath }
                    setKey(PLUGINS_KEY_LOCAL, plugins + data)
                }
            }
        }
    }

    fun getPluginsOnline(): Array<PluginData> {
        return getKey(PLUGINS_KEY) ?: emptyArray()
    }

    fun getPluginsLocal(): Array<PluginData> {
        return getKey(PLUGINS_KEY_LOCAL) ?: emptyArray()
    }

    private val LOCAL_PLUGINS_PATH =
        Environment.getExternalStorageDirectory().absolutePath + "/Cloudstream3/plugins"


    private val plugins: MutableMap<String, Plugin> =
        LinkedHashMap<String, Plugin>()
    private val classLoaders: MutableMap<PathClassLoader, Plugin> =
        HashMap<PathClassLoader, Plugin>()
    private val failedToLoad: MutableMap<File, Any> = LinkedHashMap()
    var loadedLocalPlugins = false
    private val gson = Gson()

    private fun maybeLoadPlugin(context: Context, file: File) {
        val name = file.name
        if (file.extension == "zip" || file.extension == "cs3") {
            loadPlugin(context, file, PluginData(name, null, false, file.absolutePath))
        }
    }

    fun loadAllOnlinePlugins(context: Context) {
        File(context.filesDir, ONLINE_PLUGINS_FOLDER).listFiles()?.sortedBy { it.name }
            ?.forEach { file ->
                maybeLoadPlugin(context, file)
            }
    }

    fun loadAllLocalPlugins(context: Context) {
        val dir = File(LOCAL_PLUGINS_PATH)
        removeKey(PLUGINS_KEY_LOCAL)

        if (!dir.exists()) {
            val res = dir.mkdirs()
            if (!res) {
                //logger.error("Failed to create directories!", null);
                return
            }
        }

        val sortedPlugins = dir.listFiles()
        // Always sort plugins alphabetically for reproducible results

        sortedPlugins?.sortedBy { it.name }?.forEach { file ->
            maybeLoadPlugin(context, file)
        }

        loadedLocalPlugins = true
    }

    /**
     * @return True if successful, false if not
     * */
    private fun loadPlugin(context: Context, file: File, data: PluginData): Boolean {
        val fileName = file.nameWithoutExtension
        setPluginData(data)
        println("Loading plugin: $data")

        //logger.info("Loading plugin: " + fileName);
        return try {
            val loader = PathClassLoader(file.absolutePath, context.classLoader)
            var manifest: Plugin.Manifest
            loader.getResourceAsStream("manifest.json").use { stream ->
                if (stream == null) {
                    failedToLoad[file] = "No manifest found"
                    //logger.error("Failed to load plugin " + fileName + ": No manifest found", null);
                    return false
                }
                InputStreamReader(stream).use { reader ->
                    manifest = gson.fromJson(
                        reader,
                        Plugin.Manifest::class.java
                    )
                }
            }
            val name: String = manifest.name ?: "NO NAME"
            val pluginClass: Class<*> =
                loader.loadClass(manifest.pluginClassName) as Class<out Plugin?>
            val pluginInstance: Plugin =
                pluginClass.newInstance() as Plugin
            if (plugins.containsKey(name)) {
                println("Plugin with name $name already exists")
                return false
            }
            pluginInstance.__filename = fileName
            if (pluginInstance.needsResources) {
                // based on https://stackoverflow.com/questions/7483568/dynamic-resource-loading-from-other-apk
                val assets = AssetManager::class.java.newInstance()
                val addAssetPath =
                    AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                addAssetPath.invoke(assets, file.absolutePath)
                pluginInstance.resources = Resources(
                    assets,
                    context.resources.displayMetrics,
                    context.resources.configuration
                )
            }
            plugins[name] = pluginInstance
            classLoaders[loader] = pluginInstance
            pluginInstance.load(context)
            true
        } catch (e: Throwable) {
            failedToLoad[file] = e
            e.printStackTrace()
            showToast(
                context as Activity,
                context.getString(R.string.plugin_load_fail).format(fileName),
                Toast.LENGTH_LONG
            )
            false
        }
    }

    suspend fun downloadPlugin(context: Context, pluginUrl: String, name: String): Boolean {
        val file = downloadPluginToFile(context, pluginUrl, name)
        return loadPlugin(
            context,
            file ?: return false,
            PluginData(name, pluginUrl, true, file.absolutePath)
        )
    }

    fun deletePlugin(context: Context, pluginUrl: String, name: String): Boolean {
        val data = getPluginsOnline()
            .firstOrNull { it.url == pluginUrl }
            ?: return false
        deletePluginData(data)
        return File(data.filePath).delete()
    }
}