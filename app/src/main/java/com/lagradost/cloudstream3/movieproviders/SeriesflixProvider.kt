package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class SeriesflixProvider : MainAPI() {
    override var mainUrl = "https://seriesflix.video"
    override var name = "Seriesflix"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/ver-series-online/", "Series"),
            Pair("$mainUrl/genero/accion/", "Acción"),
            Pair("$mainUrl/genero/ciencia-ficcion/", "Ciencia ficción"),
        )
        for (i in urls) {
            try {
                val soup = app.get(i.first).document
                val home = soup.select("article.TPost.B").map {
                    val title = it.selectFirst("h2.title")!!.text()
                    val link = it.selectFirst("a")!!.attr("href")
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        TvType.Movie,
                        it.selectFirst("figure img")!!.attr("src"),
                        null,
                        null,
                    )
                }

                items.add(HomePageList(i.second, home))
            } catch (e: Exception) {
                logError(e)
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        return doc.select("article.TPost.B").map {
            val href = it.selectFirst("a")!!.attr("href")
            val poster = it.selectFirst("figure img")!!.attr("src")
            val name = it.selectFirst("h2.title")!!.text()
            val isMovie = href.contains("/movies/")
            if (isMovie) {
                MovieSearchResponse(
                    name,
                    href,
                    this.name,
                    TvType.Movie,
                    poster,
                    null
                )
            } else {
                TvSeriesSearchResponse(
                    name,
                    href,
                    this.name,
                    TvType.TvSeries,
                    poster,
                    null,
                    null
                )
            }
        }.toList()
    }


    override suspend fun load(url: String): LoadResponse {
        val type = if (url.contains("/movies/")) TvType.Movie else TvType.TvSeries

        val document = app.get(url).document

        val title = document.selectFirst("h1.Title")!!.text()
        val descRegex = Regex("(Recuerda.*Seriesflix.)")
        val descipt = document.selectFirst("div.Description > p")!!.text().replace(descRegex, "")
        val rating =
            document.selectFirst("div.Vote > div.post-ratings > span")?.text()?.toRatingInt()
        val year = document.selectFirst("span.Date")?.text()
        // ?: does not work
        val duration = try {
            document.selectFirst("span.Time")!!.text()
        } catch (e: Exception) {
            null
        }
        val postercss = document.selectFirst("head").toString()
        val posterRegex =
            Regex("(\"og:image\" content=\"https://seriesflix.video/wp-content/uploads/(\\d+)/(\\d+)/?.*.jpg)")
        val poster = try {
            posterRegex.findAll(postercss).map {
                it.value.replace("\"og:image\" content=\"", "")
            }.toList().first()
        } catch (e: Exception) {
            document.select(".TPostBg").attr("src")
        }

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
                val seasonDocument = app.get(season.second).document
                val episodes = seasonDocument.select("table > tbody > tr")
                if (episodes.isNotEmpty()) {
                    episodes.forEach { episode ->
                        val epNum = episode.selectFirst("> td > span.Num")?.text()?.toIntOrNull()
                        val epthumb = episode.selectFirst("img")?.attr("src")
                        val aName = episode.selectFirst("> td.MvTbTtl > a")
                        val name = aName!!.text()
                        val href = aName!!.attr("href")
                        val date = episode.selectFirst("> td.MvTbTtl > span")?.text()
                        episodeList.add(
                            newEpisode(href) {
                                this.name = name
                                this.season = season.first
                                this.episode = epNum
                                this.posterUrl = fixUrlNull(epthumb)
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
                fixUrlNull(poster),
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
                url
            ) {
                posterUrl = fixUrlNull(poster)
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
        app.get(data).document.select("ul.ListOptions li").forEach {
            val movieID = it.attr("data-id")
            val serverID = it.attr("data-key")
            val type = if (data.contains("movies")) 1 else 2
            val url =
                "$mainUrl/?trembed=$serverID&trid=$movieID&trtype=$type" //This is to get the POST key value
            val doc1 = app.get(url).document
            doc1.select("div.Video iframe").apmap {
                val iframe = it.attr("src")
                val postkey =
                    iframe.replace("https://sc.seriesflix.video/index.php?h=", "") // this obtains
                // djNIdHNCR2lKTGpnc3YwK3pyRCs3L2xkQmljSUZ4ai9ibTcza0JRODNMcmFIZ0hPejdlYW0yanJIL2prQ1JCZA POST KEY
                app.post(
                    "https://sc.seriesflix.video/r.php",
                    headers = mapOf(
                        "Host" to "sc.seriesflix.video",
                        "User-Agent" to USER_AGENT,
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Content-Type" to "application/x-www-form-urlencoded",
                        "Origin" to "null",
                        "DNT" to "1",
                        "Alt-Used" to "sc.seriesflix.video",
                        "Connection" to "keep-alive",
                        "Upgrade-Insecure-Requests" to "1",
                        "Sec-Fetch-Dest" to "iframe",
                        "Sec-Fetch-Mode" to "navigate",
                        "Sec-Fetch-Site" to "same-origin",
                        "Sec-Fetch-User" to "?1",
                    ),
                    params = mapOf(Pair("h", postkey)),
                    data = mapOf(Pair("h", postkey)),
                    allowRedirects = false
                ).okhttpResponse.headers.values("location").apmap { link ->
                    val url1 = link.replace("#bu", "")
                    loadExtractor(url1, data, subtitleCallback, callback)
                }
            }
        }
        return true
    }
}
