package com.lagradost.cloudstream3.ui

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink

class ErrorLoadingException(message: String) : Exception(message)

class APIRepository(val api: MainAPI) {
    val name : String get() = api.name
    val mainUrl : String get() = api.mainUrl

    suspend fun load(url: String): Resource<LoadResponse> {
        return safeApiCall {
            api.load(url) ?: throw ErrorLoadingException("Error Loading")
        }
    }

    suspend fun search(query: String): Resource<ArrayList<SearchResponse>> {
        return safeApiCall {
            api.search(query) ?: throw ErrorLoadingException("Error Loading")
        }
    }

    suspend fun quickSearch(query: String): Resource<ArrayList<SearchResponse>> {
        return safeApiCall {
            api.quickSearch(query) ?: throw ErrorLoadingException("Error Loading")
        }
    }

    suspend fun getMainPage(): Resource<HomePageResponse> {
        return safeApiCall {
            api.getMainPage() ?: throw ErrorLoadingException("Error Loading")
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