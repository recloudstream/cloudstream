package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8

class Moviesm4u : Filesim() {
    override val mainUrl = "https://moviesm4u.com"
    override val name = "Moviesm4u"
}

class FileMoonIn : Filesim() {
    override val mainUrl = "https://filemoon.in"
    override val name = "FileMoon"
}

class StreamhideCom : Filesim() {
    override var name: String = "Streamhide"
    override var mainUrl: String = "https://streamhide.com"
}

class Movhide : Filesim() {
    override var name: String = "Movhide"
    override var mainUrl: String = "https://movhide.pro"
}

class Ztreamhub : Filesim() {
    override val mainUrl: String = "https://ztreamhub.com" //Here 'cause works
    override val name = "Zstreamhub"
}
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
        val response = app.get(url, referer = mainUrl).document
        response.select("script[type=text/javascript]").map { script ->
            if (script.data().contains(Regex("eval\\(function\\(p,a,c,k,e,[rd]"))) {
                val unpackedscript = getAndUnpack(script.data())
                val m3u8Regex = Regex("file.\"(.*?m3u8.*?)\"")
                val m3u8 = m3u8Regex.find(unpackedscript)?.destructured?.component1() ?: ""
                if (m3u8.isNotEmpty()) {
                    generateM3u8(
                        name,
                        m3u8,
                        mainUrl
                    ).forEach(callback)
                }
            }
        }
    }

   /* private data class ResponseSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("label") val label: String?
    ) */

}