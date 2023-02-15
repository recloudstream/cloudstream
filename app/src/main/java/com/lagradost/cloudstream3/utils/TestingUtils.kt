package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.*
import org.junit.Assert

object TestingUtils {
    open class TestResult(val success: Boolean) {
        companion object {
            val Pass = TestResult(true)
            val Fail = TestResult(false)
        }
    }

    class TestResultSearch(val results: List<SearchResponse>) : TestResult(true)
    class TestResultLoad(val extractorData: String) : TestResult(true)

    class TestResultProvider(success: Boolean, val log: String, val exception: Throwable?) :
        TestResult(success)

    @Throws(AssertionError::class, CancellationException::class)
    suspend fun testHomepage(
        api: MainAPI,
        logger: (String) -> Unit
    ): TestResult {
        if (api.hasMainPage) {
            try {
                val f = api.mainPage.first()
                val homepage =
                    api.getMainPage(1, MainPageRequest(f.name, f.data, f.horizontalImages))
                when {
                    homepage == null -> {
                        logger.invoke("Homepage provider ${api.name} did not correctly load homepage!")
                    }
                    homepage.items.isEmpty() -> {
                        logger.invoke("Homepage provider ${api.name} does not contain any items!")
                    }
                    homepage.items.any { it.list.isEmpty() } -> {
                        logger.invoke("Homepage provider ${api.name} does not have any items on result!")
                    }
                }
            } catch (e: Throwable) {
                if (e is NotImplementedError) {
                    Assert.fail("Provider marked as hasMainPage, while in reality is has not been implemented")
                } else if (e is CancellationException) {
                    throw e
                }
                logError(e)
            }
        }
        return TestResult.Pass
    }

    @Throws(AssertionError::class, CancellationException::class)
    private suspend fun testSearch(
        api: MainAPI
    ): TestResult {
        val searchQueries = listOf("over", "iron", "guy")
        val searchResults = searchQueries.firstNotNullOfOrNull { query ->
            try {
                api.search(query).takeIf { !it.isNullOrEmpty() }
            } catch (e: Throwable) {
                if (e is NotImplementedError) {
                    Assert.fail("Provider has not implemented search()")
                } else if (e is CancellationException) {
                    throw e
                }
                logError(e)
                null
            }
        }

        return if (searchResults.isNullOrEmpty()) {
            Assert.fail("Api ${api.name} did not return any valid search responses")
            TestResult.Fail // Should not be reached
        } else {
            TestResultSearch(searchResults)
        }

    }


    @Throws(AssertionError::class, CancellationException::class)
    private suspend fun testLoad(
        api: MainAPI,
        result: SearchResponse,
        logger: (String) -> Unit
    ): TestResult {
        try {
            Assert.assertEquals(
                "Invalid apiName on SearchResponse on ${api.name}",
                result.apiName,
                api.name
            )

            val loadResponse = api.load(result.url)

            if (loadResponse == null) {
                logger.invoke("Returned null loadResponse on ${result.url} on ${api.name}")
                return TestResult.Fail
            }

            Assert.assertEquals(
                "Invalid apiName on LoadResponse on ${api.name}",
                loadResponse.apiName,
                result.apiName
            )
            Assert.assertTrue(
                "Api ${api.name} on load does not contain any of the supportedTypes: ${loadResponse.type}",
                api.supportedTypes.contains(loadResponse.type)
            )

            val url = when (loadResponse) {
                is AnimeLoadResponse -> {
                    val gotNoEpisodes =
                        loadResponse.episodes.keys.isEmpty() || loadResponse.episodes.keys.any { loadResponse.episodes[it].isNullOrEmpty() }

                    if (gotNoEpisodes) {
                        logger.invoke("Api ${api.name} got no episodes on ${loadResponse.url}")
                        return TestResult.Fail
                    }

                    (loadResponse.episodes[loadResponse.episodes.keys.firstOrNull()])?.firstOrNull()?.data
                }
                is MovieLoadResponse -> {
                    val gotNoEpisodes = loadResponse.dataUrl.isBlank()
                    if (gotNoEpisodes) {
                        logger.invoke("Api ${api.name} got no movie on ${loadResponse.url}")
                        return TestResult.Fail
                    }

                    loadResponse.dataUrl
                }
                is TvSeriesLoadResponse -> {
                    val gotNoEpisodes = loadResponse.episodes.isEmpty()
                    if (gotNoEpisodes) {
                        logger.invoke("Api ${api.name} got no episodes on ${loadResponse.url}")
                        return TestResult.Fail
                    }
                    loadResponse.episodes.firstOrNull()?.data
                }
                is LiveStreamLoadResponse -> {
                    loadResponse.dataUrl
                }
                else -> {
                    logger.invoke("Unknown load response: ${loadResponse.javaClass.name}")
                    return TestResult.Fail
                }
            } ?: return TestResult.Fail

            return TestResultLoad(url)

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
                Assert.fail("Provider has not implemented load()")
            }
            throw e
        }
    }

    @Throws(AssertionError::class, CancellationException::class)
    private suspend fun testLinkLoading(
        api: MainAPI,
        url: String?,
        logger: (String) -> Unit
    ): TestResult {
        Assert.assertNotNull("Api ${api.name} has invalid url on episode", url)
        if (url == null) return TestResult.Fail // Should never trigger

        var linksLoaded = 0
        try {
            val success = api.loadLinks(url, false, {}) { link ->
                logger.invoke("Video loaded: ${link.name}")
                Assert.assertTrue(
                    "Api ${api.name} returns link with invalid url ${link.url}",
                    link.url.length > 4
                )
                linksLoaded++
            }
            if (success) {
                logger.invoke("Links loaded: $linksLoaded")
                return TestResult(linksLoaded > 0)
            } else {
                Assert.fail("Api ${api.name} returns false on loadLinks() with $linksLoaded links loaded")
            }
        } catch (e: Throwable) {
            when (e) {
                is NotImplementedError -> {
                    Assert.fail("Provider has not implemented loadLinks()")
                }
                else -> {
                    logger.invoke("Failed link loading on ${api.name} using data: $url")
                    throw e
                }
            }
        }
        return TestResult.Pass
    }

    fun getDeferredProviderTests(
        scope: CoroutineScope,
        providers: List<MainAPI>,
        logger: (String) -> Unit,
        callback: (MainAPI, TestResultProvider) -> Unit
    ) {
        providers.forEach { api ->
            scope.launch {
                var log = ""
                fun addToLog(string: String) {
                    log += string + "\n"
                    logger.invoke(string)
                }
                fun getLog(): String {
                    return log.removeSuffix("\n")
                }

                val result = try {
                    addToLog("Trying ${api.name}")

                    // Test Homepage
                    val homepage = testHomepage(api, logger).success
                    Assert.assertTrue("Homepage failed to load", homepage)

                    // Test Search Results
                    val searchResults = testSearch(api)
                    Assert.assertTrue("Failed to get search results", searchResults.success)
                    searchResults as TestResultSearch

                    // Test Load and LoadLinks
                    // Only try the first 3 search results to prevent spamming
                    val success = searchResults.results.take(3).any { searchResponse ->
                        addToLog("Testing search result: ${searchResponse.url}")
                        val loadResponse = testLoad(api, searchResponse, ::addToLog)
                        if (loadResponse !is TestResultLoad) {
                            false
                        } else {
                            testLinkLoading(api, loadResponse.extractorData, ::addToLog).success
                        }
                    }

                    if (success) {
                        logger.invoke("Success ${api.name}")
                        TestResultProvider(true, getLog(), null)
                    } else {
                        logger.invoke("Error ${api.name}")
                        TestResultProvider(false, getLog(), null)
                    }
                } catch (e: Throwable) {
                    TestResultProvider(false, getLog(), e)
                }
                callback.invoke(api, result)
            }
        }
    }
}