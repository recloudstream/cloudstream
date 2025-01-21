package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.schemaStripRegex
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.SubtitlesStream

class YoutubeShortLinkExtractor : YoutubeExtractor() {
    override val mainUrl = "https://youtu.be"

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/$id"
    }
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

    companion object {
        private var ytVideos: MutableMap<String, String> = mutableMapOf()
        private var ytVideosSubtitles: MutableMap<String, List<SubtitlesStream>> = mutableMapOf()
    }

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/watch?v=$id"
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (ytVideos[url].isNullOrEmpty()) {
            val link =
                YoutubeStreamLinkHandlerFactory.getInstance().fromUrl(
                    url.replace(
                        schemaStripRegex, ""
                    )
                )

            val s = object : YoutubeStreamExtractor(
                ServiceList.YouTube,
                link
            ) {

            }
            s.fetchPage()
            ytVideos[url] = s.hlsUrl

            ytVideosSubtitles[url] = try {
                s.subtitlesDefault.filterNotNull()
            } catch (e: Exception) {
                logError(e)
                emptyList()
            }
        }
        ytVideos[url]?.let {
            callback(
                ExtractorLink(
                    this.name,
                    this.name,
                    it,
                    "",
                    Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
        }

        ytVideosSubtitles[url]?.mapNotNull {
            SubtitleFile(
                it.languageTag ?: return@mapNotNull null,
                it.content ?: return@mapNotNull null
            )
        }?.forEach(subtitleCallback)
    }
}
