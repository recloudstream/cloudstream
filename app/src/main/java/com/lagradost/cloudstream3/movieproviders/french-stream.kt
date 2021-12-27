package com.lagradost.cloudstream3.movieproviders

import org.jsoup.Jsoup
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.ExtractorLink


class FrenchStreamProvider : MainAPI() {
    override val mainUrl = "https://french-stream.re"
    override val name = "French Stream"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val lang = "fr"
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.TvSeries, TvType.Movie)

    override fun search(query: String): ArrayList<SearchResponse> {
        val link = "$mainUrl/?do=search&subaction=search&story=$query"
        val soup = app.post(link).document

        return ArrayList(soup.select("div.short-in.nl").map { li ->
            val href = fixUrl(li.selectFirst("a.short-poster").attr("href"))
            val poster = li.selectFirst("img")?.attr("src")
            val title = li.selectFirst("> a.short-poster").text().toString().replace(". ", "")
            val year = li.selectFirst(".date")?.text()?.split("-")?.get(0)?.toIntOrNull()
            if (title.contains("saison", ignoreCase = true)) {  // if saison in title ==> it's a TV serie
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    poster,
                    year,
                    (title.split("Eps ", " ")[1]).split(" ")[0].toIntOrNull()
                )
            } else {  // it's a movie
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    poster,
                    year,
                )
            }
        })
    }

    override fun load(url: String): LoadResponse? {
        val soup = app.get(url).document

        val title = soup.selectFirst("h1#s-title").text().toString()
        val isMovie = !title.contains("saison", ignoreCase = true)
        val description =
            soup.selectFirst("div.fdesc").text().toString()
                .split("streaming", ignoreCase = true)[1].replace(" :  ", "")
        var poster: String? = soup.selectFirst("div.fposter > img").attr("src").toString()
        val listEpisode = soup.selectFirst("div.elink")

        if (isMovie) {
            val trailer = soup.selectFirst("div.fleft > span > a").attr("href").toString()
            val date = soup.select("ul.flist-col > li")[2].text().toIntOrNull()
            val ratingAverage = soup.select("div.fr-count > div").text().toIntOrNull()
            val tags = soup.select("ul.flist-col > li")[1]
            val tagsList = tags.select("a")
                .map {   // all the tags like action, thriller ...; unused variable
                    it.text()
                }
            return MovieLoadResponse(
                title,
                url,
                this.name,
                TvType.Movie,
                url,
                poster,
                date,
                description,
                null,
                ratingAverage,
                tagsList,
                null,
                trailer
            )
        } else  // a tv serie
        {
            val episodes = listEpisode.select("a").map { a ->
                val epTitle = if (a.text()?.toString() != null)
                    if (a.text().contains("Episode")) {
                        "Episode " + a.text().split("Episode")[1].trim()
                    } else {
                        a.text()
                    }
                else ""
                if (poster == null) {
                    poster = a.selectFirst("div.fposter > img")?.attr("src")
                }

                val epNum = Regex("""Episode (\d+)""").find(epTitle)?.destructured?.component1()
                    ?.toIntOrNull()
                TvSeriesEpisode(
                    epTitle,
                    null,
                    epNum,
                    fixUrl(url).plus("-episodenumber:$epNum"),
                    null,  // episode Thumbnail
                    null // episode date
                )
            }
            return TvSeriesLoadResponse(
                title,
                url,
                this.name,
                TvType.TvSeries,
                episodes,
                poster,
                null,
                description,
                ShowStatus.Ongoing,
                null,
                null
            )
        }
    }

    fun translate(
        // the website has weird naming of series for episode 2 and 1 and original version content
        episodeNumber: String,
    ): String {
        return when (episodeNumber) {
            "ABCDE" -> "34" // ABCDE is episode 2
            "1" -> "FGHIJK"
            else -> (episodeNumber.toInt() + 32).toString()
        }
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val servers =
            if (data.contains("-episodenumber:"))// It's a serie:
            {
                val split =
                    data.split("-episodenumber:")  // the data contains the url and the wanted episode number (a temporary dirty fix that will last forever)
                val url = split[0]
                val wantedEpisode =
                    if (split[1] == "2") { // the episode number 2 has id of ABCDE, no questions asked
                        "ABCDE"
                    } else {
                        split[1]
                    }

                val translated = translate(wantedEpisode)
                val soup = app.get(fixUrl(url)).document
                val div =
                    if (wantedEpisode == "ABCDE") {  // Causes issues trying to convert to int with ABCDE abviously
                        ""
                    } else if (wantedEpisode.toInt() == 1) {
                        "> div.tabs-sel "  // this element is added when the wanted episode is one (the place changes in the document)
                    } else {
                        ""
                    }

                val serversvf =// French version servers
                    soup.select("div#episode$wantedEpisode > div.selink > ul.btnss $div> li")
                        .mapNotNull { li ->  // list of all french version servers
                            val serverUrl = fixUrl(li.selectFirst("a").attr("href"))
//                            val litext = li.text()
                            if (serverUrl != "") {
                                if (li.text().replace("&nbsp;", "").replace(" ", "") != "") {
                                    Pair(li.text().replace(" ", ""), fixUrl(serverUrl))
                                } else {
                                    null
                                }
                            } else {
                                null
                            }
                        }

                val serversvo =  // Original version servers
                    soup.select("div#episode$translated > div.selink > ul.btnss $div> li")
                        .mapNotNull { li ->
                            val serverurl = fixUrl(li.selectFirst("a").attr("href"))
                            if (serverurl != "") {
                                if (li.text().replace("&nbsp;", "").replace(" ", "") != "") {
                                    Pair(li.text().replace(" ", ""), fixUrl(serverurl))
                                } else {
                                    null
                                }
                            } else {
                                null
                            }
                        }
                serversvf + serversvo
            } else {  // it's a movie
                val soup = app.get(fixUrl(data)).document
                val movieServers =
                    soup.select("nav#primary_nav_wrap > ul > li > ul > li > a").mapNotNull { a ->
                        val serverurl = fixUrl(a.attr("href"))
                        val parent = a.parents()[2]
                        val element = parent.selectFirst("a").text().plus(" ")
                        if (a.text().replace("&nbsp;", "").trim() != "") {
                            Pair(element.plus(a.text()), fixUrl(serverurl))
                        } else {
                            null
                        }
                    }
                movieServers
            }

        servers.forEach {
            for (extractor in extractorApis) {
                if (it.first.contains(extractor.name, ignoreCase = true)) {
//                    val name = it.first
//                    print("true for $name")
                    extractor.getSafeUrl(it.second, it.second)?.forEach(callback)
                    break
                }
            }
        }

        return true
    }


    override fun getMainPage(): HomePageResponse? {
        val document = app.get(mainUrl).document
        val docs = document.select("div.sect")
        val returnList = docs.mapNotNull {
            val epList = it.selectFirst("> div.sect-c.floats.clearfix") ?: return@mapNotNull null
            val title =
                it.selectFirst("> div.sect-t.fx-row.icon-r > div.st-left > a.st-capt").text()
            val list = epList.select("> div.short")
            val isMovieType = title.contains("Films")  // if truen type is Movie
            val currentList = list.map { head ->
                val hrefItem = head.selectFirst("> div.short-in.nl > a")
                val href = fixUrl(hrefItem.attr("href"))
                val img = hrefItem.selectFirst("> img")
                val posterUrl = img.attr("src")
                val name = img.attr("> div.short-title").toString()
                return@map if (isMovieType) MovieSearchResponse(
                    name,
                    href,
                    this.name,
                    TvType.Movie,
                    posterUrl,
                    null
                ) else TvSeriesSearchResponse(
                    name,
                    href,
                    this.name,
                    TvType.TvSeries,
                    posterUrl,
                    null, null
                )
            }
            if (currentList.isNotEmpty()) {
                HomePageList(title, currentList)
            } else null
        }
        if (returnList.isEmpty()) return null
        return HomePageResponse(returnList)
    }
}
