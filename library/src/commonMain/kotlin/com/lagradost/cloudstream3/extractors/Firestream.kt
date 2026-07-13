package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class Firestream : ExtractorApi() {
    override val name: String = "Firestream"
    override val mainUrl: String = "https://firestream.to"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.removeSuffix("/").substringAfterLast("/")
        val url = getExtractorUrl(id)

        val doc = app.get(url).document
        val token = doc.selectFirst("script[id=token-blob]")!!.data()

        val videoResponse =
            app.post("$mainUrl/api/videos/$id/resolve", json = mapOf("blob" to token))
                .parsed<VideoResponse>()

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = videoResponse.signedVideoUrl
            )
        )
    }

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/e/$id"
    }

    @Serializable
    private data class VideoResponse(
        @SerialName("signedVideoUrl")
        val signedVideoUrl: String,
    )
}