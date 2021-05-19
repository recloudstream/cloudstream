package com.lagradost.cloudstream3.utils.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

class Shiro : ExtractorApi() {
    override val name: String = "Shiro"
    override val mainUrl: String = "https://cherry.subsplea.se"
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/$id"
    }

    override fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val headers = mapOf("Referer" to "https://shiro.is/")
        val res = khttp.get(url, headers = headers).text
        Jsoup.parse(res).select("source").firstOrNull()?.attr("src")?.replace("&amp;", "?")?.let {
            return listOf(
                ExtractorLink(
                    name,
                    it.replace(" ", "%20"),
                    "https://cherry.subsplea.se/",
                    // UHD to give top priority
                    Qualities.UHD.value
                )
            )
        }
        return null
    }
}