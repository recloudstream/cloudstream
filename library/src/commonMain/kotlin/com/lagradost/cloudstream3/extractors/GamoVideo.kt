package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*


open class GamoVideo : ExtractorApi() {
    override val name = "GamoVideo"
    override val mainUrl = "https://gamovideo.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {
        return app.get(url, referer = referer).document.select("script")
            .firstOrNull { it.html().contains("sources:") }!!.html().substringAfter("file: \"")
            .substringBefore("\",").let {
            listOf(
                newExtractorLink(
                    name,
                    name,
                    it,
                ) {
                    this.referer = url
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}
