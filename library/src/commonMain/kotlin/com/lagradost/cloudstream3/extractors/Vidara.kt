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

class Vidavaca : Vidara() {
    override val mainUrl = "https://vidavaca.net"
}

class VidaaraxNet : Vidara() {
    override val mainUrl = "https://vidaarax.net"
}

class VidaaraxCom : Vidara() {
    override val mainUrl = "https://vidaarax.com"
}

class Vidaratem : Vidara() {
    override val mainUrl = "https://vidaratem.com"
}

class Vidaraw : Vidara() {
    override val mainUrl = "https://vidaraw.com"
}

class Vidarax : Vidara() {
    override val mainUrl = "https://vidarax.cc"
}

class Vidaraa : Vidara() {
    override val mainUrl = "https://vidaraa.cc"
}

class VidaraSo : Vidara() {
    override val mainUrl = "https://vidara.so"
}

open class Vidara : ExtractorApi() {
    override val name = "Vidara"
    override val mainUrl = "https://vidara.to"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val fileCode = url.substringAfterLast("/")
        val fileInfo = app.post(
            "$mainUrl/api/stream", json = mapOf(
                "filecode" to fileCode,
                "device" to "web"
            )
        ).parsed<StreamUpFileInfo>()
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
