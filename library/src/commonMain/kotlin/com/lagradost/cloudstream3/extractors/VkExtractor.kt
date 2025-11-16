// Made by @kraptor123 for cs-kraptor
package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

open class VkExtractor : ExtractorApi() {
    override val name = "Vk"
    override val mainUrl = "https://vkvideo.ru"
    override val requiresReferer = false
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5",
            "Sec-GPC" to "1",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Sec-Fetch-User" to "?1",
            "Priority" to "u=0, i",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache"
        )
        val cookie = app.get(url, headers = headers, allowRedirects = false).cookies

        val response =
            app.get(url, headers = headers, allowRedirects = false, cookies = cookie).text

        val listUrl = listOf("url144", "url240", "url360", "url480", "url720", "dash_sep", "hls")

        listUrl.forEach { link ->
            val regex =
                Regex(pattern = "\"$link\":\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))

            val video = regex.find(response)?.groupValues[1]?.replace("\\", "").toString()

            val type = if (link.contains("hls")) {
                "HLS"
            } else if (link.contains("dash")) {
                "Dash"
            } else {
                ""
            }

            callback.invoke(
                newExtractorLink(
                    "${this.name} $type",
                    "${this.name} $type",
                    video,
                    if (link.contains("dash")) {
                        ExtractorLinkType.DASH
                    } else if (link.contains("hls")) {
                        ExtractorLinkType.M3U8
                    } else {
                        ExtractorLinkType.VIDEO
                    }

                ) {
                    this.referer = "${mainUrl}/"
                    this.headers = mapOf(
                        "Host" to "vkvd1.okcdn.ru",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                        "Accept" to "*/*",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Referer" to "${mainUrl}/",
                        "Sec-GPC" to "1",
                        "Connection" to "keep-alive",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site",
                        "Pragma" to "no-cache",
                        "Cache-Control" to "no-cache"
                    )
                    this.quality = getQualityFromName(link.substringAfter("url"))
                })
        }
    }
}