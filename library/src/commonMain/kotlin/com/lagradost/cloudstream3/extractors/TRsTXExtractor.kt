// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

open class TRsTX : ExtractorApi() {
    override val name = "TRsTX"
    override val mainUrl = "https://trstx.org"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val extRef = referer ?: ""
        val videoReq = app.get(url, referer = extRef).text
        val file = Regex("""file\":\"([^\"]+)""").find(videoReq)?.groupValues?.get(1) ?: throw ErrorLoadingException("File not found")
        val postLink = "$mainUrl/" + file.replace("\\", "")
        val rawList = app.post(postLink, referer = extRef).parsedSafe<List<JsonElement>>() ?: throw ErrorLoadingException("Post link not found")
        val postJson: List<TrstxVideoData> = rawList.drop(1).map { item ->
            val mapItem = item as Map<*, *>
            TrstxVideoData(
                title = mapItem["title"] as? String,
                file = mapItem["file"] as? String,
            )
        }

        val vidLinks = mutableSetOf<String>()
        val vidMap = mutableListOf<Map<String, String>>()
        for (item in postJson) {
            if (item.file == null || item.title == null) continue
            val fileUrl = "$mainUrl/playlist/" + item.file.substring(1) + ".txt"
            val videoData = app.post(fileUrl, referer = extRef).text
            if (videoData in vidLinks) continue
            vidLinks.add(videoData)
            vidMap.add(mapOf(
                "title" to item.title,
                "videoData" to videoData,
            ))
        }

        for (mapEntry in vidMap) {
            val title = mapEntry["title"] ?: continue
            val m3uLink = mapEntry["videoData"] ?: continue
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = "${this.name} - $title",
                    url = m3uLink,
                ) { this.referer = extRef }
            )
        }
    }

    @Serializable
    data class TrstxVideoData(
        @JsonProperty("title") @SerialName("title") val title: String? = null,
        @JsonProperty("file") @SerialName("file") val file: String? = null,
    )
}
