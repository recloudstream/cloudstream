package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

open class Uservideo : ExtractorApi() {
    override val name = "Uservideo"
    override val mainUrl = "https://uservideo.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val script = app.get(url).document.selectFirst("script:containsData(hosts =)")?.data()
        val host = script?.substringAfter("hosts = [\"")?.substringBefore("\"];")
        val servers = script?.substringAfter("servers = \"")?.substringBefore("\";")
        val quality = Regex("(\\d{3,4})[Pp]").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val sources = app.get("$host/s/$servers").text.substringAfter("\"sources\":[").substringBefore("],").let {
            tryParseJson<List<Sources>>("[$it]")
        }

        sources?.map { source ->
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    source.src ?: return@map null,
                ) {
                    this.referer = url
                    this.quality = quality ?: Qualities.Unknown.value
                }
            )
        }
    }

    @Serializable
    data class Sources(
        @JsonProperty("src") @SerialName("src") val src: String? = null,
        @JsonProperty("type") @SerialName("type") val type: String? = null,
        @JsonProperty("label") @SerialName("label") val label: String? = null,
    )
}
