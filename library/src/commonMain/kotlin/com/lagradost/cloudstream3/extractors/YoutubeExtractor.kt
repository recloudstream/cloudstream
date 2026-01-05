// Made For cs-kraptor By @trup40, @kraptor123, @ByAyzen
package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder

class YoutubeShortLinkExtractor : YoutubeExtractor() {
    override val mainUrl = "https://youtu.be"
}

class YoutubeMobileExtractor : YoutubeExtractor() {
    override val mainUrl = "https://m.youtube.com"
}

class YoutubeNoCookieExtractor : YoutubeExtractor() {
    override val mainUrl = "https://www.youtube-nocookie.com"
}


open class YoutubeExtractor : ExtractorApi() {
    override val mainUrl = "https://www.youtube.com"
    override val requiresReferer = false
    override val name = "YouTube"
    private val youtubeUrl = "https://www.youtube.com"

    companion object {
        private val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
        private val HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept-Language" to "en-US,en;q=0.5"
        )
    }


    private fun extractYtCfg(html: String): JSONObject? {
        try {
            val regex = Regex("""ytcfg\.set\(\s*(\{.*?\})\s*\)\s*;""")
            val match = regex.find(html)
            if (match != null) {
                return JSONObject(match.groupValues[1])
            }
        } catch (e: Exception) {
            logError(e)
        }
        return null
    }

    private suspend fun getPageConfig(videoId: String? = null): Map<String, String>? =
        withContext(Dispatchers.IO) {
            try {
                val url = if (videoId != null) "$mainUrl/watch?v=$videoId" else mainUrl
                val response = app.get(url, headers = HEADERS)
                val html = response.text
                val ytCfg = extractYtCfg(html) ?: return@withContext null

                val apiKey = ytCfg.optString("INNERTUBE_API_KEY")
                val clientVersion = ytCfg.optString("INNERTUBE_CLIENT_VERSION", "2.20240725.01.00")
                val visitorData = ytCfg.optString("VISITOR_DATA", "")

                if (apiKey.isNotEmpty()) {
                    return@withContext mapOf(
                        "apiKey" to apiKey,
                        "clientVersion" to clientVersion,
                        "visitorData" to visitorData
                    )
                }
            } catch (e: Exception) {
                logError(e)
            }
            return@withContext null
        }

    fun extractYouTubeId(url: String): String {
        return when {
            url.contains("oembed") && url.contains("url=") -> {
                val encodedUrl = url.substringAfter("url=").substringBefore("&")
                val decodedUrl = URLDecoder.decode(encodedUrl, "UTF-8")
                extractYouTubeId(decodedUrl)
            }

            url.contains("attribution_link") && url.contains("u=") -> {
                val encodedUrl = url.substringAfter("u=").substringBefore("&")
                val decodedUrl = URLDecoder.decode(encodedUrl, "UTF-8")
                extractYouTubeId(decodedUrl)
            }

            url.contains("watch?v=") -> url.substringAfter("watch?v=").substringBefore("&").substringBefore("#")
            url.contains("&v=") -> url.substringAfter("&v=").substringBefore("&").substringBefore("#")
            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?").substringBefore("#").substringBefore("&")
            url.contains("/embed/") -> url.substringAfter("/embed/").substringBefore("?").substringBefore("#")
            url.contains("/v/") -> url.substringAfter("/v/").substringBefore("?").substringBefore("#")
            url.contains("/e/") -> url.substringAfter("/e/").substringBefore("?").substringBefore("#")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?").substringBefore("#")
            url.contains("/live/") -> url.substringAfter("/live/").substringBefore("?").substringBefore("#")
            url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?").substringBefore("#")
            url.contains("watch%3Fv%3D") -> url.substringAfter("watch%3Fv%3D").substringBefore("%26").substringBefore("#")
            url.contains("v%3D") -> url.substringAfter("v%3D").substringBefore("%26").substringBefore("#")

            else -> error("No Id Found")
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = extractYouTubeId(url)

        val config = getPageConfig(videoId) ?: return

        val apiKey = config["apiKey"]
        val clientVersion = config["clientVersion"]
        val visitorData = config["visitorData"]

        val apiUrl = "$youtubeUrl/youtubei/v1/player?key=$apiKey"

        val jsonBody = """
        {
            "context": {
                "client": {
                    "hl": "en",
                    "gl": "US",
                    "clientName": "WEB",
                    "clientVersion": "$clientVersion",
                    "visitorData": "$visitorData",
                    "platform": "DESKTOP",
                    "userAgent": "$USER_AGENT"
                }
            },
            "videoId": "$videoId",
            "playbackContext": {
                "contentPlaybackContext": {
                    "html5Preference": "HTML5_PREF_WANTS"
                }
            }
        }
        """

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toRequestBody(mediaType)

            val response = app.post(apiUrl, headers = HEADERS, requestBody = requestBody)
            val jsonResponse = JSONObject(response.text)

            /*

            Subtitles Not Working Help Wanted

             val subtitles = mapper.readValue<Captions>(response.text)

              subtitles.captions.playerCaptionsTracklistRenderer.captionTracks?.forEach { subtitle ->
                  val url = subtitle.baseUrl ?: ""
                  val lang = subtitle.name?.simpleText ?: ""
                  subtitleCallback.invoke(newSubtitleFile(
                      lang = lang, url = url
                  ) {
                  this.headers = HEADERS
                  })
              }

              */

            val streamingData = jsonResponse.optJSONObject("streamingData")

            if (streamingData != null) {
                val hlsUrl = streamingData.optString("hlsManifestUrl")

                M3u8Helper2.generateM3u8("Youtube", hlsUrl, mainUrl).forEach(callback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/*

Subtitle Data Class

data class Captions(
    val captions: PlayerCaptions
)

data class PlayerCaptions(
    val playerCaptionsTracklistRenderer: CaptionsTracklistRenderer
)

data class CaptionsTracklistRenderer(
    val captionTracks: List<CaptionTrack>?
)

data class CaptionTrack(
    val baseUrl: String?,
    val name: LanguageName?,
    val languageCode: String?
)

data class LanguageName(
    val simpleText: String?
)

*/