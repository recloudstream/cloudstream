package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import java.net.URL

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
        val req = app.get(embedUrl)
        val prefix = "window.__PLAYER_CONFIG__ = "
        val configStr = req.document.selectFirst("script:containsData($prefix)")?.data() ?: return
        val config = tryParseJson<Config>(configStr.substringAfter(prefix).substringBefore(";").trim()) ?: return
        val id = getVideoId(embedUrl) ?: return
        val dmV1st = config.dmInternalData.v1st
        val dmTs = config.dmInternalData.ts
        val embedder = config.context.embedder
        val metaDataUrl = "$baseUrl/player/metadata/video/$id?embedder=$embedder&locale=en-US&dmV1st=$dmV1st&dmTs=$dmTs&is_native_app=0"
        val metaData = app.get(metaDataUrl, referer = embedUrl, cookies = req.cookies)
            .parsedSafe<MetaData>() ?: return
        metaData.qualities.forEach { (_, video) ->
            video.forEach {
                getStream(it.url, this.name, callback)
            }
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
        val path = URL(url).path
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
    data class Config(
        val context: Context,
        val dmInternalData: InternalData
    )

    data class InternalData(
        val ts: Long,
        val v1st: String
    )

    data class Context(
        @JsonProperty("access_token") val accessToken: String?,
        val embedder: String?,
    )

    data class MetaData(
        val qualities: Map<String, List<VideoLink>>
    )

    data class VideoLink(
        val type: String,
        val url: String
    )

}
