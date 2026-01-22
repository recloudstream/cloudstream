package com.lagradost.cloudstream3.plugins

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.Repository
import com.lagradost.cloudstream3.SitePlugin
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object RepositoryManager {
    const val ONLINE_PLUGINS_FOLDER = "Extensions"
    private val GH_REGEX =
        Regex("^https://raw.githubusercontent.com/([A-Za-z0-9-]+)/([A-Za-z0-9_.-]+)/(.*)$")
    var useJsdelivr: Boolean = false

    fun convertRawGitUrl(url: String): String {
        if (!useJsdelivr) return url
        val match = GH_REGEX.find(url) ?: return url
        val (user, repo, rest) = match.destructured
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$rest"
    }

    suspend fun parseRepoUrl(url: String): String? {
        val fixedUrl = url.trim()
        return if (fixedUrl.contains("^https?://".toRegex())) {
            fixedUrl
        } else if (fixedUrl.contains("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)".toRegex())) {
            fixedUrl.replace("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)".toRegex(), "").let {
                if (!it.contains("^https?://".toRegex())) "https://${it}" else fixedUrl
            }
        } else if (fixedUrl.matches("^[a-zA-Z0-9!_-]+$".toRegex())) {
            runCatching {
                app.get("https://cutt.ly/${fixedUrl}", allowRedirects = false)
                    .headers["Location"]?.let { target ->
                        if (target.startsWith("https://cutt.ly/404")) null
                        else if (target.removeSuffix("/") == "https://cutt.ly") null
                        else target
                    }
            }.getOrNull()
        } else {
            null
        }
    }

    suspend fun parseRepository(url: String): Repository? {
        return runCatching { app.get(convertRawGitUrl(url)).parsedSafe<Repository>() }.getOrNull()
    }

    private suspend fun parsePlugins(pluginUrls: String): List<SitePlugin> {
        return try {
            val response = app.get(convertRawGitUrl(pluginUrls))
            tryParseJson<Array<SitePlugin>>(response.text)?.toList() ?: emptyList()
        } catch (t: Throwable) {
            Log.e("RepositoryManager", "Failed to parse plugins: ${t.message}")
            emptyList()
        }
    }

    suspend fun getRepoPlugins(repositoryUrl: String): List<Pair<String, SitePlugin>>? {
        val repo = parseRepository(repositoryUrl) ?: return null
        return repo.pluginLists.flatMap { url ->
            parsePlugins(url).map { repositoryUrl to it }
        }
    }

    suspend fun downloadPluginToFile(pluginUrl: String, file: File): File? {
        return runCatching {
            file.parentFile?.mkdirs()
            if (file.exists()) file.delete()
            file.createNewFile()
            val body = app.get(convertRawGitUrl(pluginUrl)).okhttpResponse.body
            write(body.byteStream(), file.outputStream())
            file
        }.getOrNull()
    }

    private fun write(stream: InputStream, output: OutputStream) {
        val input = BufferedInputStream(stream)
        val dataBuffer = ByteArray(512)
        var readBytes: Int
        while (input.read(dataBuffer).also { readBytes = it } != -1) {
            output.write(dataBuffer, 0, readBytes)
        }
    }
}
