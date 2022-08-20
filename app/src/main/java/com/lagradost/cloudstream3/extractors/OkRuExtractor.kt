package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

data class DataOptionsJson (
    @JsonProperty("flashvars") var flashvars : Flashvars? = Flashvars(),
)
data class Flashvars (
    @JsonProperty("metadata") var metadata : String? = null,
    @JsonProperty("hlsManifestUrl") var hlsManifestUrl : String? = null, //m3u8
)

data class MetadataOkru (
    @JsonProperty("videos") var videos: ArrayList<Videos> = arrayListOf(),
)

data class Videos (
    @JsonProperty("name") var name : String,
    @JsonProperty("url") var url : String,
    @JsonProperty("seekSchema") var seekSchema : Int? = null,
    @JsonProperty("disallowed") var disallowed : Boolean? = null
)

class OkRuHttps: OkRu(){
    override var mainUrl = "https://ok.ru"
}

open class OkRu : ExtractorApi() {
    override var name = "Okru"
    override var mainUrl = "http://ok.ru"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url).document
        val sources = ArrayList<ExtractorLink>()
        val datajson = doc.select("div[data-options]").attr("data-options")
        if (datajson.isNotBlank()) {
            val main = parseJson<DataOptionsJson>(datajson)
            val metadatajson = parseJson<MetadataOkru>(main.flashvars?.metadata!!)
            val servers = metadatajson.videos
            servers.forEach {
                val quality = it.name.uppercase()
                    .replace("MOBILE","144p")
                    .replace("LOWEST","240p")
                    .replace("LOW","360p")
                    .replace("SD","480p")
                    .replace("HD","720p")
                    .replace("FULL","1080p")
                    .replace("QUAD","1440p")
                    .replace("ULTRA","4k")
                val extractedurl = it.url.replace("\\\\u0026", "&")
                sources.add(ExtractorLink(
                    name,
                    name = this.name,
                    extractedurl,
                    url,
                    getQualityFromName(quality),
                    isM3u8 = false
                ))
            }
        }
        return sources
    }
}