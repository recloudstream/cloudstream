package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class OlgplyProvider : TmdbProvider() {
    override var mainUrl = "https://olgply.com"
    override val apiName = "Olgply"
    override var name = "Olgply"
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mappedData = parseJson<TmdbLink>(data)
        val imdbId = mappedData.imdbID ?: return false
        val iframeUrl =
            "$mainUrl/api/?imdb=$imdbId${mappedData.season?.let { "&season=$it" } ?: ""}${mappedData.episode?.let { "&episode=$it" } ?: ""}"

        val iframeRegex = Regex("""file:.*?"([^"]*)""")

        val html = app.get(iframeUrl).text
        val link = iframeRegex.find(html)?.groupValues?.getOrNull(1) ?: return false
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                link,
                this.mainUrl + "/",
                Qualities.Unknown.value,
                headers = mapOf("range" to "bytes=0-")
            )
        )
        return true
    }
}