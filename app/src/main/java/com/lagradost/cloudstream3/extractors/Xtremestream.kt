package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

open class Xtremestream : ExtractorApi() {
    override var name = "Xtremestream"
    override var mainUrl = "xtremestream.co"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.get(
            url, referer = "https://${url.substringAfter("//").substringBefore("/")}/",
        )
        val playerScript =
            response.document.selectXpath("//script[contains(text(),'var video_id')]")
                .html()

        val sources = mutableListOf<ExtractorLink>()
        if (playerScript.isNotBlank()) {
            val videoId = playerScript.substringAfter("var video_id = `").substringBefore("`;")
            val m3u8LoaderUrl =
                playerScript.substringAfter("var m3u8_loader_url = `").substringBefore("`;")

            if (videoId.isNotBlank() && m3u8LoaderUrl.isNotBlank()) {
                M3u8Helper.generateM3u8(
                    name,
                    "$m3u8LoaderUrl/$videoId",
                    "$m3u8LoaderUrl/$videoId"
                ).forEach { link ->
                    sources.add(link)
                }
            }
        }
        return sources
    }
}