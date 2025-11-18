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
    override val requiresReferer = true
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
        val response = app.get(url, headers = headers, allowRedirects = false, cookies = cookie).text
        val listUrl = listOf("url", "dash_sep", "hls")

        listUrl.forEach { linkType ->
            if (linkType == "url") {
                val regex = Regex(pattern = "\"url([0-9]+)\":\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))
                regex.findAll(response).forEach { url ->
                    val video = url.groupValues[2].replace("\\", "")
                    val quality = url.groupValues[1].replace("\\", "")
                    callback.invoke(
                        newExtractorLink(
                            this.name,
                            this.name,
                            video,
                            ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "${mainUrl}/"
                            this.headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                                "Accept" to "*/*",
                                "Accept-Language" to "en-US,en;q=0.5",
                                "Referer" to "${mainUrl}/",
                            )
                            this.quality = getQualityFromName(quality)
                        })
                }
            } else {
                val regex = Regex(pattern = "\"$linkType\":\"([^\"]*)\"", options = setOf(RegexOption.IGNORE_CASE))
                val video = regex.find(response)?.groupValues?.getOrNull(1)?.replace("\\", "") ?: return@forEach
                val type = when {
                    linkType.contains("hls") -> "HLS"
                    linkType.contains("dash") -> "Dash"
                    else -> ""
                }
                callback.invoke(
                    newExtractorLink(
                        "${this.name} $type",
                        "${this.name} $type",
                        video,
                        when {
                            linkType.contains("dash") -> ExtractorLinkType.DASH
                            linkType.contains("hls") -> ExtractorLinkType.M3U8
                            else -> ExtractorLinkType.VIDEO
                        }
                    ) {
                        this.referer = "${mainUrl}/"
                        this.headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
                            "Accept" to "*/*",
                            "Accept-Language" to "en-US,en;q=0.5",
                            "Referer" to "${mainUrl}/",
                        )
                    })
            }
        }
    }
}