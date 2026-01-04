package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

open class BullStream : ExtractorApi() {
    override val name = "BullStream"
    override val mainUrl = "https://bullstream.xyz"
    override val requiresReferer = false
    val regex = Regex("(?<=sniff\\()(.*)(?=\\)\\);)")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val data = regex.find(app.get(url).text)?.value
            ?.replace("\"", "")
            ?.split(",")
            ?: return null

        val m3u8 = "$mainUrl/m3u8/${data[1]}/${data[2]}/master.txt?s=1&cache=${data[4]}"
        //println("shiv : $m3u8")
        return M3u8Helper.generateM3u8(
            name,
            m3u8,
            url,
            headers = mapOf("referer" to url, "accept" to "*/*")
        )
    }
}