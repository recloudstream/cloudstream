package com.lagradost.cloudstream3.plugins

import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.ConfigStore
import com.lagradost.cloudstream3.PLUGIN_VERSION_ALWAYS_UPDATE
import com.lagradost.cloudstream3.PLUGIN_VERSION_NOT_SET
import com.lagradost.cloudstream3.PluginData
import com.lagradost.cloudstream3.ServerContext
import com.lagradost.cloudstream3.SitePlugin
import com.lagradost.cloudstream3.utils.extractorApis
import com.googlecode.d2j.dex.Dex2jar
import java.io.File
import java.io.FileInputStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

object PluginManager {
    const val LOCAL_PLUGINS_FOLDER = "plugins"
    private const val MANIFEST_NAME = "manifest.json"
    private const val JAR_NAME = "plugin.jar"
    private val stubContext = android.content.Context()
    private var configStore: ConfigStore? = null

    private data class LoadedPlugin(
        val classLoader: URLClassLoader,
        val instance: BasePlugin
    )

    private val plugins = LinkedHashMap<String, LoadedPlugin>()

    fun init(configStore: ConfigStore) {
        this.configStore = configStore
    }

    fun sanitizeFilename(name: String, removeSpaces: Boolean = false): String {
        val reservedChars = "|\\?*<\":>+[]/\'"
        var tempName = name
        for (c in reservedChars) {
            tempName = tempName.replace(c, ' ')
        }
        if (removeSpaces) tempName = tempName.replace(" ", "")
        return tempName.replace("  ", " ").trim(' ')
    }

    fun getPluginSanitizedFileName(name: String): String {
        return sanitizeFilename(name, true) + "." + name.hashCode()
    }

    fun getPluginPath(baseDir: Path, internalName: String, repositoryUrl: String): File {
        val folderName = getPluginSanitizedFileName(repositoryUrl)
        val fileName = getPluginSanitizedFileName(internalName)
        return baseDir.resolve(RepositoryManager.ONLINE_PLUGINS_FOLDER)
            .resolve(folderName)
            .resolve(fileName)
            .toFile()
    }

    fun getLocalPluginPath(baseDir: Path, fileName: String): File {
        val baseName = File(fileName).nameWithoutExtension
        val safeName = sanitizeFilename(baseName, false)
        return baseDir.resolve(LOCAL_PLUGINS_FOLDER).resolve(safeName).toFile()
    }

    fun toPluginData(
        sitePlugin: SitePlugin,
        repositoryUrl: String,
        file: File
    ): PluginData {
        return PluginData(
            internalName = sitePlugin.internalName,
            url = sitePlugin.url,
            isOnline = true,
            filePath = file.absolutePath,
            version = sitePlugin.version,
            repositoryUrl = repositoryUrl,
            name = sitePlugin.name,
            status = sitePlugin.status,
            apiVersion = sitePlugin.apiVersion,
            authors = sitePlugin.authors,
            description = sitePlugin.description,
            tvTypes = sitePlugin.tvTypes,
            language = sitePlugin.language,
            iconUrl = sitePlugin.iconUrl,
            fileSize = sitePlugin.fileSize,
        )
    }

    fun toLocalPluginData(file: File, internalName: String? = null): PluginData {
        val name = internalName ?: file.nameWithoutExtension
        return PluginData(
            internalName = name,
            url = null,
            isOnline = false,
            filePath = file.absolutePath,
            version = PLUGIN_VERSION_NOT_SET,
            repositoryUrl = null,
            name = name,
            fileSize = file.length(),
            uploadedAt = System.currentTimeMillis()
        )
    }

    fun shouldUpdate(local: PluginData, remote: SitePlugin): Boolean {
        if (remote.version == PLUGIN_VERSION_ALWAYS_UPDATE) return true
        return remote.version > local.version
    }

    fun isDisabled(remote: SitePlugin): Boolean = remote.status == 0

    fun validOnlineData(baseDir: Path, local: PluginData, repositoryUrl: String): Boolean {
        return getPluginPath(baseDir, local.internalName, repositoryUrl).absolutePath == local.filePath
    }

    fun extractPluginArchive(archive: File, targetDir: File): Boolean {
        return runCatching {
            if (targetDir.exists()) targetDir.deleteRecursively()
            Files.createDirectories(targetDir.toPath())
            ZipInputStream(FileInputStream(archive)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val resolved = targetDir.toPath().resolve(entry.name).normalize()
                    if (!resolved.startsWith(targetDir.toPath())) {
                        Log.e("PluginManager", "Blocked zip entry: ${entry.name}")
                        return@runCatching false
                    }
                    if (entry.isDirectory) {
                        Files.createDirectories(resolved)
                    } else {
                        resolved.parent?.let { Files.createDirectories(it) }
                        Files.copy(zip, resolved, StandardCopyOption.REPLACE_EXISTING)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            true
        }.getOrElse {
            Log.e("PluginManager", "Failed to extract plugin: ${it.message}")
            false
        }
    }

    fun readManifest(pluginDir: File): BasePlugin.Manifest? {
        val manifestFile = findByName(pluginDir, MANIFEST_NAME) ?: return null
        return runCatching {
            com.lagradost.cloudstream3.mapper.readValue(
                manifestFile,
                BasePlugin.Manifest::class.java
            )
        }.getOrElse {
            Log.e("PluginManager", "Failed to parse manifest: ${it.message}")
            null
        }
    }

    fun loadPlugin(pluginDir: File, data: PluginData): PluginData? {
        val manifest = readManifest(pluginDir) ?: return null
        val className = manifest.pluginClassName
        if (className.isNullOrBlank()) {
            Log.e("PluginManager", "Manifest missing pluginClassName for ${pluginDir.name}")
            return null
        }
        val jarFile = ensureJar(pluginDir) ?: return null
        Log.i("PluginManager", "Loading plugin ${pluginDir.name} with class $className")
        unloadPlugin(pluginDir.absolutePath)
        val loader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), PluginManager::class.java.classLoader)
        return try {
            val clazz = loader.loadClass(className)
            val instance = clazz.getDeclaredConstructor().newInstance() as? BasePlugin
                ?: throw IllegalStateException("Class $className is not a BasePlugin")
            instance.filename = pluginDir.absolutePath
            val updated = applyManifest(data, manifest)
            plugins[pluginDir.absolutePath] = LoadedPlugin(loader, instance)
            if (instance is Plugin) {
                val context = configStore?.let { ServerContext(it, data.internalName, pluginDir) } ?: stubContext
                instance.load(context)
            } else {
                instance.load()
            }
            APIHolder.initAll()
            updated
        } catch (e: Throwable) {
            Log.e(
                "PluginManager",
                "Failed to load plugin ${pluginDir.name}: ${e.message}\n${e.stackTraceToString()}"
            )
            runCatching { loader.close() }
            null
        }
    }

    fun unloadPlugin(absolutePath: String) {
        val loaded = plugins.remove(absolutePath) ?: return
        val instance = loaded.instance
        runCatching { instance.beforeUnload() }.onFailure {
            Log.e("PluginManager", "Failed to unload plugin: ${it.message}")
        }

        synchronized(APIHolder.apis) {
            APIHolder.apis.filter { it.sourcePlugin == instance.filename }.forEach {
                APIHolder.removePluginMapping(it)
            }
        }
        synchronized(APIHolder.allProviders) {
            APIHolder.allProviders.removeIf { it.sourcePlugin == instance.filename }
        }
        extractorApis.removeIf { it.sourcePlugin == instance.filename }
        runCatching { loaded.classLoader.close() }
    }

    private fun applyManifest(data: PluginData, manifest: BasePlugin.Manifest): PluginData {
        val version = manifest.version ?: data.version
        val name = manifest.name ?: data.name
        return data.copy(version = version, name = name)
    }

    private fun ensureJar(pluginDir: File): File? {
        val dexFile = findDexFile(pluginDir) ?: run {
            Log.e("PluginManager", "No dex file found in ${pluginDir.absolutePath}")
            return null
        }
        val jarFile = File(pluginDir, JAR_NAME)
        if (jarFile.exists() && jarFile.lastModified() >= dexFile.lastModified()) return jarFile
        return try {
            Log.i("PluginManager", "Converting dex to jar for ${pluginDir.name}")
            Dex2jar.from(dexFile)
                .skipDebug(true)
                .dontSanitizeNames(true)
                .topoLogicalSort()
                .to(jarFile.toPath())
            jarFile.setLastModified(dexFile.lastModified())
            jarFile
        } catch (e: Throwable) {
            Log.e(
                "PluginManager",
                "Failed to build jar for ${pluginDir.name}: ${e.message}\n${e.stackTraceToString()}"
            )
            null
        }
    }

    private fun findByName(root: File, name: String): File? {
        val direct = File(root, name)
        if (direct.exists()) return direct
        return root.walkTopDown().firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) }
    }

    private fun findDexFile(root: File): File? {
        return root.walkTopDown().firstOrNull { it.isFile && it.extension.equals("dex", ignoreCase = true) }
    }

    fun deletePluginFile(file: File): Boolean {
        return runCatching {
            if (file.exists()) file.deleteRecursively()
            true
        }.getOrElse {
            Log.e("PluginManager", "Failed to delete plugin file: ${it.message}")
            false
        }
    }
}
