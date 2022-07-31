package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.extractorApis


class FrenchStreamProvider : MainAPI() {
    override var mainUrl = "https://french-stream.re"
    override var name = "French Stream"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.AnimeMovie, TvType.TvSeries, TvType.Movie)

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?do=search&subaction=search&story=$query"
        val soup = app.post(link).document

        return soup.select("div.short-in.nl").map { li ->
            val href = fixUrl(li.selectFirst("a.short-poster")!!.attr("href"))
            val poster = li.selectFirst("img")?.attr("src")
            val title = li.selectFirst("> a.short-poster")!!.text().toString().replace(". ", "")
            val year = li.selectFirst(".date")?.text()?.split("-")?.get(0)?.toIntOrNull()
            if (title.contains(
                    "saison",
                    ignoreCase = true
                )
            ) {  // if saison in title ==> it's a TV serie
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
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val soup = app.get(url).document

        val title = soup.selectFirst("h1#s-title")!!.text().toString()
        val isMovie = !title.contains("saison", ignoreCase = true)
        val description =
            soup.selectFirst("div.fdesc")!!.text().toString()
                .split("streaming", ignoreCase = true)[1].replace(" :  ", "")
        var poster = fixUrlNull(soup.selectFirst("div.fposter > img")?.attr("src"))
        val listEpisode = soup.select("div.elink")

        if (isMovie) {
            val tags = soup.select("ul.flist-col > li").getOrNull(1)
            val tagsList = tags?.select("a")
                ?.mapNotNull {   // all the tags like action, thriller ...; unused variable
                    it?.text()
                }
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                addRating(soup.select("div.fr-count > div").text())
                this.year = soup.select("ul.flist-col > li").getOrNull(2)?.text()?.toIntOrNull()
                this.tags = tagsList
                this.plot = description
                addTrailer(soup.selectFirst("div.fleft > span > a")?.attr("href"))
            }
        } else  // a tv serie
        {
            //println(listEpisode)
            //println("listeEpisode:")
            val episodeList = if ("<a" !in (listEpisode[0]).toString()) {  // check if VF is empty
                listEpisode[1]  // no vf, return vostfr
            } else {
                listEpisode[0] // no vostfr, return vf
            }

            //println(url)

            val episodes = episodeList.select("a").map { a ->
                val epNum = a.text().split("Episode")[1].trim().toIntOrNull()
                val epTitle = if (a.text().contains("Episode")) {
                    val type = if ("honey" in a.attr("id")) {
                        "VF"
                    } else {
                        "VOSTFR"
                    }
                    "Episode " + epNum?.toString() + " en " + type
                } else {
                    a.text()
                }
                if (poster == null) {
                    poster = a.selectFirst("div.fposter > img")?.attr("src")
                }
                Episode(
                    fixUrl(url).plus("-episodenumber:$epNum"),
                    epTitle,
                    null,
                    epNum,
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
            )
        }
    }

    fun translate(
        // the website has weird naming of series for episode 2 and 1 and original version content
        episodeNumber: String,
        is_vf_available: Boolean,
    ): String {
        return if (episodeNumber == "1") {
            if (is_vf_available) {  // 1 translate differently if vf is available or not
                "FGHIJK"
            } else {
                "episode033"
            }
        } else {
            "episode" + (episodeNumber.toInt() + 32).toString()
        }
    }

    override suspend fun loadLinks(
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
                    if (split[1] == "2") { // the episode number 2 has id of ABCDE, don't ask any question
                        "ABCDE"
                    } else {
                        "episode" + split[1]
                    }


                val soup = app.get(fixUrl(url)).document
                val div =
                    if (wantedEpisode == "episode1") {
                        "> div.tabs-sel "  // this element is added when the wanted episode is one (the place changes in the document)
                    } else {
                        ""
                    }
                val serversvf =// French version servers
                    soup.select("div#$wantedEpisode > div.selink > ul.btnss $div> li")
                        .mapNotNull { li ->  // list of all french version servers
                            val serverUrl = fixUrl(li.selectFirst("a")!!.attr("href"))
//                            val litext = li.text()
                            if (serverUrl.isNotBlank()) {
                                if (li.text().replace("&nbsp;", "").replace(" ", "").isNotBlank()) {
                                    Pair(li.text().replace(" ", ""), "vf" + fixUrl(serverUrl))
                                } else {
                                    null
                                }
                            } else {
                                null
                            }
                        }

                val translated = translate(split[1], serversvf.isNotEmpty())
                val serversvo =  // Original version servers
                    soup.select("div#$translated > div.selink > ul.btnss $div> li")
                        .mapNotNull { li ->
                            val serverUrl = fixUrlNull(li.selectFirst("a")?.attr("href"))
                            if (!serverUrl.isNullOrEmpty()) {
                                if (li.text().replace("&nbsp;", "").isNotBlank()) {
                                    Pair(li.text().replace(" ", ""), "vo" + fixUrl(serverUrl))
                                } else {
                                    null
                                }
                            } else {
                                null
                            }
                        }
                serversvf + serversvo
            } else {  // it's a movie
                val movieServers =
                    app.get(fixUrl(data)).document.select("nav#primary_nav_wrap > ul > li > ul > li > a")
                        .mapNotNull { a ->
                            val serverurl = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
                            val parent = a.parents()[2]
                            val element = parent.selectFirst("a")!!.text().plus(" ")
                            if (a.text().replace("&nbsp;", "").isNotBlank()) {
                                Pair(element.plus(a.text()), fixUrl(serverurl))
                            } else {
                                null
                            }
                        }
                movieServers
            }

        servers.apmap {
            for (extractor in extractorApis) {
                if (it.first.contains(extractor.name, ignoreCase = true)) {
                    //                    val name = it.first
                    //                    print("true for $name")
                    extractor.getSafeUrl(it.second, it.second, subtitleCallback, callback)
                    break
                }
            }
        }

        return true
    }


    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document
        val docs = document.select("div.sect")
        val returnList = docs.mapNotNull {
            val epList = it.selectFirst("> div.sect-c.floats.clearfix") ?: return@mapNotNull null
            val title =
                it.selectFirst("> div.sect-t.fx-row.icon-r > div.st-left > a.st-capt")!!.text()
            val list = epList.select("> div.short")
            val isMovieType = title.contains("Films")  // if truen type is Movie
            val currentList = list.map { head ->
                val hrefItem = head.selectFirst("> div.short-in.nl > a")
                val href = fixUrl(hrefItem!!.attr("href"))
                val img = hrefItem.selectFirst("> img")
                val posterUrl = img!!.attr("src")
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
