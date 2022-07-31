package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

class HDMProvider : MainAPI() {
    override var name = "HD Movies"
    override var mainUrl = "https://hdm.to"
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.Movie,
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/$query"
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val items = document.select("div.col-md-2 > article > a")
        if (items.isEmpty()) return emptyList()

        return items.map { i ->
            val href = i.attr("href")
            val data = i.selectFirst("> div.item")!!
            val img = data.selectFirst("> img")!!.attr("src")
            val name = data.selectFirst("> div.movie-details")!!.text()
            MovieSearchResponse(name, href, this.name, TvType.Movie, img, null)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "") return false
        val slug = Regex(".*/(.*?)\\.mp4").find(data)?.groupValues?.get(1) ?: return false
        val response = app.get(data).text
        val key = Regex("playlist\\.m3u8(.*?)\"").find(response)?.groupValues?.get(1) ?: return false
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

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val title = document.selectFirst("h2.movieTitle")?.text() ?: throw ErrorLoadingException("No Data Found")
        val poster = document.selectFirst("div.post-thumbnail > img")!!.attr("src")
        val descript = document.selectFirst("div.synopsis > p")!!.text()
        val year = document.select("div.movieInfoAll > div.row > div.col-md-6").getOrNull(1)?.selectFirst("> p > a")?.text()
            ?.toIntOrNull()
        val data = "src/player/\\?v=(.*?)\"".toRegex().find(response)?.groupValues?.get(1) ?: return null

        return MovieLoadResponse(
            title, url, this.name, TvType.Movie,
            "$mainUrl/src/player/?v=$data", poster, year, descript, null
        )
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val html = app.get(mainUrl, timeout = 25).text
        val document = Jsoup.parse(html)
        val all = ArrayList<HomePageList>()

        val mainbody = document.getElementsByTag("body")
            ?.select("div.homeContentOuter > section > div.container > div")
        // Fetch row title
        val inner = mainbody?.select("div.col-md-2.col-sm-2.mrgb")
        val title = mainbody?.select("div > div")?.firstOrNull()?.select("div.title.titleBar")?.text() ?: "Unnamed Row"
        // Fetch list of items and map
        if (inner != null) {
            val elements: List<SearchResponse> = inner.map {

                val aa = it.select("a").firstOrNull()
                val item = aa?.select("div.item")
                val href = aa?.attr("href")
                val link = when (href != null) {
                    true -> fixUrl(href)
                    false -> ""
                }
                val name = item?.select("div.movie-details")?.text() ?: "<No Title>"
                var image = item?.select("img")?.get(1)?.attr("src") ?: ""
                val year = null

                MovieSearchResponse(
                    name,
                    link,
                    this.name,
                    TvType.Movie,
                    image,
                    year,
                    null,
                )
            }

            all.add(
                HomePageList(
                    title, elements
                )
            )
        }
        return HomePageResponse(all)
    }
}