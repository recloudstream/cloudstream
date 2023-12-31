// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class PeaceMakerst : ExtractorApi() {
    override val name            = "PeaceMakerst"
    override val mainUrl         = "https://peacemakerst.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val m3u_link:String?
        val ext_ref  = referer ?: ""
        val post_url = "${url}?do=getVideo"
        Log.d("Kekik_${this.name}", "post_url » ${post_url}")

        val response = app.post(
            post_url,
            data = mapOf(
                "hash" to url.substringAfter("video/"),
                "r"    to ext_ref,
                "s"    to ""
            ),
            referer = ext_ref,
            headers = mapOf(
                "Content-Type"     to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest"
            )
        )
        if (response.text.contains("teve2.com.tr\\/embed\\/")) {
            val teve2_id       = response.text.substringAfter("teve2.com.tr\\/embed\\/").substringBefore("\"")
            val teve2_response = app.get(
                "https://www.teve2.com.tr/action/media/${teve2_id}",
                referer = "https://www.teve2.com.tr/embed/${teve2_id}"
            ).parsedSafe<Teve2ApiResponse>() ?: throw ErrorLoadingException("teve2 response is null")

            m3u_link           = teve2_response.media.link.serviceUrl + "//" + teve2_response.media.link.securePath
        } else {
            val video_response = response.parsedSafe<PeaceResponse>() ?: throw ErrorLoadingException("peace response is null")
            val video_sources  = video_response.videoSources
            if (video_sources.isNotEmpty()) {
                m3u_link = video_sources.lastOrNull()?.file
            } else {
                m3u_link = null
            }
        }

        callback.invoke(
            ExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = m3u_link ?: throw ErrorLoadingException("m3u link not found"),
                referer = ext_ref,
                quality = Qualities.Unknown.value,
                type    = INFER_TYPE
            )
        )
    }

    data class PeaceResponse(
        @JsonProperty("videoImage")   val videoImage: String?,
        @JsonProperty("videoSources") val videoSources: List<VideoSource>,
        @JsonProperty("sIndex")       val sIndex: String,
        @JsonProperty("sourceList")   val sourceList: Map<String, String>
    )

    data class VideoSource(
        @JsonProperty("file")  val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("type")  val type: String
    )

    data class Teve2ApiResponse(
        @JsonProperty("Media") val media: Teve2Media
    )

    data class Teve2Media(
        @JsonProperty("Link") val link: Teve2Link
    )

    data class Teve2Link(
        @JsonProperty("ServiceUrl") val serviceUrl: String,
        @JsonProperty("SecurePath") val securePath: String
    )
}