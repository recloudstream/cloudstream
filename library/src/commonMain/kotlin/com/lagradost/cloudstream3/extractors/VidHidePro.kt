package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.JwPlayerHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked

class VidHidePro1 : VidHidePro() {
    override var mainUrl = "https://filelions.live"
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
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        JwPlayerHelper.extractStreamLinks(script, name, mainUrl, callback, subtitleCallback, headers)
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
