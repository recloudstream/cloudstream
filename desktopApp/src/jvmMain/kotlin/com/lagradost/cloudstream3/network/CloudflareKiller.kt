package com.lagradost.cloudstream3.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * On Android this solves Cloudflare challenges with a WebView. There is no WebView on desktop,
 * so requests pass through unchanged and sites behind a challenge will fail as they would without it.
 */
class CloudflareKiller : Interceptor {
    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
