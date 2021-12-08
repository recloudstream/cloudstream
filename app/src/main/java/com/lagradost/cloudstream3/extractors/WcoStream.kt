package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.*

class WcoStream : ExtractorApi() {
    override val name = "WcoStream"
    override val mainUrl = "https://vidstream.pro"
    override val requiresReferer = false
    private val hlsHelper = M3u8Helper()

    override fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val baseUrl = url.split("/e/")[0]

        val html = app.get(url, headers = mapOf("Referer" to "https://wcostream.cc/")).text
        val (Id) = "/e/(.*?)?domain".toRegex().find(url)!!.destructured
        val (skey) = """skey\s=\s['"](.*?)['"];""".toRegex().find(html)!!.destructured

        val apiLink = "$baseUrl/info/$Id?domain=wcostream.cc&skey=$skey"
        val referrer = "$baseUrl/e/$Id?domain=wcostream.cc"

        val response = app.get(apiLink, headers = mapOf("Referer" to referrer)).text

        data class Sources(
            @JsonProperty("file") val file: String,
            @JsonProperty("label") val label: String?
        )

        data class Media(
            @JsonProperty("sources") val sources: List<Sources>
        )

        data class WcoResponse(
            @JsonProperty("success") val success: Boolean,
            @JsonProperty("media") val media: Media
        )

        val mapped = response.let { mapper.readValue<WcoResponse>(it) }
        val sources = mutableListOf<ExtractorLink>()

        if (mapped.success) {
            mapped.media.sources.forEach {
                if (it.file.contains("m3u8")) {
                    hlsHelper.m3u8Generation(M3u8Helper.M3u8Stream(it.file, null), true).forEach { stream ->
                        val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                        sources.add(
                            ExtractorLink(
                                name,
                                "$name $qualityString",
                                stream.streamUrl,
                                "",
                                getQualityFromName(stream.quality.toString()),
                                true
                            )
                        )
                    }
                } else {
                    sources.add(
                        ExtractorLink(
                            name,
                            name + if (it.label != null) " - ${it.label}" else "",
                            it.file,
                            "",
                            Qualities.P720.value,
                            false
                        )
                    )
                }
            }
        }
        return sources
    }
}
