package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.text
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

class HDMProvider : MainAPI() {
    override val name: String
        get() = "HD Movies"
    override val mainUrl: String
        get() = "https://hdm.to"

    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.Movie,
        )

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/$query"
        val response = get(url).text
        val document = Jsoup.parse(response)
        val items = document.select("div.col-md-2 > article > a")
        if (items.isEmpty()) return ArrayList()
        val returnValue = ArrayList<SearchResponse>()
        for (i in items) {
            val href = i.attr("href")
            val data = i.selectFirst("> div.item")
            val img = data.selectFirst("> img").attr("src")
            val name = data.selectFirst("> div.movie-details").text()
            returnValue.add(MovieSearchResponse(name, href, this.name, TvType.Movie, img, null))
        }

        return returnValue
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "") return false
        val slug = ".*/(.*?)\\.mp4".toRegex().find(data)?.groupValues?.get(1) ?: return false
        val response = get(data).text
        val key = "playlist\\.m3u8(.*?)\"".toRegex().find(response)?.groupValues?.get(1) ?: return false
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                "https://hls.1o.to/vod/$slug/playlist.m3u8$key",
                "",
                Qualities.P720.value,
                true
            )
        )
        return true
    }

    override fun load(url: String): LoadResponse? {
        val response = get(url).text
        val document = Jsoup.parse(response)
        val title = document.selectFirst("h2.movieTitle")?.text() ?: throw ErrorLoadingException("No Data Found")
        val poster = document.selectFirst("div.post-thumbnail > img").attr("src")
        val descript = document.selectFirst("div.synopsis > p").text()
        val year = document.select("div.movieInfoAll > div.row > div.col-md-6")?.get(1)?.selectFirst("> p > a")?.text()
            ?.toIntOrNull()
        val data = "src/player/\\?v=(.*?)\"".toRegex().find(response)?.groupValues?.get(1) ?: return null

        return MovieLoadResponse(
            title, url, this.name, TvType.Movie,
                "$mainUrl/src/player/?v=$data", poster, year, descript, null
        )
    }
}