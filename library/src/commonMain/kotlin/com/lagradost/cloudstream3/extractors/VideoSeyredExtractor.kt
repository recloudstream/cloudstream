// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

open class VideoSeyred : ExtractorApi() {
    override val name = "VideoSeyred"
    override val mainUrl = "https://videoseyred.in"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val extRef = referer ?: ""
        val videoId = url.substringAfter("embed/").substringBefore("?")
        val videoUrl = "$mainUrl/playlist/$videoId.json"
        val responseRaw = app.get(videoUrl)
        val responseList = tryParseJson<List<VideoSeyredSource>>(responseRaw.text) ?: throw ErrorLoadingException("VideoSeyred")
        val response = responseList[0]
        for (track in response.tracks) {
            if (track.label != null && track.kind == "captions") {
                subtitleCallback.invoke(
                    newSubtitleFile(
                        lang = track.label,
                        url = fixUrl(track.file),
                    )
                )
            }
        }

        for (source in response.sources) {
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = source.file,
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }

    @Serializable
    data class VideoSeyredSource(
        @JsonProperty("image") @SerialName("image") val image: String,
        @JsonProperty("title") @SerialName("title") val title: String,
        @JsonProperty("sources") @SerialName("sources") val sources: List<VSSource>,
        @JsonProperty("tracks") @SerialName("tracks") val tracks: List<VSTrack>,
    )

    @Serializable
    data class VSSource(
        @JsonProperty("file") @SerialName("file") val file: String,
        @JsonProperty("type") @SerialName("type") val type: String,
        @JsonProperty("default") @SerialName("default") val default: String,
    )

    @Serializable
    data class VSTrack(
        @JsonProperty("file") @SerialName("file") val file: String,
        @JsonProperty("kind") @SerialName("kind") val kind: String,
        @JsonProperty("language") @SerialName("language") val language: String? = null,
        @JsonProperty("label") @SerialName("label") val label: String? = null,
        @JsonProperty("default") @SerialName("default") val default: String? = null,
    )
}
