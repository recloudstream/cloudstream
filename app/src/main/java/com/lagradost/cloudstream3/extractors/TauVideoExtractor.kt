// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class TauVideo : ExtractorApi() {
    override val name            = "TauVideo"
    override val mainUrl         = "https://tau-video.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val ext_ref   = referer ?: ""
        val video_key = url.split("/").last()
        val video_url = "${mainUrl}/api/video/${video_key}"
        Log.d("Kekik_${this.name}", "video_url » ${video_url}")

        val api = app.get(video_url).parsedSafe<TauVideoUrls>() ?: throw ErrorLoadingException("TauVideo")

        for (video in api.urls) {
            callback.invoke(
                ExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = video.url,
                    referer = ext_ref,
                    quality = getQualityFromName(video.label),
                    type    = INFER_TYPE
                )
            )
        }
    }

    data class TauVideoUrls(
        @JsonProperty("urls") val urls: List<TauVideoData>
    )

    data class TauVideoData(
        @JsonProperty("url")   val url: String,
        @JsonProperty("label") val label: String,
    )
}