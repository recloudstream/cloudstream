package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.JwPlayerHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink

class Meownime : JWPlayer() {
    override val name = "Meownime"
    override val mainUrl = "https://meownime.ltd"
}

class DesuOdchan : JWPlayer() {
    override val name = "DesuOdchan"
    override val mainUrl = "https://desustream.me/odchan/"
}

class DesuArcg : JWPlayer() {
    override val name = "DesuArcg"
    override val mainUrl = "https://desustream.me/arcg/"
}

class DesuDrive : JWPlayer() {
    override val name = "DesuDrive"
    override val mainUrl = "https://desustream.me/desudrive/"
}

class DesuOdvip : JWPlayer() {
    override val name = "DesuOdvip"
    override val mainUrl = "https://desustream.me/odvip/"
}

class Vidnest : JWPlayer() {
    override var name = "Vidnest"
    override var mainUrl = "https://vidnest.io"
}

open class BigwarpIO : JWPlayer() {
    override var name = "Bigwarp"
    override var mainUrl = "https://bigwarp.io"
}

class BgwpCC : BigwarpIO() {
    override var mainUrl = "https://bgwp.cc"
}

class BigwarpArt : BigwarpIO() {
    override var mainUrl = "https://bigwarp.art"
}

open class JWPlayer : ExtractorApi() {
    override val name = "JWPlayer"
    override val mainUrl = "https://www.jwplayer.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val script = app.get(url).document.selectFirst("script:containsData(sources:)") ?: return
        JwPlayerHelper.extractStreamLinks(script.data(), name, mainUrl, callback, subtitleCallback)
    }
}