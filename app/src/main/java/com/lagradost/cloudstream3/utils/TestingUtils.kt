package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.*
import org.junit.Assert
import kotlin.random.Random

object TestingUtils {
    open class TestResult(val success: Boolean) {
        companion object {
            val Pass = TestResult(true)
            val Fail = TestResult(false)
        }
    }

    class Logger {
        enum class LogLevel {
            Normal,
            Warning,
            Error;
        }

        data class Message(val level: LogLevel, val message: String) {
            override fun toString(): String {
                val level = when (this.level) {
                    LogLevel.Normal -> ""
                    LogLevel.Warning -> "Warning: "
                    LogLevel.Error -> "Error: "
                }
                return "$level$message"
            }
        }

        private val messageLog = mutableListOf<Message>()

        fun getRawLog(): List<Message> = messageLog

        fun log(message: String) {
            messageLog.add(Message(LogLevel.Normal, message))
        }

        fun warn(message: String) {
            messageLog.add(Message(LogLevel.Warning, message))
        }

        fun error(message: String) {
            messageLog.add(Message(LogLevel.Error, message))
        }
    }

    class TestResultList(val results: List<SearchResponse>) : TestResult(true)
    class TestResultLoad(val extractorData: String, val shouldLoadLinks: Boolean) : TestResult(true)

    class TestResultProvider(
        success: Boolean,
        val log: List<Logger.Message>,
        val exception: Throwable?
    ) :
        TestResult(success)

    @Throws(AssertionError::class, CancellationException::class)
    suspend fun testHomepage(
        api: MainAPI,
        logger: Logger
    ): TestResult {
        if (api.hasMainPage) {
            try {
                val f = api.mainPage.first()
                val homepage =
                    api.getMainPage(1, MainPageRequest(f.name, f.data, f.horizontalImages))
                when {
                    homepage == null -> {
                        logger.error("Provider ${api.name} did not correctly load homepage!")
                    }

                    homepage.items.isEmpty() -> {
                        logger.warn("Provider ${api.name} does not contain any homepage rows!")
                    }

                    homepage.items.any { it.list.isEmpty() } -> {
                        logger.warn("Provider ${api.name} does not have any items in a homepage row!")
                    }
                }
                val homePageList = homepage?.items?.flatMap { it.list } ?: emptyList()
                return TestResultList(homePageList)
            } catch (e: Throwable) {
                when (e) {
                    is NotImplementedError -> {
                        Assert.fail("Provider marked as hasMainPage, while in reality is has not been implemented")
                    }

                    is CancellationException -> {
                        throw e
                    }

                    else -> {
                        e.message?.let { logger.warn("Exception thrown when loading homepage: \"$it\"") }
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
        logger: Logger,
    ): TestResult {
        val searchResults = testQueries.firstNotNullOfOrNull { query ->
            try {
                logger.log("Searching for: $query")
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
            Assert.fail("Api ${api.name} did not return any search responses")
            TestResult.Fail // Should not be reached
        } else {
            TestResultList(searchResults)
        }
    }


    @Throws(AssertionError::class, CancellationException::class)
    private suspend fun testLoad(
        api: MainAPI,
        result: SearchResponse,
        logger: Logger
    ): TestResult {
        try {
            if (result.apiName != api.name) {
                logger.warn("Wrong apiName on SearchResponse: ${api.name} != ${result.apiName}")
            }

            val loadResponse = api.load(result.url)

            if (loadResponse == null) {
                logger.error("Returned null loadResponse on ${result.url} on ${api.name}")
                return TestResult.Fail
            }

            if (loadResponse.apiName != api.name) {
                logger.warn("Wrong apiName on LoadResponse: ${api.name} != ${loadResponse.apiName}")
            }

            if (!api.supportedTypes.contains(loadResponse.type)) {
                logger.warn("Api ${api.name} on load does not contain any of the supportedTypes: ${loadResponse.type}")
            }

            val url = when (loadResponse) {
                is AnimeLoadResponse -> {
                    val gotNoEpisodes =
                        loadResponse.episodes.keys.isEmpty() || loadResponse.episodes.keys.any { loadResponse.episodes[it].isNullOrEmpty() }

                    if (gotNoEpisodes) {
                        logger.error("Api ${api.name} got no episodes on ${loadResponse.url}")
                        return TestResult.Fail
                    }

                    (loadResponse.episodes[loadResponse.episodes.keys.firstOrNull()])?.firstOrNull()?.data
                }

                is MovieLoadResponse -> {
                    val gotNoEpisodes = loadResponse.dataUrl.isBlank()
                    if (gotNoEpisodes) {
                        logger.error("Api ${api.name} got no movie on ${loadResponse.url}")
                        return TestResult.Fail
                    }

                    loadResponse.dataUrl
                }

                is TvSeriesLoadResponse -> {
                    val gotNoEpisodes = loadResponse.episodes.isEmpty()
                    if (gotNoEpisodes) {
                        logger.error("Api ${api.name} got no episodes on ${loadResponse.url}")
                        return TestResult.Fail
                    }
                    loadResponse.episodes.firstOrNull()?.data
                }

                is LiveStreamLoadResponse -> {
                    loadResponse.dataUrl
                }

                else -> {
                    logger.error("Unknown load response: ${loadResponse.javaClass.name}")
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
                Assert.fail("Provider has not implemented load()")
            }
            throw e
        }
    }

    @Throws(AssertionError::class, CancellationException::class)
    private suspend fun testLinkLoading(
        api: MainAPI,
        url: String?,
        logger: Logger
    ): TestResult {
        Assert.assertNotNull("Api ${api.name} has invalid url on episode", url)
        if (url == null) return TestResult.Fail // Should never trigger

        var linksLoaded = 0
        try {
            val success = api.loadLinks(url, false, {}) { link ->
                logger.log("Video loaded: ${link.name}")
                Assert.assertTrue(
                    "Api ${api.name} returns link with invalid url ${link.url}",
                    link.url.length > 4
                )
                linksLoaded++
            }
            if (success) {
                logger.log("Links loaded: $linksLoaded")
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
                    logger.error("Failed link loading on ${api.name} using data: $url")
                    throw e
                }
            }
        }
        return TestResult.Pass
    }

    fun getDeferredProviderTests(
        scope: CoroutineScope,
        providers: Array<MainAPI>,
        callback: (MainAPI, TestResultProvider) -> Unit
    ) {
        providers.forEach { api ->
            scope.launch {
                val logger = Logger()

                val result = try {
                    logger.log("Trying ${api.name}")

                    // Test Homepage
                    val homepage = testHomepage(api, logger)
                    Assert.assertTrue("Homepage failed to load", homepage.success)
                    val homePageList = (homepage as? TestResultList)?.results ?: emptyList()

                    // Test Search Results
                    val searchQueries =
                        // Use the random 3 home page results as queries since they are guaranteed to exist
                        (homePageList.shuffled(Random).take(3).map { it.name.split(" ").first() } +
                                // If home page is sparse then use generic search queries
                                listOf("over", "iron", "guy")).take(3)

                    val searchResults = testSearch(api, searchQueries, logger)
                    Assert.assertTrue("Failed to get search results", searchResults.success)
                    searchResults as TestResultList

                    // Test Load and LoadLinks
                    // Only try the first 3 search results to prevent spamming
                    val success = searchResults.results.take(3).any { searchResponse ->
                        logger.log("Testing search result: ${searchResponse.url}")
                        val loadResponse = testLoad(api, searchResponse, logger)
                        if (loadResponse !is TestResultLoad) {
                            false
                        } else {
                            if (loadResponse.shouldLoadLinks) {
                                testLinkLoading(api, loadResponse.extractorData, logger).success
                            } else {
                                logger.log("Skipping link loading test")
                                true
                            }
                        }
                    }

                    if (success) {
                        logger.log("Success ${api.name}")
                        TestResultProvider(true, logger.getRawLog(), null)
                    } else {
                        logger.error("Link loading failed")
                        TestResultProvider(false, logger.getRawLog(), null)
                    }
                } catch (e: Throwable) {
                    TestResultProvider(false, logger.getRawLog(), e)
                }
                callback.invoke(api, result)
            }
        }
    }
}