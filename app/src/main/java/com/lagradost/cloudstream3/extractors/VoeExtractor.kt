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
        @JsonProperty("hls") val hls: String?,
        @JsonProperty("mp4") val mp4: String?,
        @JsonProperty("video_height") val label: Int?
        //val type: String // Mp4
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val html = app.get(url).text
        if (html.isNotBlank()) {
            val src = html.substringAfter("const sources =").substringBefore(";")
                // Remove last comma, it is not proper json otherwise
                .replace("0,", "0")
                // Make json use the proper quotes
                .replace("'", "\"")

            //Log.i(this.name, "Result => (src) ${src}")
            parseJson<ResponseLinks?>(src)?.let { voeLink ->
                //Log.i(this.name, "Result => (voeLink) ${voeLink}")

                // Always defaults to the hls link, but returns the mp4 if null
                val linkUrl = voeLink.hls ?: voeLink.mp4
                val linkLabel = voeLink.label?.toString() ?: ""
                if (!linkUrl.isNullOrEmpty()) {
                    return listOf(
                        ExtractorLink(
                            name = this.name,
                            source = this.name,
                            url = linkUrl,
                            quality = getQualityFromName(linkLabel),
                            referer = url,
                            isM3u8 = voeLink.hls != null
                        )
                    )
                }
            }
        }
        return emptyList()
    }
}