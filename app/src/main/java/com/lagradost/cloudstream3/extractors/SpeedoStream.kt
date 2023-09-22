package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

class SpeedoStream2 : SpeedoStream() {
    override val mainUrl = "https://speedostream.mom"
}

class SpeedoStream1 : SpeedoStream() {
    override val mainUrl = "https://speedostream.pm"
}

open class SpeedoStream : ExtractorApi() {
    override val name = "SpeedoStream"
    override val mainUrl = "https://speedostream.bond"
    override val requiresReferer = true

    // .bond, .pm, .mom redirect to .bond
    private val hostUrl = "https://speedostream.bond"

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
