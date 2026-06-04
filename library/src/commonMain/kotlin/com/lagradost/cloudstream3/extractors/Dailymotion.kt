package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

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
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = getEmbedUrl(url) ?: return
        val id = getVideoId(embedUrl) ?: return
        val metaDataUrl = "$baseUrl/player/metadata/video/$id"

        val response = app.get(metaDataUrl, referer = embedUrl).text
        val meta = parseJson<MetaData>(response)

        meta.qualities?.get("auto")?.forEach { quality ->
            val videoUrl = quality.url
            if (!videoUrl.isNullOrEmpty() && videoUrl.contains(".m3u8")) {
                getStream(videoUrl, this.name, callback)
            }
        }

        meta.subtitles?.data?.forEach { (_, subData) ->
            subData.urls.forEach { subUrl ->
                subtitleCallback(
                    newSubtitleFile(
                        subData.label,
                        subUrl
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
        val path = URI(url).path
        val id = path.substringAfter("/video/")
        return if (id.matches(videoIdRegex)) id else null
    }

    private suspend fun getStream(
        streamLink: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ) {
        return generateM3u8(name, streamLink, "").forEach(callback)
    }

    @Serializable
    data class MetaData(
        @SerialName("qualities") val qualities: Map<String, List<Quality>>?,
        @SerialName("subtitles") val subtitles: SubtitlesWrapper?,
    )

    @Serializable
    data class Quality(
        @SerialName("type") val type: String?,
        @SerialName("url") val url: String?,
    )

    @Serializable
    data class SubtitlesWrapper(
        @SerialName("enable") val enable: Boolean,
        @SerialName("data") val data: Map<String, SubtitleData>?,
    )

    @Serializable
    data class SubtitleData(
        @SerialName("label") val label: String,
        @SerialName("urls") val urls: List<String>,
    )
}
