package com.lagradost.cloudstream3.plugins

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream4.desktop.data.AppData
import com.lagradost.cloudstream4.desktop.data.RepositoryEntry
import com.lagradost.cloudstream4.desktop.plugins.PluginManager

/** Same shape as the Android app's Repository, for extensions that read repositories themselves */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Repository(
    val iconUrl: String?,
    val name: String,
    val description: String?,
    val manifestVersion: Int,
    val pluginLists: List<String>,
)

/**
 * Desktop stand-in for the Android app's RepositoryManager, backed by the desktop [PluginManager].
 * Only the functions extensions are known to call are provided, with the same binary signatures.
 */
@Suppress("unused")
object RepositoryManager {
    suspend fun parseRepoUrl(url: String): String? = PluginManager.parseRepoUrl(url)

    suspend fun parseRepository(url: String): Repository? = PluginManager.fetchRepository(url)?.let {
        Repository(it.iconUrl, it.name, it.description, it.manifestVersion, it.pluginLists)
    }

    fun getRepositories(): Array<RepositoryData> =
        AppData.repositories.value.map { RepositoryData(it.iconUrl, it.name, it.url) }.toTypedArray()

    suspend fun addRepository(repository: RepositoryData) {
        AppData.addRepository(RepositoryEntry(repository.name, repository.url, repository.iconUrl))
    }

    suspend fun removeRepository(@Suppress("UNUSED_PARAMETER") context: android.content.Context, repository: RepositoryData) {
        PluginManager.removeRepository(repository.url)
    }
}
