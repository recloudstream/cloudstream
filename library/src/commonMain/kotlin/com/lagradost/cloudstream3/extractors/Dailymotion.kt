package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class Geodailymotion : Dailymotion() {
    override val name = "GeoDailymotion"
    override val mainUrl = "https://geo.dailymotion.com"
}

open class Dailymotion : ExtractorApi() {
    override val mainUrl = "https://www.dailymotion.com"
    override val name = "Dailymotion"
    override val requiresReferer = false
    private val baseUrl = "https://www.dailymotion.com"

    private val videoIdRegex = "^[kx][a-zA-Z0-9]+$".toRegex()

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val embedUrl = getEmbedUrl(url) ?: return
        val id = getVideoId(embedUrl) ?: return
        val metaDataUrl = "$baseUrl/player/metadata/video/$id"

        val response = app.get(metaDataUrl, referer = embedUrl).text
        val meta = parseJson<MetaData>(response)
        meta.qualities?.get("auto")?.forEach { quality ->
            val videoUrl = quality.url
            if (!videoUrl.isNullOrEmpty() && videoUrl.contains(".m3u8")) {
                callback.invoke(newExtractorLink(
                    name,
                    name,
                    videoUrl,
                    ExtractorLinkType.M3U8,
                ))
            }
        }

        meta.subtitles?.data?.forEach { (_, subData) ->
            subData.urls.forEach { subUrl ->
                subtitleCallback(
                    newSubtitleFile(
                        subData.label,
                        subUrl,
                    )
                )
            }
        }
    }

    private fun getEmbedUrl(url: String): String? {
        if (url.contains("/embed/") || url.contains("/video/")) return url
        if (url.contains("geo.dailymotion.com")) {
            val videoId = url.substringAfter("video=")
            return "$baseUrl/embed/video/$videoId"
        }

        return null
    }

    private fun getVideoId(url: String): String? {
        val path = Url(url).encodedPath.decodeURLPart()
        val id = path.substringAfter("/video/")
        return if (id.matches(videoIdRegex)) id else null
    }

    @Serializable
    data class MetaData(
        @JsonProperty("qualities") @SerialName("qualities") val qualities: Map<String, List<Quality>>?,
        @JsonProperty("subtitles") @SerialName("subtitles") val subtitles: SubtitlesWrapper?,
    )

    @Serializable
    data class Quality(
        @JsonProperty("type") @SerialName("type") val type: String?,
        @JsonProperty("url") @SerialName("url") val url: String?,
    )

    @Serializable
    data class SubtitlesWrapper(
        @JsonProperty("enable") @SerialName("enable") val enable: Boolean,
        @JsonProperty("data") @SerialName("data") val data: Map<String, SubtitleData>?,
    )

    @Serializable
    data class SubtitleData(
        @JsonProperty("label") @SerialName("label") val label: String,
        @JsonProperty("urls") @SerialName("urls") val urls: List<String>,
    )
}
