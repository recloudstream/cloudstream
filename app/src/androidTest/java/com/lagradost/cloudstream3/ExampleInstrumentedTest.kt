package com.lagradost.cloudstream3

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    //@Test
    //fun useAppContext() {
    //    // Context of the app under test.
    //    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    //    assertEquals("com.lagradost.cloudstream3", appContext.packageName)
    //}

    private fun getAllProviders(): List<MainAPI> {
        return APIHolder.allProviders //.filter { !it.usesWebView }
    }

    private suspend fun loadLinks(api: MainAPI, url: String?): Boolean {
        Assert.assertNotNull("Api ${api.name} has invalid url on episode", url)
        if (url == null) return true
        var linksLoaded = 0
        try {
            val success = api.loadLinks(url, false, {}) { link ->
                Assert.assertTrue(
                    "Api ${api.name} returns link with invalid Quality",
                    Qualities.values().map { it.value }.contains(link.quality)
                )
                Assert.assertTrue(
                    "Api ${api.name} returns link with invalid url ${link.url}",
                    link.url.length > 4
                )
                linksLoaded++
            }
            if (success) {
                return linksLoaded > 0
            }
            Assert.assertTrue("Api ${api.name} has returns false on .loadLinks", success)
        } catch (e: Exception) {
            if (e.cause is NotImplementedError) {
                Assert.fail("Provider has not implemented .loadLinks")
            }
            logError(e)
        }
        return true
    }

    private suspend fun testSingleProviderApi(api: MainAPI): Boolean {
        val searchQueries = listOf("over", "iron", "guy")
        var correctResponses = 0
        var searchResult: List<SearchResponse>? = null
        for (query in searchQueries) {
            val response = try {
                api.search(query)
            } catch (e: Exception) {
                if (e.cause is NotImplementedError) {
                    Assert.fail("Provider has not implemented .search")
                }
                logError(e)
                null
            }
            if (!response.isNullOrEmpty()) {
                correctResponses++
                if (searchResult == null) {
                    searchResult = response
                }
            }
        }

        if (correctResponses == 0 || searchResult == null) {
            System.err.println("Api ${api.name} did not return any valid search responses")
            return false
        }

        try {
            var validResults = false
            for (result in searchResult) {
                Assert.assertEquals(
                    "Invalid apiName on response on ${api.name}",
                    result.apiName,
                    api.name
                )
                val load = api.load(result.url) ?: continue
                Assert.assertEquals(
                    "Invalid apiName on load on ${api.name}",
                    load.apiName,
                    result.apiName
                )
                Assert.assertTrue(
                    "Api ${api.name} on load does not contain any of the supportedTypes",
                    api.supportedTypes.contains(load.type)
                )
                when (load) {
                    is AnimeLoadResponse -> {
                        val gotNoEpisodes =
                            load.episodes.keys.isEmpty() || load.episodes.keys.any { load.episodes[it].isNullOrEmpty() }

                        if (gotNoEpisodes) {
                            println("Api ${api.name} got no episodes on ${load.url}")
                            continue
                        }

                        val url = (load.episodes[load.episodes.keys.first()])?.first()?.data
                        validResults = loadLinks(api, url)
                        if (!validResults) continue
                    }
                    is MovieLoadResponse -> {
                        val gotNoEpisodes = load.dataUrl.isBlank()
                        if (gotNoEpisodes) {
                            println("Api ${api.name} got no movie on ${load.url}")
                            continue
                        }

                        validResults = loadLinks(api, load.dataUrl)
                        if (!validResults) continue
                    }
                    is TvSeriesLoadResponse -> {
                        val gotNoEpisodes = load.episodes.isEmpty()
                        if (gotNoEpisodes) {
                            println("Api ${api.name} got no episodes on ${load.url}")
                            continue
                        }

                        validResults = loadLinks(api, load.episodes.first().data)
                        if (!validResults) continue
                    }
                }
                break
            }
            if(!validResults) {
                System.err.println("Api ${api.name} did not load on any")
            }

            return validResults
        } catch (e: Exception) {
            if (e.cause is NotImplementedError) {
                Assert.fail("Provider has not implemented .load")
            }
            logError(e)
            return false
        }
    }

    @Test
    fun providersExist() {
        Assert.assertTrue(getAllProviders().isNotEmpty())
        println("Done providersExist")
    }

    @Test
    fun providerCorrectData() {
        val isoNames = SubtitleHelper.languages.map { it.ISO_639_1 }
        Assert.assertFalse("ISO does not contain any languages", isoNames.isNullOrEmpty())
        for (api in getAllProviders()) {
            Assert.assertTrue("Api does not contain a mainUrl", api.mainUrl != "NONE")
            Assert.assertTrue("Api does not contain a name", api.name != "NONE")
            Assert.assertTrue(
                "Api ${api.name} does not contain a valid language code",
                isoNames.contains(api.lang)
            )
            Assert.assertTrue(
                "Api ${api.name} does not contain any supported types",
                api.supportedTypes.isNotEmpty()
            )
        }
        println("Done providerCorrectData")
    }

    @Test
    fun providerCorrectHomepage() {
        runBlocking {
            getAllProviders().apmap { api ->
                if (api.hasMainPage) {
                    try {
                        val homepage = api.getMainPage()
                        when {
                            homepage == null -> {
                                System.err.println("Homepage provider ${api.name} did not correctly load homepage!")
                            }
                            homepage.items.isEmpty() -> {
                                System.err.println("Homepage provider ${api.name} does not contain any items!")
                            }
                            homepage.items.any { it.list.isEmpty() } -> {
                                System.err.println ("Homepage provider ${api.name} does not have any items on result!")
                            }
                        }
                    } catch (e: Exception) {
                        if (e.cause is NotImplementedError) {
                            Assert.fail("Provider marked as hasMainPage, while in reality is has not been implemented")
                        }
                        logError(e)
                    }
                }
            }
        }
        println("Done providerCorrectHomepage")
    }

//    @Test
//    fun testSingleProvider() {
//        testSingleProviderApi(ThenosProvider())
//    }

    @Test
    fun providerCorrect() {
        runBlocking {
            val invalidProvider = ArrayList<Pair<MainAPI, Exception?>>()
            val providers = getAllProviders()
            providers.apmap { api ->
                try {
                    println("Trying $api")
                    if (testSingleProviderApi(api)) {
                        println("Success $api")
                    } else {
                        System.err.println("Error $api")
                        invalidProvider.add(Pair(api, null))
                    }
                } catch (e: Exception) {
                    logError(e)
                    invalidProvider.add(Pair(api, e))
                }
            }
            if(invalidProvider.isEmpty()) {
                println("No Invalid providers! :D")
            } else {
                println("Invalid providers are: ")
                for (provider in invalidProvider) {
                    println("${provider.first}")
                }
            }
        }
        println("Done providerCorrect")
    }
}
