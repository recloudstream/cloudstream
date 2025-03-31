package com.lagradost.cloudstream3.extractors
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

class Vido : ExtractorApi() {
    override var name = "Vido"
    override var mainUrl = "https://vido.lol"
    private val srcRegex = Regex("""sources:\s*\["(.*?)"\]""")
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val methode = app.get(url.replace("/e/", "/embed-")) // fix wiflix and mesfilms
        with(methode) {
            if (!methode.isSuccessful) return null
            //val quality = unpackedText.lowercase().substringAfter(" height=").substringBefore(" ").toIntOrNull()
            srcRegex.find(this.text)?.groupValues?.get(1)?.let { link ->
                return listOf(
                    newExtractorLink(
                        name,
                        name,
                        link,
                    ) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                        this.isM3u8 = true
                    }
                )
            }
        }
        return null
    }
}