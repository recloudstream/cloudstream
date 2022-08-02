package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class LayarKacaProvider : MainAPI() {
    override var mainUrl = "https://lk21.xn--6frz82g"
    override var name = "LayarKaca"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Film Terplopuler",
        "$mainUrl/latest-series/page/" to "Series Terbaru",
        "$mainUrl/series/asian/page/" to "Film Asian Terbaru",
        "$mainUrl/latest/page/" to "Film Upload Terbaru",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article.mega-item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h1.grid-title > a")?.ownText()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("h1.grid-title > a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst(".grid-poster > a > img")?.attr("src"))
        val quality = this.select("div.quality").text().trim()
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            addQuality(quality)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = app.get(link).document

        return document.select("div.search-item").map {
            val title = it.selectFirst("h2 > a")!!.text().trim()
            val href = it.selectFirst("h2 > a")!!.attr("href")
            val posterUrl = fixUrl(it.selectFirst("img.img-thumbnail")?.attr("src").toString())
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("li.last > span[itemprop=name]")?.text()?.trim().toString()
        val poster = fixUrl(document.select("img.img-thumbnail").attr("src").toString())
        val tags = document.select("div.content > div:nth-child(5) > h3 > a").map { it.text() }

        val year = Regex("\\d, (\\d+)").find(
            document.select("div.content > div:nth-child(7) > h3").text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val tvType = if (document.select("div.serial-wrapper")
                .isNotEmpty()
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div.content > blockquote").text().trim()
        val trailer = document.selectFirst("div.action-player li > a.fancybox")?.attr("href")
        val rating =
            document.selectFirst("div.content > div:nth-child(6) > h3")?.text()?.toRatingInt()
        val actors =
            document.select("div.col-xs-9.content > div:nth-child(3) > h3 > a").map { it.text() }

        val recommendations = document.select("div.row.item-media").map {
            val recName = it.selectFirst("h3")?.text()?.trim().toString()
            val recHref = it.selectFirst(".content-media > a")!!.attr("href")
            val recPosterUrl =
                fixUrl(it.selectFirst(".poster-media > a > img")?.attr("src").toString())
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.episode-list > a:matches(\\d+)").map {
                val href = fixUrl(it.attr("href"))
                val episode = it.text().toIntOrNull()
                val season =
                    it.attr("href").substringAfter("season-").substringBefore("-").toIntOrNull()
                Episode(
                    href,
                    "Episode $episode",
                    season,
                    episode,
                )
            }.reversed()
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

        val document = app.get(data).document

        val sources = if (data.contains("-episode-")) {
            document.select("script").mapNotNull { script ->
                if (script.data().contains("var data =")) {
                    val scriptData =
                        script.toString().substringAfter("var data = '").substringBefore("';")
                    Jsoup.parse(scriptData).select("li").map {
                        fixUrl(it.select("a").attr("href"))
                    }
                } else {
                    null
                }
            }[0]
        } else {
            document.select("ul#loadProviders > li").map {
                fixUrl(it.select("a").attr("href"))
            }
        }

        sources.apmap {
            val link = if (it.startsWith("https://layarkacaxxi.icu")) {
                it.substringBeforeLast("/")
            } else {
                it
            }
            loadExtractor(link, data, subtitleCallback, callback)
        }

        return true
    }


}