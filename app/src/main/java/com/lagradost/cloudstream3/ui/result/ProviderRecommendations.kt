package com.lagradost.cloudstream3.ui.result

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer

internal object ProviderRecommendations {
    private fun title(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "").lowercase().replace("[^\\p{L}\\p{N}]".toRegex(), "")
    suspend fun collect(item: LoadResponse, providers: List<MainAPI>, limit: Int, emit: (List<SearchResponse>) -> Unit) = coroutineScope {
        val allowed = providers.map { it.name }.toSet()
        val results = linkedMapOf<String, SearchResponse>()
        val gate = Any()
        fun add(items: List<SearchResponse>) = synchronized(gate) {
            items.filter { (allowed.isEmpty() || it.apiName in allowed) && title(it.name) != title(item.name) }.forEach {
                if (results.size < limit) results.putIfAbsent(title(it.name) + ":" + it.type, it)
            }
            emit(results.values.toList())
        }
        add(item.recommendations.orEmpty())
        val permits = Semaphore(2)
        withTimeoutOrNull(18_000) {
            providers.map { provider -> async {
                permits.withPermit {
                    if (synchronized(gate) { results.size >= limit }) return@withPermit
                    withTimeoutOrNull(8_000) {
                        val repo = APIRepository(provider)
                        if (provider.name != item.apiName && provider.providerType == ProviderType.DirectProvider) {
                            val search = repo.search(item.name, 1)
                            currentCoroutineContext().ensureActive()
                            if (search is Resource.Success) {
                                val match = search.value.items.firstOrNull { title(it.name) == title(item.name) }
                                if (match != null) {
                                    val loaded = repo.load(match.url)
                                    currentCoroutineContext().ensureActive()
                                    if (loaded is Resource.Success && (item.year == null || loaded.value.year == null || item.year == loaded.value.year)) {
                                        add(loaded.value.recommendations.orEmpty())
                                    }
                                }
                            }
                        }
                        if (synchronized(gate) { results.size < limit }) {
                            val home = repo.getMainPage(1, 0)
                            currentCoroutineContext().ensureActive()
                            if (home is Resource.Success) add(home.value.filterNotNull().flatMap { it.items }.flatMap { it.list }.filter { it.type == item.type })
                        }
                    }
                }
            } }.awaitAll()
        }
    }
}
