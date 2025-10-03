package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import java.net.URI
import org.json.JSONObject



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
        val json = JSONObject(response)

        val qualities = json.getJSONObject("qualities")
        if (qualities.has("auto")) {
            val autoArray = qualities.getJSONArray("auto")
            for (i in 0 until autoArray.length()) {
                val obj = autoArray.getJSONObject(i)
                val videoUrl = obj.optString("url")
                if (videoUrl.isNotEmpty() && videoUrl.contains(".m3u8")) {
                    getStream(videoUrl, this.name, callback)
                }
            }
        }

        if (json.has("subtitles")) {
            val subs = json.getJSONObject("subtitles")
            if (subs.optBoolean("enable", false)) {
                val data = subs.optJSONObject("data")
                data?.let {
                    val keys = it.keys()
                    while (keys.hasNext()) {
                        val lang = keys.next()
                        val subObj = it.getJSONObject(lang)
                        val label = subObj.getString("label")
                        val urls = subObj.getJSONArray("urls")
                        for (i in 0 until urls.length()) {
                            val subUrl = urls.getString(i)
                            subtitleCallback(SubtitleFile(label, subUrl))
                        }
                    }
                }
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