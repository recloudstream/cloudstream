package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor


class TantifilmProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://tantifilm.autos"
    override var name = "Tantifilm"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val mainPage = mainPageOf(
        Pair("$mainUrl/watch-genre/al-cinema/page/", "Ultimi Film"),
        Pair("$mainUrl/serie-tv/page/", "Ultime Serie Tv"),
        Pair("$mainUrl/watch-genre/film-aggiornati/page/", "Ultimi Film Aggiornati"),
    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val url = request.data + page
        val soup = app.get(url).document
        val home = soup.select("div.media3").map {
            val title = it.selectFirst("p")!!.text().substringBefore("(")
            val link = it.selectFirst("a")!!.attr("href")
            TvSeriesSearchResponse(
                title,
                link,
                this.name,
                TvType.Movie,
                it.selectFirst("img")!!.attr("src"),
                null,
                null,
            )
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val queryformatted = query.replace(" ", "+")
        val url = "$mainUrl/search/$queryformatted"
        val doc = app.get(url).document
        return doc.select("div.film.film-2").map {
            val href = it.selectFirst("a")!!.attr("href")
            val poster = it.selectFirst("img")!!.attr("src")
            val name = it.selectFirst("a > p")!!.text().substringBeforeLast("(")
            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                poster,
                null
            )

        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val type = if (document.selectFirst("div.category-film")!!.text().contains("Serie")
                .not()
        ) TvType.Movie else TvType.TvSeries
        val title = document.selectFirst("div.title-film-left")!!.text().substringBefore("(")
        val descipt = document.select("div.content-left-film > p").map { it.text() }
        val rating =
            document.selectFirst("div.star-rating.star-rating-f > span > span")!!
                .attr("data-rateit-value").toFloatOrNull()
                ?.times(2857)?.toInt()?.let { minOf(it, 10000) }

        var year = document.selectFirst("div.title-film-left")!!.text().substringAfter("(")
            .filter { it.isDigit() }
        year = if (year.length > 4) {
            year.dropLast(4)
        } else {
            year
        }
        // ?: does not wor
        val poster = document.selectFirst("div.image-right-film > img")!!.attr("src")

        val recomm = document.select("div.mediaWrap.mediaWrapAlt.recomended_videos").map {
            val href = it.selectFirst("a")!!.attr("href")
            val poster = it.selectFirst("img")!!.attr("src")
            val name = it.selectFirst("a > p")!!.text().substringBeforeLast("(")
            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                poster,
                null
            )

        }

        val trailerurl = document.selectFirst("#trailer_mob > iframe")!!.attr("src")

        if (type == TvType.TvSeries) {
            val list = ArrayList<Pair<Int, String>>()
            val urlvideocontainer = document.selectFirst("iframe")!!.attr("src")
            val videocontainer = app.get(urlvideocontainer).document
            videocontainer.select("nav.nav1 > select > option").forEach { element ->
                val season = element.text().toIntOrNull()
                val href = element.attr("value")
                if (season != null && season > 0 && !href.isNullOrBlank()) {
                    list.add(Pair(season, fixUrl(href)))
                }
            }
            if (list.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodeList = ArrayList<Episode>()

            for ((season, seasonurl) in list) {
                val seasonDocument = app.get(seasonurl).document
                val episodes = seasonDocument.select("nav.second_nav > select > option")
                if (episodes.isNotEmpty()) {
                    episodes.forEach { episode ->
                        val href = episode.attr("value")
                        val epNum = episode.text().toIntOrNull()
                        episodeList.add(
                            Episode(
                                href,
                                title,
                                season,
                                epNum,
                            )
                        )
                    }
                }
            }
            return newTvSeriesLoadResponse(
                title,
                url,
                type,
                episodeList
            ) {
                this.posterUrl = fixUrlNull(poster)
                this.year = year.toIntOrNull()
                this.plot = descipt[0]
                this.rating = rating
                this.recommendations = recomm
                addTrailer(trailerurl)
            }
        } else {
            val url2 = document.selectFirst("iframe")!!.attr("src")
            val actorpagelink =
                document.select("div.content-left-film > p:nth-child(2) > a").attr("href")
            val actorpagelink2 = document.select("div.content-left-film > p > a").attr("href")
            val Linkactor: String = actorpagelink.ifEmpty {
                actorpagelink2
            }

            val actors: List<ActorData>? = if (Linkactor.isNotEmpty()) {
                val actorpage = app.get(Linkactor + "cast/").document
                actorpage.select("article.membro-cast").filter {
                    it -> it.selectFirst("img")
                        ?.attr("src") != "https://www.filmtv.it/imgbank/DUMMY/no_portrait.jpg"
                }.mapNotNull {
                    val name = it.selectFirst("div.info > h3")!!.text()
                    val image = it.selectFirst("img")?.attr("src")
                    val roleString: String = if (it.selectFirst("h2")?.text() == "Regia") {
                        "Regia"
                    } else {
                        "Attore"
                    }
                    val mainActor = Actor(name, image)
                    ActorData(actor = mainActor, roleString = roleString)
                }
            } else {
                null
            }


            val duratio: Int? = if (descipt.size == 2) {
                descipt[0].filter { it.isDigit() }.toInt()
            } else {
                null
            }
            val tags: List<String>? = if (descipt.size == 2) {
                mutableListOf(descipt[0].substringBefore(" "))
            } else {
                null
            }
            val plot: String = if (descipt.size == 2) {
                descipt[1]
            } else {
                descipt[0]
            }
            return newMovieLoadResponse(
                title,
                url2,
                type,
                url2
            ) {
                posterUrl = fixUrlNull(poster)
                this.year = year.toIntOrNull()
                this.plot = plot
                this.rating = rating
                this.recommendations = recomm
                this.tags = tags
                this.duration = duratio
                this.actors = actors
                addTrailer(trailerurl)

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
        val iframe =
            doc.select("option").map { fixUrl(it.attr("value")) }.filter { it.contains("label") }
        iframe.forEach { id ->
            val doc2 = app.get(id).document
            val id2 = app.get(doc2.selectFirst("iframe")!!.attr("src")).url
            loadExtractor(id2, data, subtitleCallback, callback)
        }
        return true
    }
}