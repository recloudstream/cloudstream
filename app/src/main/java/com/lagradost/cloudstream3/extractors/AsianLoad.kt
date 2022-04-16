package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import java.net.URI

class AsianLoad : ExtractorApi() {
    override var name = "AsianLoad"
    override var mainUrl = "https://asianembed.io"
    override val requiresReferer = true

    private val sourceRegex = Regex("""sources:[\W\w]*?file:\s*?["'](.*?)["']""")
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        with(app.get(url, referer = referer)) {
            sourceRegex.findAll(this.text).forEach { sourceMatch ->
                val extractedUrl = sourceMatch.groupValues[1]
                // Trusting this isn't mp4, may fuck up stuff
                if (URI(extractedUrl).path.endsWith(".m3u8")) {
                    M3u8Helper().m3u8Generation(
                        M3u8Helper.M3u8Stream(
                            extractedUrl,
                            headers = mapOf("referer" to this.url)
                        ), true
                    )
                        .forEach { stream ->
                            extractedLinksList.add(
                                ExtractorLink(
                                    name,
                                    name = name,
                                    stream.streamUrl,
                                    url,
                                    getQualityFromName(stream.quality?.toString()),
                                    true
                                )
                            )
                        }
                } else if (extractedUrl.endsWith(".mp4")) {
                    extractedLinksList.add(
                        ExtractorLink(
                            name,
                            "$name ${sourceMatch.groupValues[2]}",
                            extractedUrl,
                            url.replace(" ", "%20"),
                            Qualities.Unknown.value,
                        )
                    )
                }
            }
            return extractedLinksList
        }
    }
}