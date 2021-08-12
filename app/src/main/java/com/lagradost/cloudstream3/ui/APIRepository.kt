package com.lagradost.cloudstream3.ui

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink

class APIRepository(val api: MainAPI) {
    companion object {
        var providersActive = HashSet<String>()
    }

    val name: String get() = api.name
    val mainUrl: String get() = api.mainUrl
    val hasQuickSearch: Boolean  get() = api.hasQuickSearch

    suspend fun load(url: String): Resource<LoadResponse> {
        return safeApiCall {
            // remove suffix for some slugs to handle correctly
            api.load(url.removeSuffix("/")) ?: throw ErrorLoadingException()
        }
    }

    suspend fun search(query: String): Resource<ArrayList<SearchResponse>> {
        return safeApiCall {
            api.search(query) ?: throw ErrorLoadingException()
        }
    }

    suspend fun quickSearch(query: String): Resource<ArrayList<SearchResponse>> {
        return safeApiCall {
            api.quickSearch(query) ?: throw ErrorLoadingException()
        }
    }

    suspend fun getMainPage(): Resource<HomePageResponse> {
        return safeApiCall {
            api.getMainPage() ?: throw ErrorLoadingException()
        }
    }

    fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return normalSafeApiCall { api.loadLinks(data, isCasting, subtitleCallback, callback) } ?: false
    }
}