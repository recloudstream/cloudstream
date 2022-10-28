package com.lagradost.cloudstream3.ui

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.delay

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

        private val cacheHash: HashMap<Pair<String,String>, LoadResponse> = hashMapOf()
    }

    val hasMainPage = api.hasMainPage
    val name = api.name
    val mainUrl = api.mainUrl
    val mainPage = api.mainPage
    val hasQuickSearch = api.hasQuickSearch
    val vpnStatus = api.vpnStatus
    val providerType = api.providerType

    suspend fun load(url: String): Resource<LoadResponse> {
        return safeApiCall {
            if (isInvalidData(url)) throw ErrorLoadingException()
            val fixedUrl = api.fixUrl(url)
            val key = Pair(api.name,url)
            cacheHash[key] ?: api.load(fixedUrl)?.also {
                // we cache 20 responses because ppl often go back to the same shit + 20 because I dont want to cause too much memory leak
                if (cacheHash.size > 20) cacheHash.remove(cacheHash.keys.random())
                cacheHash[key] = it
            } ?: throw ErrorLoadingException()
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

    suspend fun waitForHomeDelay() {
        val delta = api.sequentialMainPageScrollDelay + api.lastHomepageRequest - unixTimeMS
        if (delta < 0) return
        delay(delta)
    }

    suspend fun getMainPage(page: Int, nameIndex: Int? = null): Resource<List<HomePageResponse?>> {
        return safeApiCall {
            api.lastHomepageRequest = unixTimeMS

            nameIndex?.let { api.mainPage.getOrNull(it) }?.let { data ->
                listOf(
                    api.getMainPage(
                        page,
                        MainPageRequest(data.name, data.data, data.horizontalImages)
                    )
                )
            } ?: run {
                if (api.sequentialMainPage) {
                    var first = true
                    api.mainPage.map { data ->
                        if (!first) // dont want to sleep on first request
                            delay(api.sequentialMainPageDelay)
                        first = false

                        api.getMainPage(
                            page,
                            MainPageRequest(data.name, data.data, data.horizontalImages)
                        )
                    }
                } else {
                    api.mainPage.apmap { data ->
                        api.getMainPage(
                            page,
                            MainPageRequest(data.name, data.data, data.horizontalImages)
                        )
                    }
                }
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