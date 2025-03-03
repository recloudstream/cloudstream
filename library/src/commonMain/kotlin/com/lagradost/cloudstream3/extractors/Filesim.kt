package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8

class Guccihide : Filesim() {
    override val name = "Guccihide"
    override var mainUrl = "https://guccihide.com"
}

class Ahvsh : Filesim() {
    override val name = "Ahvsh"
    override var mainUrl = "https://ahvsh.com"
}

class Moviesm4u : Filesim() {
    override val mainUrl = "https://moviesm4u.com"
    override val name = "Moviesm4u"
}

class FileMoonIn : Filesim() {
    override val mainUrl = "https://filemoon.in"
    override val name = "FileMoon"
}

class StreamhideTo : Filesim() {
    override val mainUrl = "https://streamhide.to"
    override val name = "Streamhide"
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
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var response = app.get(url.replace("/download/", "/e/"), referer = referer)
        val iframe = response.document.selectFirst("iframe")
        if (iframe != null) {
            response = app.get(
                iframe.attr("src"), headers = mapOf(
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Sec-Fetch-Dest" to "iframe"
                ), referer = response.url
            )
        }

        var script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }

         //In my case packed function is not directly available in the first response, instead it is in iframe response
        if(script == null){
            val iframeUrl = Regex("""<iframe src="(.*?)"""").find(response.text,0)?.groupValues?.getOrNull(1)
            if(iframeUrl != null){
                val iframeResponse = app.get(iframeUrl,referer=null, headers = mapOf("Accept-Language" to "en-US,en;q=0.5"))
                script = if (!getPacked(iframeResponse.text).isNullOrEmpty()) { getAndUnpack(iframeResponse.text) } else return
            }
            else return
        }

        val m3u8 =
            Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script)?.groupValues?.getOrNull(1)
        generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl
        ).forEach(callback)
    }

}