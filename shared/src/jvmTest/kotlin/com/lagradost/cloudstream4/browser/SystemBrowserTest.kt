package com.lagradost.cloudstream4.browser

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.network.DesktopWebView
import com.lagradost.cloudstream3.network.WebViewResolver
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the installed Edge or Chrome, so it only runs when asked for with
 * -Dcloudstream.browserTests=true (gradle: -PbrowserTests), on a machine with one of them.
 */
class SystemBrowserTest {
    private val enabled = System.getProperty("cloudstream.browserTests") == "true" && SystemBrowser.find() != null

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/player") { exchange ->
            val page = """
                <html><head><title>Player</title></head><body>
                <script>
                  document.cookie = "seen=yes; path=/";
                  fetch("/stream/master.m3u8?token=abc", { headers: { "X-Token": "abc" } });
                </script>
                </body></html>
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, page.size.toLong())
            exchange.responseBody.use { it.write(page) }
        }
        // Like Cloudflare's check: passed in a window once the test lets it, which sets a cookie
        createContext("/guarded") { exchange ->
            val passed = exchange.requestHeaders["Cookie"].orEmpty().any { "passed=1" in it }
            if (!passed && guardOpen) exchange.responseHeaders.add("Set-Cookie", "passed=1; Path=/; Max-Age=600")
            val page = when {
                passed -> """<html><head><title>Player</title></head><body><script>fetch("/stream/guarded.m3u8");</script></body></html>"""
                guardOpen -> "<html><head><title>Passed</title></head><body>You are human</body></html>"
                else -> "<html><head><title>Just a moment...</title></head><body>Checking your browser</body></html>"
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, page.size.toLong())
            exchange.responseBody.use { it.write(page) }
        }
        // Like an ad: opens more tabs, which Windows would list in Alt+Tab
        createContext("/ads") { exchange ->
            val page = """
                <html><head><title>Ads</title></head><body>
                <a id="ad" href="/stream/ad" target="_blank">ad</a>
                <script>
                  window.open("/stream/popup");
                  document.getElementById("ad").click();
                  setTimeout(() => fetch("/stream/ads.m3u8"), 1000);
                </script>
                </body></html>
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, page.size.toLong())
            exchange.responseBody.use { it.write(page) }
        }
        createContext("/stream") { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.close()
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"
    @Volatile private var guardOpen = false
    private val browser = if (enabled) SystemBrowser(SystemBrowser.find()!!, Files.createTempDirectory("cs-browser").toFile()) else null

    @AfterTest
    fun stop() {
        server.stop(0)
        browser?.stop()
        DesktopWebView.engine = null
    }

    @Test
    fun findsTheRequestAPageMakesAndItsCookies() = runBlocking {
        if (browser == null) return@runBlocking
        DesktopWebView.engine = browser
        val (found, extra) = WebViewResolver(Regex("""master\.m3u8"""), additionalUrls = listOf(Regex("/player")), timeout = 30_000)
            .resolveUsingWebView("$base/player", referer = "https://referer.invalid/")
        assertNotNull(found, "the page's request for the stream")
        assertEquals("$base/stream/master.m3u8?token=abc", found.url.toString())
        assertEquals("abc", found.header("X-Token"))
        assertEquals(listOf("$base/player"), extra.map { it.url.toString() })
        assertTrue(browser.cookies("$base/player").orEmpty().contains("seen=yes"))
        assertTrue(WebViewResolver.webViewUserAgent.orEmpty().contains("Mozilla"))
        // Sites refuse a headless browser, which says so in its user agent
        assertFalse(WebViewResolver.webViewUserAgent.orEmpty().contains("Headless"))
        assertFalse(found.header("User-Agent").orEmpty().contains("Headless"))
    }

    @Test
    fun keepsNoTabOnceAPageIsLoaded() = runBlocking {
        if (browser == null) return@runBlocking
        DesktopWebView.engine = browser
        val (found, _) = WebViewResolver(Regex("""ads\.m3u8"""), timeout = 30_000).resolveUsingWebView("$base/ads")
        assertNotNull(found, "the page's request for the stream")
        // Neither the tab the browser starts with nor the page's popups are left behind
        delay(1_000)
        assertEquals(emptyList(), browser.tabs())
    }

    /** A test page behind Cloudflare's check (it answers 403 to plain clients), so only with -PcloudflareTest as well */
    @Test
    fun passesCloudflaresCheck() = runBlocking {
        if (browser == null || System.getProperty("cloudstream.cloudflareTest") != "true") return@runBlocking
        DesktopWebView.engine = browser
        val url = System.getProperty("cloudstream.cloudflareUrl").orEmpty().ifBlank { "https://www.scrapingcourse.com/cloudflare-challenge" }
        val direct = app.get(url, timeout = 30)
        println("Without the browser: ${direct.code} ${direct.headers["Server"]}")
        val response = app.get(url, interceptor = CloudflareKiller(), timeout = 90)
        val heading = Regex("<title>(.*?)</title>|<h1[^>]*>(.*?)</h1>").findAll(response.text).joinToString(" / ") { it.value }
        println("With the browser: ${response.code}: $heading")
        assertEquals(200, response.code)
    }

    /** Opens a browser window for a moment, for the check */
    @Test
    fun aSiteThatWantsAPersonGivesUpAtOnceAndIsPassedInAWindowWhenAsked() = runBlocking {
        if (browser == null) return@runBlocking
        DesktopWebView.engine = browser
        val asked = async(start = CoroutineStart.UNDISPATCHED) { browser.asked.first() }
        val start = System.nanoTime()
        val (blocked, _) = WebViewResolver(Regex("""guarded\.m3u8"""), timeout = 60_000).resolveUsingWebView("$base/guarded")
        assertNull(blocked)
        // It gave up once the check showed it wants a person, not after the whole minute
        assertTrue(System.nanoTime() - start < 30_000_000_000, "gave up after ${(System.nanoTime() - start) / 1_000_000} ms")
        assertEquals("127.0.0.1", asked.await())

        guardOpen = true
        assertTrue(browser.pass("127.0.0.1"))
        // The cookie of the passed check came back to the browser without a window
        val (found, _) = WebViewResolver(Regex("""guarded\.m3u8"""), timeout = 30_000).resolveUsingWebView("$base/guarded")
        assertNotNull(found, "the page's request after the check")
    }

    @Test
    fun withoutABrowserNothingIsFound() = runBlocking {
        DesktopWebView.engine = null
        val (found, extra) = WebViewResolver(Regex("x")).resolveUsingWebView("$base/player")
        assertNull(found)
        assertTrue(extra.isEmpty())
    }
}
