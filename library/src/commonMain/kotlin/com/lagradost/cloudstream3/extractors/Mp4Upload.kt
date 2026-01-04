package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Mp4Upload : ExtractorApi() {
    override var name = "Mp4Upload"
    override var mainUrl = "https://www.mp4upload.com"
    private val srcRegex = Regex("""player\.src\("(.*?)"""")
    private val srcRegex2 = Regex("""player\.src\([\w\W]*src: "(.*?)"""")

    override val requiresReferer = true
    private val idMatch = Regex("""mp4upload\.com/(embed-|)([A-Za-z0-9]*)""")
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val realUrl = idMatch.find(url)?.groupValues?.get(2)?.let { id ->
            "$mainUrl/embed-$id.html"
        } ?: url
        val response = app.get(realUrl)
        val unpackedText = getAndUnpack(response.text)
        val quality =
            unpackedText.lowercase().substringAfter(" height=").substringBefore(" ").toIntOrNull()
        srcRegex.find(unpackedText)?.groupValues?.get(1)?.let { link ->
            return listOf(
                newExtractorLink(
                    name,
                    name,
                    link,
                ) {
                    this.referer = url
                    this.quality = quality ?: Qualities.Unknown.value
                }
            )
        }
        srcRegex2.find(unpackedText)?.groupValues?.get(1)?.let { link ->
            return listOf(
                newExtractorLink(
                    name,
                    name,
                    link,
                ) {
                    this.referer = url
                    this.quality = quality ?: Qualities.Unknown.value
                }
            )
        }
        return null
    }
}