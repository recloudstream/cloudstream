// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class Sobreatsesuyp : ExtractorApi() {
    override val name            = "Sobreatsesuyp"
    override val mainUrl         = "https://sobreatsesuyp.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""

        val videoReq = app.get(url, referer = extRef).text

        val file     = Regex("""file\":\"([^\"]+)""").find(videoReq)?.groupValues?.get(1) ?: throw ErrorLoadingException("File not found")
        val postLink = "${mainUrl}/" + file.replace("\\", "")
        val rawList  = app.post(postLink, referer = extRef).parsedSafe<List<Any>>() ?: throw ErrorLoadingException("Post link not found")

        val postJson: List<SobreatsesuypVideoData> = rawList.drop(1).map { item ->
            val mapItem = item as Map<*, *>
            SobreatsesuypVideoData(
                title = mapItem["title"] as? String,
                file  = mapItem["file"]  as? String
            )
        }

        for (item in postJson) {
            if (item.file == null || item.title == null) continue

            val videoData = app.post("${mainUrl}/playlist/${item.file.substring(1)}.txt", referer = extRef).text

            callback.invoke(
                newExtractorLink(
                    source  = this.name,
                    name    = "${this.name} - ${item.title}",
                    url     = videoData,
                ) {
                    this.referer = extRef
                }
            )
        }
    }

    data class SobreatsesuypVideoData(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("file")  val file: String?  = null
    )
}