// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class MailRu : ExtractorApi() {
    override val name            = "MailRu"
    override val mainUrl         = "https://my.mail.ru"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""

        val vidId     = url.substringAfter("video/embed/").trim()
        val videoReq  = app.get("${mainUrl}/+/video/meta/${vidId}", referer=url)
        val videoKey  = videoReq.cookies["video_key"].toString()

        val videoData = AppUtils.tryParseJson<MailRuData>(videoReq.text) ?: throw ErrorLoadingException("Video not found")

        for (video in videoData.videos) {

            val videoUrl = if (video.url.startsWith("//")) "https:${video.url}" else video.url

            callback.invoke(
                newExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = videoUrl,
                    type    = ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                    this.headers = mapOf("Cookie" to "video_key=${videoKey}")
                    this.quality = getQualityFromName(video.key)
                }
            )
        }
    }

    data class MailRuData(
        @JsonProperty("provider") val provider: String,
        @JsonProperty("videos")   val videos: List<MailRuVideoData>
    )

    data class MailRuVideoData(
        @JsonProperty("url") val url: String,
        @JsonProperty("key") val key: String
    )
}
