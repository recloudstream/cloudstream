package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

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
                            ExtractorLink(
                                name,
                                name,
                                it.play_url,
                                referer = "https://www.youtube.com/",
                                quality = when (it.format_id) {
                                    18 -> 360
                                    22 -> 720
                                    else -> Qualities.Unknown.value
                                }
                            )
                        )
                    }
                }
            }
        }
        return sources
    }

    private data class ResponseSource(
        @JsonProperty("play_url") val play_url: String,
        @JsonProperty("format_id") val format_id: Int
    )
}