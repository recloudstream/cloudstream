package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName

class Meownime : JWPlayer() {
    override val name = "Meownime"
    override val mainUrl = "https://meownime.ltd"
}

class DesuOdchan : JWPlayer() {
    override val name = "DesuOdchan"
    override val mainUrl = "https://desustream.me/odchan/"
}

class DesuArcg : JWPlayer() {
    override val name = "DesuArcg"
    override val mainUrl = "https://desustream.me/arcg/"
}

class DesuDrive : JWPlayer() {
    override val name = "DesuDrive"
    override val mainUrl = "https://desustream.me/desudrive/"
}

class DesuOdvip : JWPlayer() {
    override val name = "DesuOdvip"
    override val mainUrl = "https://desustream.me/odvip/"
}

open class JWPlayer : ExtractorApi() {
    override val name = "JWPlayer"
    override val mainUrl = "https://www.jwplayer.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()
        with(app.get(url).document) {
            val data = this.select("script").mapNotNull { script ->
                if (script.data().contains("sources: [")) {
                    script.data().substringAfter("sources: [")
                        .substringBefore("],").replace("'", "\"")
                } else if (script.data().contains("otakudesu('")) {
                    script.data().substringAfter("otakudesu('")
                        .substringBefore("');")
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
                        referer = url,
                        quality = getQualityFromName(
                            Regex("(\\d{3,4}p)").find(it.file)?.groupValues?.get(
                                1
                            )
                        )
                    )
                )
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