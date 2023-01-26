package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

open class ByteShare : ExtractorApi() {
    override val name = "ByteShare"
    override val mainUrl = "https://byteshare.net"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        with(app.get(url).document) {
            this.select("script").map { script ->
                if (script.data().contains("'use strict';")) {
                    val data = script.data()
                        .substringAfter("sources: [").substringBefore("]")
                        .replace(" ", "")
                        .substringAfter("src:\"").substringBefore("\",")
                    sources.add(
                        ExtractorLink(
                            name,
                            name,
                            data,
                            "",
                            Qualities.Unknown.value
                        )
                    )
                }
            }
        }
        return sources
    }

}