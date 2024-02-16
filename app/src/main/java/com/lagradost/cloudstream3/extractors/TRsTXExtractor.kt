// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.lagradost.cloudstream3.extractors

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

open class TRsTX : ExtractorApi() {
    override val name            = "TRsTX"
    override val mainUrl         = "https://trstx.org"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val ext_ref = referer ?: ""

        val video_req = app.get(url, referer=ext_ref).text

        val file     = Regex("""file\":\"([^\"]+)""").find(video_req)?.groupValues?.get(1) ?: throw ErrorLoadingException("File not found")
        val postLink = "${mainUrl}/" + file.replace("\\", "")
        val rawList  = app.post(postLink, referer=ext_ref).parsedSafe<List<Any>>() ?: throw ErrorLoadingException("Post link not found")

        val postJson: List<TrstxVideoData> = rawList.drop(1).map { item ->
            val mapItem = item as Map<*, *>
            TrstxVideoData(
                title = mapItem["title"] as? String,
                file  = mapItem["file"]  as? String
            )
        }
        Log.d("Kekik_${this.name}", "postJson » ${postJson}")

		val vid_links = mutableSetOf<String>()
        val vid_map   = mutableListOf<Map<String, String>>()
        for (item in postJson) {
            if (item.file == null || item.title == null) continue

            val fileUrl   = "${mainUrl}/playlist/" + item.file.substring(1) + ".txt"
            val videoData = app.post(fileUrl, referer=ext_ref).text

			if (videoData in vid_links) { continue }
 			vid_links.add(videoData)

            vid_map.add(mapOf(
                "title"     to item.title,
                "videoData" to videoData
            ))
        }


        for (mapEntry in vid_map) {
            Log.d("Kekik_${this.name}", "mapEntry » ${mapEntry}")
            val title    = mapEntry["title"] ?: continue
            val m3u_link = mapEntry["videoData"] ?: continue

	        callback.invoke(
                ExtractorLink(
                    source  = this.name,
                    name    = "${this.name} - ${title}",
                    url     = m3u_link,
                    referer = ext_ref,
                    quality = Qualities.Unknown.value,
                    type    = INFER_TYPE
                )
            )
        }
    }

    data class TrstxVideoData(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("file")  val file: String?  = null
    )
}
