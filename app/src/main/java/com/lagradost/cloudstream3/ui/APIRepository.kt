package com.lagradost.cloudstream3.ui

import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.Coroutines.atomicListOf
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

class APIRepository(val api: MainAPI) {
    companion object {
        // 2 minute timeout to prevent bad extensions/extractors from hogging the resources
        // No real provider should take longer, so we hard kill them.
        private const val DEFAULT_TIMEOUT = 120_000L
        private const val MAX_TIMEOUT = 4 * DEFAULT_TIMEOUT
        private const val MIN_TIMEOUT = 5_000L

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

        @Serializable
        data class SavedHomePageResponse(
            val unixTime: Long,
            val response: List<HomePageResponse?>,
            val hash: Pair<String, Pair<Int, Int?>>
        )

        private val cache = atomicListOf<SavedLoadResponse>()
        private var cacheIndex: Int = 0
        const val CACHE_SIZE = 20

        private val homeCache = atomicListOf<SavedHomePageResponse>()
        private var homeCacheIndex: Int = 0
        const val HOME_CACHE_SIZE = 20
        const val HOME_CACHE_FOLDER = "home_cache"

        fun getTimeout(desired: Long?): Long {
            return (desired ?: DEFAULT_TIMEOUT).coerceIn(MIN_TIMEOUT, MAX_TIMEOUT)
        }

        fun clearCache(apiName: String? = null) {
            if (apiName == null) {
                cache.clear()
                homeCache.clear()
                CloudStreamApp.removeKeys(HOME_CACHE_FOLDER)
            } else {
                homeCache.withLock {
                    homeCache.removeAll { it.hash.first == apiName }
                }
                CloudStreamApp.getKeys(HOME_CACHE_FOLDER)?.forEach { key ->
                    if (key.startsWith("${apiName}_")) {
                        CloudStreamApp.removeKey(HOME_CACHE_FOLDER, key)
                    }
                }
            }
        }

        fun getEffectiveHomepageCacheTtl(maxHomepageCacheTimeMs: Long?): Long {
            val userCacheTtl = DataStoreHelper.cacheTimeSeconds
            if (userCacheTtl <= 0L) return 0L
            val providerMaxSeconds = maxHomepageCacheTimeMs?.let { it / 1000L } ?: return userCacheTtl
            return if (providerMaxSeconds <= 0L) 0L else minOf(userCacheTtl, providerMaxSeconds)
        }

        fun hasHomePageCache(
            apiName: String,
            maxHomepageCacheTimeMs: Long? = null,
            page: Int = 1,
            nameIndex: Int? = null
        ): Boolean {
            val cacheTtl = getEffectiveHomepageCacheTtl(maxHomepageCacheTimeMs)
            if (cacheTtl <= 0L) return false
            val lookingForHash = Pair(apiName, Pair(page, nameIndex))
            val inRam = homeCache.withLock {
                homeCache.any { it.hash == lookingForHash && unixTime - it.unixTime < cacheTtl }
            }
            if (inRam) return true
            val diskKey = "${apiName}_${page}_${nameIndex}"
            val onDisk = CloudStreamApp.getKey<SavedHomePageResponse>(HOME_CACHE_FOLDER, diskKey)
            return onDisk != null && unixTime - onDisk.unixTime < cacheTtl
        }
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
            withTimeout(getTimeout(api.loadTimeoutMs)) {
                if (isInvalidData(url)) throw ErrorLoadingException()
                val fixedUrl = api.fixUrl(url)
                val lookingForHash = Pair(api.name, fixedUrl)
                val cacheTtl = DataStoreHelper.cacheTimeSeconds
                val isCacheEnabled = DataStoreHelper.isCacheEnabled

                if (isCacheEnabled) {
                    cache.withLock {
                        cache.firstOrNull { item -> item.hash == lookingForHash && unixTime - item.unixTime < cacheTtl }?.response
                    }?.let { return@withTimeout it }
                }

                api.load(fixedUrl)?.also { response ->
                    // Remove all blank tags as early as possible
                    response.tags = response.tags?.filter { it.isNotBlank() }
                    val add = SavedLoadResponse(unixTime, response, lookingForHash)

                    if (isCacheEnabled) {
                        cache.withLock {
                            if (cache.size > CACHE_SIZE) {
                                cache[cacheIndex] = add // rolling cache
                                cacheIndex = (cacheIndex + 1) % CACHE_SIZE
                            } else {
                                cache.add(add)
                            }
                        }
                    }
                } ?: throw ErrorLoadingException()
            }
        }
    }

    suspend fun search(query: String, page: Int): Resource<SearchResponseList> {
        if (query.isEmpty())
            return Resource.Success(newSearchResponseList(emptyList()))

        return safeApiCall {
            withTimeout(getTimeout(api.searchTimeoutMs)) {
                (api.search(query, page)
                    ?: throw ErrorLoadingException())
                //                .filter { typesActive.contains(it.type) }
            }
        }
    }

    suspend fun quickSearch(query: String): Resource<SearchResponseList> {
        if (query.isEmpty())
            return Resource.Success(newSearchResponseList(emptyList()))

        return safeApiCall {
            withTimeout(getTimeout(api.quickSearchTimeoutMs)) {
                newSearchResponseList(
                    api.quickSearch(query) ?: throw ErrorLoadingException(),
                    false
                )
            }
        }
    }

    suspend fun waitForHomeDelay() {
        val delta = api.sequentialMainPageScrollDelay + api.lastHomepageRequest - unixTimeMS
        if (delta < 0) return
        delay(delta)
    }

    suspend fun getMainPage(page: Int, nameIndex: Int? = null, forceReload: Boolean = false): Resource<List<HomePageResponse?>> {
        val lookingForHash = Pair(api.name, Pair(page, nameIndex))
        val cacheTtl = getEffectiveHomepageCacheTtl(api.maxHomepageCacheTime)
        val isCacheEnabled = cacheTtl > 0L
        val diskKey = "${api.name}_${page}_${nameIndex}"

        if (isCacheEnabled && !forceReload) {
            homeCache.withLock {
                homeCache.firstOrNull { item -> item.hash == lookingForHash && unixTime - item.unixTime < cacheTtl }?.response
            }?.let { return Resource.Success(it) }

            val cachedOnDisk = CloudStreamApp.getKey<SavedHomePageResponse>(HOME_CACHE_FOLDER, diskKey)
            if (cachedOnDisk != null && unixTime - cachedOnDisk.unixTime < cacheTtl) {
                homeCache.withLock {
                    if (homeCache.size > HOME_CACHE_SIZE) {
                        homeCache[homeCacheIndex] = cachedOnDisk
                        homeCacheIndex = (homeCacheIndex + 1) % HOME_CACHE_SIZE
                    } else {
                        homeCache.add(cachedOnDisk)
                    }
                }
                return Resource.Success(cachedOnDisk.response)
            }
        }

        return safeApiCall {
            withTimeout(getTimeout(api.getMainPageTimeoutMs)) {
                api.lastHomepageRequest = unixTimeMS

                val res = nameIndex?.let { api.mainPage.getOrNull(it) }?.let { data ->
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

                if (isCacheEnabled && res.isNotEmpty()) {
                    val add = SavedHomePageResponse(unixTime, res, lookingForHash)
                    homeCache.withLock {
                        if (homeCache.size > HOME_CACHE_SIZE) {
                            homeCache[homeCacheIndex] = add // rolling cache
                            homeCacheIndex = (homeCacheIndex + 1) % HOME_CACHE_SIZE
                        } else {
                            homeCache.add(add)
                        }
                    }
                    CloudStreamApp.setKey(HOME_CACHE_FOLDER, diskKey, add)
                }

                res
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
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        if (isInvalidData(data)) return false // this makes providers cleaner
        return try {
            withTimeout(getTimeout(api.loadLinksTimeoutMs)) {
                api.loadLinks(data, isCasting, subtitleCallback, callback)
            }
        } catch (throwable: Throwable) {
            logError(throwable)
            return false
        }
    }
}
