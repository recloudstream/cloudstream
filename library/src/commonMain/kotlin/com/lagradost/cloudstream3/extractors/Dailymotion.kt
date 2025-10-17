package com.lagradost.cloudstream3.extractors

import com.google.gson.Gson
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
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
        val gson = Gson()
        val meta = gson.fromJson(response, MetaData::class.java)

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


    data class MetaData(
        val qualities: Map<String, List<Quality>>?,
        val subtitles: SubtitlesWrapper?
    )

    data class Quality(
        val type: String?,
        val url: String?
    )

    data class SubtitlesWrapper(
        val enable: Boolean,
        val data: Map<String, SubtitleData>?
    )

    data class SubtitleData(
        val label: String,
        val urls: List<String>
    )

}