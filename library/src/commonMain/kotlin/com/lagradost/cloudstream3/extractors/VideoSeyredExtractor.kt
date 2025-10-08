// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

open class VideoSeyred : ExtractorApi() {
    override val name            = "VideoSeyred"
    override val mainUrl         = "https://videoseyred.in"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef   = referer ?: ""
        val videoId  = url.substringAfter("embed/").substringBefore("?")
        val videoUrl = "${mainUrl}/playlist/${videoId}.json"

        val responseRaw                          = app.get(videoUrl)
        val responseList:List<VideoSeyredSource> = jacksonObjectMapper().readValue(responseRaw.text) ?: throw ErrorLoadingException("VideoSeyred")
        val response                              = responseList[0] ?: throw ErrorLoadingException("VideoSeyred")

        for (track in response.tracks) {
            if (track.label != null && track.kind == "captions") {
                subtitleCallback.invoke(
                    newSubtitleFile(
                        lang = track.label,
                        url  = fixUrl(track.file)
                    )
                )
            }
        }

        for (source in response.sources) {
            callback.invoke(
                newExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = source.file,
                ) {
                    this.referer = "${mainUrl}/"
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }

    data class VideoSeyredSource(
        @JsonProperty("image")   val image: String,
        @JsonProperty("title")   val title: String,
        @JsonProperty("sources") val sources: List<VSSource>,
        @JsonProperty("tracks")  val tracks: List<VSTrack>
    )

    data class VSSource(
        @JsonProperty("file")    val file: String,
        @JsonProperty("type")    val type: String,
        @JsonProperty("default") val default: String
    )

    data class VSTrack(
        @JsonProperty("file")     val file: String,
        @JsonProperty("kind")     val kind: String,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("label")    val label: String?    = null,
        @JsonProperty("default")  val default: String?  = null
    )
}