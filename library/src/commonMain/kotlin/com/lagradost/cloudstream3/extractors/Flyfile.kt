package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

open class Flyfile : ExtractorApi() {
    override val name: String = "FlyFile"
    override val mainUrl: String = "https://flyfile.app"
    open val apiUrl: String = "https://api.flyfile.app"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = url.substringAfterLast("/")
        val videoInfo = app.get("$apiUrl/api/streaming/assign/$videoId")
            .parsed<StreamInfo>()

        val streamUrl = "${videoInfo.url}/hls/${videoInfo.token}/master.m3u8"
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = streamUrl,
                type = ExtractorLinkType.M3U8
            )
        )
    }

    @Serializable
    private data class StreamInfo(
        @SerialName("url")
        val url: String,
        @SerialName("token")
        val token: String
    )
}