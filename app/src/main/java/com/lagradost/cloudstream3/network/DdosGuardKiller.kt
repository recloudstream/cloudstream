package com.lagradost.cloudstream3.network

import androidx.annotation.AnyThread
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.nicehttp.getCookies
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * @param alwaysBypass will pre-emptively fetch ddos guard cookies if true.
 * If false it will only try to get cookies when a request returns 403
 * */
// As seen in https://github.com/anime-dl/anime-downloader/blob/master/anime_downloader/sites/erairaws.py
@AnyThread
class DdosGuardKiller(private val alwaysBypass: Boolean) : Interceptor {
    val savedCookiesMap = mutableMapOf<String, Map<String, String>>()

    private var ddosBypassPath: String? = null

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        if (alwaysBypass) return@runBlocking bypassDdosGuard(request)

        val response = chain.proceed(request)
        return@runBlocking if (response.code == 403) {
            bypassDdosGuard(request)
        } else response
    }

    private suspend fun bypassDdosGuard(request: Request): Response {
        ddosBypassPath = ddosBypassPath ?: Regex("'(.*?)'").find(
            app.get(
                "https://check.ddos-guard.net/check.js"
            ).text
        )?.groupValues?.get(1)

        val cookies =
            savedCookiesMap[request.url.host]
            // If no cookies are found fetch and save em.
                ?: (request.url.scheme + "://" + request.url.host + (ddosBypassPath ?: "")).let {
                    app.get(it, cacheTime = 0).cookies.also { cookies ->
                        savedCookiesMap[request.url.host] = cookies
                    }
                }

        val headers = getHeaders(request.headers.toMap(), cookies + request.cookies)
        return app.baseClient.newCall(
            request.newBuilder()
                .headers(headers)
                .build()
        ).await()
    }
}