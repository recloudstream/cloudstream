package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName

class StreamSB : ExtractorApi() {
    override val name = "StreamSB"
    override val mainUrl = "https://sbplay.org"
    private val sourceRegex = Regex("""sources:[\W\w]*?file:\s*"(.*?)"""")

    //private val m3u8Regex = Regex(""".*?(\d*).m3u8""")
    //private val urlRegex = Regex("""(.*?)([^/]+$)""")

    // 1: Resolution 2: url
    private val m3u8UrlRegex = Regex("""RESOLUTION=\d*x(\d*).*\n(http.*.m3u8)""")
    override val requiresReferer = false

    // 	https://sbembed.com/embed-ns50b0cukf9j.html   ->   https://sbvideo.net/play/ns50b0cukf9j
    override fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        val newUrl = url.replace("sbplay.org/embed-", "sbplay.org/play/").removeSuffix(".html")
        with(app.get(newUrl, timeout = 10)) {
            getAndUnpack(this.text).let {
                sourceRegex.findAll(it).forEach { sourceMatch ->
                    val extractedUrl = sourceMatch.groupValues[1]
                    if (extractedUrl.contains(".m3u8")) {
                        with(app.get(extractedUrl)) {
                            m3u8UrlRegex.findAll(this.text).forEach { match ->
                                val extractedUrlM3u8 = match.groupValues[2]
                                val extractedRes = match.groupValues[1]
                                extractedLinksList.add(
                                    ExtractorLink(
                                        name,
                                        "$name ${extractedRes}p",
                                        extractedUrlM3u8,
                                        extractedUrl,
                                        getQualityFromName(extractedRes),
                                        true
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return extractedLinksList
    }
}