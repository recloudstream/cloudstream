package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

class GMPlayer : ExtractorApi() {
    override val name = "GM Player"
    override val mainUrl = "https://gmplayer.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val ref = referer ?: return null
        val id = url.substringAfter("/video/").substringBefore("/")

        val m3u8 = app.post(
            "$mainUrl/player/index.php?data=$id&do=getVideo",
            mapOf(
                "accept" to "*/*",
                "referer" to ref,
                "x-requested-with" to "XMLHttpRequest",
                "origin" to mainUrl
            ),
            data = mapOf("hash" to id, "r" to ref)
        ).parsed<GmResponse>().videoSource ?: return null

        return M3u8Helper.generateM3u8(
            name,
            m3u8,
            ref,
            headers = mapOf("accept" to "*/*")
        )
    }

    private data class GmResponse(
        val videoSource: String? = null
    )
}