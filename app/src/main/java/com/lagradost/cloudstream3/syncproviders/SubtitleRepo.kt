package com.lagradost.cloudstream3.syncproviders

import androidx.annotation.WorkerThread
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf

/** Stateless safe abstraction of SubtitleAPI */
class SubtitleRepo(override val api: SubtitleAPI) : AuthRepo(api) {
    companion object {
        data class SavedSearchResponse(
            val unixTime: Long,
            val response: List<SubtitleEntity>,
            val query: SubtitleSearch
        )

        data class SavedResourceResponse(
            val unixTime: Long,
            val response: SubtitleResource,
            val query: SubtitleEntity
        )

        // maybe make this a generic struct? right now there is a lot of boilerplate
        private val searchCache = threadSafeListOf<SavedSearchResponse>()
        private var searchCacheIndex: Int = 0
        private val resourceCache = threadSafeListOf<SavedResourceResponse>()
        private var resourceCacheIndex: Int = 0
        const val CACHE_SIZE = 20
    }

    @WorkerThread
    suspend fun resource(data: SubtitleEntity): Result<SubtitleResource> = runCatching {
        synchronized(resourceCache) {
            for (item in resourceCache) {
                // 20 min save
                if (item.query == data && (unixTime - item.unixTime) < 60 * 20) {
                    return@runCatching item.response
                }
            }
        }

        val returnValue = api.resource(freshAuth(), data)
        synchronized(resourceCache) {
            val add = SavedResourceResponse(unixTime, returnValue, data)
            if (resourceCache.size > CACHE_SIZE) {
                resourceCache[resourceCacheIndex] = add // rolling cache
                resourceCacheIndex = (resourceCacheIndex + 1) % CACHE_SIZE
            } else {
                resourceCache.add(add)
            }
        }
        returnValue
    }

    @WorkerThread
    suspend fun search(query: SubtitleSearch): Result<List<SubtitleEntity>> {
        return runCatching {
            synchronized(searchCache) {
                for (item in searchCache) {
                    // 120 min save
                    if (item.query == query && (unixTime - item.unixTime) < 60 * 120) {
                        return@runCatching item.response
                    }
                }
            }

            val returnValue =
                api.search(freshAuth(), query) ?: emptyList()

            // only cache valid return values
            if (returnValue.isNotEmpty()) {
                val add = SavedSearchResponse(unixTime, returnValue, query)
                synchronized(searchCache) {
                    if (searchCache.size > CACHE_SIZE) {
                        searchCache[searchCacheIndex] = add // rolling cache
                        searchCacheIndex = (searchCacheIndex + 1) % CACHE_SIZE
                    } else {
                        searchCache.add(add)
                    }
                }
            }
            returnValue
        }
    }
}

