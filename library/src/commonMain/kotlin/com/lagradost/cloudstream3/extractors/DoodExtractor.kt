package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import kotlin.random.Random

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

class DoodLiExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.li"
}

class Ds2play : DoodLaExtractor() {
    override var mainUrl = "https://ds2play.com"
}

class Ds2video : DoodLaExtractor() {
    override var mainUrl = "https://ds2video.com"
}

open class DoodLaExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://dood.la"
    override val requiresReferer = false
	
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = url.replace("/d/", "/e/")
		val req = app.get(embedUrl)
        val host = getBaseUrl(req.url)
        val response0 = req.text
	val md5 = host + (Regex("/pass_md5/[^']*").find(response0)?.value ?: return)
        val trueUrl = app.get(md5, referer = req.url).text + createHashTable() + "?token=" + md5.substringAfterLast("/")
		
	val quality = Regex("\\d{3,4}p")
            .find(response0.substringAfter("<title>").substringBefore("</title>"))
            ?.groupValues
            ?.getOrNull(0)
		
	callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                trueUrl,
            ) {
                this.referer = "$mainUrl/"
                this.quality = getQualityFromName(quality)
            }
        )

    }
	
private fun createHashTable(): String {
    return buildString {
        repeat(10) {
            append(alphabet.random())
        }
    }
}

	
private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}
