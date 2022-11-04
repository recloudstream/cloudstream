package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.content.Context
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.nicehttp.Requests
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
    val naiveTrustManager = @SuppressLint("CustomX509TrustManager")
    object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
    }

    val insecureSocketFactory = SSLContext.getInstance("SSL").apply {
        val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
        init(null, trustAllCerts, SecureRandom())
    }.socketFactory

    sslSocketFactory(insecureSocketFactory, naiveTrustManager)
    hostnameVerifier { _, _ -> true }
    return this
}

fun Requests.initClient(context: Context): OkHttpClient {
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
    val dns = settingsManager.getInt(context.getString(R.string.dns_pref), 0)
    baseClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .ignoreAllSSLErrors()
        .cache(
            // Note that you need to add a ResponseInterceptor to make this 100% active.
            // The server response dictates if and when stuff should be cached.
            Cache(
                directory = File(context.cacheDir, "http_cache"),
                maxSize = 50L * 1024L * 1024L // 50 MiB
            )
        ).apply {
            when (dns) {
                1 -> addGoogleDns()
                2 -> addCloudFlareDns()
//                3 -> addOpenDns()
                4 -> addAdGuardDns()
                5 -> addDNSWatchDns()
                6 -> addQuad9Dns()
            }
        }
        // Needs to be build as otherwise the other builders will change this object
        .build()
    return baseClient
}

//val Request.cookies: Map<String, String>
//    get() {
//        return this.headers.getCookies("Cookie")
//    }

private val DEFAULT_HEADERS = mapOf("user-agent" to USER_AGENT)

/**
 * Set headers > Set cookies > Default headers > Default Cookies
 * TODO REMOVE AND REPLACE WITH NICEHTTP
 */
fun getHeaders(
    headers: Map<String, String>,
    cookie: Map<String, String>
): Headers {
    val cookieMap =
        if (cookie.isNotEmpty()) mapOf(
            "Cookie" to cookie.entries.joinToString(" ") {
                "${it.key}=${it.value};"
            }) else mapOf()
    val tempHeaders = (DEFAULT_HEADERS + headers + cookieMap)
    return tempHeaders.toHeaders()
}