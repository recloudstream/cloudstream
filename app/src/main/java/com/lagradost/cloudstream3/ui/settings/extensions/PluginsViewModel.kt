package com.lagradost.cloudstream3.ui.settings.extensions

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import kotlinx.coroutines.launch

typealias Plugin = Pair<String, SitePlugin>

class PluginsViewModel : ViewModel() {
    private val _plugins = MutableLiveData<List<PluginViewData>>()
    val plugins: LiveData<List<PluginViewData>> = _plugins

    companion object {
        private val repositoryCache: MutableMap<String, List<Plugin>> = mutableMapOf()
        const val TAG = "PLG"
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

    private fun getStoredPlugins(): Array<PluginData> {
        return PluginManager.getPluginsOnline()
    }

    private fun getDownloads(): Set<String> {
        return getStoredPlugins().map { it.internalName }.toSet()
    }

    private fun isDownloaded(plugin: Plugin, data: Set<String>? = null): Boolean {
        return (data ?: getDownloads()).contains(plugin.second.internalName)
    }

    fun downloadAll(activity: Activity?, repositoryUrl: String) = ioSafe {
        if (activity == null) return@ioSafe
        val stored = getDownloads()
        val plugins = getPlugins(repositoryUrl)

        plugins.filter { plugin -> !isDownloaded(plugin, stored) }.also { list ->
            main {
                showToast(
                    activity,
                    if (list.isEmpty()) {
                        txt(
                            R.string.batch_download_nothing_to_download_format,
                            txt(R.string.plugin)
                        )
                    } else {
                        txt(R.string.batch_download_start_format, list.size, txt(if(list.size == 1) R.string.plugin_singular else R.string.plugin))
                    },
                    Toast.LENGTH_SHORT
                )
            }
        }.apmap { (repo, metadata) ->
            PluginManager.downloadAndLoadPlugin(
                activity,
                metadata.url,
                metadata.name,
                repo
            )
        }.main { list ->
            if (list.any { it }) {
                showToast(
                    activity,
                    txt(
                        R.string.batch_download_finish_format,
                        list.count { it },
                        txt(if(list.size == 1) R.string.plugin_singular else R.string.plugin)
                    ),
                    Toast.LENGTH_SHORT
                )
                updatePluginListPrivate(repositoryUrl)
            } else if (list.isNotEmpty()) {
                showToast(activity, R.string.download_failed, Toast.LENGTH_SHORT)
            }
        }
    }

    fun handlePluginAction(activity: Activity?, repositoryUrl: String, plugin: Plugin) = ioSafe {
        Log.i(TAG, "handlePluginAction = $repositoryUrl, $plugin")

        if (activity == null) return@ioSafe
        val (repo, metadata) = plugin

        val (success, message) = if (isDownloaded(plugin)) {
            PluginManager.deletePlugin(
                metadata.url,
            ) to R.string.plugin_deleted
        } else {
            PluginManager.downloadAndLoadPlugin(
                activity,
                metadata.url,
                metadata.name,
                repo
            ) to R.string.plugin_loaded
        }

        runOnMainThread {
            if (success)
                showToast(activity, message, Toast.LENGTH_SHORT)
            else
                showToast(activity, R.string.error, Toast.LENGTH_SHORT)
        }

        if (success)
            updatePluginListPrivate(repositoryUrl)
    }

    private suspend fun updatePluginListPrivate(repositoryUrl: String) {
        val stored = getDownloads()
        val plugins = getPlugins(repositoryUrl)
        val list = plugins.map { plugin ->
            PluginViewData(plugin, isDownloaded(plugin, stored))
        }

        _plugins.postValue(list)
    }

    fun updatePluginList(repositoryUrl: String) = viewModelScope.launch {
        Log.i(TAG, "updatePluginList = $repositoryUrl")
        updatePluginListPrivate(repositoryUrl)
    }
}