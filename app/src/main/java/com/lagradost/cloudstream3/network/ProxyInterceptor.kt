package com.lagradost.cloudstream3.network

import android.util.Log
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import org.xbill.DNS.*
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * An OkHttp Interceptor that routes requests through a proxy with custom DNS resolution.
 *
 * @param host The proxy server hostname or IP address.
 * @param port The proxy server port.
 * @param proxyType The type of proxy (e.g., HTTP, SOCKS). Defaults to HTTP.
 * @param username Optional proxy username for authentication.
 * @param password Optional proxy password for authentication.
 * @param allowFallback Whether to fall back to a direct connection if the proxy fails. Defaults to false.
 * @param connectTimeoutMillis Connection timeout in seconds. Defaults to 15.
 * @param readTimeoutMillis Read timeout in seconds. Defaults to 15.
 * @param dnsServer Optional custom DNS server (e.g., "8.8.8.8" or "cloudflare" for DoH).
 */
class ProxyInterceptor(
    private val host: String,
    private val port: Int,
    private val proxyType: Proxy.Type = Proxy.Type.HTTP,
    private val username: String? = null,
    private val password: String? = null,
    private val allowFallback: Boolean = false,
    private val connectTimeoutMillis: Long = 15_000L,
    private val readTimeoutMillis: Long = 15_000L,
    private val dnsServer: String? = null
) : Interceptor {

    companion object {
        private const val TAG = "ProxyDebug"
        private val DNS_OVER_HTTPS_URLS = mapOf(
            "cloudflare" to "https://cloudflare-dns.com/dns-query",
            "google" to "https://dns.google/dns-query",
            "quad9" to "https://dns.quad9.net/dns-query",
            "adguard" to "https://dns.adguard.com/dns-query"
        )
    }

    private val internalDns by lazy {
        dnsServer?.let { createDnsResolver(it) } ?: Dns.SYSTEM
    }

    private val proxyClient by lazy {
        Log.d(TAG, "Building proxy client for $host:$port")

        val proxy = Proxy(proxyType, InetSocketAddress(host, port))
        OkHttpClient.Builder()
            .proxy(proxy)
            .dns(internalDns)
            .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
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

    /**
     * Creates a custom DNS resolver based on the provided server.
     *
     * @param server The DNS server (e.g., DoH keyword, DoH URL, or IP address).
     * @return A configured Dns instance.
     */
    private fun createDnsResolver(server: String): Dns {
        return when {
            server in DNS_OVER_HTTPS_URLS -> {
                val url = DNS_OVER_HTTPS_URLS.getValue(server)
                DnsOverHttps.Builder()
                    .client(OkHttpClient())
                    .url(url.toHttpUrl())
                    .build()
            }

            server.startsWith("https://") -> {
                try {
                    DnsOverHttps.Builder()
                        .client(OkHttpClient())
                        .url(server.toHttpUrl())
                        .build()
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid DoH URL: $server")
                    Dns.SYSTEM
                }
            }

            else -> {
                Log.d(TAG, "Using dnsjava for custom DNS server: $server")
                val resolver = SimpleResolver(server)
                val cacheA = Lookup.getDefaultCache(Type.A)
                val cacheAAAA = Lookup.getDefaultCache(Type.AAAA)

                Dns { hostname ->
                    try {
                        val lookupA = Lookup(hostname, Type.A)
                        lookupA.setResolver(resolver)
                        lookupA.setCache(cacheA)
                        val aRecords =
                            lookupA.run()?.map { InetAddress.getByName(hostname) } ?: emptyList()

                        val lookupAAAA = Lookup(hostname, Type.AAAA)
                        lookupAAAA.setResolver(resolver)
                        lookupAAAA.setCache(cacheAAAA)
                        val aaaaRecords =
                            lookupAAAA.run()?.map { InetAddress.getByName(hostname) }
                                ?: emptyList()

                        (aRecords + aaaaRecords).ifEmpty {
                            throw IOException("No DNS records found for $hostname")
                        }
                    } catch (e: UnknownHostException) {
                        Log.w(TAG, "DNS lookup failed for $hostname: ${e.message}")
                        Dns.SYSTEM.lookup(hostname)
                    } catch (e: IOException) {
                        Log.w(TAG, "IO error during DNS lookup for $hostname: ${e.message}")
                        Dns.SYSTEM.lookup(hostname)
                    } catch (e: Exception) {
                        Log.e(TAG, "Unexpected error during DNS lookup for $hostname", e)
                        throw e // Rethrow unexpected errors
                    }
                }
            }
        }
    }

    /**
     * Intercepts the request and routes it through the proxy.
     *
     * @param chain The interceptor chain.
     * @return The response from the proxy or fallback.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        Log.d(TAG, "Intercepting request to ${chain.request().url.host}")

        return try {
            val response = proxyClient.newCall(chain.request()).execute()

            Log.d(
                TAG,
                "Proxy response:" + listOf(
                    "url=${response.request.url}",
                    "status=${response.code}",
                    "headers=${response.headers.size}",
                    "body=${response.body.contentLength()} bytes"
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
                "Proxy error:" + listOf(
                    "type=${e.javaClass}",
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
                Log.d(TAG, "Timeout connecting to proxy (${connectTimeoutMillis}s)")
                if (allowFallback) fallback(chain) else throw e
            }

            else -> {
                Log.d(TAG, "Unexpected proxy error: ${e.javaClass}")
                throw e
            }
        }
    }

    private fun fallback(chain: Interceptor.Chain): Response {
        Log.d(TAG, "Using direct connection to ${chain.request().url.host}")
        return chain.proceed(chain.request()).also { response ->
            Log.d(
                TAG,
                "Direct connection: " + listOf(
                    "status=${response.code}",
                    "via=${response.handshake?.tlsVersion ?: "Plaintext"}",
                    "server=${response.header("Server") ?: "Unknown"}"
                ).joinToString(separator = " , ")
            )
        }
    }

    /**
     * Tests the DNS configuration by resolving "example.com".
     *
     * @return True if DNS resolution succeeds, false otherwise.
     */
    fun testDnsConfiguration(): Boolean {
        val testDomain = "example.com"
        Log.d(TAG, "Testing DNS resolution for $testDomain")
        return try {
            val addresses = internalDns.lookup(testDomain)
            if (addresses.isNotEmpty()) {
                Log.d(
                    TAG,
                    "DNS resolution successful: ${addresses.joinToString { it.hostAddress ?: "unknown" }}"
                )
                true
            } else {
                Log.w(TAG, "DNS resolution returned no addresses for $testDomain")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "DNS test failed: ${e.javaClass} - ${e.message}")
            false
        }
    }
}