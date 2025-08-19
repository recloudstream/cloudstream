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
        val qualityUrlRegex = Regex(""""url"\s*:\s*"([^"]+)"""")
        val subtitlesRegex = Regex(""""subtitles"\s*:\s*\{[^}]*"data"\s*:\s*(\[[^\]]*\])""")

        val urls = qualityUrlRegex.findAll(response)
            .map { it.groupValues[1] }
            .toList().filter { it.contains(".m3u8") }

        urls.forEach { videoUrl ->
            getStream(videoUrl, this.name, callback)
        }

        val subtitlesMatches = subtitlesRegex.findAll(response).map { it.groupValues[1] }.toList()
        subtitlesMatches.forEach { subtitleJson ->
            val subRegex = Regex("""\{\s*"label"\s*:\s*"([^"]+)",\s*"urls"\s*:\s*\["([^"]+)"""")
            subRegex.findAll(subtitleJson).forEach { match ->
                val label = match.groupValues[1]
                val subUrl = match.groupValues[2]
                subtitleCallback(SubtitleFile(label, subUrl))
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
}