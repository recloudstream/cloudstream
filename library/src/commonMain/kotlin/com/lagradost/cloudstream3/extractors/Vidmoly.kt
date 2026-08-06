package com.lagradost.cloudstream3.extractors
import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.JwPlayerHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink

@Prerelease
class Vidmolyme : Vidmoly() {
    override val mainUrl = "https://vidmoly.me"
}

@Prerelease
class Vidmolyto : Vidmoly() {
    override val mainUrl = "https://vidmoly.to"
}

@Prerelease
class Vidmolybiz : Vidmoly() {
    override val mainUrl = "https://vidmoly.biz"
}

@Prerelease
open class Vidmoly : ExtractorApi() {
    override val name = "Vidmoly"
    override val mainUrl = "https://vidmoly.net"
    override val requiresReferer = true
    val downloadUrl = "https://vidmoly.biz"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "user-agent" to USER_AGENT,
            "Sec-Fetch-Dest" to "iframe"
        )
        
        val vidmolyId=url.removeSuffix("/").substringAfterLast("/")
        val iframeUrl ="${downloadUrl}/embed-${vidmolyId}.html"

        val script = app.get(iframeUrl, headers = headers, referer = referer)
            .document.select("script")
            .map { it.data().replace("'", "\"") }
            .firstOrNull { it.contains("sources:") }

        // Extracts and parses videoData
        JwPlayerHelper.extractStreamLinks(script.orEmpty(), name, mainUrl, callback, subtitleCallback)
    }
}
