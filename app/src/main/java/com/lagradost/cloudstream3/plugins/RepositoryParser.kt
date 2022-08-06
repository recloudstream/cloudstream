package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
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

data class SitePlugin(
    @JsonProperty("url") val url: String,
    @JsonProperty("tvTypes") val tvTypes: List<String>?,
    @JsonProperty("version") val version: Int,
    @JsonProperty("apiVersion") val apiVersion: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("authors") val authors: List<String>,
    @JsonProperty("description") val description: String?,
    @JsonProperty("repositoryUrl") val repositoryUrl: String?,
    @JsonProperty("language") val language: String?,
    @JsonProperty("iconUrl") val iconUrl: String?
)


object RepositoryParser {
    private suspend fun parseRepository(url: String): Repository? {
        return suspendSafeApiCall {
            // Take manifestVersion and such into account later
            app.get(url).parsedSafe()
        }
    }

    private suspend fun parsePlugins(pluginUrls: String): ArrayList<SitePlugin>? {
        // Take manifestVersion and such into account later
        val response = app.get(pluginUrls)
        // Normal parsed function not working?
//        return response.parsedSafe()
        return tryParseJson<ArrayList<SitePlugin>>(response.text)
    }

    suspend fun getRepoPlugins(repositoryUrl: String): List<SitePlugin>? {
        val repo = parseRepository(repositoryUrl) ?: return null
        return repo.pluginLists.apmap {
            parsePlugins(it)
        }.filterNotNull().flatten()
    }

    private suspend fun downloadSiteTemp(context: Context, pluginUrl: String, name: String): File? {
        return suspendSafeApiCall {
            val dir = context.cacheDir
            val file = File.createTempFile(name, ".cs3", dir)
            val body = app.get(pluginUrl).okhttpResponse.body
            write(body.byteStream(), file.outputStream())
            file
        }
    }

    suspend fun loadSiteTemp(context: Context, pluginUrl: String, name: String) {
        val file = downloadSiteTemp(context, pluginUrl, name)
        PluginManager.loadPlugin(context, file ?: return)
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