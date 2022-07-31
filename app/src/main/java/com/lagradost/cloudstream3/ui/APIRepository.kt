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
            override var name = "None"
            override val supportedTypes = emptySet<TvType>()
            override var lang = ""
        }
        val randomApi = object : MainAPI() {
            override var name = "Random"
            override val supportedTypes = emptySet<TvType>()
            override var lang = ""
        }

        fun isInvalidData(data: String): Boolean {
            return data.isEmpty() || data == "[]" || data == "about:blank"
        }
    }

    val hasMainPage = api.hasMainPage
    val name = api.name
    val mainUrl = api.mainUrl
    val mainPage = api.mainPage
    val hasQuickSearch = api.hasQuickSearch

    suspend fun load(url: String): Resource<LoadResponse> {
        return safeApiCall {
            if (isInvalidData(url)) throw ErrorLoadingException()
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

    suspend fun getMainPage(page: Int, nameIndex: Int? = null): Resource<List<HomePageResponse?>> {
        return safeApiCall {
            nameIndex?.let { api.mainPage.getOrNull(it) }?.let { data ->
                listOf(api.getMainPage(page, MainPageRequest(data.name, data.data)))
            } ?: api.mainPage.apmap { data ->
                api.getMainPage(page, MainPageRequest(data.name, data.data))
            }
        }
    }

    suspend fun extractorVerifierJob(extractorData: String?) {
        safeApiCall {
            api.extractorVerifierJob(extractorData)
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