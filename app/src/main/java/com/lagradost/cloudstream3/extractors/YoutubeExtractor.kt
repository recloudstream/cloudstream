package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.schemaStripRegex
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.VideoStream

class YoutubeShortLinkExtractor : YoutubeExtractor() {
    override val mainUrl = "https://youtu.be"

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/$id"
    }
}

open class YoutubeExtractor : ExtractorApi() {
    override val mainUrl = "https://www.youtube.com"
    override val requiresReferer = false
    override val name = "YouTube"

    companion object {
        private var ytVideos: MutableMap<String, List<VideoStream>> = mutableMapOf()
    }

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/watch?v=$id"
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val streams = safeApiCall {
            val streams = ytVideos[url] ?: let {
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
                val streams = s.videoStreams ?: return@let emptyList()
                ytVideos[url] = streams
                streams
            }
            if (streams.isEmpty()) {
                throw ErrorLoadingException("No Youtube streams")
            }

            streams
            //streams.sortedBy { it.height }
            //    .firstOrNull { !it.isVideoOnly && it.height > 0 }
            //    ?: throw ErrorLoadingException("No valid Youtube stream")
        }
        if (streams is Resource.Success) {
            return streams.value.mapNotNull {
                if (it.isVideoOnly || it.height <= 0) return@mapNotNull null

                ExtractorLink(
                    this.name,
                    this.name,
                    it.url ?: return@mapNotNull null,
                    "",
                    it.height
                )
            }
        } else {
            return null
        }
    }
}