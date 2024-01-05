package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.*

open class Acefile : ExtractorApi() {
    override val name = "Acefile"
    override val mainUrl = "https://acefile.co"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val script = getAndUnpack(app.get(url).text)
        val id = script.substringAfter("\"code\":\"").substringBefore("\",")
        val doc = app.get("https://drive.google.com/uc?id=${base64Decode(id)}&export=download").document
        val form = doc.select("form#download-form").attr("action")
        val uc = doc.select("input#uc-download-link").attr("value")
        val video = app.post(
            form, data = mapOf(
                "uc-download-link" to uc
            )
        ).url

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                video,
                "",
                Qualities.Unknown.value,
                INFER_TYPE
            )
        )

    }

}