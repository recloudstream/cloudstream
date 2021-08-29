package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class XStreamCdn : ExtractorApi() {
    override val name: String = "XStreamCdn"
    override val mainUrl: String = "https://embedsito.com"
    override val requiresReferer = false

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
        return "$mainUrl/api/source/$id"
    }

    private fun getQuality(string: String): Int {
        return when (string) {
            "360p" -> Qualities.P480.value
            "480p" -> Qualities.P480.value
            "720p" -> Qualities.P720.value
            "1080p" -> Qualities.P1080.value
            else -> Qualities.Unknown.value
        }
    }

    override fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val headers = mapOf(
            "Referer" to url,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0",
        )
        val newUrl = url.replace("$mainUrl/v/", "$mainUrl/api/source/")
        val extractedLinksList: MutableList<ExtractorLink> = mutableListOf()
        with(khttp.post(newUrl, headers = headers)) {
            mapper.readValue<ResponseJson?>(this.text)?.let {
                if (it.success && it.data != null) {
                    it.data.forEach { data ->
                        extractedLinksList.add(
                            ExtractorLink(
                                name,
                                "$name ${data.label}",
                                data.file,
                                url,
                                getQuality(data.label),
                            )
                        )
                    }
                }
            }
        }
        return extractedLinksList
    }
}