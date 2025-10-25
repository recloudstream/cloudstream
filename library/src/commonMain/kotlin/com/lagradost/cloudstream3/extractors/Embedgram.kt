package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Embedgram : ExtractorApi() {
    override val name = "Embedgram"
    override val mainUrl = "https://embedgram.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        val link = document.select("video source:last-child").attr("src")
        val quality = document.select("video source:last-child").attr("title")
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                httpsify(link),
            ) {
                this.referer = "$mainUrl/"
                this.quality = getQualityFromName(quality)
                this.headers = mapOf(
                    "Range" to "bytes=0-"
                )
            }
        )
    }
}