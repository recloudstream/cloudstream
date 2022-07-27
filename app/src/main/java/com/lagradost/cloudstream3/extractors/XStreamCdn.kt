package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName

class LayarKaca: XStreamCdn() {
    override val name: String = "LayarKaca-xxi"
    override val mainUrl: String = "https://layarkacaxxi.icu"
}

class DBfilm: XStreamCdn() {
    override val name: String = "DBfilm"
    override val mainUrl: String = "https://dbfilm.bar"
}

class Luxubu : XStreamCdn(){
    override val name: String = "FE"
    override val mainUrl: String = "https://www.luxubu.review"
}

class FEmbed: XStreamCdn() {
    override val name: String = "FEmbed"
    override val mainUrl: String = "https://www.fembed.com"
}

class Fplayer: XStreamCdn() {
    override val name: String = "Fplayer"
    override val mainUrl: String = "https://fplayer.info"
}

class FeHD: XStreamCdn() {
    override val name: String = "FeHD"
    override val mainUrl: String = "https://fembed-hd.com"
    override var domainUrl: String = "fembed-hd.com"
}

open class XStreamCdn : ExtractorApi() {
    override val name: String = "XStreamCdn"
    override val mainUrl: String = "https://embedsito.com"
    override val requiresReferer = false
    open var domainUrl: String = "embedsito.com"

    private data class ResponseData(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        //val type: String // Mp4
    )

    private data class ResponseJson(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("data") val data: List<ResponseData>?
    )

    override fun getExtractorUrl(id: String): String {
        return "$domainUrl/api/source/$id"
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val headers = mapOf(
            "Referer" to url,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0",
        )
        val id = url.trimEnd('/').split("/").last()
        val newUrl = "https://${domainUrl}/api/source/${id}"
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        with(app.post(newUrl, headers = headers)) {
            if (this.code != 200) return listOf()
            val text = this.text
            if (text.isEmpty()) return listOf()
            if (text == """{"success":false,"data":"Video not found or has been removed"}""") return listOf()
            AppUtils.parseJson<ResponseJson?>(text)?.let {
                if (it.success && it.data != null) {
                    it.data.forEach { data ->
                        extractedLinksList.add(
                            ExtractorLink(
                                name,
                                name = name,
                                data.file,
                                url,
                                getQualityFromName(data.label),
                            )
                        )
                    }
                }
            }
        }
        return extractedLinksList
    }
}