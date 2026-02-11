package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.JwPlayerHelper
import com.lagradost.cloudstream3.utils.*

class Neonime7n : Hxfile() {
    override val name = "Neonime7n"
    override val mainUrl = "https://neonime.fun"
    override val redirect = false
}

class Neonime8n : Hxfile() {
    override val name = "Neonime8n"
    override val mainUrl = "https://8njctn.neonime.net"
    override val redirect = false
}

class KotakAnimeid : Hxfile() {
    override val name = "KotakAnimeid"
    override val mainUrl = "https://nontonanimeid.bio"
    override val requiresReferer = true
}

class Yufiles : Hxfile() {
    override val name = "Yufiles"
    override val mainUrl = "https://yufiles.com"
}

class Aico : Hxfile() {
    override val name = "Aico"
    override val mainUrl = "https://aico.pw"
}

open class Hxfile : ExtractorApi() {
    override val name = "Hxfile"
    override val mainUrl = "https://hxfile.co"
    override val requiresReferer = false
    open val redirect = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, allowRedirects = redirect, referer = referer).document
        with(document) {
            this.select("script").map { script ->
                if (getPacked(script.data()) != null) {
                    val data = getAndUnpack(script.data())
                    JwPlayerHelper.extractStreamLinks(data, name, mainUrl, callback, subtitleCallback)
                } else if (JwPlayerHelper.canParseJwScript(script.data())) {
                    JwPlayerHelper.extractStreamLinks(script.data(), name, mainUrl, callback, subtitleCallback)
                }
            }
        }
    }
}
