package com.lagradost.cloudstream3.utils.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mapper

class WcoStream : ExtractorApi() {
    override val name: String = "WcoStream"
    override val mainUrl: String = "https://vidstream.pro"
    override val requiresReferer = false

    override fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        try {
            val baseUrl = url.split("/e/")[0]

            val html = khttp.get(url, headers=mapOf("Referer" to "https://wcostream.cc/")).text
            val (Id) = "/e/(.*?)?domain".toRegex().find(url)!!.destructured
            val (skey) = """skey\s=\s['\"](.*?)['\"];""".toRegex().find(html)!!.destructured

            val apiLink = "$baseUrl/info/$Id?domain=wcostream.cc&skey=$skey"
            val referrer = "$baseUrl/e/$Id?domain=wcostream.cc"

            val response = khttp.get(apiLink, headers=mapOf("Referer" to referrer)).text

            data class Sources (
                @JsonProperty("file") val file : String,
                @JsonProperty("label") val label : String
            )

            data class Media (
                @JsonProperty("sources") val sources : List<Sources>
            )

            data class WcoResponse (
                @JsonProperty("success") val success : Boolean,
                @JsonProperty("media") val media : Media
            )

            val mapped = response.let { mapper.readValue<WcoResponse>(it) }
            val sources = mutableListOf<ExtractorLink>()

            if (mapped.success) {
                mapped.media.sources.forEach {
                    sources.add(
                        ExtractorLink(
                            "WcoStream",
                            "WcoStream" + "- ${it.label}",
                            it.file,
                            "",
                            Qualities.HD.value,
                            it.file.contains(".m3u8")
                        )
                    )
                }
            }
            return sources
        } catch (e: Exception) {
            return listOf()
        }
    }
}
