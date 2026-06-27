package com.lagradost.cloudstream3.network

import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.annotation.AnyThread
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugWarning
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.nicehttp.cookies
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URI


@AnyThread
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val ERROR_CODES = listOf(403, 503)
        private val CLOUDFLARE_SERVERS = listOf("cloudflare-nginx", "cloudflare")

        /**
         * Whether the system WebView is present and functional.
         *
         * Many Android TV devices (and FireSticks) ship without WebView or with
         * a severely outdated/broken version. When WebView is absent:
         *  - CookieManager.getInstance() throws (the original code has a comment
         *    about this: "can throw on unsupported devices").
         *  - Every subsequent WebView call silently fails via safe{}.
         *  - The Cloudflare challenge is never solved.
         *  - The request goes out without CF cookies → server returns 403
         *    → ExoPlayer reports error 2004 / 2001.
         *
         * We probe once at class-load time and store the result. All WebView-
         * dependent paths are gated on this flag so they are completely skipped
         * on devices where WebView doesn't work, rather than failing silently.
         *
         * On normal phones/tablets this will always be true so behaviour is
         * identical to before.
         */
        val isWebViewAvailable: Boolean = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // API 26+: fast, no WebView instantiation needed.
                WebView.getCurrentWebViewPackage() != null
            } else {
                // API 23-25 (minSdk = 23): fall back to probing CookieManager.
                CookieManager.getInstance() != null
            }
        }.getOrDefault(false)

        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    init {
        if (isWebViewAvailable) {
            // Needs to clear cookies between sessions to generate new cookies.
            safe {
                // This can throw an exception on unsupported devices :(
                CookieManager.getInstance().removeAllCookies(null)
            }
        }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    /**
     * Gets the headers with cookies, webview user agent included!
     * Returns plain headers (no WebView UA) when WebView is unavailable.
     * */
    fun getCookieHeaders(url: String): Headers {
        // getWebViewUserAgent() instantiates a WebView internally — skip it
        // when WebView is not available to avoid a crash on Android TV.
        val userAgentHeaders = if (isWebViewAvailable) {
            WebViewResolver.webViewUserAgent?.let { mapOf("user-agent" to it) } ?: emptyMap()
        } else emptyMap()

        return getHeaders(userAgentHeaders, savedCookies[URI(url).host] ?: emptyMap())
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()

        // Skip the entire CF-bypass path when WebView is unavailable (e.g. Android TV).
        // Without a working WebView the challenge can never be solved, so we would
        // just waste time before failing with the same 403. Falling through to the
        // direct request at least works for sources that don't strictly need CF cookies.
        if (!isWebViewAvailable) {
            Log.d(TAG, "WebView unavailable, skipping CF bypass for ${request.url.host}")
            return@runBlocking chain.proceed(request)
        }

        when (val cookies = savedCookies[request.url.host]) {
            null -> {
                val response = chain.proceed(request)
                if(!(response.header("Server") in CLOUDFLARE_SERVERS && response.code in ERROR_CODES)) {
                    return@runBlocking response
                } else {
                    response.close()
                    bypassCloudflare(request)?.let {
                        Log.d(TAG, "Succeeded bypassing cloudflare: ${request.url}")
                        return@runBlocking it
                    }
                }
            }
            else -> {
                return@runBlocking proceed(request, cookies)
            }
        }

        debugWarning({ true }) { "Failed cloudflare at: ${request.url}" }
        return@runBlocking chain.proceed(request)
    }

    private fun getWebViewCookie(url: String): String? {
        return safe {
            CookieManager.getInstance()?.getCookie(url)
        }
    }

    /**
     * Returns true if the cf cookies were successfully fetched from the CookieManager
     * Also saves the cookies.
     * */
    private fun trySolveWithSavedCookies(request: Request): Boolean {
        // Not sure if this takes expiration into account
        return getWebViewCookie(request.url.toString())?.let { cookie ->
            cookie.contains("cf_clearance").also { solved ->
                if (solved) savedCookies[request.url.host] = parseCookieMap(cookie)
            }
        } ?: false
    }

    private suspend fun proceed(request: Request, cookies: Map<String, String>): Response {
        // getWebViewUserAgent() instantiates a WebView internally — only call it
        // when WebView is available. This path is already unreachable when
        // isWebViewAvailable == false (intercept() returns early), but guard it
        // explicitly so future refactors can't accidentally call proceed() directly.
        val userAgentMap = if (isWebViewAvailable) {
            WebViewResolver.getWebViewUserAgent()?.let { mapOf("user-agent" to it) } ?: emptyMap()
        } else emptyMap()

        val headers =
            getHeaders(request.headers.toMap() + userAgentMap, cookies + request.cookies)
        return app.baseClient.newCall(
            request.newBuilder()
                .headers(headers)
                .build()
        ).await()
    }

    private suspend fun bypassCloudflare(request: Request): Response? {
        val url = request.url.toString()

        // If no cookies then try to get them
        // Remove this if statement if cookies expire
        if (!trySolveWithSavedCookies(request)) {
            Log.d(TAG, "Loading webview to solve cloudflare for ${request.url}")
            WebViewResolver(
                // Never exit based on url
                Regex(".^"),
                // Cloudflare needs default user agent
                userAgent = null,
                // Cannot use okhttp (i think intercepting cookies fails which causes the issues)
                useOkhttp = false,
                // Match every url for the requestCallBack
                additionalUrls = listOf(Regex("."))
            ).resolveUsingWebView(
                url
            ) {
                trySolveWithSavedCookies(request)
            }
        }

        val cookies = savedCookies[request.url.host] ?: return null
        return proceed(request, cookies)
    }
}
