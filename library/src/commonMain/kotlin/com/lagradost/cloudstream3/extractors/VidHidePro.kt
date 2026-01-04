package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8

class VidHidePro1 : VidHidePro() {
    override var mainUrl = "https://filelions.live"
}

class Dhcplay: VidHidePro() {
    override var name = "DHC Play"
    override var mainUrl = "https://dhcplay.com"
}

class VidHidePro2 : VidHidePro() {
    override var mainUrl = "https://filelions.online"
}

class VidHidePro3 : VidHidePro() {
    override var mainUrl = "https://filelions.to"
}

class VidHidePro4 : VidHidePro() {
    override val mainUrl = "https://kinoger.be"
}

class VidHidePro5: VidHidePro() {
    override val mainUrl = "https://vidhidevip.com"
}

class VidHidePro6 : VidHidePro() {
    override val mainUrl = "https://vidhidepre.com"
}

class Smoothpre: VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://smoothpre.com"
}

class Dhtpre: VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://dhtpre.com"
}

class Peytonepre : VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://peytonepre.com"
}

open class VidHidePro : ExtractorApi() {
    override val name = "VidHidePro"
    override val mainUrl = "https://vidhidepro.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
	        "User-Agent" to USER_AGENT,
        )
        
        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if(result.contains("var links")){
                result = result.substringAfter("var links")
            }
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        // m3u8 urls could be prefixed by 'file:', 'hls2:' or 'hls4:', so we just match ':'
        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            generateM3u8(
                name,
                fixUrl(m3u8Match.groupValues[1]),
                referer = "$mainUrl/",
                headers = headers
            ).forEach(callback)
        }
    }

    private fun getEmbedUrl(url: String): String {
		return when {
			url.contains("/d/") -> url.replace("/d/", "/v/")
			url.contains("/download/") -> url.replace("/download/", "/v/")
			url.contains("/file/") -> url.replace("/file/", "/v/")
			else -> url.replace("/f/", "/v/")
		}
	}

}
