package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.extractorApis
import dalvik.system.PathClassLoader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

const val PLUGINS_KEY = "PLUGINS_KEY"
const val PLUGINS_KEY_LOCAL = "PLUGINS_KEY_LOCAL"
const val PLUGIN_VERSION_NOT_SET = Int.MIN_VALUE

@Serializable
data class PluginData(
    @JsonProperty("internalName") @SerialName("internalName") val internalName: String,
    @JsonProperty("url") @SerialName("url") val url: String?,
    @JsonProperty("isOnline") @SerialName("isOnline") val isOnline: Boolean,
    @JsonProperty("filePath") @SerialName("filePath") val filePath: String,
    @JsonProperty("version") @SerialName("version") val version: Int,
)

/** Provider/extractor lifecycle only: no player actions, media downloads or background jobs. */
object PluginManager {
    private val dataLock = Mutex()
    private val loadLock = Mutex()
    val plugins = ConcurrentHashMap<String, BasePlugin>()
    val urlPlugins = ConcurrentHashMap<String, BasePlugin>()
    @Volatile var currentlyLoading: String? = null
        private set

    fun getPluginsOnline(): Array<PluginData> = getKey<Array<PluginData>>(PLUGINS_KEY) ?: emptyArray()
    fun getPluginsLocal(): Array<PluginData> = getKey<Array<PluginData>>(PLUGINS_KEY_LOCAL) ?: emptyArray()

    private suspend fun save(data: PluginData) = dataLock.withLock {
        val key = if (data.isOnline) PLUGINS_KEY else PLUGINS_KEY_LOCAL
        val current = if (data.isOnline) getPluginsOnline() else getPluginsLocal()
        setKey(key, (current.filter { it.filePath != data.filePath } + data).toTypedArray())
    }

    /** Keep the original filename mapping so already installed extensions survive this cleanup. */
    fun getPluginSanitizedFileName(name: String): String {
        val reserved = "|\\?*<\":>+[]/\'"
        var sanitized = name
        for (char in reserved) sanitized = sanitized.replace(char, ' ')
        sanitized = sanitized.replace(" ", "").replace("  ", " ").trim(' ')
        return "$sanitized.${name.hashCode()}"
    }

    fun getPluginPath(context: Context, internalName: String, repositoryUrl: String): File =
        File(context.filesDir, "${RepositoryManager.ONLINE_PLUGINS_FOLDER}/${getPluginSanitizedFileName(repositoryUrl)}/${getPluginSanitizedFileName(internalName)}.cs3")

    suspend fun ___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(context: Context) {
        for (data in getPluginsOnline()) {
            val file = File(data.filePath)
            if (file.isFile) loadPlugin(context, file, data)
        }
    }

    suspend fun ___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(context: Context, forceReload: Boolean) {
        val folder = File(context.getExternalFilesDir(null), "plugins").apply { mkdirs() }
        // Include persisted locations to keep previously installed local extensions available.
        val records = getPluginsLocal().associateBy { it.filePath }
        val files = (records.keys.map(::File) + folder.listFiles().orEmpty())
            .distinctBy { it.absolutePath }.filter { it.isFile && it.extension in setOf("cs3", "zip") }
        for (file in files.sortedBy { it.name }) {
            if (forceReload) unloadPlugin(file.absolutePath)
            loadPlugin(context, file, records[file.absolutePath]
                ?: PluginData(file.nameWithoutExtension, null, false, file.absolutePath, PLUGIN_VERSION_NOT_SET))
        }
    }

    internal suspend fun loadPlugin(context: Context, file: File, data: PluginData): Boolean = loadLock.withLock {
        val path = file.absolutePath
        if (plugins.containsKey(path)) return@withLock true
        currentlyLoading = file.nameWithoutExtension
        try {
            check(file.setReadOnly()) { "Cannot mark extension read-only: $path" }
            val loader = PathClassLoader(path, context.classLoader)
            val manifest = loader.getResourceAsStream("manifest.json")?.bufferedReader()?.use {
                parseJson<BasePlugin.Manifest>(it.readText())
            } ?: error("Extension manifest.json is missing")
            val pluginClassName = requireNotNull(manifest.pluginClassName) { "Plugin class name is missing" }
            val instance = loader.loadClass(pluginClassName).getDeclaredConstructor().newInstance() as BasePlugin
            instance.filename = path
            if (manifest.requiresResources && instance is Plugin) {
                val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, path)
                @Suppress("DEPRECATION")
                instance.resources = Resources(assets, context.resources.displayMetrics, context.resources.configuration)
            }
            // Register first so unload can roll back partial provider registration on failure.
            plugins[path] = instance
            urlPlugins[data.url ?: path] = instance
            if (instance is Plugin) instance.load(context) else instance.load()
            APIHolder.initAll()
            save(data.copy(version = manifest.version ?: PLUGIN_VERSION_NOT_SET))
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            unloadPlugin(path)
            throw cancelled
        } catch (error: Throwable) {
            unloadPlugin(path)
            Log.e("PluginManager", "Unable to load ${file.name}", error)
            false
        } finally {
            currentlyLoading = null
        }
    }

    fun unloadPlugin(absolutePath: String) {
        val instance = plugins.remove(absolutePath) ?: return
        try { instance.beforeUnload() } catch (error: Throwable) { Log.w("PluginManager", "beforeUnload failed", error) }
        APIHolder.apis.filter { it.sourcePlugin == absolutePath }.forEach(APIHolder::removePluginMapping)
        APIHolder.allProviders.withLock { APIHolder.allProviders.removeAll { it.sourcePlugin == absolutePath } }
        extractorApis.withLock { extractorApis.removeAll { it.sourcePlugin == absolutePath } }
        urlPlugins.entries.removeAll { it.value === instance }
    }

    suspend fun deletePlugin(file: File): Boolean = loadLock.withLock {
        unloadPlugin(file.absolutePath)
        if (file.exists() && !file.delete()) return@withLock false
        dataLock.withLock {
            setKey(PLUGINS_KEY, getPluginsOnline().filter { it.filePath != file.absolutePath }.toTypedArray())
            setKey(PLUGINS_KEY_LOCAL, getPluginsLocal().filter { it.filePath != file.absolutePath }.toTypedArray())
        }
        true
    }

    suspend fun deleteRepositoryData(repositoryPath: String) {
        val folder = File(repositoryPath)
        val records = getPluginsOnline().filter { File(it.filePath).parentFile == folder }
        for (record in records) check(deletePlugin(File(record.filePath))) { "Cannot delete ${record.internalName}" }
        check(!folder.exists() || folder.deleteRecursively()) { "Cannot delete repository directory" }
    }
}
