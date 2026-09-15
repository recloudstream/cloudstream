package com.lagradost.cloudstream3.tv.data

import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.getApiFromUrlNull
import com.lagradost.cloudstream3.metaproviders.SyncRedirector
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.tv.model.TvContentRef
import com.lagradost.cloudstream3.tv.model.TvDetailsContent
import com.lagradost.cloudstream3.ui.APIRepository

/**
 * Thin read-only bridge: content identity → APIRepository.load → immutable details.
 * No Composables / Activity / navigation / focus / DataStore / player.
 *
 * Documented load flow (mirrors ResultViewModel2.load, without persistence side effects):
 * 1. Resolve MainAPI via getApiFromNameNull(apiName) ?: getApiFromUrlNull(url)
 * 2. SyncRedirector.redirect(url, api) (same as ResultViewModel2)
 * 3. APIRepository(api).load(validUrl) → Resource&lt;LoadResponse&gt;
 *    - fixUrl, 10-min rolling cache, api.loadTimeoutMs, blank-tag filter
 * 4. Map via [TvDetailsMapper] — never invent a load mechanism
 *
 * Plugin timing: if plugins are not loaded yet, getApiFromNameNull may miss the provider
 * ("This provider does not exist"). APIRepository also clears its load cache on
 * afterPluginsLoadedEvent. Callers should Retry after plugins settle.
 *
 * Intentionally omitted vs ResultViewModel2: DOWNLOAD_HEADER_CACHE writes, trailers,
 * fillers, AutoResume watch-position writes, applyMeta sync — Phase 6 maps episodes read-only for TV selector.
 */
class TvDetailsRepository {

    sealed interface LoadResult {
        data class Success(val details: TvDetailsContent) : LoadResult
        data class Failure(val message: String) : LoadResult
    }

    suspend fun load(ref: TvContentRef): LoadResult {
        if (APIRepository.isInvalidData(ref.url)) {
            return LoadResult.Failure("Invalid content URL")
        }

        val api = getApiFromNameNull(ref.apiName) ?: getApiFromUrlNull(ref.url)
        if (api == null) {
            return LoadResult.Failure(
                "This provider does not exist (${ref.apiName}). " +
                    "Retry after plugins finish loading.",
            )
        }

        val validUrlResource = safeApiCall {
            SyncRedirector.redirect(ref.url, api)
        }
        val validUrl = when (validUrlResource) {
            is Resource.Success -> validUrlResource.value
            is Resource.Failure -> {
                return LoadResult.Failure(
                    validUrlResource.errorString.ifBlank {
                        "Failed to resolve content URL for ${ref.apiName}"
                    },
                )
            }
            is Resource.Loading -> ref.url
        }

        val repo = APIRepository(api)
        return when (val data = repo.load(validUrl)) {
            is Resource.Success -> LoadResult.Success(TvDetailsMapper.toDetails(data.value))
            is Resource.Failure -> LoadResult.Failure(
                data.errorString.ifBlank {
                    "Failed to load details from ${repo.name}"
                },
            )
            is Resource.Loading -> LoadResult.Failure("Unexpected loading state from APIRepository.load")
        }
    }
}
