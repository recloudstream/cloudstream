package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class AllMoviesForYouProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return when {
                t.contains("series") -> TvType.TvSeries
                t.contains("movies") -> TvType.Movie
                else -> TvType.Movie
            }
        }
    }

    // Fetching movies will not work if this link is outdated.
    override var mainUrl = "https://allmoviesforyou.net"
    override var name = "AllMoviesForYou"
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = app.get(mainUrl).document
        val urls = listOf(
            Pair("Movies", "section[data-id=movies] article.TPost.B"),
            Pair("TV Series", "section[data-id=series] article.TPost.B"),
        )
        for ((name, element) in urls) {
            try {
                val home = soup.select(element).map {
                    val title = it.selectFirst("h2.title")!!.text()
                    val link = it.selectFirst("a")!!.attr("href")
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        TvType.Movie,
                        fixUrl(it.selectFirst("figure img")!!.attr("data-src")),
                        null,
                        null,
                    )
                }

                items.add(HomePageList(name, home))
            } catch (e: Exception) {
                logError(e)
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document

        val items = document.select("ul.MovieList > li > article > a")
        return items.map { item ->
            val href = item.attr("href")
            val title = item.selectFirst("> h2.Title")!!.text()
            val img = fixUrl(item.selectFirst("> div.Image > figure > img")!!.attr("data-src"))
            val type = getType(href)
            if (type == TvType.Movie) {
                MovieSearchResponse(title, href, this.name, type, img, null)
            } else {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    type,
                    img,
                    null,
                    null
                )
            }
        }
    }

//    private fun getLink(document: Document): List<String>? {
//         val list = ArrayList<String>()
//         Regex("iframe src=\"(.*?)\"").find(document.html())?.groupValues?.get(1)?.let {
//             list.add(it)
//         }
//         document.select("div.OptionBx")?.forEach { element ->
//             val baseElement = element.selectFirst("> a.Button")
//             val elementText = element.selectFirst("> p.AAIco-dns")?.text()
//             if (elementText == "Streamhub" || elementText == "Dood") {
//                 baseElement?.attr("href")?.let { href ->
//                     list.add(href)
//                 }
//             }
//         }
//
//         return if (list.isEmpty()) null else list
//     }

    override suspend fun load(url: String): LoadResponse {
        val type = getType(url)

        val document = app.get(url).document

        val title = document.selectFirst("h1.Title")!!.text()
        val descipt = document.selectFirst("div.Description > p")!!.text()
        val rating =
            document.selectFirst("div.Vote > div.post-ratings > span")?.text()?.toRatingInt()
        val year = document.selectFirst("span.Date")?.text()
        val duration = document.selectFirst("span.Time")!!.text()
        val backgroundPoster =
            fixUrlNull(document.selectFirst("div.Image > figure > img")?.attr("data-src"))

        if (type == TvType.TvSeries) {
            val list = ArrayList<Pair<Int, String>>()

            document.select("main > section.SeasonBx > div > div.Title > a").forEach { element ->
                val season = element.selectFirst("> span")?.text()?.toIntOrNull()
                val href = element.attr("href")
                if (season != null && season > 0 && !href.isNullOrBlank()) {
                    list.add(Pair(season, fixUrl(href)))
                }
            }
            if (list.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodeList = ArrayList<Episode>()

            for (season in list) {
                val seasonResponse = app.get(season.second).text
                val seasonDocument = Jsoup.parse(seasonResponse)
                val episodes = seasonDocument.select("table > tbody > tr")
                if (episodes.isNotEmpty()) {
                    episodes.forEach { episode ->
                        val epNum = episode.selectFirst("> td > span.Num")?.text()?.toIntOrNull()
                        val poster = episode.selectFirst("> td.MvTbImg > a > img")?.attr("data-src")
                        val aName = episode.selectFirst("> td.MvTbTtl > a")
                        val name = aName!!.text()
                        val href = aName.attr("href")
                        val date = episode.selectFirst("> td.MvTbTtl > span")?.text()

                        episodeList.add(
                            newEpisode(href) {
                                this.name = name
                                this.season = season.first
                                this.episode = epNum
                                this.posterUrl = fixUrlNull(poster)
                                addDate(date)
                            }
                        )
                    }
                }
            }
            return TvSeriesLoadResponse(
                title,
                url,
                this.name,
                type,
                episodeList,
                backgroundPoster,
                year?.toIntOrNull(),
                descipt,
                null,
                rating
            )
        } else {
            return newMovieLoadResponse(
                title,
                url,
                type,
                fixUrl(url)
            ) {
                posterUrl = backgroundPoster
                this.year = year?.toIntOrNull()
                this.plot = descipt
                this.rating = rating
                addDuration(duration)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val iframe = doc.select("body iframe").map { fixUrl(it.attr("src")) }
        iframe.apmap { id ->
            if (id.contains("trembed")) {
                val soup = app.get(id).document
                soup.select("body iframe").map {
                    val link = fixUrl(it.attr("src").replace("streamhub.to/d/", "streamhub.to/e/"))
                    loadExtractor(link, data, subtitleCallback, callback)
                }
            } else loadExtractor(id, data, subtitleCallback, callback)
        }
        return true
    }
}
