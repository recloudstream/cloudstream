package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class PelisflixProvider : MainAPI() {
    override var mainUrl = "https://pelisflix.li"
    override var name = "Pelisflix"
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
            Pair("$mainUrl/ver-peliculas-online-gratis-fullhdc3/", "Películas"),
            Pair("$mainUrl/ver-series-online-gratis/", "Series"),
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
                        it.selectFirst("figure img")!!.attr("data-src"),
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
            val poster = it.selectFirst("figure img")!!.attr("data-src")
            val name = it.selectFirst("h2.title")!!.text()
            val isMovie = href.contains("/pelicula/")
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

    override suspend fun load(url: String): LoadResponse? {
        val type = if (url.contains("/pelicula/")) TvType.Movie else TvType.TvSeries

        val document = app.get(url).document

        val title = document.selectFirst("h1.Title")!!.text()
        val descRegex = Regex("(.Recuerda.*Pelisflix.+)")
        val descRegex2 = Regex("(Actualmente.*.)")
        val descRegex3 = Regex("(.*Director:.*)")
        val descRegex4 = Regex("(.*Actores:.*)")
        val descRegex5 = Regex("(Ver.*(\\)|)((\\d+).))")
        val descipt = document.selectFirst("div.Description")!!.text().replace(descRegex, "")
            .replace(descRegex2, "").replace(descRegex3, "")
            .replace(descRegex4, "").replace(descRegex5, "")
        val desc2Regex = Regex("(G(e|é)nero:.*..)")
        val descipt2 = document.selectFirst("div.Description")!!.text().replace(desc2Regex, "")
        val rating =
            document.selectFirst("div.rating-content button.like-mov span.vot_cl")?.text()
                ?.toFloatOrNull()
                ?.times(0)?.toInt()
        val year = document.selectFirst("span.Date")?.text()
        val duration =
            if (type == TvType.Movie) document.selectFirst(".Container .Container  span.Time")!!
                .text() else null
        val postercss = document.selectFirst("head").toString()
        val posterRegex =
            Regex("(\"og:image\" content=\"https:\\/\\/seriesflix.video\\/wp-content\\/uploads\\/(\\d+)\\/(\\d+)\\/?.*.jpg)")
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

            for ((seasonInt, seasonUrl) in list) {
                val seasonDocument = app.get(seasonUrl).document
                val episodes = seasonDocument.select("table > tbody > tr")
                if (episodes.isNotEmpty()) {
                    episodes.forEach { episode ->
                        val epNum = episode.selectFirst("> td > span.Num")?.text()?.toIntOrNull()
                        val epthumb = episode.selectFirst("img")?.attr("src")
                        val aName = episode.selectFirst("> td.MvTbTtl > a")
                        val name = aName!!.text()
                        val href = aName.attr("href")
                        val date = episode.selectFirst("> td.MvTbTtl > span")?.text()
                        episodeList.add(
                            newEpisode(href) {
                                this.name = name
                                this.season = seasonInt
                                this.episode =  epNum
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
                descipt2,
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
        app.get(data).document.select("li button.Button.sgty").forEach {
            val movieID = it.attr("data-id")
            val serverID = it.attr("data-key")
            val type = if (data.contains("pelicula")) 1 else 2
            val url =
                "$mainUrl/?trembed=$serverID&trid=$movieID&trtype=$type" //This is to get the POST key value
            val doc1 = app.get(url).document
            doc1.select("div.Video iframe").apmap {
                val iframe = it.attr("src")
                val postkey = iframe.replace("/stream/index.php?h=", "") // this obtains
                // djNIdHNCR2lKTGpnc3YwK3pyRCs3L2xkQmljSUZ4ai9ibTcza0JRODNMcmFIZ0hPejdlYW0yanJIL2prQ1JCZA POST KEY
                app.post(
                    "https://pelisflix.li/stream/r.php",
                    headers = mapOf(
                        "Host" to "pelisflix.li",
                        "User-Agent" to USER_AGENT,
                        "Accept" to "ext/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Content-Type" to "application/x-www-form-urlencoded",
                        "Origin" to "null",
                        "DNT" to "1",
                        "Connection" to "keep-alive",
                        "Upgrade-Insecure-Requests" to "1",
                        "Sec-Fetch-Dest" to "iframe",
                        "Sec-Fetch-Mode" to "navigate",
                        "Sec-Fetch-Site" to "same-origin",
                        "Sec-Fetch-User" to "?1",
                        "Pragma" to "no-cache",
                        "Cache-Control" to "no-cache",
                        "TE" to "trailers"
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