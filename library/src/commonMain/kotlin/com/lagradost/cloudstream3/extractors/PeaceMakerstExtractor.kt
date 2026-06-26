// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

open class PeaceMakerst : ExtractorApi() {
    override val name = "PeaceMakerst"
    override val mainUrl = "https://peacemakerst.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val m3uLink: String?
        val extRef = referer ?: ""
        val postUrl = "$url?do=getVideo"
        val response = app.post(
            postUrl,
            data = mapOf(
                "hash" to url.substringAfter("video/"),
                "r" to extRef,
                "s" to "",
            ),
            referer = extRef,
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest",
            ),
        )

        if (response.text.contains("teve2.com.tr\\/embed\\/")) {
            val teve2Id = response.text.substringAfter("teve2.com.tr\\/embed\\/").substringBefore("\"")
            val teve2Response = app.get(
                "https://www.teve2.com.tr/action/media/$teve2Id",
                referer = "https://www.teve2.com.tr/embed/$teve2Id",
            ).parsedSafe<Teve2ApiResponse>() ?: throw ErrorLoadingException("teve2 response is null")
            m3uLink = teve2Response.media.link.serviceUrl + "//" + teve2Response.media.link.securePath
        } else {
            val videoResponse = response.parsedSafe<PeaceResponse>() ?: throw ErrorLoadingException("peace response is null")
            val videoSources = videoResponse.videoSources
            if (videoSources.isNotEmpty()) {
                m3uLink = videoSources.lastOrNull()?.file
            } else m3uLink = null
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = m3uLink ?: throw ErrorLoadingException("m3u link not found"),
            ) {
                this.referer = extRef
                this.quality = Qualities.Unknown.value
            }
        )
    }

    @Serializable
    data class PeaceResponse(
        @JsonProperty("videoImage") @SerialName("videoImage") val videoImage: String?,
        @JsonProperty("videoSources") @SerialName("videoSources") val videoSources: List<VideoSource>,
        @JsonProperty("sIndex") @SerialName("sIndex") val sourceIndex: String,
        @JsonProperty("sourceList") @SerialName("sourceList") val sourceList: Map<String, String>,
    )

    @Serializable
    data class VideoSource(
        @JsonProperty("file") @SerialName("file") val file: String,
        @JsonProperty("label") @SerialName("label") val label: String,
        @JsonProperty("type") @SerialName("type") val type: String,
    )

    @Serializable
    data class Teve2ApiResponse(
        @JsonProperty("Media") @SerialName("Media") val media: Teve2Media,
    )

    @Serializable
    data class Teve2Media(
        @JsonProperty("Link") @SerialName("Link") val link: Teve2Link,
    )

    @Serializable
    data class Teve2Link(
        @JsonProperty("ServiceUrl") @SerialName("ServiceUrl") val serviceUrl: String,
        @JsonProperty("SecurePath") @SerialName("SecurePath") val securePath: String,
    )
}
