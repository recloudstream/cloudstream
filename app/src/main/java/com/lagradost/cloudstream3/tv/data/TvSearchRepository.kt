package com.lagradost.cloudstream3.tv.data

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.tv.model.TvSearchCatalog
import com.lagradost.cloudstream3.tv.model.TvSearchResult
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Thin read-only bridge: multi-provider APIRepository.search → immutable TV results.
 * No Composables / Activity / navigation / focus / DataStore writes / search history.
 *
 * Documented APIRepository search usage (inspected, not invented):
 * - constructor(MainAPI)
 * - search(query, page) → Resource&lt;SearchResponseList&gt; (items + hasNext)
 * - quickSearch(query) → Resource&lt;SearchResponseList&gt; (hasQuickSearch providers only)
 * - Empty query → Success(empty list) without provider call
 * - Timeout via api.searchTimeoutMs / safeApiCall
 *
 * Multi-provider flow mirrors [com.lagradost.cloudstream3.ui.search.SearchViewModel]:
 * - repos from APIHolder.apis → APIRepository
 * - Filter by DataStoreHelper.searchPreferenceProviders when non-empty (read-only)
 * - Parallel amap; page=1 full search (not quickSearch) on explicit submit
 * - Generation / cancel handled by caller (ViewModel job cancel)
 * - Partial provider Failure: skip that provider, keep successes (same as SearchViewModel)
 * - Does NOT write SEARCH_HISTORY_KEY / DataStore search history
 *
 * SearchResponse retains apiName + url from providers; [TvSearchMapper] copies both into
 * [com.lagradost.cloudstream3.tv.model.TvContentRef] for Details convergence with Home.
 */
class TvSearchRepository {

    sealed interface SearchResult {
        data class Success(val catalog: TvSearchCatalog) : SearchResult
        data class Empty(
            val query: String,
            val providerCount: Int,
            val failedProviderCount: Int,
        ) : SearchResult
        data class Failure(val query: String, val message: String) : SearchResult
    }

    /**
     * Resolve providers the same way mobile search prefers: saved searchPreferenceProviders,
     * else all loaded APIs. Read-only — never writes provider prefs.
     */
    fun resolveSearchRepos(): List<APIRepository> {
        val all = APIHolder.apis.withLock { APIHolder.apis.map { APIRepository(it) } }
        val preferred = DataStoreHelper.searchPreferenceProviders
        if (preferred.isEmpty()) return all
        val set = preferred.toSet()
        val filtered = all.filter { set.contains(it.name) }
        return filtered.ifEmpty { all }
    }

    /**
     * Full multi-provider search (page 1). Call from IO dispatcher.
     * [isActive] should return false when a newer search superseded this one.
     */
    suspend fun search(
        query: String,
        isActive: () -> Boolean = { true },
    ): SearchResult {
        val trimmed = query.trim()
        if (trimmed.length <= 1) {
            return SearchResult.Failure(
                query = trimmed,
                message = "Enter at least 2 characters to search.",
            )
        }

        val repos = resolveSearchRepos()
        if (repos.isEmpty()) {
            return SearchResult.Failure(
                query = trimmed,
                message = "No search providers available. Install/enable plugins, then retry.",
            )
        }

        if (!isActive()) {
            return SearchResult.Failure(trimmed, "Search cancelled")
        }

        var failed = 0
        var succeeded = 0

        // Parallel per provider — same amap pattern as SearchViewModel.
        val perProvider = repos.amap { repo ->
            coroutineContext.ensureActive()
            if (!isActive()) return@amap ProviderOutcome.Cancelled
            when (val data = repo.search(trimmed, page = 1)) {
                is Resource.Success -> {
                    val mapped = TvSearchMapper.toSearchResults(data.value.items)
                    ProviderOutcome.Ok(mapped)
                }
                is Resource.Failure -> ProviderOutcome.Fail(
                    data.errorString.ifBlank { "Failed: ${repo.name}" },
                )
                is Resource.Loading -> ProviderOutcome.Fail("Unexpected loading from ${repo.name}")
            }
        }

        if (!isActive()) {
            return SearchResult.Failure(trimmed, "Search cancelled")
        }

        for (outcome in perProvider) {
            when (outcome) {
                is ProviderOutcome.Ok -> {
                    succeeded++
                }
                is ProviderOutcome.Fail -> failed++
                ProviderOutcome.Cancelled -> {
                    return SearchResult.Failure(trimmed, "Search cancelled")
                }
            }
        }

        // Round-robin merge like SearchViewModel.bundleSearch for relevance.
        val bundled = bundleRoundRobin(perProvider)

        return when {
            bundled.isNotEmpty() -> SearchResult.Success(
                TvSearchCatalog(
                    query = trimmed,
                    results = bundled,
                    providerCount = repos.size,
                    failedProviderCount = failed,
                ),
            )
            succeeded == 0 && failed > 0 -> SearchResult.Failure(
                query = trimmed,
                message = "All $failed provider(s) failed. Check network/plugins, then retry.",
            )
            else -> SearchResult.Empty(
                query = trimmed,
                providerCount = repos.size,
                failedProviderCount = failed,
            )
        }
    }

    /**
     * Round-robin merge across successful provider lists — mirrors SearchViewModel.bundleSearch
     * so the first hit from each provider rises toward the top.
     */
    private fun bundleRoundRobin(outcomes: List<ProviderOutcome>): List<TvSearchResult> {
        val lists = outcomes.mapNotNull { (it as? ProviderOutcome.Ok)?.items }.filter { it.isNotEmpty() }
        if (lists.isEmpty()) return emptyList()
        if (lists.size == 1) return lists.first().distinctBy { it.id }

        val out = ArrayList<TvSearchResult>()
        var index = 0
        while (true) {
            var added = 0
            for (sub in lists) {
                if (sub.size > index) {
                    out.add(sub[index])
                    added++
                }
            }
            if (added == 0) break
            index++
        }
        return out.distinctBy { it.id }
    }

    private sealed interface ProviderOutcome {
        data class Ok(val items: List<TvSearchResult>) : ProviderOutcome
        data class Fail(val message: String) : ProviderOutcome
        data object Cancelled : ProviderOutcome
    }
}
