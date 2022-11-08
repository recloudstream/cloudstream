package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.net.URI

open class AsianLoad : ExtractorApi() {
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
                    M3u8Helper.generateM3u8(
                        name,
                        extractedUrl,
                        url,
                        headers = mapOf("referer" to this.url)
                    ).forEach { link ->
                        extractedLinksList.add(link)
                    }
                } else if (extractedUrl.endsWith(".mp4")) {
                    extractedLinksList.add(
                        ExtractorLink(
                            name,
                            name,
                            extractedUrl,
                            url.replace(" ", "%20"),
                            getQualityFromName(sourceMatch.groupValues[2]),
                        )
                    )
                }
            }
            return extractedLinksList
        }
    }
}