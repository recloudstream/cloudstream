package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

class MixDropBz : MixDrop(){
    override var mainUrl = "https://mixdrop.bz"
}

class MixDropAg : MixDrop(){
    override var mainUrl = "https://mixdrop.ag"
}

class MixDropCh : MixDrop(){
    override var mainUrl = "https://mixdrop.ch"
}
class MixDropTo : MixDrop(){
    override var mainUrl = "https://mixdrop.to"
}

open class MixDrop : ExtractorApi() {
    override var name = "MixDrop"
    override var mainUrl = "https://mixdrop.co"
    private val srcRegex = Regex("""wurl.*?=.*?"(.*?)";""")
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/e/$id"
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url.replaceFirst("/f/", "/e/"))) {
            getAndUnpack(this.text).let { unpackedText ->
                srcRegex.find(unpackedText)?.groupValues?.get(1)?.let { link ->
                    return listOf(
                        newExtractorLink(
                            name,
                            name,
                            httpsify(link),
                        ) {
                            this.referer = url
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        }
        return null
    }
}