package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.jsoup.select.Elements

class StreamoUpload1 : StreamoUpload() {
    override val mainUrl = "https://streamoupload.xyz"
}

open class StreamoUpload : ExtractorApi() {
    override val name = "StreamoUpload"
    override val mainUrl = "https://streamoupload.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        app.get(url, referer = referer).document.select("script").map { it.data() }
            .filter { it.contains("eval(function(p,a,c,k,e,d)") }
            .map { script ->
                val unpacked = if (script.contains("m3u8")) {
                    getAndUnpack(script)
                } else {
                    null
                }
                if (script.contains("jwplayer(\"vplayer\").setup(")) {
                    val data = script.substringAfter("sources: [")
                        .substringBefore("],").replace("file", "\"file\"").trim()
                    tryParseJson<File>(data)?.let {
                        M3u8Helper.generateM3u8(
                            name,
                            it.file,
                            "$mainUrl/",
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
