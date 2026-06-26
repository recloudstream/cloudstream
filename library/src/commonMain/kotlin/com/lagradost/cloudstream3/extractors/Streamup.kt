package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class Streamix() : Streamup() {
    override val name = "Streamix"
    override val mainUrl = "https://streamix.so"
}

class Vidara() : Streamup() {
    override val name = "Vidara"
    override val mainUrl = "https://vidara.to"
    override val apiPath = "/api/stream"
}

open class Streamup() : ExtractorApi() {
    override val name = "Streamup"
    override val mainUrl = "https://strmup.to"
    override val requiresReferer = false
    open val apiPath = "/ajax/stream"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val fileCode = url.substringAfterLast("/")
        val fileInfo = app.get("$mainUrl$apiPath?filecode=$fileCode")
            .parsed<StreamUpFileInfo>()
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = fileInfo.streamingUrl,
                type = ExtractorLinkType.M3U8,
            )
        )

        fileInfo.subtitles?.forEach { subtitle ->
            subtitleCallback.invoke(
                newSubtitleFile(subtitle.language, subtitle.filePath)
            )
        }
    }

    @Serializable
    private data class StreamUpFileInfo(
        @JsonProperty("title") @SerialName("title") val title: String,
        @JsonProperty("thumbnail") @SerialName("thumbnail") val thumbnail: String,
        @JsonProperty("streaming_url") @SerialName("streaming_url") val streamingUrl: String,
        @JsonProperty("subtitles") @SerialName("subtitles") val subtitles: List<StreamUpSubtitle>?,
    )

    @Serializable
    private data class StreamUpSubtitle(
        @JsonProperty("file_path") @SerialName("file_path") val filePath: String,
        @JsonProperty("language") @SerialName("language") val language: String,
    )
}
