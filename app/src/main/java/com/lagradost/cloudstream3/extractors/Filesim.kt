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
        val response = app.get(url, referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 =
            Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl
        ).forEach(callback)
    }

}