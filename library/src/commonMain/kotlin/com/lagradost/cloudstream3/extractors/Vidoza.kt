package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class Videzz : Vidoza() {
    override val mainUrl = "https://videzz.net"
}

open class Vidoza : ExtractorApi() {
    override val name = "Vidoza"
    override val mainUrl = "https://vidoza.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val response = app.get(url).document
        val script = response.selectFirst("script:containsData(sourcesCode)")?.data()
            ?: throw RuntimeException("couldn't find script containing video data")

        // e.g. sourcesCode: [{ src: "https://str38.vidoza.net/vod/v2/.../v.mp4", type: "video/mp4", label:"SD", res:"720"}],
        var sourcesArray = script.substringAfter("sourcesCode:").substringBefore("\n")
        arrayOf("src", "type", "label", "res").forEach {
            // Add missing quotation marks, e.g. src: "https..." -> "src": "https..."
            sourcesArray = sourcesArray.replace(Regex(""""?$it"?:"""), """"$it":""")
        }

        val videoData = parseJson<ArrayList<VinovoVideoData>>(sourcesArray)
        for (stream in videoData) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = stream.source,
                ) { quality = getQualityFromName(stream.resolution) }
            )
        }
    }

    @Serializable
    private data class VinovoVideoData(
        @JsonProperty("src") @SerialName("src") val source: String,
        @JsonProperty("type") @SerialName("type") val type: String?,
        @JsonProperty("label") @SerialName("label") val label: String?,
        @JsonProperty("res") @SerialName("res") val resolution: String?,
    )
}
