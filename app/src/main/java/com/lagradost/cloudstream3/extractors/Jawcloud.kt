package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app


open class Jawcloud : ExtractorApi() {
    override val name = "Jawcloud"
    override val mainUrl = "https://jawcloud.co"
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
                val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                sources.add(  ExtractorLink(
                    name,
                    "$name $qualityString",
                    stream.streamUrl,
                    url,
                    getQualityFromName(stream.quality.toString()),
                    true
                ))
            }
        return sources
    }
}