package com.lagradost.cloudstream4.desktop.plugins

import android.content.DesktopContext
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.data.AppDirs
import com.lagradost.cloudstream4.desktop.data.InstalledPlugin
import com.lagradost.cloudstream4.desktop.data.RepositoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

@JsonIgnoreProperties(ignoreUnknown = true)
data class Repository(
    val name: String,
    val iconUrl: String? = null,
    val description: String? = null,
    val manifestVersion: Int = 1,
    val pluginLists: List<String> = emptyList(),
)

/** An extension as listed in a repository's plugins.json */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RemotePlugin(
    /** Url to the Android .cs3 file */
    val url: String,
    /** 0: Down, 1: Ok, 2: Slow, 3: Beta only */
    val status: Int = 1,
    val version: Int = 0,
    val apiVersion: Int = 1,
    val name: String,
    val internalName: String,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val repositoryUrl: String? = null,
    val tvTypes: List<String>? = null,
    val language: String? = null,
    val iconUrl: String? = null,
    val fileSize: Long? = null,
    val fileHash: String? = null,
    /** Url to a JVM build of the extension, published by newer versions of the gradle plugin */
    val jarUrl: String? = null,
    val jarFileSize: Long? = null,
    val jarHash: String? = null,
) {
    fun icon(size: Int = 64): String? = iconUrl?.replace("%size%", "$size")
}

data class LoadError(val name: String, val message: String)

/**
 * Desktop counterpart of the Android PluginManager and RepositoryManager.
 *
 * Extensions are stored as JVM jars. If a repository only provides Android .cs3 files the
 * contained dex is converted to JVM bytecode when installing.
 */
object PluginManager {
    private class LoadedPlugin(val instance: BasePlugin, val loader: ExtensionClassLoader)

    private val loaded = HashMap<String, LoadedPlugin>()
    private val pluginLock = Mutex()

    /** Bumped whenever providers are added or removed so the UI can refresh */
    private val _providersVersion = MutableStateFlow(0)
    val providersVersion: StateFlow<Int> = _providersVersion.asStateFlow()

    private val _errors = MutableStateFlow<List<LoadError>>(emptyList())
    val errors: StateFlow<List<LoadError>> = _errors.asStateFlow()

    private val GH_REGEX = Regex("^https://raw.githubusercontent.com/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+)/(.*)$")

    fun providers(): List<MainAPI> = APIHolder.allProviders.withLock { APIHolder.allProviders.toList() }

    private fun convertRawGitUrl(url: String): String {
        if (!AppData.settings.value.jsdelivrProxy) return url
        val match = GH_REGEX.find(url) ?: return url
        val (user, repo, rest) = match.destructured
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$rest"
    }

    /** Resolves full urls, cloudstreamrepo:// links and shortcodes to a repository url */
    suspend fun parseRepoUrl(input: String): String? {
        val fixed = input.trim()
        val repoScheme = Regex("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)")
        return when {
            fixed.contains(repoScheme) -> fixed.replace(repoScheme, "").let {
                if (it.contains("^https?://".toRegex())) it else "https://$it"
            }

            fixed.contains("^https?://".toRegex()) -> fixed
            fixed.matches("^[a-zA-Z0-9!_-]+$".toRegex()) -> {
                val (host, code) = if (fixed.startsWith("!")) "py.md" to fixed.removePrefix("!") else "cutt.ly" to fixed
                try {
                    val response = app.get("https://$host/$code", allowRedirects = false)
                    response.headers["Location"]?.takeUnless {
                        it.startsWith("https://$host/404") || it.removeSuffix("/") == "https://$host"
                    }
                } catch (t: Throwable) {
                    logError(t)
                    null
                }
            }

            else -> null
        }
    }

    suspend fun fetchRepository(url: String): Repository? = withContext(Dispatchers.IO) {
        try {
            val text = app.get(convertRawGitUrl(url), cacheTime = 15, cacheUnit = TimeUnit.MINUTES).text
            mapper.readValue<Repository>(text)
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    suspend fun fetchRepositoryPlugins(repository: Repository): List<RemotePlugin> = coroutineScope {
        repository.pluginLists.map { listUrl ->
            async(Dispatchers.IO) {
                try {
                    val text = app.get(convertRawGitUrl(listUrl), cacheTime = 15, cacheUnit = TimeUnit.MINUTES).text
                    mapper.readValue<List<RemotePlugin>>(text)
                } catch (t: Throwable) {
                    logError(t)
                    emptyList()
                }
            }
        }.awaitAll().flatten()
            // Lists of one repository can overlap, the UI keys extensions by internal name
            .distinctBy { it.internalName }
    }

    /** @return the added repository or null if the url is not a valid repository */
    suspend fun addRepository(input: String): RepositoryEntry? {
        val url = parseRepoUrl(input) ?: return null
        val repo = fetchRepository(url) ?: return null
        return RepositoryEntry(repo.name, url, repo.iconUrl).also { AppData.addRepository(it) }
    }

    suspend fun removeRepository(url: String) {
        AppData.plugins.value.filter { it.repositoryUrl == url }.forEach { uninstall(it) }
        File(AppDirs.extensionsDir, sanitizedFileName(url)).deleteRecursively()
        AppData.removeRepository(url)
    }

    private fun sanitizedFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "").take(100) + "." + name.hashCode()

    fun pluginFile(repositoryUrl: String, internalName: String): File =
        File(AppDirs.extensionsDir, "${sanitizedFileName(repositoryUrl)}/${sanitizedFileName(internalName)}.jar")

    fun installedFor(repositoryUrl: String, internalName: String): InstalledPlugin? {
        val path = pluginFile(repositoryUrl, internalName).absolutePath
        return AppData.plugins.value.firstOrNull { it.filePath == path }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun download(url: String, expectedHash: String?): File = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("extension", ".tmp", AppDirs.cacheDir)
        app.get(convertRawGitUrl(url)).okhttpResponse.body.byteStream().use { body ->
            temp.outputStream().use { body.copyTo(it) }
        }
        if (expectedHash != null) {
            val hash = sha256(temp)
            if (hash != expectedHash) {
                temp.delete()
                throw IllegalStateException("Extension hash mismatch, expected $expectedHash got $hash")
            }
        }
        temp
    }

    /** Downloads (or updates) and loads an extension */
    suspend fun install(repositoryUrl: String, plugin: RemotePlugin): Result<InstalledPlugin> = runCatching {
        val target = pluginFile(repositoryUrl, plugin.internalName)
        val converted: Boolean
        val jar = if (plugin.jarUrl != null) {
            converted = false
            download(plugin.jarUrl, plugin.jarHash)
        } else {
            converted = true
            val cs3 = download(plugin.url, plugin.fileHash)
            try {
                withContext(Dispatchers.IO) { DexConverter.cs3ToJar(cs3) }
            } finally {
                cs3.delete()
            }
        }

        // Unload the old version before replacing the file, the class loader keeps it open
        AppData.plugins.value.firstOrNull { it.filePath == target.absolutePath }?.let { unload(it.filePath) }
        withContext(Dispatchers.IO) {
            target.parentFile.mkdirs()
            Files.move(jar.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        val installed = InstalledPlugin(
            internalName = plugin.internalName,
            name = plugin.name,
            repositoryUrl = repositoryUrl,
            version = plugin.version,
            filePath = target.absolutePath,
            iconUrl = plugin.icon(),
            convertedFromDex = converted,
            converterVersion = if (converted) DexConverter.VERSION else 0,
        )
        AppData.putPlugin(installed)
        load(installed).getOrThrow()
        installed
    }

    suspend fun uninstall(plugin: InstalledPlugin) {
        unload(plugin.filePath)
        AppData.removePlugin(plugin.filePath)
        withContext(Dispatchers.IO) { File(plugin.filePath).delete() }
    }

    /** Loads every installed extension, used on startup */
    suspend fun loadAll() {
        loadAllInstalled()
        // Some extensions finish setting up once every extension is loaded
        runCatching { MainActivity.afterPluginsLoadedEvent.invoke(true) }.onFailure { logError(it) }
    }

    private suspend fun loadAllInstalled() = withContext(Dispatchers.IO) {
        AppData.plugins.value.forEach { plugin ->
            if (!File(plugin.filePath).exists()) {
                AppData.removePlugin(plugin.filePath)
            } else if (plugin.convertedFromDex && plugin.converterVersion < DexConverter.RECONVERT_BELOW) {
                // Falls back to the old conversion if the repository cannot be reached
                if (reinstall(plugin).onFailure { logError(it) }.isFailure) load(plugin)
            } else if (plugin.convertedFromDex && plugin.converterVersion < DexConverter.VERSION) {
                val repaired = runCatching { DexConverter.repairJar(File(plugin.filePath)) }
                    .onFailure { logError(it) }.isSuccess
                val updated = if (repaired) plugin.copy(converterVersion = DexConverter.VERSION) else plugin
                if (repaired) AppData.putPlugin(updated)
                load(updated)
            } else {
                load(plugin)
            }
        }
    }

    /** Downloads and converts an installed extension again, from the latest version in its repository */
    private suspend fun reinstall(plugin: InstalledPlugin): Result<InstalledPlugin> = runCatching {
        val repository = fetchRepository(plugin.repositoryUrl) ?: throw IllegalStateException("Repository unavailable")
        val remote = fetchRepositoryPlugins(repository).firstOrNull { it.internalName == plugin.internalName }
            ?: throw IllegalStateException("${plugin.name} is no longer in its repository")
        install(plugin.repositoryUrl, remote).getOrThrow()
    }

    /** Checks every repository for newer versions of the installed extensions and installs them */
    suspend fun updateAll(): Int {
        var updated = 0
        AppData.repositories.value.forEach { repoEntry ->
            val repository = fetchRepository(repoEntry.url) ?: return@forEach
            fetchRepositoryPlugins(repository).forEach { remote ->
                val installed = installedFor(repoEntry.url, remote.internalName) ?: return@forEach
                if (remote.version > installed.version && remote.status != 0) {
                    if (install(repoEntry.url, remote).isSuccess) updated++
                }
            }
        }
        return updated
    }

    private fun findPluginClassName(file: File): String? = ZipFile(file).use { zip ->
        // Converted and Android extensions have a manifest
        zip.getEntry("manifest.json")?.let { entry ->
            val manifest = mapper.readValue<BasePlugin.Manifest>(zip.getInputStream(entry))
            return manifest.pluginClassName
        }
        null
    }

    private fun scanForPluginClass(file: File, loader: ClassLoader): Class<*>? = ZipFile(file).use { zip ->
        zip.entries().asSequence()
            .map { it.name }
            .filter { it.endsWith(".class") && !it.contains('$') }
            .map { it.removeSuffix(".class").replace('/', '.') }
            .firstNotNullOfOrNull { name ->
                try {
                    val clazz = Class.forName(name, false, loader)
                    clazz.takeIf {
                        it.isAnnotationPresent(CloudstreamPlugin::class.java) &&
                            BasePlugin::class.java.isAssignableFrom(it)
                    }
                } catch (_: Throwable) {
                    null
                }
            }
    }

    /** Extensions may do blocking work such as network requests in load(), so this never runs on the UI thread */
    suspend fun load(plugin: InstalledPlugin): Result<Unit> = withContext(Dispatchers.IO) { loadLocked(plugin) }

    private suspend fun loadLocked(plugin: InstalledPlugin): Result<Unit> = pluginLock.withLock {
        val file = File(plugin.filePath)
        if (loaded.containsKey(file.absolutePath)) return@withLock Result.success(Unit)
        _errors.update { list -> list.filterNot { it.name == plugin.name } }

        runCatching {
            val loader = ExtensionClassLoader(file, PluginManager::class.java.classLoader)
            try {
                val pluginClass = findPluginClassName(file)?.let { loader.loadClass(it) }
                    ?: scanForPluginClass(file, loader)
                    ?: throw IllegalStateException("No @CloudstreamPlugin class found")
                val instance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
                instance.filename = file.absolutePath
                loaded[file.absolutePath] = LoadedPlugin(instance, loader)
                if (instance is Plugin) instance.load(DesktopContext) else instance.load()
            } catch (t: Throwable) {
                loaded.remove(file.absolutePath)
                removeRegistered(file.absolutePath)
                loader.close()
                throw t
            }
            _providersVersion.update { it + 1 }
            Unit
        }.onFailure { t ->
            logError(t)
            val message = when (t) {
                // NoClassDefFoundError, NoSuchMethodError, NoSuchFieldError...
                is LinkageError ->
                    "Uses Android-only features that are not available on desktop (${t.message})"

                else -> t.message ?: t.javaClass.simpleName
            }
            _errors.update { it + LoadError(plugin.name, message) }
        }
    }

    private fun removeRegistered(filename: String) {
        APIHolder.apis.filter { it.sourcePlugin == filename }.forEach { APIHolder.removePluginMapping(it) }
        APIHolder.allProviders.withLock { APIHolder.allProviders.removeAll { it.sourcePlugin == filename } }
        extractorApis.withLock { extractorApis.removeAll { it.sourcePlugin == filename } }
    }

    suspend fun unload(filePath: String) = pluginLock.withLock {
        val plugin = loaded.remove(filePath) ?: return@withLock
        try {
            plugin.instance.beforeUnload()
        } catch (t: Throwable) {
            logError(t)
        }
        removeRegistered(filePath)
        plugin.loader.close()
        _providersVersion.update { it + 1 }
    }
}
