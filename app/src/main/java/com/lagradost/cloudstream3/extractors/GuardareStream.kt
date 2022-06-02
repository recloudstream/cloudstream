package com.lagradost.cloudstream3.extractors
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

open class GuardareStream : ExtractorApi() {
    override var name = "Guardare"
    override var mainUrl = "https://guardare.stream"
    override val requiresReferer = false

    data class GuardareJsonData (
        @JsonProperty("data") val data : List<GuardareData>,
    )

    data class GuardareData (
        @JsonProperty("file") val file : String,
        @JsonProperty("label") val label : String,
        @JsonProperty("type") val type : String
    )

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response = app.post(url.replace("/v/","/api/source/"), data = mapOf("d" to mainUrl)).text
        val jsonvideodata = AppUtils.parseJson<GuardareJsonData>(response)
        return jsonvideodata.data.map {
            ExtractorLink(
                it.file+".${it.type}",
                this.name,
                it.file+".${it.type}",
                mainUrl,
                it.label.filter{ it.isDigit() }.toInt(),
                false
            )
        }
    }
}