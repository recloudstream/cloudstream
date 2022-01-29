package com.lagradost.cloudstream3.ui

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink

class APIRepository(val api: MainAPI) {
    companion object {
        var dubStatusActive = HashSet<DubStatus>()

        val noneApi = object : MainAPI() {
            override val name = "None"
            override val supportedTypes = emptySet<TvType>()
        }
        val randomApi = object : MainAPI() {
            override val name = "Random"
            override val supportedTypes = emptySet<TvType>()
        }

        fun isInvalidData(data : String): Boolean {
            return data.isEmpty() || data == "[]" || data == "about:blank"
        }
    }

    val hasMainPage = api.hasMainPage
    val name = api.name
    val mainUrl = api.mainUrl
    val hasQuickSearch = api.hasQuickSearch

    suspend fun load(url: String): Resource<LoadResponse> {
        if(isInvalidData(url)) throw ErrorLoadingException()

        return safeApiCall {
            api.load(api.fixUrl(url)) ?: throw ErrorLoadingException()
        }
    }

    suspend fun search(query: String): Resource<List<SearchResponse>> {
        if (query.isEmpty())
            return Resource.Success(emptyList())

        return safeApiCall {
            return@safeApiCall (api.search(query)
                ?: throw ErrorLoadingException())
//                .filter { typesActive.contains(it.type) }
                .toList()
        }
    }

    suspend fun quickSearch(query: String): Resource<List<SearchResponse>> {
        if (query.isEmpty())
            return Resource.Success(emptyList())

        return safeApiCall {
            api.quickSearch(query) ?: throw ErrorLoadingException()
        }
    }

    suspend fun getMainPage(): Resource<HomePageResponse?> {
        return safeApiCall {
            api.getMainPage() ?: throw ErrorLoadingException()
        }
    }

    suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (isInvalidData(data)) return false // this makes providers cleaner
        return try {
            api.loadLinks(data, isCasting, subtitleCallback, callback)
        } catch (throwable: Throwable) {
            logError(throwable)
            return false
        }
    }
}