package com.lagradost.cloudstream3.utils


import khttp.structures.authorization.Authorization
import khttp.structures.cookie.CookieJar
import khttp.structures.files.FileLike
import khttp.structures.cookie.Cookie
import khttp.responses.Response


/**
 * An HTTP session manager.
 *
 * This class simply keeps cookies across requests.
 *
 * @property sessionCookies A cookie jar.
 */
class HttpSession {
    companion object {
        const val DEFAULT_TIMEOUT = 30.0

        fun mergeCookies(cookie1: CookieJar, cookie2: Map<String, String>?): Map<String, String> {
            val a = cookie1
            if (!cookie2.isNullOrEmpty()) {
                a.putAll(cookie2)
            }
            return a
        }
    }

    val sessionCookies = CookieJar()

    fun get(
        url: String, headers: Map<String, String?> = mapOf(),
        params: Map<String, String> = mapOf(),
        data: Any? = null, json: Any? = null,
        auth: Authorization? = null,
        cookies: Map<String, String>? = null,
        timeout: Double = DEFAULT_TIMEOUT,
        allowRedirects: Boolean? = null,
        stream: Boolean = false, files: List<FileLike> = listOf(),
    ): Response {
        val res = get(
            url, headers, params,
            data, json, auth,
            mergeCookies(sessionCookies, cookies), timeout,
            allowRedirects,
            stream, files
        )
        sessionCookies.putAll(res.cookies)
        sessionCookies.putAll(CookieJar(*res.headers.filter { it.key.toLowerCase() == "set-cookie" }.map { Cookie(it.value) }.toTypedArray()))
        return res
    }

    fun post(
        url: String, headers: Map<String, String?> = mapOf(),
        params: Map<String, String> = mapOf(),
        data: Any? = null, json: Any? = null,
        auth: Authorization? = null,
        cookies: Map<String, String>? = null,
        timeout: Double = DEFAULT_TIMEOUT,
        allowRedirects: Boolean? = null,
        stream: Boolean = false, files: List<FileLike> = listOf()
    ): Response {
        val res = post(
            url, headers, params,
            data, json, auth,
            mergeCookies(sessionCookies, cookies), timeout,
            allowRedirects,
            stream, files
        )
        sessionCookies.putAll(res.cookies)
        sessionCookies.putAll(CookieJar(*res.headers.filter { it.key.toLowerCase() == "set-cookie" }.map { Cookie(it.value) }.toTypedArray()))
        return res
    }
}
