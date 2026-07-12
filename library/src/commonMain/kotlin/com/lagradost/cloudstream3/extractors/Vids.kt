package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Vids : ExtractorApi() {
    override val name: String = "Vids"
    override val mainUrl: String = "https://vids.st"
    override val requiresReferer: Boolean = false

    private val streamUrlRegex = Regex("""const url = "(.*?)";""")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).text
        val streamUrlMatch =
            streamUrlRegex.find(doc) ?: throw RuntimeException("vids: failed to find stream link")
        val streamUrl = streamUrlMatch.groupValues[1].replace("\\", "")

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = streamUrl
            )
        )
    }
}