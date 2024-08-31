package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ignoreAllSSLErrors
import okhttp3.Cache
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import java.io.File
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

fun Requests.initClient(context: Context): OkHttpClient {

    // see trust manager function below, this lib was used earlier
    // normalSafeApiCall { Security.insertProviderAt(Conscrypt.newProvider(), 1) }
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
    val dns = settingsManager.getInt(context.getString(R.string.dns_pref), 0)

    val builder = OkHttpClient.Builder()
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
        )
    when (dns) {
        1 -> builder.addGoogleDns()
        2 -> builder.addCloudFlareDns()
        // 3 -> builder.addOpenDns()
        4 -> builder.addAdGuardDns()
        5 -> builder.addDNSWatchDns()
        6 -> builder.addQuad9Dns()
    }

    // Apply the custom SSL settings on Android versions <= 9 (API level 28)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        normalSafeApiCall {
            val sslSocketFactory = getUnsafeSSLSocketFactory()
            builder.sslSocketFactory(sslSocketFactory, TrustAllCerts())
            builder.hostnameVerifier { _, _ -> true }
        }
    }

    baseClient = builder.build()
    return baseClient
}

// Trust is an expensive thing, Cheap people can't afford it

/** So what happens in older android versions like 9 is that their network security provider is
 * not much robust, and some extensions store data in non protected cheap servers and urls, thus
 * the app rejects connection, We used google conscrypt provider lib earlier but its version 5.2.3
 * broke and crashed the app without resolving certs, so we removed it and implemented own trust
 * manager that trust all certificates + network_security_config.xml especially for Android 9 =-below
 **/
@SuppressLint("CustomX509TrustManager")
class TrustAllCerts : X509TrustManager {
    @SuppressLint("TrustAllX509TrustManager")
    override fun checkClientTrusted(
        chain: Array<java.security.cert.X509Certificate>,
        authType: String
    ) {
        // Trust all client certificates
    }

    @SuppressLint("TrustAllX509TrustManager")
    override fun checkServerTrusted(
        chain: Array<java.security.cert.X509Certificate>,
        authType: String
    ) {
        // Trust all server certificates
    }

    @SuppressLint("TrustAllX509TrustManager")
    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
        return arrayOf()
    }
}

/**
 * Creates an SSLSocketFactory that uses a TrustManager which trusts all certificates.
 * @return A custom SSLSocketFactory for bypassing SSL verification.
 */
fun getUnsafeSSLSocketFactory(): SSLSocketFactory {
    val trustAllCerts = arrayOf<TrustManager>(TrustAllCerts())

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
    return sslContext.socketFactory
}

private val DEFAULT_HEADERS = mapOf("User-Agent" to USER_AGENT)

/**
 * Set headers > Set cookies > Default headers > Default Cookies
 */
fun getHeaders(
    headers: HashMap<String, String>,
    cookie: HashMap<String, String>
): Headers {
    val cookieMap =
        if (cookie.isNotEmpty()) mapOf(
            "Cookie" to cookie.entries.joinToString(" ") {
                "${it.key}=${it.value};"
            }) else mapOf()
    val tempHeaders = (DEFAULT_HEADERS + headers + cookieMap)
    return tempHeaders.toHeaders()
}