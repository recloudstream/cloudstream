package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URL

open class Dailymotion : ExtractorApi() {
    override val mainUrl = "https://www.dailymotion.com"
    override val name = "Dailymotion"
    override val requiresReferer = false

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
        val doc = app.get(embedUrl).document
        val prefix = "window.__PLAYER_CONFIG__ = "
        val configStr = doc.selectFirst("script:containsData($prefix)")?.data() ?: return
        val config = tryParseJson<Config>(configStr.substringAfter(prefix)) ?: return
        val id = getVideoId(embedUrl) ?: return
        val dmV1st = config.dmInternalData.v1st
        val dmTs = config.dmInternalData.ts
        val metaDataUrl =
            "$mainUrl/player/metadata/video/$id?locale=en&dmV1st=$dmV1st&dmTs=$dmTs&is_native_app=0"
        val cookies = mapOf(
            "v1st" to dmV1st,
            "dmvk" to config.context.dmvk,
            "ts" to dmTs.toString()
        )
        val metaData = app.get(metaDataUrl, referer = embedUrl, cookies = cookies)
            .parsedSafe<MetaData>() ?: return
        metaData.qualities.forEach { (_, video) ->
            video.forEach {
                getStream(it.url, this.name, callback)
            }
        }
    }

    private fun getEmbedUrl(url: String): String? {
        if (url.contains("/embed/")) {
            return url
        }
        val vid = getVideoId(url) ?: return null
        return "$mainUrl/embed/video/$vid"
    }

    private fun getVideoId(url: String): String? {
        val path = URL(url).path
        val id = path.substringAfter("video/")
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
        val ts: Int,
        val v1st: String
    )

    data class Context(
        @JsonProperty("access_token") val accessToken: String?,
        val dmvk: String,
    )

    data class MetaData(
        val qualities: Map<String, List<VideoLink>>
    )

    data class VideoLink(
        val type: String,
        val url: String
    )

}
