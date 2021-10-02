package com.lagradost.cloudstream3.network

import com.lagradost.cloudstream3.USER_AGENT
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit

private const val DEFAULT_TIME = 10
private val DEFAULT_TIME_UNIT = TimeUnit.MINUTES
private const val DEFAULT_USER_AGENT = USER_AGENT
private val DEFAULT_HEADERS = mapOf("User-Agent" to DEFAULT_USER_AGENT)
private val DEFAULT_DATA: Map<String, String> = mapOf()
private val DEFAULT_COOKIES: Map<String, String> = mapOf()
private val DEFAULT_REFERER: String? = null


// lowercase to avoid conflicts
class response(response: Response) {
    companion object {
        private var responseText: String? = null
        private var responseBody: ResponseBody? = null
    }

    public val rawResponse: Response = response

    val text: String
        get() {
            if (responseText != null) return responseText!!
            if (responseBody == null) responseBody = rawResponse.body

            responseText = responseBody.toString()
            return responseText!!
        }

    val body: ResponseBody
        get() {
            if (responseBody == null) responseBody = rawResponse.body
            return responseBody!!
        }

    val url: String
        get() {
            return rawResponse.request.url.toString()
        }

    val cookies: Map<String, String>
        get() {
            val cookieList =
                rawResponse.headers.filter { it.first.equals("set-cookie", ignoreCase = true) }.getOrNull(0)?.second?.split(";")
            return cookieList?.associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }?.filter { it.key.isNotBlank() && it.value.isNotBlank() } ?: mapOf()
        }

    val headers: Headers
        get() {
            return rawResponse.headers
        }

    val ok: Boolean
        get() {
            return rawResponse.isSuccessful
        }

    val status: Int
        get() {
            return rawResponse.code
        }

}


/** WARNING! CAN ONLY BE READ ONCE */
val Response.text: String
    get() {
        return this.body?.string() ?: ""
    }

val Response.url: String
    get() {
        return this.request.url.toString()
    }

val Response.cookies: Map<String, String>
    get() {
        val cookieList =
            this.headers.filter { it.first.toLowerCase(Locale.ROOT) == "set-cookie" }.getOrNull(0)?.second?.split(";")
        return cookieList?.associate {
            val split = it.split("=")
            (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
        }?.filter { it.key.isNotBlank() && it.value.isNotBlank() } ?: mapOf()
    }

fun getData(data: Map<String, String>): RequestBody {
    val builder = FormBody.Builder()
    data.forEach {
        builder.add(it.key, it.value)
    }
    return builder.build()
}

// https://github.com, id=test -> https://github.com?id=test
fun appendUri(uri: String, appendQuery: String): String {
    val oldUri = URI(uri)
    return URI(
        oldUri.scheme, oldUri.authority, oldUri.path,
        if (oldUri.query == null) appendQuery else oldUri.query + "&" + appendQuery, oldUri.fragment
    ).toString()
}

// Can probably be done recursively
fun addParamsToUrl(url: String, params: Map<String, String>): String {
    var appendedUrl = url
    params.forEach {
        appendedUrl = appendUri(appendedUrl, "${it.key}=${it.value}")
    }
    return appendedUrl
}

fun getCache(cacheTime: Int, cacheUnit: TimeUnit): CacheControl {
    return CacheControl.Builder().maxAge(cacheTime, cacheUnit).build()
}

/**
 * Referer > Set headers > Set cookies > Default headers > Default Cookies
 */
fun getHeaders(headers: Map<String, String>, referer: String?, cookie: Map<String, String>): Headers {
    val refererMap = (referer ?: DEFAULT_REFERER)?.let { mapOf("referer" to it) } ?: mapOf()
    val cookieHeaders = (DEFAULT_COOKIES + cookie)
    val cookieMap = if(cookieHeaders.isNotEmpty()) mapOf("Cookie" to cookieHeaders.entries.joinToString(separator = "; ") {
        "${it.key}=${it.value};"
    }) else mapOf()
    val tempHeaders = (DEFAULT_HEADERS + cookieMap + headers + refererMap)
    return tempHeaders.toHeaders()
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
    timeout: Long = 0L
): response {
    val client = OkHttpClient().newBuilder()
        .followRedirects(allowRedirects)
        .followSslRedirects(allowRedirects)
        .callTimeout(timeout, TimeUnit.SECONDS)
        .build()

    val request = getRequestCreator(url, headers, referer, params, cookies, cacheTime, cacheUnit)
    return response(client.newCall(request).execute())
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
    timeout: Long = 0L
): response {
    val client = OkHttpClient().newBuilder()
        .followRedirects(allowRedirects)
        .followSslRedirects(allowRedirects)
        .callTimeout(timeout, TimeUnit.SECONDS)
        .build()
    val request = postRequestCreator(url, headers, referer, params, cookies, data, cacheTime, cacheUnit)
    return response(client.newCall(request).execute())
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
