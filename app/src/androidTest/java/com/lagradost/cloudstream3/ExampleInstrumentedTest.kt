package com.lagradost.cloudstream3

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.TestingUtils
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
    private fun getAllProviders(): List<MainAPI> {
        println("Providers: ${APIHolder.allProviders.size}")
        return APIHolder.allProviders //.filter { !it.usesWebView }
    }

    @Test
    fun providersExist() {
        Assert.assertTrue(getAllProviders().isNotEmpty())
        println("Done providersExist")
    }

    @Test
    @Throws(AssertionError::class)
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
            getAllProviders().amap { api ->
                TestingUtils.testHomepage(api, ::println)
            }
        }
        println("Done providerCorrectHomepage")
    }

    @Test
    fun testAllProvidersCorrect() {
        runBlocking {
            TestingUtils.getDeferredProviderTests(
                this,
                getAllProviders(),
                ::println
            ) { _, _ -> }
        }
    }
}
