package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app


open class Jawcloud : ExtractorApi() {
    override var name = "Jawcloud"
    override var mainUrl = "https://jawcloud.co"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url).document
        val urlString = doc.select("html body div source").attr("src")
        val sources = mutableListOf<ExtractorLink>()
        if (urlString.contains("m3u8"))  M3u8Helper().m3u8Generation(
            M3u8Helper.M3u8Stream(
                urlString,
                headers = app.get(url).headers.toMap()
            ), true
        )
            .map { stream ->
                sources.add(  ExtractorLink(
                    name,
                    name = name,
                    stream.streamUrl,
                    url,
                    getQualityFromName(stream.quality?.toString()),
                    true
                ))
            }
        return sources
    }
}