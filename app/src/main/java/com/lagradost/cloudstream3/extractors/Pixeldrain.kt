package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

open class Pixeldrain : ExtractorApi() {
    override val name = "Pixeldrain"
    override val mainUrl = "https://pixeldrain.com"
    override val requiresReferer = false
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mId = Regex("/([ul]/[\\da-zA-Z\\-]+)").find(url)?.groupValues?.get(1)?.split("/")
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                "$mainUrl/api/file/${mId?.last() ?: return}?download",
                url,
                Qualities.Unknown.value,
            )
        )
    }

}