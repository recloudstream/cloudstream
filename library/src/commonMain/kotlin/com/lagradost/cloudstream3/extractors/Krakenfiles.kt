package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Krakenfiles : ExtractorApi() {
    override val name = "Krakenfiles"
    override val mainUrl = "https://krakenfiles.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("/(?:view|embed-video)/([\\da-zA-Z]+)")
            .find(url)
            ?.groupValues
            ?.get(1)
            ?: return

        val doc = app.get("$mainUrl/embed-video/$id").document
        val title = doc.select("span.coin-name").text()
        val link = doc.selectFirst("source")?.attr("src") ?: return
        val quality = getQualityFromName(title)

        callback.invoke(
            newExtractorLink(
                name,
                name,
                httpsify(link)
            ) {
                this.quality = quality
            }
        )
    }

}

