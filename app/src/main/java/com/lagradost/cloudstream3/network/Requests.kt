package com.lagradost.cloudstream3.network

import android.content.Context
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit


class Session(
    client: OkHttpClient = app.baseClient
) : Requests() {
    init {
        this.baseClient = client
            .newBuilder()
            .cookieJar(CustomCookieJar())
            .build()
    }

    inner class CustomCookieJar : CookieJar {
        private var cookies = mapOf<String, Cookie>()

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return this.cookies.values.toList()
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies += cookies.map { it.name to it }
        }
    }
}

private const val DEFAULT_TIME = 10
private val DEFAULT_TIME_UNIT = TimeUnit.MINUTES
private const val DEFAULT_USER_AGENT = USER_AGENT
private val DEFAULT_HEADERS = mapOf("user-agent" to DEFAULT_USER_AGENT)
private val DEFAULT_DATA: Map<String, String> = mapOf()
private val DEFAULT_COOKIES: Map<String, String> = mapOf()
private val DEFAULT_REFERER: String? = null

/** WARNING! CAN ONLY BE READ ONCE */
val Response.text: String
    get() {
        return this.body?.string() ?: ""
    }

val Response.url: String
    get() {
        return this.request.url.toString()
    }


fun Headers.getCookies(cookieKey: String): Map<String, String> {
    val cookieList =
        this.filter { it.first.equals(cookieKey, ignoreCase = true) }
            .getOrNull(0)?.second?.split(";")
    return cookieList?.associate {
        val split = it.split("=")
        (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
    }?.filter { it.key.isNotBlank() && it.value.isNotBlank() } ?: mapOf()
}

val Response.cookies: Map<String, String>
    get() {
        return this.headers.getCookies("set-cookie")
    }

val Request.cookies: Map<String, String>
    get() {
        return this.headers.getCookies("Cookie")
    }

class AppResponse(
    val response: Response
) {
    /** Lazy, initialized on use. */
    val text by lazy { response.text }
    val url by lazy { response.url }
    val cookies by lazy { response.cookies }
    val body by lazy { response.body }
    val code = response.code
    val headers = response.headers
    val document: Document by lazy { Jsoup.parse(text) }

    /** Same as using mapper.readValue<T>() */
    inline fun <reified T : Any> mapped(): T {
        return mapper.readValue(this.text)
    }
}

private fun getData(data: Map<String, String?>): RequestBody {
    val builder = FormBody.Builder()
    data.forEach {
        it.value?.let { value ->
            builder.add(it.key, value)
        }
    }
    return builder.build()
}

// https://github.com, id=test -> https://github.com?id=test
private fun appendUri(uri: String, appendQuery: String): String {
    val oldUri = URI(uri)
    return URI(
        oldUri.scheme,
        oldUri.authority,
        oldUri.path,
        if (oldUri.query == null) appendQuery else oldUri.query + "&" + appendQuery,
        oldUri.fragment
    ).toString()
}

// Can probably be done recursively
private fun addParamsToUrl(url: String, params: Map<String, String?>): String {
    var appendedUrl = url
    params.forEach {
        it.value?.let { value ->
            appendedUrl = appendUri(appendedUrl, "${it.key}=${value}")
        }
    }
    return appendedUrl
}

private fun getCache(cacheTime: Int, cacheUnit: TimeUnit): CacheControl {
    return CacheControl.Builder().maxStale(cacheTime, cacheUnit).build()
}

/**
 * Referer > Set headers > Set cookies > Default headers > Default Cookies
 */
fun getHeaders(
    headers: Map<String, String>,
    referer: String?,
    cookie: Map<String, String>
): Headers {
    val refererMap = (referer ?: DEFAULT_REFERER)?.let { mapOf("referer" to it) } ?: mapOf()
    val cookieHeaders = (DEFAULT_COOKIES + cookie)
    val cookieMap =
        if (cookieHeaders.isNotEmpty()) mapOf(
            "Cookie" to cookieHeaders.entries.joinToString(" ") {
                "${it.key}=${it.value};"
            }) else mapOf()
    val tempHeaders = (DEFAULT_HEADERS + headers + cookieMap + refererMap)
    return tempHeaders.toHeaders()
}

fun postRequestCreator(
    url: String,
    headers: Map<String, String>,
    referer: String?,
    params: Map<String, String>,
    cookies: Map<String, String>,
    data: Map<String, String>,
    cacheTime: Int,
    cacheUnit: TimeUnit
): Request {
    return Request.Builder()
        .url(addParamsToUrl(url, params))
        .cacheControl(getCache(cacheTime, cacheUnit))
        .headers(getHeaders(headers, referer, cookies))
        .post(getData(data))
        .build()
}

fun getRequestCreator(
    url: String,
    headers: Map<String, String>,
    referer: String?,
    params: Map<String, String>,
    cookies: Map<String, String>,
    cacheTime: Int,
    cacheUnit: TimeUnit
): Request {
    return Request.Builder()
        .url(addParamsToUrl(url, params))
        .cacheControl(getCache(cacheTime, cacheUnit))
        .headers(getHeaders(headers, referer, cookies))
        .build()
}

fun putRequestCreator(
    url: String,
    headers: Map<String, String>,
    referer: String?,
    params: Map<String, String?>,
    cookies: Map<String, String>,
    data: Map<String, String?>,
    cacheTime: Int,
    cacheUnit: TimeUnit
): Request {
    return Request.Builder()
        .url(addParamsToUrl(url, params))
        .cacheControl(getCache(cacheTime, cacheUnit))
        .headers(getHeaders(headers, referer, cookies))
        .put(getData(data))
        .build()
}

open class Requests {
    var baseClient = OkHttpClient()

    fun initClient(context: Context): OkHttpClient {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val dns = settingsManager.getInt(context.getString(R.string.dns_pref), 0)
        baseClient = OkHttpClient.Builder()
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
                }
            }
            // Needs to be build as otherwise the other builders will change this object
            .build()
        return baseClient
    }

    fun get(
        url: String,
        headers: Map<String, String> = mapOf(),
        referer: String? = null,
        params: Map<String, String> = mapOf(),
        cookies: Map<String, String> = mapOf(),
        allowRedirects: Boolean = true,
        cacheTime: Int = DEFAULT_TIME,
        cacheUnit: TimeUnit = DEFAULT_TIME_UNIT,
        timeout: Long = 0L,
        interceptor: Interceptor? = null,
    ): AppResponse {
        val client = baseClient
            .newBuilder()
            .followRedirects(allowRedirects)
            .followSslRedirects(allowRedirects)
            .callTimeout(timeout, TimeUnit.SECONDS)

        if (interceptor != null) client.addInterceptor(interceptor)
        val request =
            getRequestCreator(url, headers, referer, params, cookies, cacheTime, cacheUnit)
        val response = client.build().newCall(request).execute()
        return AppResponse(response)
    }

    fun post(
        url: String,
        headers: Map<String, String> = mapOf(),
        referer: String? = null,
        params: Map<String, String> = mapOf(),
        cookies: Map<String, String> = mapOf(),
        data: Map<String, String> = DEFAULT_DATA,
        allowRedirects: Boolean = true,
        cacheTime: Int = DEFAULT_TIME,
        cacheUnit: TimeUnit = DEFAULT_TIME_UNIT,
        timeout: Long = 0L,
    ): AppResponse {
        val client = baseClient
            .newBuilder()
            .followRedirects(allowRedirects)
            .followSslRedirects(allowRedirects)
            .callTimeout(timeout, TimeUnit.SECONDS)
            .build()
        val request =
            postRequestCreator(url, headers, referer, params, cookies, data, cacheTime, cacheUnit)
        val response = client.newCall(request).execute()
        return AppResponse(response)
    }

    fun put(
        url: String,
        headers: Map<String, String> = mapOf(),
        referer: String? = null,
        params: Map<String, String> = mapOf(),
        cookies: Map<String, String> = mapOf(),
        data: Map<String, String?> = DEFAULT_DATA,
        allowRedirects: Boolean = true,
        cacheTime: Int = DEFAULT_TIME,
        cacheUnit: TimeUnit = DEFAULT_TIME_UNIT,
        timeout: Long = 0L
    ): AppResponse {
        val client = baseClient
            .newBuilder()
            .followRedirects(allowRedirects)
            .followSslRedirects(allowRedirects)
            .callTimeout(timeout, TimeUnit.SECONDS)
            .build()
        val request =
            putRequestCreator(url, headers, referer, params, cookies, data, cacheTime, cacheUnit)
        val response = client.newCall(request).execute()
        return AppResponse(response)
    }
}
