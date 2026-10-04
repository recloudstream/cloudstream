package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.getStackTracePretty
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream4.state.Log
import com.lagradost.cloudstream4.state.LogItem
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

object TestingUtils {
    open class TestResult(val success: Boolean) {
        companion object {
            val Pass = TestResult(true)
            val Fail = TestResult(false)
        }
    }

    data class Logger(
        var messageLog: PersistentList<LogItem> = persistentListOf<LogItem>()
    ) : Log {
        override fun log(item: LogItem) {
            messageLog = messageLog.adding(item)
        }
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)
    private fun assertTrue(message: String, condition: Boolean) {
        if (!condition) fail(message)
    }

    private fun assertNotNull(message: String, value: Any?) {
        if (value == null) fail(message)
    }

    class TestResultList(val results: List<SearchResponse>) : TestResult(true)
    class TestResultLoad(val extractorData: String, val shouldLoadLinks: Boolean) : TestResult(true)

    class TestResultProvider(
        success: Boolean,
        val exception: Throwable?
    ) :
        TestResult(success)

    @Throws(AssertionError::class, CancellationException::class)
    suspend fun testHomepage(
        api: MainAPI, logger: Log
    ): TestResult {
        val tag = "Homepage"
        if (api.hasMainPage) {
            try {
                val f = api.mainPage.first()
                val homepage =
                    api.getMainPage(1, MainPageRequest(f.name, f.data, f.horizontalImages))
                when {
                    homepage == null -> {
                        logger.e(tag, "Provider ${api.name} did not correctly load homepage!")
                    }

                    homepage.items.isEmpty() -> {
                        logger.w(tag, "Provider ${api.name} does not contain any homepage rows!")
                    }

                    homepage.items.any { it.list.isEmpty() } -> {
                        logger.w(
                            tag,
                            "Provider ${api.name} does not have any items in a homepage row!"
                        )
                    }
                }
                val homePageList = homepage?.items?.flatMap { it.list } ?: emptyList()
                return TestResultList(homePageList)
            } catch (e: Throwable) {
                when (e) {
                    is NotImplementedError -> {
                        fail("Provider marked as hasMainPage, while in reality is has not been implemented")
                    }

                    is CancellationException -> {
                        throw e
                    }

                    else -> {
                        e.message?.let {
                            logger.w(
                                tag,
                                "Exception thrown when loading homepage: \"$it\""
                            )
                        }
                    }
                }
            }
        }
        return TestResult.Pass
    }

    @Throws(AssertionError::class, CancellationException::class)
    private suspend fun testSearch(
        api: MainAPI,
        testQueries: List<String>,
        logger: Log,
    ): TestResult {
        val searchResults = testQueries.firstNotNullOfOrNull { query ->
            try {
                logger.i("Search", "Searching for: $query")
                api.search(query, 1)?.items?.takeIf { it.isNotEmpty() }
            } catch (e: Throwable) {
                if (e is NotImplementedError) {
                    fail("Provider has not implemented search()")
                } else if (e is CancellationException) {
                    throw e
                }
                logError(e)
                null
            }
        }

        return if (searchResults.isNullOrEmpty()) {
            fail("Api ${api.name} did not return any search responses")
            TestResult.Fail // Should not be reached
        } else {
            TestResultList(searchResults)
        }
    }


    @Throws(AssertionError::class, CancellationException::class)
    private suspend fun testLoad(
        api: MainAPI,
        result: SearchResponse,
        logger: Log
    ): TestResult {
        val tag = "load"
        try {
            if (result.apiName != api.name) {
                logger.w(tag, "Wrong apiName on SearchResponse: ${api.name} != ${result.apiName}")
            }

            val loadResponse = api.load(result.url)

            if (loadResponse == null) {
                logger.e(tag, "Returned null loadResponse on ${result.url} on ${api.name}")
                return TestResult.Fail
            }

            if (loadResponse.apiName != api.name) {
                logger.w(
                    tag,
                    "Wrong apiName on LoadResponse: ${api.name} != ${loadResponse.apiName}"
                )
            }

            if (!api.supportedTypes.contains(loadResponse.type)) {
                logger.w(
                    tag,
                    "Api ${api.name} on load does not contain any of the supportedTypes: ${loadResponse.type}"
                )
            }

            val url = when (loadResponse) {
                is AnimeLoadResponse -> {
                    val gotNoEpisodes =
                        loadResponse.episodes.keys.isEmpty() || loadResponse.episodes.keys.any { loadResponse.episodes[it].isNullOrEmpty() }

                    if (gotNoEpisodes) {
                        logger.e(tag, "Api ${api.name} got no episodes on ${loadResponse.url}")
                        return TestResult.Fail
                    }

                    (loadResponse.episodes[loadResponse.episodes.keys.firstOrNull()])?.firstOrNull()?.data
                }

                is MovieLoadResponse -> {
                    val gotNoEpisodes = loadResponse.dataUrl.isBlank()
                    if (gotNoEpisodes) {
                        logger.e(tag, "Api ${api.name} got no movie on ${loadResponse.url}")
                        return TestResult.Fail
                    }

                    loadResponse.dataUrl
                }

                is TvSeriesLoadResponse -> {
                    val gotNoEpisodes = loadResponse.episodes.isEmpty()
                    if (gotNoEpisodes) {
                        logger.e(tag, "Api ${api.name} got no episodes on ${loadResponse.url}")
                        return TestResult.Fail
                    }
                    loadResponse.episodes.firstOrNull()?.data
                }

                is LiveStreamLoadResponse -> {
                    loadResponse.dataUrl
                }

                else -> {
                    logger.e(tag, "Unknown load response: ${loadResponse::class.qualifiedName}")
                    return TestResult.Fail
                }
            } ?: return TestResult.Fail

            return TestResultLoad(url, loadResponse.type != TvType.CustomMedia)

//            val loadTest = testLoadResponse(api, load, logger)
//            if (loadTest is TestResultLoad) {
//                testLinkLoading(api, loadTest.extractorData, logger).success
//            } else {
//                false
//            }
//            if (!validResults) {
//                logger("Api ${api.name} did not load on the first search results: ${smallSearchResults.map { it.name }}")
//            }

//            return TestResult(validResults)
        } catch (e: Throwable) {
            if (e is NotImplementedError) {
                fail("Provider has not implemented load()")
            }
            throw e
        }
    }

    @Throws(AssertionError::class, CancellationException::class)
    private suspend fun testLinkLoading(
        api: MainAPI,
        url: String?,
        logger: Log
    ): TestResult {
        val tag = "Link"
        assertNotNull("Api ${api.name} has invalid url on episode", url)
        if (url == null) return TestResult.Fail // Should never trigger

        var linksLoaded = 0
        try {
            val success = api.loadLinks(url, false, {}) { link ->
                logger.i(tag, "Video loaded: ${link.name}")
                assertTrue(
                    "Api ${api.name} returns link with invalid url ${link.url}",
                    link.url.length > 4
                )
                linksLoaded++
            }
            if (success) {
                logger.i(tag, "Links loaded: $linksLoaded")
                return TestResult(linksLoaded > 0)
            } else {
                fail("Api ${api.name} returns false on loadLinks() with $linksLoaded links loaded")
            }
        } catch (e: Throwable) {
            when (e) {
                is NotImplementedError -> {
                    fail("Provider has not implemented loadLinks()")
                }

                else -> {
                    logger.e(tag, "Failed link loading on ${api.name} using data: $url")
                    throw e
                }
            }
        }
        return TestResult.Pass
    }

    fun getDeferredProviderTests(
        scope: CoroutineScope,
        providers: Array<MainAPI>,
        callback: (MainAPI, TestResultProvider, Logger) -> Unit,
    ) {
        providers.forEach { api ->
            scope.launch {
                val logger = Logger()
                val result = runSingleProviderTest(api, logger)
                callback.invoke(api, result, logger)
            }
        }
    }

    suspend fun runSingleProviderTest(api: MainAPI, logger: Log): TestResultProvider {
        val tag = "Test"
        return try {
            logger.i(tag, "Trying ${api.name}")

            // Test Homepage
            val homepage = testHomepage(api, logger)
            assertTrue("Homepage failed to load", homepage.success)
            val homePageList = (homepage as? TestResultList)?.results ?: emptyList()

            // Test Search Results
            val searchQueries =
                // Use the random 3 home page results as queries since they are guaranteed to exist
                (homePageList.shuffled(Random).take(3).map { it.name.split(" ").first() } +
                        // If home page is sparse then use generic search queries
                        listOf("over", "iron", "guy")).take(3)

            val searchResults = testSearch(api, searchQueries, logger)
            assertTrue("Failed to get search results", searchResults.success)
            searchResults as TestResultList

            // Test Load and LoadLinks
            // Only try the first 3 search results to prevent spamming
            val success = searchResults.results.take(3).any { searchResponse ->
                logger.i("Search", "Testing search result: ${searchResponse.url}")
                val loadResponse = testLoad(api, searchResponse, logger)
                if (loadResponse !is TestResultLoad) {
                    false
                } else {
                    if (loadResponse.shouldLoadLinks) {
                        testLinkLoading(api, loadResponse.extractorData, logger).success
                    } else {
                        logger.i(tag, "Skipping link loading test")
                        true
                    }
                }
            }

            if (success) {
                logger.i(tag, "Success ${api.name}")
                TestResultProvider(true, null)
            } else {
                logger.e(tag, "Link loading failed")
                TestResultProvider(false, null)
            }
        } catch (e : CancellationException) {
            logger.e(tag, "Testing of ${api.name} was cancelled")
            TestResultProvider(false, e)
        } catch (e: Throwable) {
            logger.e(tag, e.getStackTracePretty().trim())
            TestResultProvider(false, e)
        }
    }
}
