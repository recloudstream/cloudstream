package com.lagradost.cloudstream3.ui

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.async
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

        data class SavedLoadResponse(
            val unixTime: Long,
            val response: LoadResponse,
            val hash: Pair<String, String>
        )

        private val cache = threadSafeListOf<SavedLoadResponse>()
        private var cacheIndex: Int = 0
        const val cacheSize = 20
    }

    private fun afterPluginsLoaded(forceReload: Boolean) {
        if (forceReload) {
            synchronized(cache) {
                cache.clear()
            }
        }
    }

    init {
        afterPluginsLoadedEvent += ::afterPluginsLoaded
    }

    val hasMainPage = api.hasMainPage
    val providerType = api.providerType
    val name = api.name
    val mainUrl = api.mainUrl
    val mainPage = api.mainPage
    val hasQuickSearch = api.hasQuickSearch
    val vpnStatus = api.vpnStatus

    suspend fun load(url: String): Resource<LoadResponse> {
        return safeApiCall {
            if (isInvalidData(url)) throw ErrorLoadingException()
            val fixedUrl = api.fixUrl(url)
            val lookingForHash = Pair(api.name, fixedUrl)

            synchronized(cache) {
                for (item in cache) {
                    // 10 min save
                    if (item.hash == lookingForHash && (unixTime - item.unixTime) < 60 * 10) {
                        return@safeApiCall item.response
                    }
                }
            }

            api.load(fixedUrl)?.also { response ->
                // Remove all blank tags as early as possible
                response.tags = response.tags?.filter { it.isNotBlank() }
                val add = SavedLoadResponse(unixTime, response, lookingForHash)

                synchronized(cache) {
                    if (cache.size > cacheSize) {
                        cache[cacheIndex] = add // rolling cache
                        cacheIndex = (cacheIndex + 1) % cacheSize
                    } else {
                        cache.add(add)
                    }
                }
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
                    with(CoroutineScope(coroutineContext)) {
                        api.mainPage.map { data ->
                            async {
                                api.getMainPage(
                                    page,
                                    MainPageRequest(data.name, data.data, data.horizontalImages)
                                )
                            }
                        }.map { it.await() }
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