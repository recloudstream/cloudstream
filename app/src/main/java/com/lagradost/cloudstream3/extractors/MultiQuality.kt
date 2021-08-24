package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class MultiQuality : ExtractorApi() {
    override val name: String = "MultiQuality"
    override val mainUrl: String = "https://gogo-play.net"
    private val sourceRegex = Regex("""file:\s*'(.*?)',label:\s*'(.*?)'""")
    private val m3u8Regex = Regex(""".*?(\d*).m3u8""")
    private val urlRegex = Regex("""(.*?)([^/]+$)""")
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/loadserver.php?id=$id"
    }

    private fun getQuality(string: String): Int {
        return when (string) {
            "360" -> Qualities.P480.value
            "480" -> Qualities.P480.value
            "720" -> Qualities.P720.value
            "1080" -> Qualities.P1080.value
            else -> Qualities.Unknown.value
        }
    }

    override fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        with(khttp.get(url)) {
            sourceRegex.findAll(this.text).forEach { sourceMatch ->
                val extractedUrl = sourceMatch.groupValues[1]
                // Trusting this isn't mp4, may fuck up stuff
                if (extractedUrl.endsWith(".m3u8")) {
                    with(khttp.get(extractedUrl)) {
                        m3u8Regex.findAll(this.text).forEach { match ->
                            extractedLinksList.add(
                                ExtractorLink(
                                    name,
                                    "$name ${match.groupValues[1]}p",
                                    urlRegex.find(this.url)!!.groupValues[1] + match.groupValues[0],
                                    url,
                                    getQuality(match.groupValues[1]),
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
        return null
    }
}