package com.lagradost.cloudstream3.ui.settings.extensions

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.PROVIDER_STATUS_DOWN
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PluginManager.getPluginPath
import com.lagradost.cloudstream3.plugins.PluginWrapper
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.Levenshtein
import java.io.File

/**
 * The boolean signifies if the plugin list should be scrolled to the top, used for searching.
 * */
typealias PluginViewDataUpdate = Pair<Boolean, List<PluginViewData>>

class PluginsViewModel : ViewModel() {

    /** plugins is an unaltered list of plugins */
    private var plugins: List<PluginViewData> = emptyList()
        set(value) {
            // Also set all the plugin languages for easier filtering
            value.map { pluginViewData ->
                val language = pluginViewData.pluginWrapper.plugin.language?.lowercase()
                pluginLanguages.add(
                    when {
                        language.isNullOrBlank() -> "none"
                        else -> language.lowercase()
                    }
                )
                // not sorting as most likely this is a language tag instead of name
            }
            field = value
        }
    var pluginLanguages = mutableSetOf<String>() // set to avoid duplicates

    /** filteredPlugins is a subset of plugins following the current search query and tv type selection */
    private var _filteredPlugins = MutableLiveData<PluginViewDataUpdate>()
    var filteredPlugins: LiveData<PluginViewDataUpdate> = _filteredPlugins

    val tvTypes = mutableListOf<String>()
    var selectedLanguages = listOf<String>()
    private var currentQuery: String? = null

    companion object {
        private val repositoryCache: MutableMap<String, List<PluginWrapper>> = mutableMapOf()
        const val TAG = "PLG"

        private fun isDownloaded(
            context: Context,
            pluginName: String,
            repositoryUrl: String
        ): Boolean {
            return getPluginPath(context, pluginName, repositoryUrl).exists()
        }

        private suspend fun getPlugins(
            repository: RepositoryData,
            canUseCache: Boolean = true
        ): List<PluginWrapper> {
            Log.i(TAG, "getPlugins = $repository")
            if (canUseCache && repositoryCache.containsKey(repository.url)) {
                repositoryCache[repository.url]?.let {
                    return it
                }
            }

            return RepositoryManager.getRepoPlugins(repository)
                ?.also { repositoryCache[repository.url] = it } ?: emptyList()
        }

        /**
         * @param viewModel optional, updates the plugins livedata for that viewModel if included
         * */
        fun downloadAll(activity: Activity?, repository: RepositoryData, viewModel: PluginsViewModel?) =
            ioSafe {
                if (activity == null) return@ioSafe
                val plugins = getPlugins(repository)

                plugins.filter { pluginWrapper ->
                    !isDownloaded(
                        activity,
                        pluginWrapper.plugin.internalName,
                        repository.url
                    )
                }.also { list ->
                    main {
                        showToast(
                            when {
                                // No plugins at all
                                plugins.isEmpty() -> txt(
                                    R.string.no_plugins_found_error,
                                )
                                // All plugins downloaded
                                list.isEmpty() -> txt(
                                    R.string.batch_download_nothing_to_download_format,
                                    txt(R.string.plugin)
                                )

                                else -> txt(
                                    R.string.batch_download_start_format,
                                    list.size,
                                    txt(if (list.size == 1) R.string.plugin_singular else R.string.plugin)
                                )
                            },
                            Toast.LENGTH_SHORT
                        )
                    }
                }.amap { (_, repo, metadata) ->
                    PluginManager.downloadPlugin(
                        activity,
                        metadata.url,
                        metadata.fileHash,
                        metadata.internalName,
                        repo.url,
                        metadata.status != PROVIDER_STATUS_DOWN
                    )
                }.main { list ->
                    if (list.any { it }) {
                        showToast(
                            txt(
                                R.string.batch_download_finish_format,
                                list.count { it },
                                txt(if (list.size == 1) R.string.plugin_singular else R.string.plugin)
                            ),
                            Toast.LENGTH_SHORT
                        )
                        viewModel?.updatePluginListPrivate(activity, listOf(repository))
                    } else if (list.isNotEmpty()) {
                        showToast(R.string.download_failed, Toast.LENGTH_SHORT)
                    }
                }
            }
    }

    /**
     * @param isLocal defines if the plugin data is from local data instead of repo
     * Will only allow removal of plugins. Used for the local file management.
     * */
    fun handlePluginAction(
        activity: Activity?,
        repositoryUrls: List<RepositoryData>,
        pluginWrapper: PluginWrapper,
        isLocal: Boolean
    ) = ioSafe {
        Log.i(TAG, "handlePluginAction = ${repositoryUrls}, $pluginWrapper, $isLocal")

        if (activity == null) return@ioSafe
        val (_, repositoryData, metadata) = pluginWrapper

        val file = if (isLocal) File(pluginWrapper.plugin.url) else getPluginPath(
            activity,
            pluginWrapper.plugin.internalName,
            pluginWrapper.repositoryData.url
        )

        val (success, message) = if (file.exists()) {
            PluginManager.deletePlugin(file) to R.string.plugin_deleted
        } else {
            val isEnabled = pluginWrapper.plugin.status != PROVIDER_STATUS_DOWN
            val message = if (isEnabled) R.string.plugin_loaded else R.string.plugin_downloaded
            PluginManager.downloadPlugin(
                activity,
                metadata.url,
                metadata.fileHash,
                metadata.internalName,
                repositoryData.url,
                isEnabled
            ) to message
        }

        runOnMainThread {
            if (success)
                showToast(message, Toast.LENGTH_SHORT)
            else
                showToast(R.string.error, Toast.LENGTH_SHORT)
        }

        if (success)
            if (isLocal)
                updatePluginListLocal()
            else
                updatePluginListPrivate(activity, repositoryUrls)
    }

    private suspend fun updatePluginListPrivate(context: Context, repositories: List<RepositoryData>) {
        val isAdult = PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(context.getString(R.string.prefer_media_type_key), emptySet())
            ?.contains(TvType.NSFW.ordinal.toString()) == true

        val plugins = repositories.flatMap { repositoryUrl ->
            getPlugins(repositoryUrl)
        }

        val list = plugins.filter {
            // Show all non-nsfw plugins or all if nsfw is enabled
            it.plugin.tvTypes?.contains(TvType.NSFW.name) != true || isAdult
        }.map { plugin ->
            PluginViewData(plugin, isDownloaded(context, plugin.plugin.internalName, plugin.repositoryData.url))
        }

        this.plugins = list
        _filteredPlugins.postValue(
            false to list.filterTvTypes().filterLang().sortByQuery(currentQuery)
        )
    }

    // Perhaps can be optimized?
    private fun List<PluginViewData>.filterTvTypes(): List<PluginViewData> {
        if (tvTypes.isEmpty()) return this
        return this.filter {
            (it.pluginWrapper.plugin.tvTypes?.any { type -> tvTypes.contains(type) } == true) ||
                    (tvTypes.contains(TvType.Others.name) && (it.pluginWrapper.plugin.tvTypes
                        ?: emptyList()).isEmpty())
        }
    }

    private fun List<PluginViewData>.filterLang(): List<PluginViewData> {
        if (selectedLanguages.isEmpty()) return this // do not filter
        return this.filter {
            if (it.pluginWrapper.plugin.language == null) {
                return@filter selectedLanguages.contains("none")
            }
            selectedLanguages.contains(it.pluginWrapper.plugin.language?.lowercase())
        }
    }

    private fun List<PluginViewData>.sortByQuery(query: String?): List<PluginViewData> {
        return if (query.isNullOrBlank()) {
            // Return list to base state if no query
            this.sortedBy { it.pluginWrapper.plugin.name }
        } else {
            this.mapNotNull {
                // Try matching name
                val score = Levenshtein.partialRatio(
                    it.pluginWrapper.plugin.name.lowercase(),
                    query.lowercase()
                ).takeIf { score -> score > 80 } ?:
                // Fallback to description, but limit characters to reduce lag
                it.pluginWrapper.plugin.description?.lowercase()?.take(64)
                    ?.let { description ->
                        Levenshtein.partialRatio(
                            description,
                            query.lowercase()
                        )
                    }?.takeIf { score -> score > 80 } ?: return@mapNotNull null
                it to score
            }.sortedBy {
                -it.second
            }.map { it.first }
        }
    }

    fun updateFilteredPlugins() {
        _filteredPlugins.postValue(
            false to plugins.filterTvTypes().filterLang().sortByQuery(currentQuery)
        )
    }

    fun clear() {
        currentQuery = null
        _filteredPlugins.postValue(
            false to emptyList()
        )
    }

    fun updatePluginList(context: Context?, repositories: List<RepositoryData>) =
        viewModelScope.launchSafe {
            if (context == null) return@launchSafe
            Log.i(TAG, "updatePluginList = $repositories")
            updatePluginListPrivate(context, repositories)
        }

    fun search(query: String?) {
        currentQuery = query
        _filteredPlugins.postValue(
            true to plugins.filterTvTypes().filterLang().sortByQuery(query)
        )
    }

    /**
     * Update the list but only with the local data. Used for file management.
     * */
    fun updatePluginListLocal() = viewModelScope.launchSafe {
        Log.i(TAG, "updatePluginList = local")

        val downloadedPlugins = (PluginManager.getPluginsOnline() + PluginManager.getPluginsLocal())
            .distinctBy { it.filePath }
            .map {
                PluginViewData(PluginWrapper.getLocalPluginWrapper(it.toSitePlugin()), true)
            }

        plugins = downloadedPlugins
        _filteredPlugins.postValue(
            false to downloadedPlugins.filterTvTypes().filterLang().sortByQuery(currentQuery)
        )
    }
}
