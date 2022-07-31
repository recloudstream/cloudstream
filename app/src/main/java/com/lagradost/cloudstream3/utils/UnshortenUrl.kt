package com.lagradost.cloudstream3.utils

import android.util.Base64
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.nicehttp.NiceResponse
import java.net.URI
import java.net.URLDecoder

//Code heavily based on unshortenit.py form kodiondemand /addon

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

    suspend fun unshorten(uri: String, type: String? = null): String {
        var currentUrl = uri

        while (true) {
            val oldurl = currentUrl
            val domain =
                URI(currentUrl.trim()).host ?: throw IllegalArgumentException("No domain found in URI!")
            currentUrl = shortList.firstOrNull {
                it.regex.find(domain) != null || type == it.type
            }?.function?.let { it(currentUrl) } ?: break
            if (oldurl == currentUrl) {
                break
            }
        }
        return currentUrl.trim()
    }

    suspend fun unshortenAdfly(uri: String): String {
        val html = app.get(uri).text
        val ysmm = Regex("""var ysmm =.*;?""").find(html)!!.value

        if (ysmm.isNotEmpty()) {
            var left = ""
            var right = ""


            for (c in ysmm.replace(Regex("""var ysmm = '|';"""), "").chunked(2)
                .dropLastWhile { it.length == 1 }) {
                left += c[0]
                right = c[1] + right
            }
            val encodedUri = (left + right).toMutableList()
            val numbers =
                encodedUri.mapIndexed { i, n -> Pair(i, n) }.filter { it.second.isDigit() }
            for (el in numbers.chunked(2).dropLastWhile { it.size == 1 }) {
                val xor = (el[0].second).code.xor(el[1].second.code)
                if (xor < 10) {
                    encodedUri[el[0].first] = xor.digitToChar()
                }
            }
            val encodedbytearray = encodedUri.map { it.code.toByte() }.toByteArray()
            var decodedUri =
                Base64.decode(encodedbytearray, Base64.DEFAULT).decodeToString().dropLast(16)
                    .drop(16)

            if (Regex("""go\.php\?u=""").find(decodedUri) != null) {
                decodedUri =
                    Base64.decode(decodedUri.replace(Regex("""(.*?)u="""), ""), Base64.DEFAULT)
                        .decodeToString()
            }

            return decodedUri
        } else {
            return uri
        }
    }

    suspend fun unshortenLinkup(uri: String): String {
        var r: NiceResponse? = null
        var uri = uri
        when {
            uri.contains("/tv/") -> uri = uri.replace("/tv/", "/tva/")
            uri.contains("delta") -> uri = uri.replace("/delta/", "/adelta/")
            (uri.contains("/ga/") || uri.contains("/ga2/")) -> uri =
                base64Decode(uri.split('/').last()).trim()
            uri.contains("/speedx/") -> uri =
                uri.replace("http://linkup.pro/speedx", "http://speedvideo.net")
            else -> {
                r = app.get(uri, allowRedirects = true)
                uri = r.url
                val link =
                    Regex("<iframe[^<>]*src=\\'([^'>]*)\\'[^<>]*>").find(r.text)?.value
                        ?: Regex("""action="(?:[^/]+.*?/[^/]+/([a-zA-Z0-9_]+))">""").find(r.text)?.value
                        ?: Regex("""href","((.|\\n)*?)"""").findAll(r.text)
                            .elementAtOrNull(1)?.groupValues?.get(1)

                if (link != null) {
                    uri = link
                }
            }
        }

        val short = Regex("""^https?://.*?(https?://.*)""").find(uri)?.value
        if (short != null) {
            uri = short
        }
        if (r == null) {
            r = app.get(
                uri,
                allowRedirects = false
            )
            if (r.headers["location"] != null) {
                uri = r.headers["location"].toString()
            }
        }
        if (uri.contains("snip.")) {
            if (uri.contains("out_generator")) {
                uri = Regex("url=(.*)\$").find(uri)!!.value
            } else if (uri.contains("/decode/")) {
                uri = app.get(uri, allowRedirects = true).url
            }
        }
        return uri
    }

    fun unshortenLinksafe(uri: String): String {
        return base64Decode(uri.split("?url=").last())
    }

    suspend fun unshortenNuovoIndirizzo(uri: String): String {
        val soup = app.get(uri, allowRedirects = true)
        val header = soup.headers["refresh"]
        val link: String = if (header != null) {
            soup.headers["refresh"]!!.substringAfter("=")
        } else {
            "non trovato"
        }
        return link
    }

    suspend fun unshortenNuovoLink(uri: String): String {
        return app.get(uri, allowRedirects = true).document.selectFirst("a")!!.attr("href")

    }

    suspend fun unshortenUprot(uri: String): String {
        val page = app.get(uri).text
        Regex("""<a[^>]+href="([^"]+)""").findAll(page)
            .map { it.value.replace("""<a href="""", "") }
            .toList().forEach { link ->
                if (link.contains("https://maxstream.video") || link.contains("https://uprot.net") && link != uri) {
                    return link
                }
            }
        return uri
    }

    fun unshortenDavisonbarker(uri: String): String {
        return URLDecoder.decode(uri.substringAfter("dest="))
    }
    suspend fun unshortenIsecure(uri: String): String {
        val doc = app.get(uri).document
        return doc.selectFirst("iframe")?.attr("src")?.trim()?: uri
    }
}