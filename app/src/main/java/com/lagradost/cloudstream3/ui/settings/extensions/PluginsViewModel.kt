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
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.Levenshtein
import java.io.File

// String => repository url
typealias Plugin = Pair<String, SitePlugin>
/**
 * The boolean signifies if the plugin list should be scrolled to the top, used for searching.
 * */
typealias PluginViewDataUpdate = Pair<Boolean, List<PluginViewData>>

class PluginsViewModel : ViewModel() {

    /** plugins is an unaltered list of plugins */
    private var plugins: List<PluginViewData> = emptyList()
        set(value) {
            // Also set all the plugin languages for easier filtering
            value.forEach { pluginViewData ->
                val language = pluginViewData.plugin.second.language?.lowercase()
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

    val selectedPlugins = mutableSetOf<String>() // Set of plugin names/urls
    private var isSelectionMode = false

    fun toggleSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) selectedPlugins.clear()
        updateFilteredPlugins()
    }

    fun toggleSelection(pluginUrl: String) {
        if (selectedPlugins.contains(pluginUrl)) {
            selectedPlugins.remove(pluginUrl)
        } else {
            selectedPlugins.add(pluginUrl)
        }
        updateFilteredPlugins()
    }

    fun batchAction(activity: Activity?, action: BatchAction) = ioSafe {
        if (activity == null) return@ioSafe
        val pluginsToProcess = plugins.filter { selectedPlugins.contains(it.plugin.second.url) }
        
        pluginsToProcess.amap { data ->
            val (repo, metadata) = data.plugin
            val file = if (metadata.url.startsWith("http")) getPluginPath(activity, metadata.internalName, repo) else File(metadata.url)
            val exists = file.exists()

            when (action) {
                BatchAction.Download -> {
                    if (!exists) {
                        PluginManager.downloadPlugin(
                            activity = activity,
                            pluginUrl = metadata.url,
                            pluginHash = metadata.fileHash,
                            internalName = metadata.internalName,
                            repositoryUrl = repo,
                            loadPlugin = true
                        )
                    }
                }
                BatchAction.Delete -> {
                    if (exists) {
                        PluginManager.deletePlugin(file)
                    }
                }
                BatchAction.Disable -> {
                    if (exists) {
                        PluginManager.setPluginDisabled(file.absolutePath, true)
                    }
                }
                BatchAction.Enable -> {
                    if (exists) {
                        PluginManager.setPluginDisabled(file.absolutePath, false)
                        PluginManager.loadSinglePluginByPath(activity, file.absolutePath)
                    }
                }
                BatchAction.MoveToFolder -> {
                    // Logic handled in Fragment with folder picker
                }
            }
        }

        toggleSelectionMode(false)
        runOnMainThread {
            updatePluginListLocal() // Refresh
        }
    }

    enum class BatchAction {
        Download, Delete, Disable, Enable, MoveToFolder
    }

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

        private fun isLocalDisabled(
            pluginName: String,
            repositoryUrl: String,
            activity: Activity? = null
        ): Boolean {
            val path = activity?.let { getPluginPath(it, pluginName, repositoryUrl).absolutePath }
                ?: return false
            return PluginManager.getDisabledPlugins().contains(path)
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
                }.amap { (repo, metadata) ->
                    PluginManager.downloadPlugin(
                        activity,
                        metadata.url,
                        metadata.fileHash,
                        metadata.internalName,
                        repo,
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
                        viewModel?.updatePluginListPrivate(activity, repositoryUrl)
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

        val isDisabled = PluginManager.getDisabledPlugins().contains(file.absolutePath)

        val (success, message) = if (file.exists()) {
            if (isDisabled) {
                PluginManager.setPluginDisabled(file.absolutePath, false)
                PluginManager.loadSinglePluginByPath(activity, file.absolutePath)
                true to R.string.plugin_loaded
            } else {
                PluginManager.deletePlugin(file) to R.string.plugin_deleted
            }
        } else {
            val isEnabled = plugin.second.status != PROVIDER_STATUS_DOWN
            val message = if (isEnabled) R.string.plugin_loaded else R.string.plugin_downloaded
            PluginManager.downloadPlugin(
                activity,
                metadata.url,
                metadata.fileHash,
                metadata.internalName,
                repo,
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
                updatePluginListPrivate(activity, repositoryUrl)
    }

    private suspend fun updatePluginListPrivate(context: Context, repositoryUrl: String) {
        val isAdult = PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(context.getString(R.string.prefer_media_type_key), emptySet())
            ?.contains(TvType.NSFW.ordinal.toString()) == true

        val plugins = if (repositoryUrl.startsWith("folder://")) {
            val folderName = repositoryUrl.removePrefix("folder://")
            val pluginNames = DataStoreHelper.getExtensionFolders()[folderName] ?: emptyList()
            (PluginManager.getPluginsOnline() + PluginManager.getPluginsLocal())
                .filter { pluginNames.contains(it.internalName) }
                .map { "" to it.toSitePlugin() }
        } else {
            getPlugins(repositoryUrl)
        }

        val list = plugins.filter {
            // Show all non-nsfw plugins or all if nsfw is enabled
            (it.second.tvTypes?.contains(TvType.NSFW.name) != true) || isAdult
        }.map { plugin ->
            PluginViewData(
                plugin,
                isDownloaded(context, plugin.second.internalName, plugin.first),
                isLocalDisabled(plugin.second.internalName, plugin.first, context as? Activity)
            )
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
                    (tvTypes.contains(TvType.Others.name) && (it.plugin.second.tvTypes
                        ?: emptyList()).isEmpty())
        }
    }

    private fun List<PluginViewData>.filterLang(): List<PluginViewData> {
        if (selectedLanguages.isEmpty()) return this // do not filter
        return this.filter {
            if (it.plugin.second.language == null) {
                return@filter selectedLanguages.contains("none")
            }
            selectedLanguages.contains(it.plugin.second.language?.lowercase())
        }
    }

    private fun List<PluginViewData>.sortByQuery(query: String?): List<PluginViewData> {
        return if (query == null) {
            // Return list to base state if no query
            this.sortedBy { it.plugin.second.name }
        } else {
            this.sortedBy {
                -Levenshtein.partialRatio(
                    it.plugin.second.name.lowercase(),
                    query.lowercase()
                )
            }
        }
    }

    private fun List<PluginViewData>.applySelection(): List<PluginViewData> {
        return this.map { 
            it.copy(
                isSelected = selectedPlugins.contains(it.plugin.second.url),
                isInSelectionMode = isSelectionMode
            )
        }
    }

    fun updateFilteredPlugins() {
        _filteredPlugins.postValue(
            false to plugins.filterTvTypes().filterLang().sortByQuery(currentQuery).applySelection()
        )
    }

    fun clear() {
        currentQuery = null
        _filteredPlugins.postValue(
            false to emptyList()
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
    fun updatePluginListLocal(filterDisabled: Boolean = false, folderName: String? = null) = viewModelScope.launchSafe {
        Log.i(TAG, "updatePluginList = local, filterDisabled = $filterDisabled, folderName = $folderName")

        val disabled = PluginManager.getDisabledPlugins()
        val folderPlugins = folderName?.let { DataStoreHelper.getExtensionFolders()[it] }
        
        val downloadedPlugins = (PluginManager.getPluginsOnline() + PluginManager.getPluginsLocal())
            .distinctBy { it.filePath }
            .filter { !filterDisabled || it.filePath in disabled }
            .filter { folderPlugins == null || folderPlugins.contains(it.internalName) }
            .map {
                PluginViewData(
                    "" to it.toSitePlugin(),
                    true,
                    it.filePath in disabled
                )
            }

        plugins = downloadedPlugins
        _filteredPlugins.postValue(
            false to downloadedPlugins.filterTvTypes().filterLang().sortByQuery(currentQuery)
        )
    }
}
