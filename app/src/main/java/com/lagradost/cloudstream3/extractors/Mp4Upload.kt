package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*

class Mp4Upload : ExtractorApi() {
    override val name: String = "Mp4Upload"
    override val mainUrl: String = "https://www.mp4upload.com"
    private val srcRegex = Regex("""player\.src\("(.*?)"""")
    override val requiresReferer = true

    override fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(khttp.get(url)) {
            getAndUnpack(this.text)?.let { unpackedText ->
                srcRegex.find(unpackedText)?.groupValues?.get(1)?.let { link ->
                    return listOf(
                        ExtractorLink(
                            name,
                            name,
                            link,
                            url,
                            Qualities.Unknown.value,
                        )
                    )
                }
            }
        }
        return null
    }
}