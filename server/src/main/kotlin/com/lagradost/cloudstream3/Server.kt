package com.lagradost.cloudstream3

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.sortUrls
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.VotingApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.serialization.jackson.jackson
import io.ktor.utils.io.core.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.DEFAULT_BUFFER_SIZE

fun main() {
    val configPath = resolveConfigPath()
    val configStore = ConfigStore(configPath)
    val initialConfig = configStore.load()
    val providerRegistry = ProviderRegistry()
    providerRegistry.registerFromConfig(initialConfig)
    RepositoryManager.useJsdelivr = initialConfig.server.useJsdelivr
    PluginManager.init(configStore)

    val dataDir = resolveDataDir(configPath)
    Files.createDirectories(dataDir)
    Files.createDirectories(dataDir.resolve(RepositoryManager.ONLINE_PLUGINS_FOLDER))
    Files.createDirectories(dataDir.resolve(PluginManager.LOCAL_PLUGINS_FOLDER))

    normalizeRepositories(configStore)
    loadPluginsOnStartup(configStore)

    embeddedServer(Netty, host = initialConfig.server.host, port = initialConfig.server.port) {
        install(CallLogging)
        install(ContentNegotiation) {
            jackson {
                registerModule(kotlinModule())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Patch)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.Accept)
            allowHeader(HttpHeaders.ContentDisposition)
            allowNonSimpleContentTypes = true
            val allowedHosts = initialConfig.server.corsAllowedHosts
            if (allowedHosts.any { it == "*" }) {
                anyHost()
            } else {
                allowedHosts.forEach { host -> allowHost(host, listOf("http", "https")) }
            }
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                Log.e("Server", "Unhandled error: ${cause.message}")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(cause.message ?: "Internal error")
                )
            }
        }

        routing {
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }

            route("/config") {
                get {
                    call.respond(configStore.load())
                }
                put {
                    val config = call.receive<ServerConfig>()
                    configStore.save(config)
                    RepositoryManager.useJsdelivr = config.server.useJsdelivr
                    call.respond(config)
                }
            }

            route("/accounts") {
                get {
                    call.respond(configStore.load().accounts)
                }
                post {
                    val request = call.receive<AccountUpsertRequest>()
                    val id = request.id ?: UUID.randomUUID().toString()
                    val account = AccountConfig(
                        id = id,
                        type = request.type,
                        name = request.name,
                        data = request.data
                    )
                    configStore.update { config ->
                        val index = config.accounts.indexOfFirst { it.id == id }
                        if (index >= 0) {
                            config.accounts[index] = account
                        } else {
                            config.accounts.add(account)
                        }
                        config
                    }
                    call.respond(account)
                }
                put("/{id}") {
                    val id = call.parameters["id"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                    val request = call.receive<AccountUpsertRequest>()
                    val account = AccountConfig(
                        id = id,
                        type = request.type,
                        name = request.name,
                        data = request.data
                    )
                    configStore.update { config ->
                        val index = config.accounts.indexOfFirst { it.id == id }
                        if (index >= 0) {
                            config.accounts[index] = account
                        } else {
                            config.accounts.add(account)
                        }
                        config
                    }
                    call.respond(account)
                }
                delete("/{id}") {
                    val id = call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                    val removed = configStore.update { config ->
                        config.accounts.removeIf { it.id == id }
                        config
                    }
                    call.respond(removed.accounts)
                }
            }

            route("/repositories") {
                get {
                    call.respond(configStore.load().repositories)
                }
                post {
                    val request = call.receive<RepositoryAddRequest>()
                    val input = request.url ?: request.shortcode
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing url"))
                    val resolvedUrl = RepositoryManager.parseRepoUrl(input) ?: request.url ?: input
                    val manifest = RepositoryManager.parseRepository(resolvedUrl)
                    val repo = RepositoryData(
                        id = PluginManager.getPluginSanitizedFileName(resolvedUrl),
                        name = request.name ?: manifest?.name,
                        url = resolvedUrl,
                        iconUrl = manifest?.iconUrl,
                        shortcode = request.shortcode,
                        enabled = request.enabled
                    )
                    configStore.update { config ->
                        val index = config.repositories.indexOfFirst { it.url == repo.url || it.id == repo.id }
                        if (index >= 0) {
                            config.repositories[index] = repo
                        } else {
                            config.repositories.add(repo)
                        }
                        config
                    }
                    call.respond(repo)
                }
                delete("/{id}") {
                    val id = call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                    val repo = findRepository(configStore, id)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Repository not found"))
                    removeRepositoryPlugins(configStore, dataDir, repo)
                    configStore.update { config ->
                        config.repositories.removeIf { it.url == repo.url || it.id == repo.id }
                        config
                    }
                    call.respond(mapOf("removed" to (repo.name ?: repo.url)))
                }
                get("/{id}/plugins") {
                    val id = call.parameters["id"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                    val repo = findRepository(configStore, id)
                        ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Repository not found"))
                    val includeVotes = call.request.queryParameters["votes"]?.toBoolean() == true
                    val repoPlugins = RepositoryManager.getRepoPlugins(repo.url)
                        ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Repository not found"))
                    val plugins = if (includeVotes) {
                        repoPlugins.map { (_, plugin) ->
                            SitePluginWithVotes(plugin, VotingApi.getVotes(plugin.url))
                        }
                    } else {
                        repoPlugins.map { (_, plugin) -> SitePluginWithVotes(plugin, null) }
                    }
                    call.respond(RepositoryPluginsResponse(repo, plugins))
                }
                post("/{id}/plugins/install-all") {
                    val id = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                    val repo = findRepository(configStore, id)
                        ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Repository not found"))
                    val installed = installAllPluginsFromRepo(configStore, dataDir, repo)
                    call.respond(installed)
                }
                post("/{id}/plugins/{internalName}/install") {
                    val id = call.parameters["id"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                    val internalName = call.parameters["internalName"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing internalName"))
                    val repo = findRepository(configStore, id)
                        ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Repository not found"))
                    val plugin = installPluginFromRepo(configStore, dataDir, repo, internalName)
                        ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Plugin not found"))
                    call.respond(plugin)
                }
                delete("/{id}/plugins") {
                    val id = call.parameters["id"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
                    val repo = findRepository(configStore, id)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Repository not found"))
                    val removed = removeRepositoryPlugins(configStore, dataDir, repo)
                    call.respond(removed)
                }
            }

            route("/plugins") {
                get {
                    call.respond(configStore.load().plugins)
                }
                post("/install") {
                    val request = call.receive<PluginInstallRequest>()
                    val repoInput = request.repositoryUrl
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing repositoryUrl"))
                    val repo = findRepository(configStore, repoInput)
                        ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Repository not found"))
                    val internalName = request.internalName
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing internalName"))
                    val plugin = installPluginFromRepo(configStore, dataDir, repo, internalName)
                        ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Plugin not found"))
                    call.respond(plugin)
                }
                post("/local") {
                    val multipart = call.receiveMultipart()
                    var storedPlugin: PluginData? = null
                    var storedPath: String? = null
                    var error: String? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                if (storedPlugin != null || error != null) return@forEachPart
                                val original = part.originalFileName ?: "plugin.cs3"
                                val tempFile = Files.createTempFile(dataDir, "plugin-upload-", ".cs3").toFile()
                                try {
                                    val channel = part.provider()
                                    tempFile.outputStream().use { output ->
                                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                        while (true) {
                                            val read = channel.readAvailable(buffer, 0, buffer.size)
                                            if (read <= 0) break
                                            output.write(buffer, 0, read)
                                        }
                                    }
                                    val targetDir = PluginManager.getLocalPluginPath(dataDir, original)
                                    if (!PluginManager.extractPluginArchive(tempFile, targetDir)) {
                                        error = "Failed to extract plugin"
                                        return@forEachPart
                                    }
                                    val baseData = PluginManager.toLocalPluginData(targetDir)
                                    val loaded = PluginManager.loadPlugin(targetDir, baseData)
                                    if (loaded == null) {
                                        error = "Failed to load plugin"
                                        return@forEachPart
                                    }
                                    storedPlugin = upsertPluginData(configStore, loaded)
                                    storedPath = targetDir.absolutePath
                                } finally {
                                    tempFile.delete()
                                }
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }

                    val pluginData = storedPlugin
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(error ?: "Missing file")
                        )
                    call.respond(PluginUploadResponse(pluginData, storedPath ?: pluginData.filePath))
                }
                delete {
                    val request = call.receive<PluginRemoveRequest>()
                    val removed = removePlugin(configStore, request)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Plugin not found"))
                    call.respond(removed)
                }
                post("/autoupdate") {
                    val updated = autoUpdatePlugins(configStore, dataDir)
                    call.respond(updated)
                }
            }

            route("/extract") {
                post {
                    val request = call.receive<ExtractorRequest>()
                    val result = runExtractor(request.url, request.referer)
                    call.respond(result)
                }
            }

            route("/proxy") {
                post {
                    val request = call.receive<ExtractorRequest>()
                    val index = call.request.queryParameters["index"]?.toIntOrNull()
                    val (links, error) = collectExtractorLinks(request.url, request.referer)
                    val selected = selectProxyLink(links, index)
                    if (selected == null) {
                        val message = error ?: "No extractor links found"
                        return@post call.respond(HttpStatusCode.NotFound, ErrorResponse(message))
                    }
                    call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
                    proxyExtractorLink(call, selected)
                }
            }

            route("/providers") {
                get {
                    call.respond(providerRegistry.listProviders().map { it.toInfo() })
                }
                post("/register") {
                    val request = call.receive<ProviderRegisterRequest>()
                    val api = providerRegistry.registerByClassName(request.className)
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Failed to register provider"))
                    configStore.update { config ->
                        if (!config.providerClasses.contains(request.className)) {
                            config.providerClasses.add(request.className)
                        }
                        config
                    }
                    call.respond(api.toInfo())
                }
                delete("/{name}") {
                    val name = call.parameters["name"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing name"))
                    val api = APIHolder.getApiFromNameNull(name)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))
                    val className = api::class.qualifiedName
                    if (!providerRegistry.removeByName(name)) {
                        return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))
                    }
                    configStore.update { config ->
                        if (className != null) {
                            config.providerClasses.remove(className)
                        }
                        config
                    }
                    call.respond(mapOf("removed" to name))
                }
                get("/{name}/main-pages") {
                    val api = resolveProvider(call.parameters["name"])
                        ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))
                    call.respond(api.mainPage)
                }
                get("/{name}/main-page") {
                    val api = resolveProvider(call.parameters["name"])
                        ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val name = call.request.queryParameters["name"]
                    val data = call.request.queryParameters["data"]
                    val request = buildMainPageRequest(api, name, data)
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing main page data"))
                    val response = runCatching { api.getMainPage(page, request) }.getOrElse { error ->
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse(error.message ?: "Main page failed")
                        )
                    } ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("No main page response")
                    )
                    call.respond(response)
                }
                get("/{name}/search") {
                    val api = resolveProvider(call.parameters["name"])
                        ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))
                    val query = call.request.queryParameters["query"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing query"))
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val response = runCatching { api.search(query, page) }.getOrElse { error ->
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse(error.message ?: "Search failed")
                        )
                    } ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No results"))
                    call.respond(response)
                }
                get("/{name}/quick-search") {
                    val api = resolveProvider(call.parameters["name"])
                        ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))
                    val query = call.request.queryParameters["query"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing query"))
                    val response = runCatching { api.quickSearch(query) }.getOrElse { error ->
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse(error.message ?: "Quick search failed")
                        )
                    } ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No results"))
                    call.respond(response)
                }
                get("/{name}/load") {
                    val api = resolveProvider(call.parameters["name"])
                        ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))
                    val url = call.request.queryParameters["url"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing url"))
                    val response = runCatching { api.load(url) }.getOrElse { error ->
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponse(error.message ?: "Load failed")
                        )
                    } ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No load response"))
                    call.respond(response)
                }
                post("/{name}/links") {
                    val api = resolveProvider(call.parameters["name"])
                        ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Provider not found"))
                    val request = call.receive<LoadLinksRequest>()
                    val response = runLoadLinks(api, request)
                    call.respond(response)
                }
            }
        }
    }.start(wait = true)
}

private fun resolveConfigPath(): Path {
    val envPath = System.getenv("CLOUDSTREAM_SERVER_CONFIG")
    return if (!envPath.isNullOrBlank()) {
        Path.of(envPath)
    } else {
        resolveProjectRoot().resolve("server").resolve("config.json")
    }
}

private fun resolveDataDir(configPath: Path): Path {
    val envPath = System.getenv("CLOUDSTREAM_SERVER_DATA_DIR")
    if (!envPath.isNullOrBlank()) {
        return Path.of(envPath)
    }
    val parent = configPath.toAbsolutePath().parent
    return (parent ?: resolveProjectRoot().resolve("server")).resolve("data")
}

private fun resolveProjectRoot(): Path {
    val cwd = Path.of("").toAbsolutePath().normalize()
    return if (cwd.fileName?.toString().equals("server", ignoreCase = true)) {
        cwd.parent ?: cwd
    } else {
        cwd
    }
}

private fun resolveProvider(name: String?): com.lagradost.cloudstream3.MainAPI? {
    if (name.isNullOrBlank()) return null
    return APIHolder.getApiFromNameNull(name)
}

private fun normalizeRepositories(configStore: ConfigStore) {
    configStore.update { config ->
        val normalized = config.repositories.map { repo ->
            val id = repo.id ?: PluginManager.getPluginSanitizedFileName(repo.url)
            if (repo.id == id) repo else repo.copy(id = id)
        }
        config.repositories.clear()
        config.repositories.addAll(normalized)
        config
    }
}

private fun loadPluginsOnStartup(configStore: ConfigStore) {
    val config = configStore.load()
    val updated = mutableListOf<PluginData>()
    for (plugin in config.plugins.filter { it.enabled }) {
        val pluginDir = File(plugin.filePath)
        if (!pluginDir.exists()) continue
        val loaded = PluginManager.loadPlugin(pluginDir, plugin) ?: continue
        if (loaded != plugin) updated.add(loaded)
    }
    if (updated.isNotEmpty()) {
        configStore.update { current ->
            current.plugins.removeIf { existing -> updated.any { it.filePath == existing.filePath } }
            current.plugins.addAll(updated)
            current
        }
    }
}

private fun findRepository(configStore: ConfigStore, idOrUrl: String): RepositoryData? {
    val repos = configStore.load().repositories
    return repos.firstOrNull { it.id == idOrUrl || it.url == idOrUrl }
}

private fun upsertPluginData(configStore: ConfigStore, plugin: PluginData): PluginData {
    configStore.update { config ->
        config.plugins.removeIf { it.filePath == plugin.filePath }
        config.plugins.add(plugin)
        config
    }
    return plugin
}

private fun findInstalledPluginIfUpToDate(
    configStore: ConfigStore,
    repositoryUrl: String,
    remote: SitePlugin
): PluginData? {
    if (remote.version == PLUGIN_VERSION_ALWAYS_UPDATE) return null
    val local = configStore.load().plugins.firstOrNull {
        it.repositoryUrl == repositoryUrl &&
            it.internalName.equals(remote.internalName, ignoreCase = true)
    } ?: return null
    if (local.version != remote.version) return null
    if (!File(local.filePath).exists()) return null
    return local
}

private fun installPluginFromArchive(
    configStore: ConfigStore,
    archive: File,
    pluginDir: File,
    pluginData: PluginData
): PluginData? {
    PluginManager.unloadPlugin(pluginDir.absolutePath)
    if (!PluginManager.extractPluginArchive(archive, pluginDir)) return null
    val loaded = PluginManager.loadPlugin(pluginDir, pluginData) ?: run {
        PluginManager.deletePluginFile(pluginDir)
        return null
    }
    return upsertPluginData(configStore, loaded)
}

private suspend fun installPluginFromRepo(
    configStore: ConfigStore,
    dataDir: Path,
    repo: RepositoryData,
    internalName: String
): PluginData? {
    val repoPlugins = RepositoryManager.getRepoPlugins(repo.url) ?: return null
    val match = repoPlugins.firstOrNull { (_, plugin) ->
        plugin.internalName.equals(internalName, ignoreCase = true)
    }?.second ?: return null
    findInstalledPluginIfUpToDate(configStore, repo.url, match)?.let { return it }
    val pluginDir = PluginManager.getPluginPath(dataDir, match.internalName, repo.url)
    val tempFile = Files.createTempFile(dataDir, "plugin-download-", ".cs3").toFile()
    return try {
        val downloaded = RepositoryManager.downloadPluginToFile(match.url, tempFile) ?: return null
        val pluginData = PluginManager.toPluginData(match, repo.url, pluginDir)
        installPluginFromArchive(configStore, downloaded, pluginDir, pluginData)
    } finally {
        tempFile.delete()
    }
}

private suspend fun installAllPluginsFromRepo(
    configStore: ConfigStore,
    dataDir: Path,
    repo: RepositoryData
): List<PluginData> {
    val repoPlugins = RepositoryManager.getRepoPlugins(repo.url) ?: return emptyList()
    val installed = mutableListOf<PluginData>()
    for ((_, plugin) in repoPlugins) {
        if (findInstalledPluginIfUpToDate(configStore, repo.url, plugin) != null) continue
        val pluginDir = PluginManager.getPluginPath(dataDir, plugin.internalName, repo.url)
        val tempFile = Files.createTempFile(dataDir, "plugin-download-", ".cs3").toFile()
        try {
            val downloaded = RepositoryManager.downloadPluginToFile(plugin.url, tempFile) ?: continue
            val pluginData = PluginManager.toPluginData(plugin, repo.url, pluginDir)
            installPluginFromArchive(configStore, downloaded, pluginDir, pluginData)?.let { installed.add(it) }
        } finally {
            tempFile.delete()
        }
    }
    return installed
}

private fun removeRepositoryPlugins(
    configStore: ConfigStore,
    dataDir: Path,
    repo: RepositoryData
): Map<String, Any> {
    val removedFiles = mutableListOf<String>()
    val repoFolder = dataDir.resolve(RepositoryManager.ONLINE_PLUGINS_FOLDER)
        .resolve(PluginManager.getPluginSanitizedFileName(repo.url))
        .toFile()
    val removedPlugins = configStore.load().plugins.filter {
        it.repositoryUrl == repo.url ||
            (repoFolder.absolutePath.isNotBlank() && it.filePath.startsWith(repoFolder.absolutePath))
    }
    removedPlugins.forEach { plugin ->
        PluginManager.unloadPlugin(plugin.filePath)
        if (PluginManager.deletePluginFile(File(plugin.filePath))) {
            removedFiles.add(plugin.filePath)
        }
    }
    if (repoFolder.exists()) repoFolder.deleteRecursively()

    configStore.update { config ->
        config.plugins.removeIf {
            it.repositoryUrl == repo.url ||
                (repoFolder.absolutePath.isNotBlank() && it.filePath.startsWith(repoFolder.absolutePath))
        }
        config
    }
    return mapOf("removed" to removedFiles.size, "files" to removedFiles)
}

private fun removePlugin(configStore: ConfigStore, request: PluginRemoveRequest): PluginData? {
    val config = configStore.load()
    val plugin = when {
        !request.filePath.isNullOrBlank() ->
            config.plugins.firstOrNull { it.filePath == request.filePath }
        !request.repositoryUrl.isNullOrBlank() && !request.internalName.isNullOrBlank() ->
            config.plugins.firstOrNull {
                it.repositoryUrl == request.repositoryUrl &&
                    it.internalName.equals(request.internalName, ignoreCase = true)
            }
        !request.internalName.isNullOrBlank() ->
            config.plugins.firstOrNull { it.internalName.equals(request.internalName, ignoreCase = true) }
        else -> null
    } ?: return null

    PluginManager.unloadPlugin(plugin.filePath)
    PluginManager.deletePluginFile(File(plugin.filePath))
    configStore.update { update ->
        update.plugins.removeIf { it.filePath == plugin.filePath }
        update
    }
    return plugin
}

private suspend fun autoUpdatePlugins(configStore: ConfigStore, dataDir: Path): List<PluginData> {
    val config = configStore.load()
    val repoList = config.repositories.filter { it.enabled }
    val repoPlugins = mutableMapOf<String, List<SitePlugin>>()
    for (repo in repoList) {
        val plugins = RepositoryManager.getRepoPlugins(repo.url)?.map { it.second } ?: emptyList()
        repoPlugins[repo.url] = plugins
    }

    val updated = mutableListOf<PluginData>()
    for (plugin in config.plugins.filter { it.isOnline }) {
        val repositoryUrl = plugin.repositoryUrl ?: continue
        val remotePlugins = repoPlugins[repositoryUrl] ?: continue
        val remote = remotePlugins.firstOrNull {
            it.internalName.equals(plugin.internalName, ignoreCase = true)
        } ?: continue
        if (PluginManager.isDisabled(remote)) continue
        if (!PluginManager.shouldUpdate(plugin, remote)) continue
        val pluginDir = PluginManager.getPluginPath(dataDir, remote.internalName, repositoryUrl)
        val tempFile = Files.createTempFile(dataDir, "plugin-download-", ".cs3").toFile()
        try {
            val downloaded = RepositoryManager.downloadPluginToFile(remote.url, tempFile) ?: continue
            val pluginData = PluginManager.toPluginData(remote, repositoryUrl, pluginDir)
            installPluginFromArchive(configStore, downloaded, pluginDir, pluginData)?.let { updated.add(it) }
        } finally {
            tempFile.delete()
        }
    }
    return updated
}

private suspend fun runExtractor(url: String, referer: String?): ExtractorResponse {
    return withContext(Dispatchers.IO) {
        val links = mutableListOf<ExtractorLink>()
        val subtitles = mutableListOf<SubtitleFile>()
        val result = runCatching {
            loadExtractor(
                url = url,
                referer = referer,
                subtitleCallback = { subtitles.add(it) },
                callback = { links.add(it) }
            )
        }
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            return@withContext ExtractorResponse(
                success = false,
                links = links.map { it.toDto() },
                subtitles = subtitles.map { it.toDto() },
                error = error?.message ?: "Extractor failed",
            )
        }
        ExtractorResponse(
            success = result.getOrNull() == true,
            links = links.map { it.toDto() },
            subtitles = subtitles.map { it.toDto() },
        )
    }
}

private suspend fun collectExtractorLinks(
    url: String,
    referer: String?
): Pair<List<ExtractorLink>, String?> {
    return withContext(Dispatchers.IO) {
        val links = mutableListOf<ExtractorLink>()
        val result = runCatching {
            loadExtractor(
                url = url,
                referer = referer,
                subtitleCallback = {},
                callback = { links.add(it) }
            )
        }
        val error = result.exceptionOrNull()?.message
        links to error
    }
}

private fun selectProxyLink(links: List<ExtractorLink>, index: Int?): ExtractorLink? {
    if (links.isEmpty()) return null
    if (index != null) return links.getOrNull(index)
    return sortUrls(links.toSet()).firstOrNull()
}

private suspend fun proxyExtractorLink(call: io.ktor.server.application.ApplicationCall, link: ExtractorLink) {
    val requestHeaders = buildProxyHeaders(link, call.request.headers)
    val connection = withContext(Dispatchers.IO) {
        (URL(link.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "GET"
            requestHeaders.forEach { (key, value) ->
                setRequestProperty(key, value)
            }
            connect()
        }
    }
    val statusCode = connection.responseCode
    call.response.status(HttpStatusCode.fromValue(statusCode))
    connection.contentType?.let { call.response.headers.append(HttpHeaders.ContentType, it) }
    if (connection.contentLengthLong >= 0) {
        call.response.headers.append(HttpHeaders.ContentLength, connection.contentLengthLong.toString())
    }
    connection.getHeaderField("Accept-Ranges")?.let {
        call.response.headers.append(HttpHeaders.AcceptRanges, it)
    }
    connection.getHeaderField("Content-Range")?.let {
        call.response.headers.append(HttpHeaders.ContentRange, it)
    }
    connection.getHeaderField("Content-Disposition")?.let {
        call.response.headers.append(HttpHeaders.ContentDisposition, it)
    }
    connection.getHeaderField("Cache-Control")?.let {
        call.response.headers.append(HttpHeaders.CacheControl, it)
    }

    call.respondOutputStream {
        withContext(Dispatchers.IO) {
            val input = if (statusCode >= 400) {
                connection.errorStream ?: connection.inputStream
            } else {
                connection.inputStream
            }
            input.use { stream ->
                stream.copyTo(this@respondOutputStream)
            }
        }
    }
    connection.disconnect()
}

private fun buildProxyHeaders(
    link: ExtractorLink,
    requestHeaders: io.ktor.http.Headers
): Map<String, String> {
    var headers = link.getAllHeaders()
    if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
        headers = headers + mapOf("User-Agent" to USER_AGENT)
    }
    val range = requestHeaders[HttpHeaders.Range]
    if (range != null && headers.keys.none { it.equals(HttpHeaders.Range, ignoreCase = true) }) {
        headers = headers + mapOf(HttpHeaders.Range to range)
    }
    return headers
}

private suspend fun runLoadLinks(api: com.lagradost.cloudstream3.MainAPI, request: LoadLinksRequest): ExtractorResponse {
    return withContext(Dispatchers.IO) {
        val links = mutableListOf<ExtractorLink>()
        val subtitles = mutableListOf<SubtitleFile>()
        val result = runCatching {
            api.loadLinks(
                data = request.data,
                isCasting = request.isCasting,
                subtitleCallback = { subtitles.add(it) },
                callback = { links.add(it) }
            )
        }
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            return@withContext ExtractorResponse(
                success = false,
                links = links.map { it.toDto() },
                subtitles = subtitles.map { it.toDto() },
                error = error?.message ?: "Load links failed",
            )
        }
        ExtractorResponse(
            success = result.getOrNull() == true,
            links = links.map { it.toDto() },
            subtitles = subtitles.map { it.toDto() },
        )
    }
}

private fun buildMainPageRequest(
    api: com.lagradost.cloudstream3.MainAPI,
    name: String?,
    data: String?
): MainPageRequest? {
    if (!data.isNullOrBlank()) {
        return MainPageRequest(
            name = name ?: data,
            data = data,
            horizontalImages = false
        )
    }
    if (name.isNullOrBlank()) return null
    val entry = api.mainPage.firstOrNull { it.name == name } ?: return null
    return MainPageRequest(
        name = entry.name,
        data = entry.data,
        horizontalImages = entry.horizontalImages
    )
}
