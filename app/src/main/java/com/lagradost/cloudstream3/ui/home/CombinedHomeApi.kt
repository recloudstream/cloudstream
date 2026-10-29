package com.lagradost.cloudstream3.ui.home

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.mvvm.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

internal class CombinedHomeApi(providers: List<MainAPI>) : MainAPI() {
    companion object { const val NAME = "Selected sources" }
    private data class Page(val provider: MainAPI, val data: MainPageData)
    private val pages = providers.flatMap { provider -> provider.mainPage.map { Page(provider, it) } }
    private val rowOwners = ConcurrentHashMap<String, Int>()
    private val limits = Semaphore(3)
    var onPartial: ((List<HomePageResponse>) -> Unit)? = null
    private val providerLocks = providers.associateWith { Mutex() }
    override var name = NAME
    override var mainUrl = ""
    override val hasMainPage = true
    override val mainPage = pages.mapIndexed { index, page ->
        MainPageData("${page.provider.name} · ${page.data.name}", index.toString(), page.data.horizontalImages)
    }

    fun pageIndexForRow(name: String): Int? = rowOwners[name]

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val index = request.data.toIntOrNull() ?: return newHomePageResponse(emptyList(), false)
        val owner = pages.getOrNull(index) ?: return newHomePageResponse(emptyList(), false)
        return providerLocks.getValue(owner.provider).withLock {
            limits.withPermit {
                try {
                    val response = withTimeoutOrNull(20_000) {
                        val api = owner.provider
                        val pause = api.lastHomepageRequest +
                            maxOf(api.sequentialMainPageScrollDelay, if (api.sequentialMainPage) api.sequentialMainPageDelay else 0) -
                            com.lagradost.cloudstream3.APIHolder.unixTimeMS
                        if (pause > 0) delay(pause)
                        api.lastHomepageRequest = com.lagradost.cloudstream3.APIHolder.unixTimeMS
                        api.getMainPage(page, MainPageRequest(owner.data.name, owner.data.data, owner.data.horizontalImages))
                    } ?: return@withPermit newHomePageResponse(emptyList(), false)
                    newHomePageResponse(response.items.map { row ->
                        val rowName = if (row.name == owner.data.name) "${owner.provider.name} · ${row.name}"
                            else "${owner.provider.name} · ${owner.data.name}: ${row.name}"
                        rowOwners[rowName] = index
                        row.copy(name = rowName)
                    }, response.hasNext)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { newHomePageResponse(emptyList(), false) }
            }
        }
    }

    suspend fun getPages(page: Int, index: Int?): Resource<List<HomePageResponse?>> = coroutineScope {
        val results = ConcurrentHashMap<Int, HomePageResponse>()
        withTimeoutOrNull(35_000) {
            (index?.let { listOf(it) } ?: pages.indices.toList()).map { position ->
                async {
                    mainPage.getOrNull(position)?.let { data ->
                        val result = getMainPage(page, MainPageRequest(data.name, data.data, data.horizontalImages))
                        if (result.items.isNotEmpty()) synchronized(results) {
                            results[position] = result
                            onPartial?.invoke(results.toSortedMap().values.toList())
                        }
                    }
                }
            }.awaitAll()
        }
        if (results.isEmpty()) Resource.Failure(false, "No selected source returned a catalogue")
        else Resource.Success(results.toSortedMap().values.toList())
    }
}
