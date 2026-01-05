package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.JwPlayerHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink


open class GamoVideo : ExtractorApi() {
    override val name = "GamoVideo"
    override val mainUrl = "https://gamovideo.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        app.get(url, referer = referer).document.select("script")
            .firstOrNull { JwPlayerHelper.canParseJwScript(it.data()) }!!.let {
                JwPlayerHelper.extractStreamLinks(it.data(), name, mainUrl, callback, subtitleCallback)
            }
    }
}
