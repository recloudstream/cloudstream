package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

open class Minoplres : ExtractorApi() {

    override val name = "Minoplres" // formerly SpeedoStream
    override val requiresReferer = true
    override val mainUrl = "https://minoplres.xyz" // formerly speedostream.bond
    private val hostUrl = "https://minoplres.xyz"

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        app.get(url, referer = referer).document.select("script").map { script ->
            if (script.data().contains("jwplayer(\"vplayer\").setup(")) {
                val data = script.data().substringAfter("sources: [")
                    .substringBefore("],").replace("file", "\"file\"").trim()
                tryParseJson<File>(data)?.let {
                    M3u8Helper.generateM3u8(
                        name,
                        it.file,
                        "$hostUrl/",
                    ).forEach { m3uData -> sources.add(m3uData) }
                }
            }
        }
        return sources
    }

    private data class File(
        @JsonProperty("file") val file: String,
    )
}
