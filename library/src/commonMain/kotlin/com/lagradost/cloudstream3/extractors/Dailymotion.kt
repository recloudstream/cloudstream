package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
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

    @Suppress("RegExpSimplifiable")
    private val videoIdRegex = "^[kx][a-zA-Z0-9]+\$".toRegex()

    // https://www.dailymotion.com/video/k3JAHfletwk94ayCVIu
    // https://www.dailymotion.com/embed/video/k3JAHfletwk94ayCVIu
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = getEmbedUrl(url) ?: return
        val id = getVideoId(embedUrl) ?: return
        val metaDataUrl = "$baseUrl/player/metadata/video/$id"
        val metaData = app.get(metaDataUrl, referer = embedUrl)
            .parsedSafe<VideoData>() ?: return
        metaData.qualities.forEach { (_, qualityList) ->
            qualityList.forEach { video ->
                getStream(video.url, this.name, callback)
            }
        }

        metaData.subtitles.data.forEach { (_, subtitle) ->
            val subUrl = subtitle.urls.firstOrNull() ?: return@forEach
            subtitleCallback(
                SubtitleFile(subtitle.label, subUrl)
            )
        }
    }

    private fun getEmbedUrl(url: String): String? {
        if (url.contains("/embed/") || url.contains("/video/")) {
            return url
        }
        if (url.contains("geo.dailymotion.com")) {
            val videoId = url.substringAfter("video=")
            return "$baseUrl/embed/video/$videoId"
        }
        return null
    }

    private fun getVideoId(url: String): String? {
        val path = URI(url).path
        val id = path.substringAfter("/video/")
        if (id.matches(videoIdRegex)) {
            return id
        }
        return null
    }

    private suspend fun getStream(
        streamLink: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    )  {
        return generateM3u8(
            name,
            streamLink,
            "",
        ).forEach(callback)
    }

    data class VideoData(
        val qualities: Map<String, List<QualityVideo>>,
        val subtitles: SubtitlesData
    )

    data class QualityVideo(
        val type: String,
        val url: String
    )

    data class SubtitlesData(
        val data: Map<String, SubtitleItem>
    )

    data class SubtitleItem(
        val label: String,
        val urls: List<String>
    )

}