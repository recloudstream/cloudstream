package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import java.lang.Thread.sleep

class DoodToExtractor : DoodLaExtractor() {
    override val mainUrl = "https://dood.to"
}

class DoodSoExtractor : DoodLaExtractor() {
    override val mainUrl = "https://dood.so"
}

class DoodWsExtractor : DoodLaExtractor() {
    override val mainUrl = "https://dood.ws"
}


open class DoodLaExtractor : ExtractorApi() {
    override val name = "DoodStream"
    override val mainUrl = "https://dood.la"
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "$mainUrl/d/$id"
    }

    override fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = url.removePrefix("$mainUrl/e/").removePrefix("$mainUrl/d/")
        val trueUrl = getExtractorUrl(id)
        val response = app.get(trueUrl).text
        Regex("href=\".*/download/(.*?)\"").find(response)?.groupValues?.get(1)?.let { link ->
            if (link.isEmpty()) return null
            sleep(5000) // might need this to not trigger anti bot
            val downloadLink = "$mainUrl/download/$link"
            val downloadResponse = app.get(downloadLink).text
            Regex("onclick=\"window\\.open\\((['\"])(.*?)(['\"])").find(downloadResponse)?.groupValues?.get(2)
                ?.let { trueLink ->
                    return listOf(
                        ExtractorLink(
                            trueLink,
                            this.name,
                            trueLink,
                            mainUrl,
                            Qualities.Unknown.value,
                            false
                        )
                    ) // links are valid in 8h
                }
        }

        return null
    }
}
