package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.AppUtils.parseJson


class Cinestart: Tomatomatela() {
    override var name: String = "Cinestart"
    override val mainUrl: String = "https://cinestart.net"
    override val details = "vr.php?v="
}

class TomatomatelalClub: Tomatomatela() {
    override var name: String = "Tomatomatela"
    override val mainUrl: String = "https://tomatomatela.club"
}

open class Tomatomatela : ExtractorApi() {
    override var name = "Tomatomatela"
    override val mainUrl = "https://tomatomatela.com"
    override val requiresReferer = false
    private data class Tomato (
        @JsonProperty("status") val status: Int,
        @JsonProperty("file") val file: String?
    )
    open val details = "details.php?v="
    open val embeddetails = "/embed.html#"
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val link = url.replace("$mainUrl$embeddetails","$mainUrl/$details")
        val sources = ArrayList<ExtractorLink>()
        val server = app.get(link, allowRedirects = false,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "Accept-Language" to "en-US,en;q=0.5",
                "X-Requested-With" to "XMLHttpRequest",
                "DNT" to "1",
                "Connection" to "keep-alive",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin"

            )
        ).parsedSafe<Tomato>()
        if (server?.file != null) {
            sources.add(
                ExtractorLink(
                    name,
                    name,
                    server.file,
                    "",
                    Qualities.Unknown.value,
                    isM3u8 = false
                )
            )
        }
        return sources
    }
}