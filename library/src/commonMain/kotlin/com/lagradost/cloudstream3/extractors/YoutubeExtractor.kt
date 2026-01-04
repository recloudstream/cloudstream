package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
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
            val streamUrl = s.hlsUrl.takeIf { !it.isNullOrEmpty() }
            ?: s.dashMpdUrl.takeIf { !it.isNullOrEmpty() }
            ?: s.videoStreams?.firstOrNull()?.content

            if (!streamUrl.isNullOrEmpty()) {
                ytVideos[url] = streamUrl
            }

            ytVideosSubtitles[url] = try {
                s.subtitlesDefault.filterNotNull()
            } catch (e: Exception) {
                logError(e)
                emptyList()
            }
        }
        ytVideos[url]?.let {
            callback(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = it,
                    type = INFER_TYPE
                )
            )
        }

        ytVideosSubtitles[url]?.mapNotNull {
            newSubtitleFile(
                it.languageTag ?: return@mapNotNull null,
                it.content ?: return@mapNotNull null
            )
        }?.forEach(subtitleCallback)
    }
}
