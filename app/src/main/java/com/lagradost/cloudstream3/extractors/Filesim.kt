package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.net.URI

class FileMoon : Filesim() {
    override val mainUrl = "https://filemoon.to"
    override val name = "FileMoon"
}

class FileMoonSx : Filesim() {
    override val mainUrl = "https://filemoon.sx"
    override val name = "FileMoonSx"
}

open class Filesim : ExtractorApi() {
    override val name = "Filesim"
    override val mainUrl = "https://files.im"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        with(app.get(url).document) {
            this.select("script").forEach { script ->
                if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                    val data = getAndUnpack(script.data())
                    val foundData = Regex("""sources:\[(.*?)]""").find(data)?.groupValues?.get(1) ?: return@forEach
                    val fixedData = foundData.replace("file:", """"file":""")

                    parseJson<List<ResponseSource>>("[$fixedData]").forEach {
                        callback.invoke(
                            ExtractorLink(
                                name,
                                name,
                                it.file,
                                "$mainUrl/",
                                Qualities.Unknown.value,
                                URI(it.file).path.endsWith(".m3u8")
                            )
                        )
                    }
                }
            }
        }
    }

    private data class ResponseSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("label") val label: String?
    )

}