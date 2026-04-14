package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.delay

class Vidmolyme : Vidmoly() {
    override val mainUrl = "https://vidmoly.me"
}

class Vidmolyto : Vidmoly() {
    override val mainUrl = "https://vidmoly.to"
}

class Vidmolybiz : Vidmoly() {
    override val mainUrl = "https://vidmoly.biz"
}

open class Vidmoly : ExtractorApi() {
    override val name = "Vidmoly"
    override val mainUrl = "https://vidmoly.net"
    override val requiresReferer = true

    private fun String.addMarks(str: String): String {
        return this.replace(Regex("\"?$str\"?"), "\"$str\"")
    }

    private data class Source(
        @JsonProperty("file") val file: String? = null,
    )

    private data class SubSource(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("kind") val kind: String? = null,
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "user-agent" to USER_AGENT,
            "Sec-Fetch-Dest" to "iframe"
        )
        
        val newUrl = if (url.contains("/w/")) 
            url.replaceFirst("/w/", "/embed-") + ".html" 
            else url
        val script = app.get(newUrl, headers = headers, referer = referer)
            .document.select("script")
            .firstOrNull { it.data().contains("sources:") }
            ?.data()
        // Extracts and parses videoData
        script?.substringAfter("sources: [")
            ?.substringBefore("]")
            ?.addMarks("file")
            ?.replace("'","\"")
            ?.let { videoData ->
                tryParseJson<Source>(videoData)?.file?.let { m3uLink ->
                    M3u8Helper.generateM3u8(name, m3uLink, "$mainUrl/")
                        .forEach(callback)
                }
            }
        // Extracts and parses captions
        script?.substringAfter("tracks: [")
            ?.substringBefore("]")
            ?.addMarks("file")?.addMarks("label")?.addMarks("kind")
            ?.replace("'","\"")
            ?.let { subData ->
                tryParseJson<List<SubSource>>("[$subData]")
                    ?.filter { it.kind == "captions" }
                    ?.forEach {
                        subtitleCallback(
                            newSubtitleFile(it.label.toString(), fixUrl(it.file.toString()))
                        )
                    }
            }
    }
}
