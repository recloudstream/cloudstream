package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName

open class VoeExtractor : ExtractorApi() {
    override val name: String = "Voe"
    override val mainUrl: String = "https://voe.sx"
    override val requiresReferer = false

    private data class ResponseLinks(
        @JsonProperty("hls") val url: String?,
        @JsonProperty("video_height") val label: Int?
        //val type: String // Mp4
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        val doc = app.get(url).text
        if (doc.isNotBlank()) {
            val start = "const sources ="
            var src = doc.substring(doc.indexOf(start))
            src = src.substring(start.length, src.indexOf(";"))
                .replace("0,", "0")
                .trim()
            //Log.i(this.name, "Result => (src) ${src}")
            parseJson<ResponseLinks?>(src)?.let { voelink ->
                //Log.i(this.name, "Result => (voelink) ${voelink}")
                val linkUrl = voelink.url
                val linkLabel = voelink.label?.toString() ?: ""
                if (!linkUrl.isNullOrEmpty()) {
                    extractedLinksList.add(
                        ExtractorLink(
                            name = this.name,
                            source = this.name,
                            url = linkUrl,
                            quality = getQualityFromName(linkLabel),
                            referer = url,
                            isM3u8 = true
                        )
                    )
                }
            }
        }
        return extractedLinksList
    }
}