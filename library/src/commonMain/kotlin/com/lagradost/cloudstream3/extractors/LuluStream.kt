package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.JwPlayerHelper
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink


class Luluvdoo : LuluStream() {
    override var mainUrl = "https://luluvdoo.com"
}

class Lulustream1 : LuluStream() {
    override val name = "Lulustream"
    override val mainUrl = "https://lulustream.com"
}

class Lulustream2 : LuluStream() {
    override val name = "Lulustream"
    override val mainUrl = "https://kinoger.pw"
}

open class LuluStream : ExtractorApi() {
    override val name = "LuluStream"
    override val mainUrl = "https://luluvdo.com"
    override val requiresReferer = true

    protected open fun getFileCode(url: String): String {
        return url.substringAfterLast("/")
    }

    protected open suspend fun getPlayerScript(
        fileCode: String,
        url: String,
        referer: String?
    ): String {
        return app.post(
            "$mainUrl/dl",
            data = mapOf(
                "op" to "embed",
                "file_code" to fileCode,
                "auto" to "1",
                "referer" to (referer ?: "")
            )
        ).document
            .selectFirst("script:containsData(vplayer)")
            ?.data()
            .orEmpty()
    }

    protected open suspend fun parsePlayerScript(
        script: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        JwPlayerHelper.extractStreamLinks(script, name, mainUrl, callback, subtitleCallback)
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val fileCode = getFileCode(url)
        val script = getPlayerScript(fileCode, url, referer)
        if (script.isBlank()) return
        parsePlayerScript(script, subtitleCallback, callback)
    }
}