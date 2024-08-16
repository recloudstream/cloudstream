package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities

class VidhideExtractor1 : VidhideExtractor() {
    override var mainUrl = "https://azipcdn.com"
}

class VidhideExtractor2 : VidhideExtractor() {
    override var mainUrl = "https://filelions.live"
}

class VidhideExtractor3 : VidhideExtractor() {
    override var mainUrl = "https://filelions.online"
}

class VidhideExtractor4 : VidhideExtractor() {
    override var mainUrl = "https://filelions.online"
}

class VidhideExtractor5 : VidhideExtractor() {
    override var mainUrl = "https://filelions.to"
}

class VidhideExtractor6 : VidhideExtractor() {
    override var mainUrl = "https://vidhidepro.com"
}

open class VidhideExtractor : ExtractorApi() {
    override var name = "VidHide"
    override var mainUrl = "https://vidhide.com"
    override val requiresReferer = false
    private val srcRegex = Regex("""sources:\[\{file:"(.*?)"""")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url)) {
            JsUnpacker(this.document.select("script").find {
                it.html().contains("eval(function(p,a,c,k,e,d)")
            }?.html()).unpack()?.let { script ->
                srcRegex.find(script)?.groupValues?.get(1)?.let { link ->
                    return listOf(
                        ExtractorLink(
                            name,
                            name,
                            link,
                            url,
                            Qualities.Unknown.value,
                            isM3u8 = link.contains("m3u8")
                        )
                    )
                }

            }

        }
        return null
    }
}