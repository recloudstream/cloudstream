package com.lagradost.cloudstream3.network

import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.AnyThread
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugWarning
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.nicehttp.cookies
import kotlinx.coroutines.runBlocking
import okhttp3.*
import java.net.URI


@AnyThread
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    init {
        // Needs to clear cookies between sessions to generate new cookies.
        normalSafeApiCall {
            // This can throw an exception on unsupported devices :(
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    /**
     * Gets the headers with cookies, webview user agent included!
     * */
    fun getCookieHeaders(url: String): Headers {
        val userAgentHeaders = WebViewResolver.webViewUserAgent?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        return getHeaders(userAgentHeaders, savedCookies[URI(url).host] ?: emptyMap())
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val cookies = savedCookies[request.url.host]

        if (cookies == null) {
            bypassCloudflare(request)?.let {
                Log.d(TAG, "Succeeded bypassing cloudflare: ${request.url}")
                return@runBlocking it
            }
        } else {
            return@runBlocking proceed(request, cookies)
        }

        debugWarning({ true }) { "Failed cloudflare at: ${request.url}" }
        return@runBlocking chain.proceed(request)
    }

    private fun getWebViewCookie(url: String): String? {
        return normalSafeApiCall {
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
        val userAgentMap = WebViewResolver.getWebViewUserAgent()?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

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