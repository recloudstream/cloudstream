package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*

class MixDrop : ExtractorApi() {
    override val name: String = "MixDrop"
    override val mainUrl: String = "https://mixdrop.co"
    private val srcRegex = Regex("""wurl.*?=.*?"(.*?)";""")
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/e/$id"
    }

    override fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(khttp.get(url)) {
            getAndUnpack(this.text)?.let { unpackedText ->
                srcRegex.find(unpackedText)?.groupValues?.get(1)?.let { link ->
                    return listOf(
                        ExtractorLink(
                            name,
                            name,
                            httpsify(link),
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