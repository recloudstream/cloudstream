package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class Neonime7n : Hxfile() {
    override val name = "Neonime7n"
    override val mainUrl = "https://7njctn.neonime.watch"
    override val redirect = false
}

class Neonime8n : Hxfile() {
    override val name = "Neonime8n"
    override val mainUrl = "https://8njctn.neonime.net"
    override val redirect = false
}

class KotakAnimeid : Hxfile() {
    override val name = "KotakAnimeid"
    override val mainUrl = "https://kotakanimeid.com"
    override val requiresReferer = true
}

class Yufiles : Hxfile() {
    override val name = "Yufiles"
    override val mainUrl = "https://yufiles.com"
}

class Aico : Hxfile() {
    override val name = "Aico"
    override val mainUrl = "https://aico.pw"
}

open class Hxfile : ExtractorApi() {
    override val name = "Hxfile"
    override val mainUrl = "https://hxfile.co"
    override val requiresReferer = false
    open val redirect = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()
        val document = app.get(url, allowRedirects = redirect, referer = referer).document
        with(document) {
            this.select("script").map { script ->
                if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                    val data =
                        getAndUnpack(script.data()).substringAfter("sources:[").substringBefore("]")
                    tryParseJson<List<ResponseSource>>("[$data]")?.map {
                        sources.add(
                            ExtractorLink(
                                name,
                                name,
                                it.file,
                                referer = mainUrl,
                                quality = when {
                                    url.contains("hxfile.co") -> getQualityFromName(
                                        Regex("\\d\\.(.*?).mp4").find(
                                            document.select("title").text()
                                        )?.groupValues?.get(1).toString()
                                    )
                                    else -> getQualityFromName(it.label)
                                }
                            )
                        )
                    }
                } else if (script.data().contains("\"sources\":[")) {
                    val data = script.data().substringAfter("\"sources\":[").substringBefore("]")
                    tryParseJson<List<ResponseSource>>("[$data]")?.map {
                        sources.add(
                            ExtractorLink(
                                name,
                                name,
                                it.file,
                                referer = mainUrl,
                                quality = when {
                                    it.label?.contains("HD") == true -> Qualities.P720.value
                                    it.label?.contains("SD") == true -> Qualities.P480.value
                                    else -> getQualityFromName(it.label)
                                }
                            )
                        )
                    }
                }
                else {
                    null
                }
            }
        }
        return sources
    }

    private data class ResponseSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("label") val label: String?
    )

}