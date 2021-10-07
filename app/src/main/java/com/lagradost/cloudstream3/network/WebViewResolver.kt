package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.*
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
        println("Initial web-view request: $url")
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
                settings.cacheMode
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
                        println("Web-view request finished: $webViewUrl")
                        destroyWebView()
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed() // Ignore ssl issues
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

        println("Web-view timeout after ${totalTime / 1000}s")
        destroyWebView()
        return null
    }

}