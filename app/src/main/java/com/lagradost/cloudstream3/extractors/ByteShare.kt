package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*

open class ByteShare : ExtractorApi() {
    override val name = "ByteShare"
    override val mainUrl = "https://byteshare.net"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        sources.add(
            ExtractorLink(
                name,
                name,
                url.replace("/embed/", "/download/"),
                "",
                Qualities.Unknown.value,
            )
        )
        return sources
    }
}