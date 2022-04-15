package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app


open class PlayerVoxzer : ExtractorApi() {
    override var name = "Voxzer"
    override var mainUrl = "https://player.voxzer.org"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val listurl = url.replace("/view/","/list/")
        val urltext = app.get(listurl, referer = url).text
        val m3u8regex = Regex("((https:|http:)\\/\\/.*\\.m3u8)")
        val sources = mutableListOf<ExtractorLink>()
        val listm3 = m3u8regex.find(urltext)?.value
        if (listm3?.contains("m3u8") == true)  M3u8Helper().m3u8Generation(
            M3u8Helper.M3u8Stream(
                listm3,
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