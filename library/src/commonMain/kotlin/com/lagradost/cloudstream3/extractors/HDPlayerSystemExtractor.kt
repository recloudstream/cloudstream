// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

open class HDPlayerSystem : ExtractorApi() {
    override val name = "HDPlayerSystem"
    override val mainUrl = "https://hdplayersystem.live"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val extRef = referer ?: ""
        val vidId = if (url.contains("video/")) {
            url.substringAfter("video/")
        } else {
            url.substringAfter("?data=")
        }
        val postUrl = "$mainUrl/player/index.php?data=$vidId&do=getVideo"
        val response = app.post(
            postUrl,
            data = mapOf(
                "hash" to vidId,
                "r" to extRef,
            ),
            referer = extRef,
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest",
            )
        )

        val videoResponse = response.parsedSafe<SystemResponse>() ?: throw ErrorLoadingException("failed to parse response")
        val m3uLink = videoResponse.securedLink
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = m3uLink,
            ) { this.referer = extRef }
        )
    }

    @Serializable
    data class SystemResponse(
        @JsonProperty("hls") @SerialName("hls") val hls: String,
        @JsonProperty("videoImage") @SerialName("videoImage") val videoImage: String? = null,
        @JsonProperty("videoSource") @SerialName("videoSource") val videoSource: String,
        @JsonProperty("securedLink") @SerialName("securedLink") val securedLink: String,
    )
}
