package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

open class Blogger : ExtractorApi() {
    override val name = "Blogger"
    override val mainUrl = "https://www.blogger.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        with(app.get(url).document) {
            this.select("script").map { script ->
                if (script.data().contains("\"streams\":[")) {
                    val data = script.data().substringAfter("\"streams\":[")
                        .substringBefore("]")
                    tryParseJson<List<ResponseSource>>("[$data]")?.map {
                        sources.add(
                            newExtractorLink(
                                name,
                                name,
                                it.playUrl,
                            ) {
                                this.referer = "https://www.youtube.com/"
                                this.quality = when (it.formatId) {
                                    18 -> 360
                                    22 -> 720
                                    else -> Qualities.Unknown.value
                                }
                            }
                        )
                    }
                }
            }
        }

        return sources
    }

    @Serializable
    private data class ResponseSource(
        @JsonProperty("play_url") @SerialName("play_url") val playUrl: String,
        @JsonProperty("format_id") @SerialName("format_id") val formatId: Int,
    )
}
