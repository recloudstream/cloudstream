package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Prerelease
class Playmate : ExtractorApi() {
    override val name: String = "Playmate"
    override val mainUrl: String = "https://playmate.to"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast("/")

        val resp = app.post(
            "$mainUrl/api/s",
            json = mapOf("c" to id, "d" to "web"),
            headers = mapOf("User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:153.0) Gecko/20100101 Firefox/153.0")
        ).parsed<StreamInfo>()

        callback.invoke(newExtractorLink(source = name, name = name, url = resp.sx, type = ExtractorLinkType.M3U8))
    }

    @Serializable
    private data class StreamInfo(
        @SerialName("sx")
        @JsonProperty("sx")
        val sx: String,
    )
}