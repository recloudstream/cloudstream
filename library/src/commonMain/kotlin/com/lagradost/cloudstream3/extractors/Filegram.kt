package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.getAndUnpack
import org.jsoup.nodes.Element

open class Filegram : ExtractorApi() {
    override val name = "Filegram"
    override val mainUrl = "https://filegram.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val header = mapOf(
            "Accept" to "*/*",
            "Accept-language" to "en-US,en;q=0.9",
            "Origin" to mainUrl,
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-site",
            "user-agent" to USER_AGENT,
        )

        val doc = app.get(getEmbedUrl(url), referer = referer).document
        val unpackedJs = unpackJs(doc).toString()
        val videoUrl = Regex("""file:\s*"([^"]+\.m3u8[^"]*)"""").find(unpackedJs)?.groupValues?.get(1)
        if (videoUrl != null) {
            M3u8Helper.generateM3u8(
                this.name,
                fixUrl(videoUrl),
                "$mainUrl/",
                headers = header
            ).forEach(callback)
        }
    }

    private fun unpackJs(script: Element): String? {
        return script.select("script").find { it.data().contains("eval(function(p,a,c,k,e,d)") }
            ?.data()?.let { getAndUnpack(it) }
    }

    private fun getEmbedUrl(url: String): String {
        return if (!url.contains("/embed-")) {
            val videoId = url.substringAfter("$mainUrl/")
            "$mainUrl/embed-$videoId"
        } else url
    }
}
