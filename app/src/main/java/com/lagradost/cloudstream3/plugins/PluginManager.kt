package com.lagradost.cloudstream3.plugins

import android.content.Context
import dalvik.system.PathClassLoader
import com.google.gson.Gson
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Environment
import android.widget.Toast
import android.app.Activity
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.RepositoryManager.ONLINE_PLUGINS_FOLDER
import com.lagradost.cloudstream3.plugins.RepositoryManager.downloadPluginToFile
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.plugins.RepositoryManager.getRepoPlugins
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.VideoDownloadManager.sanitizeFilename
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStreamReader
import java.util.*

// Different keys for local and not since local can be removed at any time without app knowing, hence the local are getting rebuilt on every app start
const val PLUGINS_KEY = "PLUGINS_KEY"
const val PLUGINS_KEY_LOCAL = "PLUGINS_KEY_LOCAL"


// Data class for internal storage
data class PluginData(
    @JsonProperty("internalName") val internalName: String,
    @JsonProperty("url") val url: String?,
    @JsonProperty("isOnline") val isOnline: Boolean,
    @JsonProperty("filePath") val filePath: String,
    @JsonProperty("version") val version: Int,
)

// This is used as a placeholder / not set version
const val PLUGIN_VERSION_NOT_SET = Int.MIN_VALUE

// This always updates
const val PLUGIN_VERSION_ALWAYS_UPDATE = -1

object PluginManager {
    // Prevent multiple writes at once
    val lock = Mutex()

    const val TAG = "PluginManager"

    /**
     * Store data about the plugin for fetching later
     * */
    private suspend fun setPluginData(data: PluginData) {
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

    private suspend fun deletePluginData(data: PluginData) {
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

    fun getPluginsOnline(): Array<PluginData> {
        return getKey(PLUGINS_KEY) ?: emptyArray()
    }

    fun getPluginsLocal(): Array<PluginData> {
        return getKey(PLUGINS_KEY_LOCAL) ?: emptyArray()
    }

    private val LOCAL_PLUGINS_PATH =
        Environment.getExternalStorageDirectory().absolutePath + "/Cloudstream3/plugins"

    // Maps filepath to plugin
    private val plugins: MutableMap<String, Plugin> =
        LinkedHashMap<String, Plugin>()

    private val classLoaders: MutableMap<PathClassLoader, Plugin> =
        HashMap<PathClassLoader, Plugin>()

    private val failedToLoad: MutableMap<File, Any> = LinkedHashMap()
    private var loadedLocalPlugins = false
    private val gson = Gson()

    private suspend fun maybeLoadPlugin(activity: Activity, file: File) {
        val name = file.name
        if (file.extension == "zip" || file.extension == "cs3") {
            loadPlugin(
                activity,
                file,
                PluginData(name, null, false, file.absolutePath, PLUGIN_VERSION_NOT_SET)
            )
        }
    }

    /**
     * Needs to be run before other plugin loading because plugin loading can not be overwritten
     **/
    fun updateAllOnlinePlugins(activity: Activity) {
        val urls = getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()

        val onlinePlugins = urls.toList().apmap {
            getRepoPlugins(it.url)?.toList() ?: emptyList()
        }.flatten()

        // Iterates over all offline plugins, compares to remote repo and returns the plugins which are outdated
        val outdatedPlugins = getPluginsOnline().map { savedData ->
            onlinePlugins.filter { onlineData -> savedData.internalName == onlineData.second.internalName }
                .mapNotNull { onlineData ->
                    val isOutdated =
                        onlineData.second.apiVersion != savedData.version || onlineData.second.version == PLUGIN_VERSION_ALWAYS_UPDATE
                    if (isOutdated) savedData to onlineData else null
                }
        }.flatten()

        Log.i(TAG, "Outdated plugins: $outdatedPlugins")

        outdatedPlugins.apmap {
            downloadAndLoadPlugin(
                activity,
                it.second.second.url,
                it.first.internalName,
                it.second.first
            )
        }

        Log.i(TAG, "Plugin update done!")
    }

    fun loadAllOnlinePlugins(activity: Activity) {
        File(activity.filesDir, ONLINE_PLUGINS_FOLDER).listFiles()?.sortedBy { it.name }
            ?.apmap { file ->
                maybeLoadPlugin(activity, file)
            }
    }

    fun loadAllLocalPlugins(activity: Activity) {
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

        sortedPlugins?.sortedBy { it.name }?.apmap { file ->
            maybeLoadPlugin(activity, file)
        }

        loadedLocalPlugins = true
    }

    /**
     * @return True if successful, false if not
     * */
    private suspend fun loadPlugin(activity: Activity, file: File, data: PluginData): Boolean {
        val fileName = file.nameWithoutExtension
        val filePath = file.absolutePath
        Log.i(TAG, "Loading plugin: $data")

        //logger.info("Loading plugin: " + fileName);
        return try {
            val loader = PathClassLoader(filePath, activity.classLoader)
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
            val version: Int = manifest.pluginVersion ?: PLUGIN_VERSION_NOT_SET
            val pluginClass: Class<*> =
                loader.loadClass(manifest.pluginClassName) as Class<out Plugin?>
            val pluginInstance: Plugin =
                pluginClass.newInstance() as Plugin

            // Sets with the proper version
            setPluginData(data.copy(version = version))

            if (plugins.containsKey(filePath)) {
                Log.i(TAG, "Plugin with name $name already exists")
                return true
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
                    activity.resources.displayMetrics,
                    activity.resources.configuration
                )
            }
            plugins[filePath] = pluginInstance
            classLoaders[loader] = pluginInstance
            pluginInstance.load(activity)
            Log.i(TAG, "Loaded plugin ${data.internalName} successfully")
            true
        } catch (e: Throwable) {
            failedToLoad[file] = e
            e.printStackTrace()
            showToast(
                activity,
                activity.getString(R.string.plugin_load_fail).format(fileName),
                Toast.LENGTH_LONG
            )
            false
        }
    }

    suspend fun downloadAndLoadPlugin(
        activity: Activity,
        pluginUrl: String,
        internalName: String,
        repositoryUrl: String
    ): Boolean {
        val folderName = (sanitizeFilename(
            repositoryUrl,
            true
        ) + "." + repositoryUrl.hashCode()) // Guaranteed unique
        val fileName = (sanitizeFilename(internalName, true) + "." + internalName.hashCode())
        Log.i(TAG, "Downloading plugin: $pluginUrl to $folderName/$fileName")
        // The plugin file needs to be salted with the repository url hash as to allow multiple repositories with the same internal plugin names
        val file = downloadPluginToFile(activity, pluginUrl, fileName, folderName)
        return loadPlugin(
            activity,
            file ?: return false,
            PluginData(internalName, pluginUrl, true, file.absolutePath, PLUGIN_VERSION_NOT_SET)
        )
    }

    suspend fun deletePlugin(pluginUrl: String): Boolean {
        val data = getPluginsOnline()
            .firstOrNull { it.url == pluginUrl }
            ?: return false
        return try {
            if (File(data.filePath).delete()) {
                deletePluginData(data)
                return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}