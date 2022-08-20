package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName

class Linkbox : ExtractorApi() {
    override val name = "Linkbox"
    override val mainUrl = "https://www.linkbox.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val id = url.substringAfter("id=")
        val sources = mutableListOf<ExtractorLink>()

        app.get("$mainUrl/api/open/get_url?itemId=$id", referer=url).parsedSafe<Responses>()?.data?.rList?.map { link ->
            sources.add(
                ExtractorLink(
                    name,
                    name,
                    link.url,
                    url,
                    getQualityFromName(link.resolution)
                )
            )
        }

        return sources
    }

    data class RList(
        @JsonProperty("url") val url: String,
        @JsonProperty("resolution") val resolution: String?,
    )

    data class Data(
        @JsonProperty("rList") val rList: List<RList>?,
    )

    data class Responses(
        @JsonProperty("data") val data: Data?,
    )

}