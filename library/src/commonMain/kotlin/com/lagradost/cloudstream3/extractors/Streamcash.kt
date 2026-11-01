package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Streamcash: ExtractorApi() {
    override val name: String = "Streamcash"
    override val mainUrl: String = "https://streamcash.to"
    open val cdnUrl: String = "https://cdn.streamcash.to"
    override val requiresReferer: Boolean = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.removeSuffix("/").substringAfterLast("/")
        callback.invoke(
            newExtractorLink(
                name = name,
                source = name,
                url = "$cdnUrl/videos/$id/index.m3u8",
                type = ExtractorLinkType.M3U8
            )
        )
    }
}