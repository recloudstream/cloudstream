package com.lagradost.cloudstream3.plugins

import android.content.Context
import dalvik.system.PathClassLoader
import com.google.gson.Gson
import com.lagradost.cloudstream3.plugins.PluginManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Environment
import java.io.File
import java.io.InputStreamReader
import java.util.*

object PluginManager {
    private val PLUGINS_PATH =
        Environment.getExternalStorageDirectory().absolutePath + "/Cloudstream3/plugins"
    private val plugins: MutableMap<String, Plugin> =
        LinkedHashMap<String, Plugin>()
    private val classLoaders: MutableMap<PathClassLoader, Plugin> =
        HashMap<PathClassLoader, Plugin>()
    private val failedToLoad: MutableMap<File, Any> = LinkedHashMap()
    var loadedPlugins = false
    private val gson = Gson()
    fun loadAllPlugins(context: Context) {
        val dir = File(PLUGINS_PATH)
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
            val name = file.name
            if (file.extension == "zip" || file.extension == "cs3") {
                loadPlugin(context, file)
            } else if (name != "oat") { // Some roms create this
                if (file.isDirectory) {
                    // Utils.showToast(String.format("Found directory %s in your plugins folder. DO NOT EXTRACT PLUGIN ZIPS!", name), true);
                } else if (name == "classes.dex" || name.endsWith(".json")) {
                    // Utils.showToast(String.format("Found extracted plugin file %s in your plugins folder. DO NOT EXTRACT PLUGIN ZIPS!", name), true);
                }
                // rmrf(f);
            }
        }

        loadedPlugins = true
        //if (!PluginManager.failedToLoad.isEmpty())
        //Utils.showToast("Some plugins failed to load.");
    }

    fun loadPlugin(context: Context, file: File) {
        val fileName = file.nameWithoutExtension
        //logger.info("Loading plugin: " + fileName);
        try {
            val loader = PathClassLoader(file.absolutePath, context.classLoader)
            var manifest: Plugin.Manifest
            loader.getResourceAsStream("manifest.json").use { stream ->
                if (stream == null) {
                    failedToLoad[file] = "No manifest found"
                    //logger.error("Failed to load plugin " + fileName + ": No manifest found", null);
                    return
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
                //logger.error("Plugin with name " + name + " already exists", null);
                return
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
        } catch (e: Throwable) {
            failedToLoad[file] = e
            e.printStackTrace()
            //logger.error("Failed to load plugin " + fileName + ":\n", e);
        }
    }
}