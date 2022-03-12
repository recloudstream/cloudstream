package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element

class EgyBestProvider : MainAPI() {
    override val lang = "ar"
    override val mainUrl = "https://egy.best"
    override val name = "EgyBest"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = this.attr("href") ?: return null
        val posterUrl = select("img")?.attr("src")
        val title = select("span.title").text()
            .replace("\\(.*\\)".toRegex(), "")
        val year = select("span.title").text()
            .replace(".*\\(|\\)".toRegex(), "")
        val tvType = if (url.contains("/movie/")) TvType.Movie else TvType.TvSeries
        // If you need to differentiate use the url.
        return MovieSearchResponse(
            title,
            url,
            this@EgyBestProvider.name,
            tvType,
            posterUrl,
            year.toIntOrNull(),
            null,
        )
    }

    override suspend fun getMainPage(): HomePageResponse {
        // url, title
        val pagesUrl = listOf(
            Pair("$mainUrl/movies/?page="+(0..25).random(), "Movies"),
            Pair("$mainUrl/tv/?page="+(0..25).random(), "Series"),
        )
        val pages = pagesUrl.apmap { (url, name) ->
            val doc = app.get(url).document
            val list = doc.select("div.movies a").not("a.auto.load.btn.b").mapNotNull { element ->
                element.toSearchResponse()
            }
            HomePageList(name, list)
        }.sortedBy { it.name }
        return HomePageResponse(pages)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ","%20")
        val result = arrayListOf<SearchResponse>()
        listOf("$mainUrl/explore/?q=$q").apmap { url ->
            val d = app.get(url).document
            d.select("div.movies a").not("a.auto.load.btn.b").mapNotNull {
                it.toSearchResponse()?.let { it1 -> result.add(it1) }
            }
        }
        return result.distinct().sortedBy { it.name }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = url.contains("/movie/")
        val posterUrl = doc.select("div.movie_img a img")?.attr("src")
        val year = doc.select("div.movie_title h1 a")?.text()?.toIntOrNull()
        val title = doc.select("div.movie_title h1 span[itemprop=\"name\"]").text()

        val synopsis = doc.select("div.mbox").firstOrNull {
            it.text().contains("القصة")
        }?.text()?.replace("القصة ", "")

        val tags = doc.select("table.movieTable tbody tr").firstOrNull {
            it.text().contains("النوع")
        }?.select("a")?.map { it.text() }

        return if (isMovie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.tags = tags
            }
        } else {
            val episodes = ArrayList<TvSeriesEpisode>()
            doc.select("#mainLoad > div:nth-child(2) > div.h_scroll > div a").map {
                it.attr("href")
            }.apmap {
                val d = app.get(it).document
                val season = Regex("season-(.....)").find(it)?.groupValues?.getOrNull(1)?.getIntFromText()
                d.select("#mainLoad > div:nth-child(3) > div.movies_small a").map { eit ->
                    val ep = Regex("ep-(.....)").find(eit.attr("href"))?.groupValues?.getOrNull(1)?.getIntFromText()
                    episodes.add(
                        TvSeriesEpisode(
                            eit.select("span.title").text(),
                            season,
                            ep,
                            eit.attr("href"),
                            null,
                            null
                        )
                    )
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().sortedBy { it.episode }) {
                this.posterUrl = posterUrl
                this.tags = tags
                this.year = year
                this.plot = synopsis
            }
        }
    }
    data class Sources (
        @JsonProperty("quality") val quality: Int?,
        @JsonProperty("link") val link: String
    )
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val requestJSON = app.get("https://zawmedia-api.herokuapp.com/egybest?url=$data").text
        val jsonArray = parseJson<List<Sources>>(requestJSON)
        for (i in jsonArray) {
            val quality = i.quality
            val link = i.link
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name + " ${quality}p",
                        link,
                        this.mainUrl,
                        2,
                        true
                    )
                )
        }
        return true
    }
}
