package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.StringUtils.decodeUrl
import com.lagradost.nicehttp.NiceResponse
import io.ktor.http.Url

// Code heavily based on unshortenit.py form kodiondemand /addon

object ShortLink {
    data class ShortUrl(
        val regex: Regex,
        val type: String,
        val function: suspend (String) -> String,
    ) {
        constructor(regex: String, type: String, function: suspend (String) -> String) : this(
            Regex(regex),
            type,
            function
        )
    }

    private val adflyRegex =
        """adf\.ly|j\.gs|q\.gs|u\.bb|ay\.gy|atominik\.com|tinyium\.com|microify\.com|threadsphere\.bid|clearload\.bid|activetect\.net|swiftviz\.net|briskgram\.net|activetect\.net|stoodsef\.com|baymaleti\.net|thouth\.net|uclaut\.net|gloyah\.net|larati\.net|scuseami\.net"""
    private val linkupRegex = """linkup\.pro|buckler.link"""
    private val linksafeRegex = """linksafe\.cc"""
    private val nuovoIndirizzoRegex = """mixdrop\.nuovoindirizzo\.com"""
    private val nuovoLinkRegex = """nuovolink\.com"""
    private val uprotRegex = """uprot\.net"""
    private val davisonbarkerRegex = """davisonbarker\.pro|lowrihouston\.pro"""
    private val isecureRegex = """isecure\.link"""

    private val shortList = listOf(
        ShortUrl(adflyRegex, "adfly", ::unshortenAdfly),
        ShortUrl(linkupRegex, "linkup", ::unshortenLinkup),
        ShortUrl(linksafeRegex, "linksafe", ::unshortenLinksafe),
        ShortUrl(nuovoIndirizzoRegex, "nuovoindirizzo", ::unshortenNuovoIndirizzo),
        ShortUrl(nuovoLinkRegex, "nuovolink", ::unshortenNuovoLink),
        ShortUrl(uprotRegex, "uprot", ::unshortenUprot),
        ShortUrl(davisonbarkerRegex, "uprot", ::unshortenDavisonbarker),
        ShortUrl(isecureRegex, "isecure", ::unshortenIsecure),
    )

    fun isShortLink(url: String): Boolean {
        return shortList.any {
            it.regex.find(url) != null
        }
    }

    suspend fun unshorten(url: String, type: String? = null): String {
        var currentUrl = url

        val visitedUrls = mutableSetOf<String>()
        var count = 10
        while (!visitedUrls.contains(currentUrl) && count > 0) {
            visitedUrls += currentUrl
            count -= 1

            val domain = Url(currentUrl.trim()).host
            currentUrl = shortList.firstOrNull {
                it.regex.find(domain) != null || type == it.type
            }?.function?.let { it(currentUrl) } ?: break
        }
        return currentUrl.trim()
    }

    suspend fun unshortenAdfly(url: String): String {
        val html = app.get(url).text
        val ysmm = Regex("""var ysmm =.*;?""").find(html)!!.value
        if (ysmm.isNotEmpty()) {
            var left = ""
            var right = ""

            for (c in ysmm.replace(Regex("""var ysmm = '|';"""), "").chunked(2)
                .dropLastWhile { it.length == 1 }) {
                left += c[0]
                right = c[1] + right
            }
            val encodedUrl = (left + right).toMutableList()
            val numbers =
                encodedUrl.mapIndexed { i, n -> Pair(i, n) }.filter { it.second.isDigit() }
            for (el in numbers.chunked(2).dropLastWhile { it.size == 1 }) {
                val xor = (el[0].second).code.xor(el[1].second.code)
                if (xor < 10) {
                    encodedUrl[el[0].first] = xor.digitToChar()
                }
            }

            val encodedByteArray = encodedUrl.map { it.code.toByte() }.toByteArray()
            var decodedUrl = base64Decode(encodedByteArray.decodeToString()).dropLast(16).drop(16)
            if (Regex("""go\.php\?u=""").find(decodedUrl) != null) {
                decodedUrl = base64Decode(decodedUrl.replace(Regex("""(.*?)u="""), ""))
            }

            return decodedUrl
        } else {
            return url
        }
    }

    suspend fun unshortenLinkup(url: String): String {
        var r: NiceResponse? = null
        var url = url
        when {
            url.contains("/tv/") -> url = url.replace("/tv/", "/tva/")
            url.contains("delta") -> url = url.replace("/delta/", "/adelta/")
            (url.contains("/ga/") || url.contains("/ga2/")) -> url =
                base64Decode(url.split('/').last()).trim()

            url.contains("/speedx/") -> url =
                url.replace("http://linkup.pro/speedx", "http://speedvideo.net")

            else -> {
                r = app.get(url, allowRedirects = true)
                url = r.url
                val link =
                    Regex("<iframe[^<>]*src=\\'([^'>]*)\\'[^<>]*>").find(r.text)?.value
                        ?: Regex("""action="(?:[^/]+.*?/[^/]+/([a-zA-Z0-9_]+))">""").find(r.text)?.value
                        ?: Regex("""href","((.|\\n)*?)"""").findAll(r.text)
                            .elementAtOrNull(1)?.groupValues?.get(1)

                if (link != null) {
                    url = link
                }
            }
        }

        val short = Regex("""^https?://.*?(https?://.*)""").find(url)?.value
        if (short != null) {
            url = short
        }
        if (r == null) {
            r = app.get(
                url,
                allowRedirects = false
            )
            if (r.headers["location"] != null) {
                url = r.headers["location"].toString()
            }
        }
        if (url.contains("snip.")) {
            if (url.contains("out_generator")) {
                url = Regex("url=(.*)\$").find(url)!!.value
            } else if (url.contains("/decode/")) {
                url = app.get(url, allowRedirects = true).url
            }
        }
        return url
    }

    fun unshortenLinksafe(url: String): String {
        return base64Decode(url.split("?url=").last())
    }

    suspend fun unshortenNuovoIndirizzo(url: String): String {
        val soup = app.get(url, allowRedirects = true)
        val header = soup.headers["refresh"]
        val link: String = if (header != null) {
            soup.headers["refresh"]!!.substringAfter("=")
        } else {
            "non trovato"
        }
        return link
    }

    suspend fun unshortenNuovoLink(url: String): String {
        return app.get(url, allowRedirects = true).document.selectFirst("a")!!.attr("href")

    }

    suspend fun unshortenUprot(url: String): String {
        val page = app.get(url).text
        Regex("""<a[^>]+href="([^"]+)".*Continue""").findAll(page)
            .map { it.value.replace("""<a href="""", "") }
            .toList().forEach { link ->
                if (link.contains("https://maxstream.video") || link.contains("https://uprot.net") && link != url) {
                    return link
                }
            }
        return url
    }

    fun unshortenDavisonbarker(url: String): String {
        return url.substringAfter("dest=").decodeUrl()
    }

    suspend fun unshortenIsecure(url: String): String {
        val doc = app.get(url).document
        return doc.selectFirst("iframe")?.attr("src")?.trim() ?: url
    }
}
