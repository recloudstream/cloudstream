package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import kotlinx.coroutines.delay

class D0000d : DoodLaExtractor() {
    override var mainUrl = "https://d0000d.com"
}

class D000dCom : DoodLaExtractor() {
    override var mainUrl = "https://d000d.com"
}

class DoodstreamCom : DoodLaExtractor() {
    override var mainUrl = "https://doodstream.com"
}

class Dooood : DoodLaExtractor() {
    override var mainUrl = "https://dooood.com"
}

class DoodWfExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.wf"
}

class DoodCxExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.cx"
}

class DoodShExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.sh"
}
class DoodWatchExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.watch"
}

class DoodPmExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.pm"
}

class DoodToExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.to"
}

class DoodSoExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.so"
}

class DoodWsExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.ws"
}

class DoodYtExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.yt"
}

open class DoodLaExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://dood.la"
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/d/$id"
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val newUrl= url.replace(mainUrl, "https://d0000d.com")
        val response0 = app.get(newUrl).text // html of DoodStream page to look for /pass_md5/...
        val md5 ="https://d0000d.com"+(Regex("/pass_md5/[^']*").find(response0)?.value ?: return null)  // get https://dood.ws/pass_md5/...
        val trueUrl = app.get(md5, referer = newUrl).text + "zUEJeL3mUN?token=" + md5.substringAfterLast("/")   //direct link to extract  (zUEJeL3mUN is random)
        val quality = Regex("\\d{3,4}p").find(response0.substringAfter("<title>").substringBefore("</title>"))?.groupValues?.get(0)
        return listOf(
            ExtractorLink(
                this.name,
                this.name,
                trueUrl,
                mainUrl,
                getQualityFromName(quality),
                false
            )
        ) // links are valid in 8h

    }
}