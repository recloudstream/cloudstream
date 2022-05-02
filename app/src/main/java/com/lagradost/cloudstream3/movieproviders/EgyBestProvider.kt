package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class EgyBestProvider : MainAPI() {
    override val lang = "ar"
    override var mainUrl = "https://www.egy.best"
    override var name = "EgyBest"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = this.attr("href") ?: return null
        val posterUrl = select("img")?.attr("src")
        var title = select("span.title").text()
        val year = title.getYearFromTitle()
        val isMovie = Regex(".*/movie/.*|.*/masrahiya/.*").matches(url)
        val tvType = if (isMovie) TvType.Movie else TvType.TvSeries
        title = if (year !== null) title else title.split(" (")[0].trim()
        val quality = select("span.ribbon span").text().replace("-", "")
        // If you need to differentiate use the url.
        return MovieSearchResponse(
            title,
            url,
            this@EgyBestProvider.name,
            tvType,
            posterUrl,
            year,
            null,
            quality = getQualityFromString(quality)
        )
    }

    override suspend fun getMainPage(): HomePageResponse {
        // url, title
        val doc = app.get(mainUrl).document
        val pages = arrayListOf<HomePageList>()
        doc.select("#mainLoad div.mbox").apmap {
            val name = it.select(".bdb.pda > strong").text()
            if (it.select(".movie").first()?.attr("href")?.contains("season-(.....)|ep-(.....)".toRegex()) == true) return@apmap
            val list = arrayListOf<SearchResponse>()
            it.select(".movie").map { element ->
                list.add(element.toSearchResponse()!!)
            }
            pages.add(HomePageList(name, list))
        }
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

    private fun String.getYearFromTitle(): Int? {
        return Regex("""\(\d{4}\)""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = Regex(".*/movie/.*|.*/masrahiya/.*").matches(url)
        val posterUrl = doc.select("div.movie_img a img")?.attr("src")
        val year = doc.select("div.movie_title h1 a")?.text()?.toIntOrNull()
        val title = doc.select("div.movie_title h1 span").text()

        val synopsis = doc.select("div.mbox").firstOrNull {
            it.text().contains("القصة")
        }?.text()?.replace("القصة ", "")

        val tags = doc.select("table.movieTable tbody tr").firstOrNull {
            it.text().contains("النوع")
        }?.select("a")?.map { it.text() }

        val actors = doc.select("div.cast_list .cast_item").mapNotNull {
            val name = it.selectFirst("div > a > img")?.attr("alt") ?: return@mapNotNull null
            val image = it.selectFirst("div > a > img")?.attr("src") ?: return@mapNotNull null
            val roleString = it.selectFirst("div > span")!!.text()
            val mainActor = Actor(name, image)
            ActorData(actor = mainActor, roleString = roleString)
        }

        return if (isMovie) {
            val recommendations = doc.select(".movies_small .movie").mapNotNull { element ->
                element.toSearchResponse()
            }

            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.recommendations = recommendations
                this.plot = synopsis
                this.tags = tags
                this.actors = actors
            }
        } else {
            val episodes = ArrayList<Episode>()
            doc.select("#mainLoad > div:nth-child(2) > div.h_scroll > div a").map {
                it.attr("href")
            }.apmap {
                val d = app.get(it).document
                val season = Regex("season-(.....)").find(it)?.groupValues?.getOrNull(1)?.getIntFromText()
                if(d.select("tr.published").isNotEmpty()) {
                    d.select("tr.published").map { element ->
                        val ep = Regex("ep-(.....)").find(element.select(".ep_title a").attr("href"))?.groupValues?.getOrNull(1)?.getIntFromText()
                        episodes.add(
                            Episode(
                                element.select(".ep_title a").attr("href"),
                                name = element.select("td.ep_title").html().replace(".*</span>|</a>".toRegex(), ""),
                                season,
                                ep,
                                rating = element.select("td.tam:not(.date, .ep_len)").text().getIntFromText()
                            )
                        )
                    }
                } else {
                    d.select("#mainLoad > div:nth-child(3) > div.movies_small a").map { eit ->
                        val ep = Regex("ep-(.....)").find(eit.attr("href"))?.groupValues?.getOrNull(1)?.getIntFromText()
                        episodes.add(
                            Episode(
                                eit.attr("href"),
                                eit.select("span.title").text(),
                                season,
                                ep,
                            )
                        )
                    }
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().sortedBy { it.episode }) {
                this.posterUrl = posterUrl
                this.tags = tags
                this.year = year
                this.plot = synopsis
                this.actors = actors
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
        val requestJSON = app.get("https://api.zr5.repl.co/egybest?url=$data").text
        val jsonArray = parseJson<List<Sources>>(requestJSON)
        for (i in jsonArray) {
            val quality = i.quality
            val link = i.link
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    link,
                    this.mainUrl,
                    quality!!,
                    true
                )
            )
        }
        return true
    }
}
