package com.lagradost.cloudstream3.ui.settings.extensions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.SitePlugin

data class RepositoryData(
    @JsonProperty("name") val name: String,
    @JsonProperty("url") val url: String
)

const val REPOSITORIES_KEY = "REPOSITORIES_KEY"

class ExtensionsViewModel : ViewModel() {
    private val _repositories = MutableLiveData<Array<RepositoryData>>()
    val repositories: LiveData<Array<RepositoryData>> = _repositories

    fun loadRepositories() {
        // Crashes weirdly with List<RepositoryData>
        val urls = getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()
        _repositories.postValue(urls)
    }

    suspend fun getPlugins(repositoryUrl: String): List<Pair<String, SitePlugin>> {
        return RepositoryManager.getRepoPlugins(repositoryUrl) ?: emptyList()
    }
}