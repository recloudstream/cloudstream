package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

open class MultiQuality : ExtractorApi() {
    override var name = "MultiQuality"
    override var mainUrl = "https://anihdplay.com"
    private val sourceRegex = Regex("""file:\s*['"](.*?)['"],label:\s*['"](.*?)['"]""")
    private val m3u8Regex = Regex(""".*?(\d*).m3u8""")
    private val urlRegex = Regex("""(.*?)([^/]+$)""")
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/loadserver.php?id=$id"
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        with(app.get(url)) {
            sourceRegex.findAll(this.text).forEach { sourceMatch ->
                val extractedUrl = sourceMatch.groupValues[1]
                // Trusting this isn't mp4, may fuck up stuff
                if (URI(extractedUrl).path.endsWith(".m3u8")) {
                    with(app.get(extractedUrl)) {
                        m3u8Regex.findAll(this.text).forEach { match ->
                            extractedLinksList.add(
                                newExtractorLink(
                                    source = name,
                                    name = name,
                                    url = urlRegex.find(this.url)!!.groupValues[1] + match.groupValues[0],
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = url
                                    this.quality = getQualityFromName(match.groupValues[1])
                                }
                            )
                        }

                    }
                } else if (extractedUrl.endsWith(".mp4")) {
                    extractedLinksList.add(
                        newExtractorLink(
                            name,
                            "$name ${sourceMatch.groupValues[2]}",
                            extractedUrl,
                        ) {
                            this.referer = url.replace(" ", "%20")
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
            return extractedLinksList
        }
    }
}
