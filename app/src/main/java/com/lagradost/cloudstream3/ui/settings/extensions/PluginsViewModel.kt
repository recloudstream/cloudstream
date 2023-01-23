package com.lagradost.cloudstream3.ui.settings.extensions

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.PROVIDER_STATUS_DOWN
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.PluginManager.getPluginPath
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.io.File

typealias Plugin = Pair<String, SitePlugin>
/**
 * The boolean signifies if the plugin list should be scrolled to the top, used for searching.
 * */
typealias PluginViewDataUpdate = Pair<Boolean, List<PluginViewData>>

class PluginsViewModel : ViewModel() {

    /** plugins is an unaltered list of plugins */
    private var plugins: List<PluginViewData> = emptyList()

    /** filteredPlugins is a subset of plugins following the current search query and tv type selection */
    private var _filteredPlugins = MutableLiveData<PluginViewDataUpdate>()
    var filteredPlugins: LiveData<PluginViewDataUpdate> = _filteredPlugins

    val tvTypes = mutableListOf<String>()
    var languages = listOf<String>()
    private var currentQuery: String? = null

    companion object {
        private val repositoryCache: MutableMap<String, List<Plugin>> = mutableMapOf()
        const val TAG = "PLG"

        private fun isDownloaded(
            context: Context,
            pluginName: String,
            repositoryUrl: String
        ): Boolean {
            return getPluginPath(context, pluginName, repositoryUrl).exists()
        }

        private suspend fun getPlugins(
            repositoryUrl: String,
            canUseCache: Boolean = true
        ): List<Plugin> {
            Log.i(TAG, "getPlugins = $repositoryUrl")
            if (canUseCache && repositoryCache.containsKey(repositoryUrl)) {
                repositoryCache[repositoryUrl]?.let {
                    return it
                }
            }
            return RepositoryManager.getRepoPlugins(repositoryUrl)
                ?.also { repositoryCache[repositoryUrl] = it } ?: emptyList()
        }

        /**
         * @param viewModel optional, updates the plugins livedata for that viewModel if included
         * */
        fun downloadAll(activity: Activity?, repositoryUrl: String, viewModel: PluginsViewModel?) =
            ioSafe {
                if (activity == null) return@ioSafe
                val plugins = getPlugins(repositoryUrl)

                plugins.filter { plugin ->
                    !isDownloaded(
                        activity,
                        plugin.second.internalName,
                        repositoryUrl
                    )
                }.also { list ->
                    main {
                        showToast(
                            activity,
                            if (list.isEmpty()) {
                                txt(
                                    R.string.batch_download_nothing_to_download_format,
                                    txt(R.string.plugin)
                                )
                            } else {
                                txt(
                                    R.string.batch_download_start_format,
                                    list.size,
                                    txt(if (list.size == 1) R.string.plugin_singular else R.string.plugin)
                                )
                            },
                            Toast.LENGTH_SHORT
                        )
                    }
                }.amap { (repo, metadata) ->
                    PluginManager.downloadPlugin(
                        activity,
                        metadata.url,
                        metadata.internalName,
                        repo,
                        metadata.status != PROVIDER_STATUS_DOWN
                    )
                }.main { list ->
                    if (list.any { it }) {
                        showToast(
                            activity,
                            txt(
                                R.string.batch_download_finish_format,
                                list.count { it },
                                txt(if (list.size == 1) R.string.plugin_singular else R.string.plugin)
                            ),
                            Toast.LENGTH_SHORT
                        )
                        viewModel?.updatePluginListPrivate(activity, repositoryUrl)
                    } else if (list.isNotEmpty()) {
                        showToast(activity, R.string.download_failed, Toast.LENGTH_SHORT)
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
        repositoryUrl: String,
        plugin: Plugin,
        isLocal: Boolean
    ) = ioSafe {
        Log.i(TAG, "handlePluginAction = $repositoryUrl, $plugin, $isLocal")

        if (activity == null) return@ioSafe
        val (repo, metadata) = plugin

        val file = if (isLocal) File(plugin.second.url) else getPluginPath(
            activity,
            plugin.second.internalName,
            plugin.first
        )

        val (success, message) = if (file.exists()) {
            PluginManager.deletePlugin(file) to R.string.plugin_deleted
        } else {
            val isEnabled = plugin.second.status != PROVIDER_STATUS_DOWN
            val message = if (isEnabled) R.string.plugin_loaded else R.string.plugin_downloaded
            PluginManager.downloadPlugin(
                activity,
                metadata.url,
                metadata.name,
                repo,
                isEnabled
            ) to message
        }

        runOnMainThread {
            if (success)
                showToast(activity, message, Toast.LENGTH_SHORT)
            else
                showToast(activity, R.string.error, Toast.LENGTH_SHORT)
        }

        if (success)
            if (isLocal)
                updatePluginListLocal()
            else
                updatePluginListPrivate(activity, repositoryUrl)
    }

    private suspend fun updatePluginListPrivate(context: Context, repositoryUrl: String) {
        val plugins = getPlugins(repositoryUrl)
        val list = plugins.map { plugin ->
            PluginViewData(plugin, isDownloaded(context, plugin.second.internalName, plugin.first))
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
            (it.plugin.second.tvTypes?.any { type -> tvTypes.contains(type) } == true) ||
                    (tvTypes.contains("Others") && (it.plugin.second.tvTypes
                        ?: emptyList()).isEmpty())
        }
    }

    private fun List<PluginViewData>.filterLang(): List<PluginViewData> {
        if (languages.isEmpty()) return this
        return this.filter {
            if (it.plugin.second.language == null) {
                return@filter languages.contains("none")
            }
            languages.contains(it.plugin.second.language)
        }
    }

    private fun List<PluginViewData>.sortByQuery(query: String?): List<PluginViewData> {
        return if (query == null) {
            // Return list to base state if no query
            this.sortedBy { it.plugin.second.name }
        } else {
            this.sortedBy { -FuzzySearch.partialRatio(it.plugin.second.name.lowercase(), query.lowercase()) }
        }
    }

    fun updateFilteredPlugins() {
        _filteredPlugins.postValue(
            false to plugins.filterTvTypes().filterLang().sortByQuery(currentQuery)
        )
    }

    fun updatePluginList(context: Context?, repositoryUrl: String) = viewModelScope.launchSafe {
        if (context == null) return@launchSafe
        Log.i(TAG, "updatePluginList = $repositoryUrl")
        updatePluginListPrivate(context, repositoryUrl)
    }

    fun search(query: String?) {
        currentQuery = query
        _filteredPlugins.postValue(
            true to (filteredPlugins.value?.second?.sortByQuery(query) ?: emptyList())
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
                PluginViewData("" to it.toSitePlugin(), true)
            }

        plugins = downloadedPlugins
        _filteredPlugins.postValue(
            false to downloadedPlugins.filterTvTypes().filterLang().sortByQuery(currentQuery)
        )
    }
}