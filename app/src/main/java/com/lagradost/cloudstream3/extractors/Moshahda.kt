package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName

class Moshahda : ExtractorApi() {
    override val name = "Moshahda"
    override val mainUrl = "https://moshahda.net"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()
        val regex = """\/embed-([0-9A-Za-z]*)""".toRegex()
        val scode = regex.find(url)?.groupValues?.getOrNull(1)
        val slink = """$mainUrl/embed-$scode.html?"""
        with(app.get(slink).document) {
            val data = this.select("body > script").mapNotNull { script ->
                if (script.data().contains("sources: [")) {
                    script.data().substringAfter("sources: [")
                        .substringBefore("],").replace("'", "\"")
                } else {
                    null
                }
            }

            tryParseJson<List<ResponseSource>>("$data")?.map {
                sources.add(
                    ExtractorLink(
                        name,
                        name,
                        it.file,
                        referer = "$mainUrl/",
                        quality = Qualities.Unknown.value
                    )
                )
            }
        }
        return sources
    }

    private data class ResponseSource(
        @JsonProperty("fileType") val type: String?,
        @JsonProperty("file") val file: String
    )

}
