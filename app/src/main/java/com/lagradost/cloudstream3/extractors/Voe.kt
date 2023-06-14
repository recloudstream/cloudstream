package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

class Tubeless : Voe() {
    override var mainUrl = "https://tubelessceliolymph.com"
}

open class Voe : ExtractorApi() {
    override val name = "Voe"
    override val mainUrl = "https://voe.sx"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).document
        val script = res.select("script").find { it.data().contains("sources =") }?.data()
        val link = Regex("[\"']hls[\"']:\\s*[\"'](.*)[\"']").find(script ?: return)?.groupValues?.get(1)

        M3u8Helper.generateM3u8(
            name,
            link ?: return,
            "$mainUrl/",
            headers = mapOf("Origin" to "$mainUrl/")
        ).forEach(callback)

    }
}