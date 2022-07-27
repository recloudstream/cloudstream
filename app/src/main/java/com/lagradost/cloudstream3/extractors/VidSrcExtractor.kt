package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor

class VidSrcExtractor2 : VidSrcExtractor() {
    override val mainUrl = "https://vidsrc.me/embed"
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val newUrl = url.lowercase().replace(mainUrl, super.mainUrl)
        super.getUrl(newUrl, referer, subtitleCallback, callback)
    }
}

open class VidSrcExtractor : ExtractorApi() {
    override val name = "VidSrc"
    private val absoluteUrl = "https://v2.vidsrc.me"
    override val mainUrl = "$absoluteUrl/embed"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val iframedoc = app.get(url).document

        val serverslist =
            iframedoc.select("div#sources.button_content div#content div#list div").map {
                val datahash = it.attr("data-hash")
                if (datahash.isNotBlank()) {
                    val links = try {
                        app.get("$absoluteUrl/src/$datahash", referer = "https://source.vidsrc.me/").url
                    } catch (e: Exception) {
                        ""
                    }
                    links
                } else ""
            }

        serverslist.apmap { server ->
            val linkfixed = server.replace("https://vidsrc.xyz/", "https://embedsito.com/")
            if (linkfixed.contains("/pro")) {
                val srcresponse = app.get(server, referer = absoluteUrl).text
                val m3u8Regex = Regex("((https:|http:)//.*\\.m3u8)")
                val srcm3u8 = m3u8Regex.find(srcresponse)?.value ?: return@apmap
                M3u8Helper.generateM3u8(
                    name,
                    srcm3u8,
                    absoluteUrl
                ).forEach(callback)
            } else {
                loadExtractor(linkfixed, url, subtitleCallback, callback)
            }
        }
    }

}