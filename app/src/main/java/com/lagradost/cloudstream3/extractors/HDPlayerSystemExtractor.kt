// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class HDPlayerSystem : ExtractorApi() {
    override val name            = "HDPlayerSystem"
    override val mainUrl         = "https://hdplayersystem.live"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val ext_ref  = referer ?: ""
        val vid_id   = if (url.contains("video/")) {
            url.substringAfter("video/")
        } else {
            url.substringAfter("?data=")
        }
        val post_url = "${mainUrl}/player/index.php?data=${vid_id}&do=getVideo"
        Log.d("Kekik_${this.name}", "post_url » ${post_url}")

        val response = app.post(
            post_url,
            data = mapOf(
                "hash" to vid_id,
                "r"    to ext_ref
            ),
            referer = ext_ref,
            headers = mapOf(
                "Content-Type"     to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest"
            )
        )

        val video_response = response.parsedSafe<SystemResponse>() ?: throw ErrorLoadingException("failed to parse response")
        val m3u_link       = video_response.securedLink

        callback.invoke(
            ExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = m3u_link,
                referer = ext_ref,
                quality = Qualities.Unknown.value,
                type    = INFER_TYPE
            )
        )
    }

    data class SystemResponse(
        @JsonProperty("hls")         val hls: String,
        @JsonProperty("videoImage")  val videoImage: String? = null,
        @JsonProperty("videoSource") val videoSource: String,
        @JsonProperty("securedLink") val securedLink: String
    )
}