// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class MailRu : ExtractorApi() {
    override val name            = "MailRu"
    override val mainUrl         = "https://my.mail.ru"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val ext_ref = referer ?: ""
        Log.d("Kekik_${this.name}", "url » ${url}")

        val vid_id     = url.substringAfter("video/embed/").trim()
        val video_req  = app.get("${mainUrl}/+/video/meta/${vid_id}", referer=url)
        val video_key  = video_req.cookies["video_key"].toString()
        Log.d("Kekik_${this.name}", "video_key » ${video_key}")

        val video_data = AppUtils.tryParseJson<MailRuData>(video_req.text) ?: throw ErrorLoadingException("Video not found")

        for (video in video_data.videos) {
            Log.d("Kekik_${this.name}", "video » ${video}")

            val video_url = if (video.url.startsWith("//")) "https:${video.url}" else video.url

            callback.invoke(
                ExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = video_url,
                    referer = url,
                    headers = mapOf("Cookie" to "video_key=${video_key}"),
                    quality = getQualityFromName(video.key),
                    isM3u8  = false
                )
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
