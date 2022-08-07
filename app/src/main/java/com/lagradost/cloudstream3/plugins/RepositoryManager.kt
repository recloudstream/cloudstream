package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.PROVIDER_STATUS_OK
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream


data class Repository(
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String?,
    @JsonProperty("manifestVersion") val manifestVersion: Int,
    @JsonProperty("pluginLists") val pluginLists: List<String>
)

/**
 * Status int as the following:
 * 0: Down
 * 1: Ok
 * 2: Slow
 * 3: Beta only
 * */
data class SitePlugin(
    @JsonProperty("url") val url: String,
    @JsonProperty("status") val status: Int,
    @JsonProperty("version") val version: Int,
    @JsonProperty("apiVersion") val apiVersion: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("authors") val authors: List<String>,
    @JsonProperty("description") val description: String?,
    @JsonProperty("repositoryUrl") val repositoryUrl: String?,
    @JsonProperty("tvTypes") val tvTypes: List<String>?,
    @JsonProperty("language") val language: String?,
    @JsonProperty("iconUrl") val iconUrl: String?,
    @JsonProperty("isAdult") val isAdult: Boolean?,
)


object RepositoryManager {
    const val ONLINE_PLUGINS_FOLDER = "Extensions"

    suspend fun parseRepository(url: String): Repository? {
        return suspendSafeApiCall {
            // Take manifestVersion and such into account later
            app.get(url).parsedSafe()
        }
    }

    private suspend fun parsePlugins(pluginUrls: String): List<SitePlugin> {
        // Take manifestVersion and such into account later
        val response = app.get(pluginUrls)
        // Normal parsed function not working?
//        return response.parsedSafe()
        return tryParseJson<Array<SitePlugin>>(response.text)?.toList() ?: emptyList()
    }

    suspend fun getRepoPlugins(repositoryUrl: String): List<SitePlugin>? {
        val repo = parseRepository(repositoryUrl) ?: return null
        return repo.pluginLists.apmap {
            parsePlugins(it)
        }.flatten()
    }

    suspend fun downloadPluginToFile(context: Context, pluginUrl: String, name: String): File? {
        return suspendSafeApiCall {
            val extensionsDir = File(context.filesDir, ONLINE_PLUGINS_FOLDER)
            if (!extensionsDir.exists())
                extensionsDir.mkdirs()

            val newFile = File(extensionsDir, "$name.${pluginUrl.hashCode()}.cs3")
            if (newFile.exists()) return@suspendSafeApiCall newFile
            newFile.createNewFile()

            val body = app.get(pluginUrl).okhttpResponse.body
            write(body.byteStream(), newFile.outputStream())
            newFile
        }
    }

    // Don't want to read before we write in another thread
    private val repoLock = Mutex()
    suspend fun addRepository(repository: RepositoryData) {
            repoLock.withLock {
                val currentRepos = getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()
                // No duplicates
                if (currentRepos.any { it.url == repository.url }) return
                setKey(REPOSITORIES_KEY, currentRepos + repository)
            }
    }

    suspend fun removeRepository(repository: RepositoryData) {
            repoLock.withLock {
                val currentRepos = getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()
                // No duplicates
                val newRepos = currentRepos.filter { it.url != repository.url }
                setKey(REPOSITORIES_KEY, newRepos)
            }
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