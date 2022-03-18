package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class AkwamProvider : MainAPI() {
    override val lang = "ar"
    override var mainUrl = "https://akwam.to"
    override var name = "Akwam"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime, TvType.Cartoon)

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("a.box")?.attr("href") ?: return null
        if (url.contains("/games/") || url.contains("/programs/")) return null
        val poster = select("picture > img")
        val title = poster.attr("alt")
        val posterUrl = poster.attr("data-src")
        val year = select(".badge-secondary")?.text()?.toIntOrNull()

        // If you need to differentiate use the url.
        return MovieSearchResponse(
            title,
            url,
            this@AkwamProvider.name,
            TvType.TvSeries,
            posterUrl,
            year,
            null,
        )
    }

    override suspend fun getMainPage(): HomePageResponse {
        // Title, Url
        val moviesUrl = listOf(
            "Movies" to "$mainUrl/movies",
            "Series" to "$mainUrl/series",
            "Shows" to "$mainUrl/shows"
        )
        val pages = moviesUrl.apmap {
            val doc = app.get(it.second).document
            val list = doc.select("div.col-lg-auto.col-md-4.col-6.mb-12").mapNotNull { element ->
                element.toSearchResponse()
            }
            HomePageList(it.first, list)
        }.sortedBy { it.name }
        return HomePageResponse(pages)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val doc = app.get(url).document
        return doc.select("div.col-lg-auto").mapNotNull {
            it.toSearchResponse()
        }
    }

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toTvSeriesEpisode(): TvSeriesEpisode {
        val a = select("a.text-white")
        val url = a.attr("href")
        val title = a.text()
        val thumbUrl = select("picture > img").attr("src")
        val date = select("p.entry-date").text()
        return TvSeriesEpisode(
            title,
            null,
            title.getIntFromText(),
            url,
            thumbUrl,
            date
        )
    }


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = url.contains("/movie/")
        val title = doc.select("h1.entry-title").text()
        val posterUrl = doc.select("picture > img").attr("src")

        val year =
            doc.select("div.font-size-16.text-white.mt-2").firstOrNull {
                it.text().contains("السنة")
            }?.text()?.getIntFromText()

        // A bit iffy to parse twice like this, but it'll do.
        val duration =
            doc.select("div.font-size-16.text-white.mt-2").firstOrNull {
                it.text().contains("مدة الفيلم")
            }?.text()?.getIntFromText()

        val synopsis = doc.select("div.widget-body p:first-child").text()

        val rating = doc.select("span.mx-2").text().split("/").lastOrNull()?.replace(" ", "")
            ?.toDoubleOrNull()
            ?.times(1000)?.toInt()

        val tags = doc.select("div.font-size-16.d-flex.align-items-center.mt-3 > a").map {
            it.text()
        }

        val actors = doc.select("div.widget-body > div > div.entry-box > a")?.mapNotNull {
            val name = it?.selectFirst("div > .entry-title")?.text() ?: return@mapNotNull null
            val image = it.selectFirst("div > img")?.attr("src") ?: return@mapNotNull null
            Actor(name, image)
        }

        val recommendations =
            doc.select("div > div.widget-body > div.row > div > div.entry-box")?.mapNotNull {
                val recTitle = it?.selectFirst("div.entry-body > .entry-title > .text-white")
                    ?: return@mapNotNull null
                val href = recTitle.attr("href") ?: return@mapNotNull null
                val name = recTitle.text() ?: return@mapNotNull null
                val poster = it.selectFirst(".entry-image > a > picture > img")?.attr("data-src")
                    ?: return@mapNotNull null
                MovieSearchResponse(name, href, this.name, TvType.Movie, fixUrl(poster))
            }

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
                this.rating = rating
                this.tags = tags
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
            }
        } else {
            val episodes = doc.select("div.bg-primary2.p-4.col-lg-4.col-md-6.col-12").map {
                it.toTvSeriesEpisode()
            }.let {
                val isReversed = it.lastOrNull()?.episode ?: 1 < it.firstOrNull()?.episode ?: 0
                if (isReversed)
                    it.reversed()
                else it
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.duration = duration
                this.posterUrl = posterUrl
                this.tags = tags.filterNotNull()
                this.rating = rating
                this.year = year
                this.plot = synopsis
                this.recommendations = recommendations
                addActors(actors)
            }
        }
    }


//    // Maybe possible to not use the url shortener but cba investigating that.
//    private suspend fun skipUrlShortener(url: String): AppResponse {
//        return app.get(app.get(url).document.select("a.download-link").attr("href"))
//    }

    private fun getQualityFromId(id: Int?): Qualities {
        return when (id) {
            2 -> Qualities.P360 // Extrapolated
            3 -> Qualities.P480
            4 -> Qualities.P720
            5 -> Qualities.P1080
            else -> Qualities.Unknown
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        val links = doc.select("div.tab-content.quality").map {
            val quality = getQualityFromId(it.attr("id").getIntFromText())
            it.select(".col-lg-6 > a:contains(تحميل)").map { linkElement ->
                if(linkElement.attr("href").contains("/download/")) { linkElement.attr("href") to quality } else {
                    "$mainUrl/download${linkElement.attr("href").split("/link")[1]}${data.split("/movie|/episode|/show/episode".toRegex())[1]}" to quality // just in case if they add the shorts urls again
                }
            }
        }.flatten()

        links.map {
            val linkDoc = app.get(it.first).document
            val button = linkDoc.select("div.btn-loader > a")
            val url = button.attr("href")

            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name + " - ${it.second.name.replace("P", "")}p",
                    url,
                    this.mainUrl,
                    it.second.value
                )
            )
        }
        return true
    }
}
