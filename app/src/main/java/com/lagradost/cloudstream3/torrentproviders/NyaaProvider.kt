package com.lagradost.cloudstream3.torrentproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

class NyaaProvider : MainAPI() {
    override var name = "Nyaa"
    override val hasChromecastSupport = false

    override var mainUrl = "https://nyaa.si"
    override val supportedTypes = setOf(TvType.Torrent)
    override val vpnStatus = VPNStatus.Torrent
    override val instantLinkLoading = true

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?f=0&c=0_0&q=$query&s=seeders&o=desc"
        val response = app.get(url).text
        val document = Jsoup.parse(response)

        val returnValues = ArrayList<SearchResponse>()
        val elements = document.select("table > tbody > tr")
        for (element in elements) {
            val tds = element.select("> td")
            if (tds.size < 2) continue
            val type = tds[0].select("> a").attr("title")
            val titleHeader = tds[1].select("> a").last()
            val href = titleHeader!!.attr("href")
            val title = titleHeader.text()
            if (title.contains("[Batch]") || !type.contains("Anime")) continue
            returnValues.add(TorrentSearchResponse(title, fixUrl(href), this.name, TvType.Torrent, null))
        }

        return returnValues
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val title = document.selectFirst("h3.panel-title")!!.text()
        val description = document.selectFirst("div#torrent-description")!!.text()
        val downloadLinks = document.select("div.panel-footer > a")
        val magnet = downloadLinks[1].attr("href")
        val torrent = downloadLinks[0].attr("href")

        return TorrentLoadResponse(title, url, this.name, magnet, fixUrl(torrent), description)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (isCasting) return false
        callback.invoke(ExtractorLink(this.name, this.name, data, "", Qualities.Unknown.value))
        return true
    }
}