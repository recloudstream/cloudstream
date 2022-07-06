package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class Filesim : ExtractorApi() {
    override val name = "Filesim"
    override val mainUrl = "https://files.im"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        with(app.get(url).document) {
            this.select("script").map { script ->
                if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                    val data = getAndUnpack(script.data()).substringAfter("sources:[").substringBefore("]")
                    tryParseJson<List<ResponseSource>>("[$data]")?.map {
                        M3u8Helper.generateM3u8(
                            name,
                            it.file,
                            "$mainUrl/",
                        ).forEach { m3uData -> sources.add(m3uData) }
                    }
                }
            }
        }
        return sources
    }

    private data class ResponseSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("label") val label: String?
    )

}