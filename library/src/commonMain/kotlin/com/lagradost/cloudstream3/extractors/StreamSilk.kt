package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

open class StreamSilk : ExtractorApi() {
    override val name = "StreamSilk"
    override val mainUrl = "https://streamsilk.com"
    override val requiresReferer = true
    private val srcRegex = Regex("var urlPlay =\\s*\"(.*?m3u8.*?)\"")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, headers = mapOf("Accept" to "*/*"))
        response.document.select("script").firstOrNull {
            it.html().contains("h,u,n,t,e,r")
        }?.html()?.let { hunted ->
            JsHunter(hunted).dehunt()?.let { script ->
                srcRegex.find(script)?.groupValues?.get(1)?.trim()?.let { link ->
                    val headers = mapOf(
                        "Origin" to mainUrl,
                        "Referer" to "$mainUrl/",
                        "User-Agent" to USER_AGENT,
                    )
                    M3u8Helper.generateM3u8(
                        name,
                        link,
                        "$mainUrl/",
                        headers = headers
                    ).forEach(callback)
                }
            }
        }
    }
}