package com.lagradost.cloudstream3.ui.home

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.Resource
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class CombinedHomeApiTest {
    private fun source(label: String, delayMs: Long = 0, fail: Boolean = false) = object : MainAPI() {
        override var name = label
        override var mainUrl = "https://example.com"
        override val hasMainPage = true
        override val mainPage = listOf(MainPageData("Movies", "/movies"))
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            delay(delayMs)
            if (fail) error("Unavailable source")
            return newHomePageResponse(request, listOf(newMovieSearchResponse("Film $label", "/film")), false)
        }
    }

    @Test fun retainsSuccessfulCataloguesWhenOneSourceFails() = runBlocking {
        val api = CombinedHomeApi(listOf(source("Good"), source("Broken", fail=true)))
        val result = api.getPages(1,null)
        assertTrue(result is Resource.Success)
        val rows = (result as Resource.Success).value.filterNotNull().flatMap { it.items }
        assertEquals(listOf("Good · Movies"), rows.map { it.name })
        assertEquals(0, api.pageIndexForRow(rows.first().name))
    }

    @Test fun emitsFastSourcesBeforeSlowSourcesAndPreservesOrder() = runBlocking {
        val api = CombinedHomeApi(listOf(source("Slow",60),source("Fast")))
        val snapshots = mutableListOf<List<String>>()
        api.onPartial = { snapshots.add(it.flatMap { page -> page.items }.map { it.name }) }
        val result = api.getPages(1,null) as Resource.Success
        assertEquals(listOf("Fast · Movies"), snapshots.first())
        assertEquals(listOf("Slow · Movies","Fast · Movies"),result.value.filterNotNull().flatMap { it.items }.map { it.name })
    }

    @Test fun cancellationStopsPendingSources() = runBlocking {
        val api = CombinedHomeApi(listOf(source("Slow",60000)))
        val job = async { api.getPages(1,null) }
        yield()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test fun capsConcurrentProvidersAtThree() = runBlocking {
        val running = AtomicInteger()
        val maximum = AtomicInteger()
        val providers = (1..8).map { index -> object : MainAPI() {
            override var name = "Source $index"
            override val hasMainPage = true
            override val mainPage = listOf(MainPageData("Movies", "/movies"))
            override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
                val count = running.incrementAndGet()
                maximum.accumulateAndGet(count, ::maxOf)
                try { delay(10); return newHomePageResponse(request,emptyList(),false) }
                finally { running.decrementAndGet() }
            }
        } }
        CombinedHomeApi(providers).getPages(1,null)
        assertTrue(maximum.get() in 2..3)
        assertEquals(0,running.get())
    }
}
