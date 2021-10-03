package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.text
import com.lagradost.cloudstream3.network.url
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.net.URI

class AsianLoad : ExtractorApi() {
    override val name: String
        get() = "AsianLoad"
    override val mainUrl: String
        get() = "https://asianload1.com"
    override val requiresReferer: Boolean
        get() = true

    private val urlRegex = Regex("""(.*?)([^/]+$)""")
    private val m3u8UrlRegex = Regex("""RESOLUTION=\d*x(\d*).*\n(.*\.m3u8)""")
    private val sourceRegex = Regex("""sources:[\W\w]*?file:\s*?["'](.*?)["']""")
    override fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        with(get(url, referer = referer)) {
            sourceRegex.findAll(this.text).forEach { sourceMatch ->
                val extractedUrl = sourceMatch.groupValues[1]
                // Trusting this isn't mp4, may fuck up stuff
                if (URI(extractedUrl).path.endsWith(".m3u8")) {
                    with(get(extractedUrl, referer = this.url)) {
                        m3u8UrlRegex.findAll(this.text).forEach { match ->
                            extractedLinksList.add(
                                ExtractorLink(
                                    name,
                                    "$name ${match.groupValues[1]}p",
                                    urlRegex.find(this.url)!!.groupValues[1] + match.groupValues[2],
                                    url,
                                    getQualityFromName(match.groupValues[1]),
                                    isM3u8 = true
                                )
                            )
                        }

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