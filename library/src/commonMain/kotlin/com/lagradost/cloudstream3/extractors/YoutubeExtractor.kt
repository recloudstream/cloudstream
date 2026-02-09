package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newAudioFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo

class YoutubeShortLinkExtractor(
    maxResolution: Int? = null
) : YoutubeExtractor(maxResolution) {
    override val mainUrl = "https://youtu.be"
}

class YoutubeMobileExtractor(
    maxResolution: Int? = null
) : YoutubeExtractor(maxResolution) {
    override val mainUrl = "https://m.youtube.com"
}

class YoutubeNoCookieExtractor(
    maxResolution: Int? = null
) : YoutubeExtractor(maxResolution) {
    override val mainUrl = "https://www.youtube-nocookie.com"
}

open class YoutubeExtractor(
    private val maxResolution: Int? = null
) : ExtractorApi() {

    override val mainUrl = "https://www.youtube.com"
    override val name = "YouTube"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = extractYouTubeId(url)
        val watchUrl = "$mainUrl/watch?v=$videoId"

        val streamInfo = StreamInfo.getInfo(YoutubeService(0), watchUrl)

        processStreams(streamInfo, subtitleCallback, callback)
    }

    private suspend fun processStreams(
        info: StreamInfo,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val videoStreams = info.videoOnlyStreams
            ?.filterByResolution(maxResolution)
            ?: emptyList()

        if (videoStreams.isEmpty()) return false

        val audioStreams = info.audioStreams.orEmpty()

        videoStreams.forEach { video ->

            callback(
                newExtractorLink(
                    source = name,
                    name = "YouTube ${normalizeCodec(video.codec)}",
                    url = video.content
                ) {
                    quality = video.height
                    audioTracks = audioStreams.map { newAudioFile(it.content) }
                }
            )
        }


        info.subtitles.forEach { subtitle ->
            subtitleCallback(
                newSubtitleFile(
                    lang = subtitle.displayLanguageName
                        ?: subtitle.languageTag
                        ?: "Unknown",
                    url = subtitle.content
                )
            )
        }

        return true
    }

    // ---------------- HELPERS ----------------

    private fun extractYouTubeId(url: String): String {
        val regex = Regex(
            "(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:.*v=|v/|u/\\w/|embed/|shorts/|live/))([\\w-]{11})"
        )
        return regex.find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Invalid YouTube URL: $url")
    }

    private fun List<org.schabi.newpipe.extractor.stream.VideoStream>.filterByResolution(
        max: Int?
    ) = if (max == null) this else filter { it.height <= max }

    private fun normalizeCodec(codec: String?): String {
        if (codec.isNullOrBlank()) return ""

        val c = codec.lowercase()

        return when {
            c.startsWith("av01") -> "AV1"
            c.startsWith("vp9") -> "VP9"
            c.startsWith("avc1") || c.startsWith("h264") -> "H264"
            c.startsWith("hev1") || c.startsWith("hvc1") || c.startsWith("hevc") -> "H265"
            else -> codec.substringBefore('.').uppercase()
        }
    }
}