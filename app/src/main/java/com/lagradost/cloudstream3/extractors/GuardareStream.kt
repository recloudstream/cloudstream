package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

class Vanfem : GuardareStream() {
    override var name = "Vanfem"
    override var mainUrl = "https://vanfem.com/"
}

class CineGrabber : GuardareStream() {
    override var name = "CineGrabber"
    override var mainUrl = "https://cinegrabber.com"
}

open class GuardareStream : ExtractorApi() {
    override var name = "Guardare"
    override var mainUrl = "https://guardare.stream"
    override val requiresReferer = false

    data class GuardareJsonData(
        @JsonProperty("data") val data: List<GuardareData>,
        @JsonProperty("captions") val captions: List<GuardareCaptions?>?,
    )

    data class GuardareData(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("type") val type: String
    )


    // https://cinegrabber.com/asset/userdata/224879/caption/gqdmzh-71ez76z8/876438.srt
    data class GuardareCaptions(
        @JsonProperty("id") val id: String,
        @JsonProperty("hash") val hash: String,
        @JsonProperty("language") val language: String?,
        @JsonProperty("extension") val extension: String
    ) {
        fun getUrl(mainUrl: String, userId: String): String {
            return "$mainUrl/asset/userdata/$userId/caption/$hash/$id.$extension"
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response =
            app.post(url.replace("/v/", "/api/source/"), data = mapOf("d" to mainUrl)).text

        val jsonVideoData = AppUtils.parseJson<GuardareJsonData>(response)
        jsonVideoData.data.forEach {
            callback.invoke(
                ExtractorLink(
                    it.file + ".${it.type}",
                    this.name,
                    it.file + ".${it.type}",
                    mainUrl,
                    it.label.filter { it.isDigit() }.toInt(),
                    false
                )
            )
        }

        if (!jsonVideoData.captions.isNullOrEmpty()){
            val iframe = app.get(url)
            // var USER_ID = '224879';
            val userIdRegex = Regex("""USER_ID.*?(\d+)""")
            val userId = userIdRegex.find(iframe.text)?.groupValues?.getOrNull(1) ?: return
            jsonVideoData.captions.forEach {
                if (it == null) return@forEach
                val subUrl = it.getUrl(mainUrl, userId)
                subtitleCallback.invoke(
                    SubtitleFile(
                        it.language ?: "",
                        subUrl
                    )
                )
            }
        }
    }
}