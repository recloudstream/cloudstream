package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class Vidmolyme : Vidmoly() {
    override val mainUrl = "https://vidmoly.me"
}

open class Vidmoly : ExtractorApi() {
    override val name = "Vidmoly"
    override val mainUrl = "https://vidmoly.to"
    override val requiresReferer = true

    private fun String.addMarks(str: String): String {
        return this.replace(Regex("\"?$str\"?"), "\"$str\"")
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val script = app.get(
            url,
            referer = referer,
        ).document.select("script")
            .find { it.data().contains("sources:") }?.data()
        val videoData = script?.substringAfter("sources: [")
            ?.substringBefore("],")?.addMarks("file")
        val subData = script?.substringAfter("tracks: [")?.substringBefore("]")?.addMarks("file")
            ?.addMarks("label")?.addMarks("kind")

        tryParseJson<Source>(videoData)?.file?.let { m3uLink ->
            M3u8Helper.generateM3u8(
                name,
                m3uLink,
                "$mainUrl/"
            ).forEach(callback)
        }

        tryParseJson<List<SubSource>>("[${subData}]")
            ?.filter { it.kind == "captions" }?.map {
                subtitleCallback.invoke(
                    SubtitleFile(
                        it.label.toString(),
                        fixUrl(it.file.toString())
                    )
                )
            }

    }

    private data class Source(
        @JsonProperty("file") val file: String? = null,
    )

    private data class SubSource(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null,
    )

}