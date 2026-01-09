package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.JwPlayerHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.delay

class Vidmolyme : Vidmoly() {
    override val mainUrl = "https://vidmoly.me"
}

open class Vidmoly : ExtractorApi() {
    override val name = "Vidmoly"
    override val mainUrl = "https://vidmoly.to"
    override val requiresReferer = true

    private fun String.addMarks(str: String): String {
        return this.replace(Regex("\"?$str\"?"), "\"$str\"")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers  = mapOf(
            "user-agent"     to USER_AGENT,
            "Sec-Fetch-Dest" to "iframe"
        )
        val newUrl = if(url.contains("/w/"))
            url.replaceFirst("/w/", "/embed-")+"-920x360.html"
            else url
        var script: String? = null;
        var attemps = 0
        while (attemps < 10 && script.isNullOrEmpty()){
            attemps++
            script = app.get(
                newUrl,
                headers = headers,
                referer = referer,
            ).document.select("script")
                .firstOrNull { it.data().contains("sources:") }?.data()
            if(script.isNullOrEmpty())
                delay(500)
        }

        JwPlayerHelper.extractStreamLinks(script.orEmpty(), name, mainUrl, callback, subtitleCallback)
    }
}
