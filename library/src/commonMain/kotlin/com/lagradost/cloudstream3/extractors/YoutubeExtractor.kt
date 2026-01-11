// Made For cs-kraptor By @trup40, @kraptor123, @ByAyzen
package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.HlsPlaylistParser
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"
        private val HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept-Language" to "en-US,en;q=0.5"
        )
    }


    private fun extractYtCfg(html: String): String? {
        val regex = Regex("""ytcfg\.set\(\s*(\{.*?\})\s*\)\s*;""")
        val match = regex.find(html)
        return match?.groupValues?.getOrNull(1)
    }

    data class PageConfig(
        @JsonProperty("INNERTUBE_API_KEY")
        val apiKey: String,
        @JsonProperty("INNERTUBE_CLIENT_VERSION")
        val clientVersion: String = "2.20240725.01.00",
        @JsonProperty("VISITOR_DATA")
        val visitorData: String = ""
    )

    private suspend fun getPageConfig(videoId: String): PageConfig? =
        tryParseJson(extractYtCfg(app.get("$mainUrl/watch?v=$videoId", headers = HEADERS).text))

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

            url.contains("watch?v=") -> url.substringAfter("watch?v=").substringBefore("&")
                .substringBefore("#")

            url.contains("&v=") -> url.substringAfter("&v=").substringBefore("&")
                .substringBefore("#")

            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
                .substringBefore("#").substringBefore("&")

            url.contains("/embed/") -> url.substringAfter("/embed/").substringBefore("?")
                .substringBefore("#")

            url.contains("/v/") -> url.substringAfter("/v/").substringBefore("?")
                .substringBefore("#")

            url.contains("/e/") -> url.substringAfter("/e/").substringBefore("?")
                .substringBefore("#")

            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
                .substringBefore("#")

            url.contains("/live/") -> url.substringAfter("/live/").substringBefore("?")
                .substringBefore("#")

            url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
                .substringBefore("#")

            url.contains("watch%3Fv%3D") -> url.substringAfter("watch%3Fv%3D")
                .substringBefore("%26").substringBefore("#")

            url.contains("v%3D") -> url.substringAfter("v%3D").substringBefore("%26")
                .substringBefore("#")

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

        val jsonBody = """
        {
            "context": {
                "client": {
                    "hl": "en",
                    "gl": "US",
                    "clientName": "WEB",
                    "clientVersion": "${config.clientVersion}",
                    "visitorData": "${config.visitorData}",
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
        """.toRequestBody("application/json; charset=utf-8".toMediaType())

        val response =
            app.post(
                "$youtubeUrl/youtubei/v1/player?key=${config.apiKey}",
                headers = HEADERS,
                requestBody = jsonBody
            ).parsed<Root>()

        val captionTracks = response.captions?.playerCaptionsTracklistRenderer?.captionTracks

        if (captionTracks != null) {
            for (caption in captionTracks) {
                subtitleCallback.invoke(
                    newSubtitleFile(
                        lang =caption.name.simpleText,
                        url  ="${caption.baseUrl}&fmt=ttml" // The default format is not supported
                    ) { headers = HEADERS })
            }
        }

        val hlsUrl = response.streamingData.hlsManifestUrl
        val getHls = app.get(hlsUrl, headers = HEADERS).text
        val playlist = HlsPlaylistParser.parse(hlsUrl, getHls) ?: return

        var variantIndex = 0
        for (tag in playlist.tags) {
            val trimmedTag = tag.trim()
            if (!trimmedTag.startsWith("#EXT-X-STREAM-INF")) {
                continue
            }
            val variant = playlist.variants.getOrNull(variantIndex++) ?: continue

            val audioId = trimmedTag.split(",")
                .find { it.trim().startsWith("YT-EXT-AUDIO-CONTENT-ID=") }
                ?.split("=")
                ?.get(1)
                ?.trim('"') ?: ""

            val langString =
                SubtitleHelper.fromTagToEnglishLanguageName(
                    audioId.substringBefore(".")
                ) ?: SubtitleHelper.fromTagToEnglishLanguageName(
                    audioId.substringBefore("-")
                ) ?: audioId

            val url = variant.url.toString()

            if (url.isBlank()) {
                continue
            }

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = "Youtube${if (langString.isNotBlank()) " $langString" else ""}",
                    url = url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "${mainUrl}/"
                    this.quality = variant.format.height
                }
            )
        }
    }


    private data class Root(
        // val responseContext: ResponseContext,
        // val playabilityStatus: PlayabilityStatus,
        @JsonProperty("streamingData")
        val streamingData: StreamingData,
        // val playbackTracking: PlaybackTracking,
        @JsonProperty("captions")
        val captions: Captions?,
        // val videoDetails: VideoDetails,
        // val annotations: List<Annotation>,
        // val playerConfig: PlayerConfig,
        // val storyboards: Storyboards,
        // val microformat: Microformat,
        // val cards: Cards,
        // val trackingParams: String,
        // val endscreen: Endscreen,
        // val paidContentOverlay: PaidContentOverlay,
        // val adPlacements: List<AdPlacement>,
        // val adBreakHeartbeatParams: String,
        // val frameworkUpdates: FrameworkUpdates,
    )

    private data class StreamingData(
        //val expiresInSeconds: String,
        //val formats: List<Format>,
        //val adaptiveFormats: List<AdaptiveFormat>,
        @JsonProperty("hlsManifestUrl")
        val hlsManifestUrl: String,
        //val serverAbrStreamingUrl: String,
    )

    private data class Captions(
        @JsonProperty("playerCaptionsTracklistRenderer")
        val playerCaptionsTracklistRenderer: PlayerCaptionsTracklistRenderer?,
    )

    private data class PlayerCaptionsTracklistRenderer(
        @JsonProperty("captionTracks")
        val captionTracks: List<CaptionTrack>?,
        //val audioTracks: List<AudioTrack>,
        //val translationLanguages: List<TranslationLanguage>,
        //@JsonProperty("defaultAudioTrackIndex")
        //val defaultAudioTrackIndex: Long,
    )

    private data class CaptionTrack(
        @JsonProperty("baseUrl")
        val baseUrl: String,
        @JsonProperty("name")
        val name: Name,
        //val vssId: String,
        //val languageCode: String,
        //val kind: String?,
        //val isTranslatable: Boolean,
        //val trackName: String,
    )

    private data class Name(
        @JsonProperty("simpleText")
        val simpleText: String,
    )

// data class AudioTrack(
//     val captionTrackIndices: List<Long>,
//     val defaultCaptionTrackIndex: Long,
//     val hasDefaultTrack: Boolean,
//     val audioTrackId: String,
//     val captionsInitialState: String,
// )
}