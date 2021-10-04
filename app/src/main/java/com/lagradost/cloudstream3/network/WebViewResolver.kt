package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

class WebViewResolver(val interceptUrl: Regex) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return runBlocking {
            val fixedRequest = resolveUsingWebView(request)
            return@runBlocking chain.proceed(fixedRequest ?: request)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveUsingWebView(request: Request): Request? {
        val url = request.url.toString()
        var webView: WebView? = null

        fun destroyWebView() {
            main {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
                println("Destroyed webview")
            }
        }

        var fixedRequest: Request? = null

        main {
            webView = WebView(
                AcraApplication.context ?: throw RuntimeException("No base context in WebViewResolver")
            ).apply {
                settings.javaScriptEnabled = true
            }

            webView?.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val webViewUrl = request.url.toString()
                    if (interceptUrl.containsMatchIn(webViewUrl)) {
                        fixedRequest = getRequestCreator(
                            webViewUrl,
                            request.requestHeaders,
                            null,
                            mapOf(),
                            mapOf(),
                            10,
                            TimeUnit.MINUTES
                        )
                        destroyWebView()
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            webView?.loadUrl(url)
        }

        var loop = 0
        // Timeouts after this amount, 20s
        val totalTime = 20000L
        val delayTime = 100L

        // A bit sloppy, but couldn't find a better way
        while (loop < totalTime / delayTime) {
            if (fixedRequest != null) return fixedRequest
            delay(delayTime)
            loop += 1
        }

        destroyWebView()
        return null
    }

}