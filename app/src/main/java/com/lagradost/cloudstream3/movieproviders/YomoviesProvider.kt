package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class YomoviesProvider : MainAPI() {
    override var mainUrl = "https://yomovies.vip"
    override var name = "Yomovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("div.movies-list-wrap.mlw-topview,div.movies-list-wrap.mlw-latestmovie")
            .forEach { block ->
                val header = fixTitle(block.selectFirst("div.ml-title span")?.text() ?: "")
                val items = block.select("div.ml-item").mapNotNull {
                    it.toSearchResult()
                }
                if (items.isNotEmpty()) homePageList.add(HomePageList(header, items))
            }

        return HomePageResponse(homePageList)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-original"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select("div.ml-item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.mvic-desc h3")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.thumb.mvic-thumb img")?.attr("src"))
        val tags = document.select("div.mvici-left p:nth-child(1) a").map { it.text() }
        val year = document.select("div.mvici-right p:nth-child(3) a").text().trim()
            .toIntOrNull()
        val tvType = if (document.selectFirst("div.les-content")
                ?.select("a")?.size!! <= 1
        ) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("p.f-desc")?.text()?.trim()
        val trailer = fixUrlNull(document.select("iframe#iframe-trailer").attr("src"))
        val rating = document.select("div.mvici-right > div.imdb_r span").text().toRatingInt()
        val actors = document.select("div.mvici-left p:nth-child(3) a").map { it.text() }
        val recommendations = document.select("div.ml-item").mapNotNull {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.les-content a").map {
                val href = it.attr("href")
                val name = it.text().trim()
                Episode(
                    data = href,
                    name = name,
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        app.get(data).document.select("div.movieplay iframe").map { fixUrl(it.attr("src")) }
            .apmap { source ->
                safeApiCall {
                    when {
                        source.startsWith("https://membed.net") -> app.get(
                            source,
                            referer = "$mainUrl/"
                        ).document.select("ul.list-server-items li")
                            .apmap {
                                loadExtractor(
                                    it.attr("data-video").substringBefore("=https://msubload"),
                                    "$mainUrl/",
                                    subtitleCallback,
                                    callback
                                )
                            }
                        else -> loadExtractor(source, "$mainUrl/", subtitleCallback, callback)
                    }
                }
            }

        return true
    }


}
