package com.lagradost.cloudstream3.ui.home

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.TorrentSearchResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.DataStoreHelper
import java.util.concurrent.ConcurrentHashMap

object HomeCache {
    private const val HOME_CACHE_FOLDER = "home_cache"

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CachedSearchResponse(
        @JsonProperty("name") val name: String = "",
        @JsonProperty("url") val url: String = "",
        @JsonProperty("apiName") val apiName: String = "",
        @JsonProperty("type") val type: TvType? = null,
        @JsonProperty("posterUrl") val posterUrl: String? = null,
        @JsonProperty("posterHeaders") val posterHeaders: Map<String, String>? = null,
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("quality") val quality: SearchQuality? = null,
        @JsonProperty("scoreDouble") val scoreDouble: Double? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("dubStatus") val dubStatus: Set<DubStatus>? = null,
        @JsonProperty("animeEpisodes") val animeEpisodes: Map<DubStatus, Int>? = null,
        @JsonProperty("tvEpisodes") val tvEpisodes: Int? = null,
        @JsonProperty("otherName") val otherName: String? = null,
        @JsonProperty("lang") val lang: String? = null
    ) {
        @Suppress("DEPRECATION_ERROR")
        fun toSearchResponse(): SearchResponse {
            val scoreObj = scoreDouble?.let { Score.from10(it) }
            return when (type) {
                TvType.Anime, TvType.AnimeMovie, TvType.OVA -> AnimeSearchResponse(
                    name = name,
                    url = url,
                    apiName = apiName,
                    type = type,
                    posterUrl = posterUrl,
                    year = year,
                    dubStatus = dubStatus?.toMutableSet(),
                    otherName = otherName,
                    episodes = animeEpisodes?.toMutableMap() ?: mutableMapOf(),
                    id = id,
                    quality = quality,
                    score = scoreObj,
                    posterHeaders = posterHeaders,
                )
                TvType.TvSeries, TvType.AsianDrama -> TvSeriesSearchResponse(
                    name = name,
                    url = url,
                    apiName = apiName,
                    type = type,
                    posterUrl = posterUrl,
                    year = year,
                    episodes = tvEpisodes,
                    id = id,
                    quality = quality,
                    score = scoreObj,
                    posterHeaders = posterHeaders,
                )
                TvType.Live -> LiveSearchResponse(
                    name = name,
                    url = url,
                    apiName = apiName,
                    type = type,
                    posterUrl = posterUrl,
                    id = id,
                    quality = quality,
                    score = scoreObj,
                    posterHeaders = posterHeaders,
                    lang = lang,
                )
                TvType.Torrent -> TorrentSearchResponse(
                    name = name,
                    url = url,
                    apiName = apiName,
                    type = type,
                    posterUrl = posterUrl,
                    id = id,
                    quality = quality,
                    score = scoreObj,
                    posterHeaders = posterHeaders,
                )
                else -> MovieSearchResponse(
                    name = name,
                    url = url,
                    apiName = apiName,
                    type = type ?: TvType.Movie,
                    posterUrl = posterUrl,
                    year = year,
                    id = id,
                    quality = quality,
                    score = scoreObj,
                    posterHeaders = posterHeaders,
                )
            }
        }

        companion object {
            fun fromSearchResponse(res: SearchResponse): CachedSearchResponse {
                val year = when (res) {
                    is MovieSearchResponse -> res.year
                    is TvSeriesSearchResponse -> res.year
                    is AnimeSearchResponse -> res.year
                    else -> null
                }
                val dubStatus = (res as? AnimeSearchResponse)?.dubStatus?.toSet()
                val animeEpisodes = (res as? AnimeSearchResponse)?.episodes?.toMap()
                val tvEpisodes = (res as? TvSeriesSearchResponse)?.episodes
                val otherName = (res as? AnimeSearchResponse)?.otherName
                val lang = (res as? LiveSearchResponse)?.lang
                val scoreDouble = res.score?.toDouble(10)

                return CachedSearchResponse(
                    name = res.name,
                    url = res.url,
                    apiName = res.apiName,
                    type = res.type,
                    posterUrl = res.posterUrl,
                    posterHeaders = res.posterHeaders,
                    id = res.id,
                    quality = res.quality,
                    scoreDouble = scoreDouble,
                    year = year,
                    dubStatus = dubStatus,
                    animeEpisodes = animeEpisodes,
                    tvEpisodes = tvEpisodes,
                    otherName = otherName,
                    lang = lang
                )
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CachedHomePageList(
        @JsonProperty("name") val name: String = "",
        @JsonProperty("list") val list: List<CachedSearchResponse> = emptyList(),
        @JsonProperty("isHorizontalImages") val isHorizontalImages: Boolean = false
    ) {
        @Suppress("DEPRECATION_ERROR")
        fun toHomePageList(): HomePageList {
            return HomePageList(
                name = name,
                list = list.map { it.toSearchResponse() },
                isHorizontalImages = isHorizontalImages
            )
        }

        companion object {
            fun fromHomePageList(homeList: HomePageList): CachedHomePageList {
                return CachedHomePageList(
                    name = homeList.name,
                    list = homeList.list.map { CachedSearchResponse.fromSearchResponse(it) },
                    isHorizontalImages = homeList.isHorizontalImages
                )
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CachedHomePageResponse(
        @JsonProperty("items") val items: List<CachedHomePageList> = emptyList(),
        @JsonProperty("hasNext") val hasNext: Boolean = false
    ) {
        @Suppress("DEPRECATION_ERROR")
        fun toHomePageResponse(): HomePageResponse {
            return HomePageResponse(
                items = items.map { it.toHomePageList() },
                hasNext = hasNext
            )
        }

        companion object {
            fun fromHomePageResponse(resp: HomePageResponse): CachedHomePageResponse {
                return CachedHomePageResponse(
                    items = resp.items.map { CachedHomePageList.fromHomePageList(it) },
                    hasNext = resp.hasNext
                )
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CachedHomeData(
        @JsonProperty("unixTime") val unixTime: Long = 0L,
        @JsonProperty("responses") val responses: List<CachedHomePageResponse> = emptyList()
    )

    private val memoryCache = ConcurrentHashMap<String, Pair<Long, List<HomePageResponse?>>>()

    fun getHomeCache(apiName: String): List<HomePageResponse?>? {
        if (!DataStoreHelper.isCacheEnabled) return null

        val cacheTtlSeconds = DataStoreHelper.cacheTimeSeconds

        memoryCache[apiName]?.let { (savedTime, data) ->
            if (unixTime - savedTime < cacheTtlSeconds) {
                return data
            } else {
                memoryCache.remove(apiName)
            }
        }

        return try {
            val diskCached = getKey<CachedHomeData>(HOME_CACHE_FOLDER, apiName)
            if (diskCached != null) {
                if (unixTime - diskCached.unixTime < cacheTtlSeconds) {
                    val deserialized = diskCached.responses.map { it.toHomePageResponse() }
                    memoryCache[apiName] = Pair(diskCached.unixTime, deserialized)
                    deserialized
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    fun setHomeCache(apiName: String, data: List<HomePageResponse?>) {
        if (!DataStoreHelper.isCacheEnabled) return
        val nonNullData = data.filterNotNull()
        if (nonNullData.isEmpty()) return

        memoryCache[apiName] = Pair(unixTime, data)

        try {
            val cachedHomeData = CachedHomeData(
                unixTime = unixTime,
                responses = nonNullData.map { CachedHomePageResponse.fromHomePageResponse(it) }
            )
            setKey(HOME_CACHE_FOLDER, apiName, cachedHomeData)
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun removeHomeCache(apiName: String) {
        memoryCache.remove(apiName)
        try {
            removeKey(HOME_CACHE_FOLDER, apiName)
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun clear() {
        memoryCache.clear()
    }

    fun clearAll(context: Context? = null) {
        memoryCache.clear()
        try {
            if (context != null) {
                context.removeKeys(HOME_CACHE_FOLDER)
            } else {
                com.lagradost.cloudstream3.CloudStreamApp.removeKeys(HOME_CACHE_FOLDER)
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun getCacheSize(context: Context?): Long {
        if (context == null) return 0L
        var totalBytes = 0L
        try {
            val prefs = context.getSharedPrefs()
            val prefix = "${HOME_CACHE_FOLDER}/"
            for ((key, value) in prefs.all) {
                if (key.startsWith(prefix) && value is String) {
                    totalBytes += value.toByteArray(Charsets.UTF_8).size.toLong()
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
        return totalBytes
    }
}
