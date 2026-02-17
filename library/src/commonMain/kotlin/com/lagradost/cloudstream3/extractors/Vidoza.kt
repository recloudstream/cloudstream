package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

class Videzz: Vidoza() {
    override val mainUrl: String = "https://videzz.net"
}

open class Vidoza: ExtractorApi() {
    override val name: String = "Vidoza"
    override val mainUrl: String = "https://vidoza.net"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url).document
        val script = response.selectFirst("script:containsData(sourcesCode)")?.data()
            ?: throw RuntimeException("couldn't find script containing video data")

        // e.g. sourcesCode: [{ src: "https://str38.vidoza.net/vod/v2/.../v.mp4", type: "video/mp4", label:"SD", res:"720"}],
        var sourcesArray = script.substringAfter("sourcesCode:").substringBefore("\n")
        arrayOf("src", "type", "label", "res").forEach {
            // add missing quotation marks, e.g. src: "https..." -> "src": "https..."
            sourcesArray = sourcesArray
                .replace(Regex(""""?$it"?:"""), """"$it":""")
        }
        val videoData = AppUtils.parseJson<VinovoDataList>(sourcesArray)

        for (stream in videoData) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = stream.source
                ) {
                    quality = getQualityFromName(stream.resolution)
                }
            )
        }
    }

    private class VinovoDataList: ArrayList<VinovoVideoData>()

    private data class VinovoVideoData(
        @JsonProperty("src") val source: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("label") val label: String?,
        @JsonProperty("res") val resolution: String?,
    )
}