package com.lagradost.cloudstream3.network

import android.util.Log
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class ProxyInterceptor(
    private val host: String,
    private val port: Int,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP,
    private val username: String? = null,
    private val password: String? = null,
    private val allowFallback: Boolean = false,
    private val connectTimeoutSeconds: Long = 15L,
    private val readTimeoutSeconds: Long = 15L
) : Interceptor {

    companion object {
        private const val TAG = "ProxyDebug"
    }

    init {
        Log.d(
            TAG,
            "proxy setup: " + listOf(
                "host=$host",
                "port=$port",
                "type=${proxyType.name}",
                "timeouts=${connectTimeoutSeconds}s/${readTimeoutSeconds}s",
                "auth=${if (username != null) "enabled" else "None"}",
                "fallback=${if (allowFallback) "Allowed" else "Disabled"}"
            ).joinToString(separator = ", ")  // Join only the parameters
        )
    }

    private val proxyClient by lazy {
        Log.d(TAG, "Building proxy client for $host:$port")

        val proxy = Proxy(proxyType, InetSocketAddress(host, port))
        OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .apply {
                if (username != null && password != null) {
                    Log.d(TAG, "Configuring proxy credentials")
                    proxyAuthenticator { _, response ->
                        Log.d(TAG, "Authenticating proxy for ${response.request.url}")
                        response.request.newBuilder()
                            .header("Proxy-Authorization", Credentials.basic(username, password))
                            .build()
                    }
                }
            }
            .build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        Log.d(TAG, "Intercepting request to ${chain.request().url.host}")

        return try {
            val response = proxyClient.newCall(chain.request()).execute()

            Log.d(
                TAG,
                "proxy response:" + listOf(
                    "url=${response.request.url}",
                    "status=${response.code}",
                    "headers=${response.headers.size}",
                    "body=${response.body?.contentLength() ?: 0} bytes"
                ).joinToString(separator = " , ")
            )

            when {
                response.code == 407 -> handleProxyAuthenticationError(chain, response)
                !response.isSuccessful -> throw IOException("HTTP ${response.code}")
                else -> response
            }
        } catch (e: IOException) {
            Log.d(
                TAG,
                "proxy error:" + listOf(
                    "type=${e.javaClass.simpleName}",
                    "message=${e.message}",
                    "request=${chain.request().url}"
                ).joinToString(separator = " , ")
            )
            handleProxyError(e, chain)
        }
    }

    private fun handleProxyAuthenticationError(
        chain: Interceptor.Chain,
        response: Response
    ): Response {
        response.close()
        Log.d(TAG, "Proxy authentication failed for $host:$port")
        return if (allowFallback) {
            Log.d(TAG, "Attempting fallback connection")
            fallback(chain)
        } else {
            throw IOException("Proxy authentication required")
        }
    }

    private fun handleProxyError(e: IOException, chain: Interceptor.Chain): Response {
        return when (e) {
            is ConnectException -> {
                Log.d(TAG, "Connection refused to proxy $host:$port")
                if (allowFallback) fallback(chain) else throw e
            }

            is SocketTimeoutException -> {
                Log.d(TAG, "Timeout connecting to proxy (${connectTimeoutSeconds}s)")
                if (allowFallback) fallback(chain) else throw e
            }

            else -> {
                Log.d(TAG, "Unexpected proxy error: ${e.javaClass.simpleName}")
                throw e
            }
        }
    }

    private fun fallback(chain: Interceptor.Chain): Response {
        Log.d(TAG, "Using direct connection to ${chain.request().url.host}")
        return chain.proceed(chain.request()).also { response ->
            Log.d(
                TAG,
                "direct connection: " + listOf(
                    "status=${response.code}",
                    "via=${response.handshake?.tlsVersion ?: "Plaintext"}",
                    "server=${response.header("Server") ?: "Unknown"}"
                ).joinToString(separator = " , ")
            )
        }
    }
}