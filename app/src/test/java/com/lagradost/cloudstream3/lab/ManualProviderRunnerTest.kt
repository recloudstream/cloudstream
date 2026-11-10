package com.lagradost.cloudstream3.lab

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.lab.ManualProviderRunner.Method
import com.lagradost.cloudstream3.lab.ManualProviderRunner.Request
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ManualProviderRunnerTest {
    private class FakeProvider : MainAPI() {
        var called = ""
        var input = ""
        var receivedPage = 0
        var request: MainPageRequest? = null
        var casting = false
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
            called = "home"; receivedPage = page; this.request = request; return null
        }
        override suspend fun search(query: String): List<SearchResponse>? {
            called = "search"; input = query; return null
        }
        override suspend fun search(query: String, page: Int): SearchResponseList? {
            called = "paged"; input = query; receivedPage = page; return null
        }
        override suspend fun quickSearch(query: String): List<SearchResponse>? {
            called = "quick"; input = query; return null
        }
        override suspend fun load(url: String): LoadResponse? {
            called = "load"; input = url; return null
        }
        override suspend fun loadLinks(data: String, isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            called = "links"; input = data; casting = isCasting
            subtitleCallback(newSubtitleFile("English", "https://example.com/sub.vtt"))
            return true
        }
    }

    @Test fun dispatchesOnlySelectedMethodWithExactInput() = runBlocking {
        val provider = FakeProvider()
        for ((method, expected) in listOf(Method.SEARCH to "search", Method.PAGED_SEARCH to "paged",
            Method.QUICK_SEARCH to "quick", Method.LOAD to "load")) {
            assertNull(ManualProviderRunner.run(provider, Request(method, "exact input", page = 3)))
            assertEquals(expected, provider.called)
            assertEquals("exact input", provider.input)
        }
        assertEquals(3, provider.receivedPage)
    }

    @Test fun forwardsMainPageRequestWithoutReplacingUserData() = runBlocking {
        val provider = FakeProvider()
        ManualProviderRunner.run(provider, Request(Method.MAIN_PAGE, "custom data", 4, "Latest", true))
        assertEquals("home", provider.called)
        assertEquals(4, provider.receivedPage)
        assertEquals(MainPageRequest("Latest", "custom data", true), provider.request)
    }

    @Test fun collectsLoadLinksCallbacksWithoutPlayback() = runBlocking {
        val provider = FakeProvider()
        val result = ManualProviderRunner.run(provider, Request(Method.LOAD_LINKS, "{\"episode\":7}", isCasting = true)) as Map<*, *>
        assertEquals(true, result["success"])
        assertEquals("{\"episode\":7}", provider.input)
        assertTrue(provider.casting)
        assertEquals(0, (result["links"] as List<*>).size)
        assertEquals(1, (result["subtitles"] as List<*>).size)
    }

    @Test fun rejectsInvalidInputsBeforeCallingProvider() = runBlocking {
        val provider = FakeProvider()
        for (request in listOf(Request(Method.SEARCH, ""), Request(Method.MAIN_PAGE, page = 0))) {
            try {
                ManualProviderRunner.run(provider, request)
                fail("Expected input validation failure")
            } catch (_: IllegalArgumentException) { }
        }
        assertEquals("", provider.called)
    }

    @Test fun preservesNotImplementedErrorsForDisplay() = runBlocking {
        try {
            ManualProviderRunner.run(object : MainAPI() {}, Request(Method.LOAD, "https://example.com"))
            fail("Expected unsupported method error")
        } catch (_: NotImplementedError) { }
    }
}
