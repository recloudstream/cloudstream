package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Streamup() : ExtractorApi() {
    override val name: String = "Streamup"
    override val mainUrl: String = "https://strmup.to"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fileCode = url.substringAfterLast("/")
        val fileInfo = app.get("$mainUrl/ajax/stream?filecode=$fileCode")
            .parsed<StreamUpFileInfo>()

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = fileInfo.streamingUrl,
                type = ExtractorLinkType.M3U8
            )
        )
    }

    private data class StreamUpFileInfo(
        val title: String,
        val thumbnail: String,
        @JsonProperty("streaming_url")
        val streamingUrl: String,
        // subtitles seems to always be empty
        // val subtitles: List<Any>
    )
}