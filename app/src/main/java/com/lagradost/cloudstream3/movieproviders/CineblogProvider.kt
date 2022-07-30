package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor


class CineblogProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://cb01.rip"
    override var name = "CineBlog"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, categoryName: String, categoryData: String): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/genere/azione/", "Azione"),
            Pair("$mainUrl/genere/avventura/", "Avventura"),
        )
        for ((url, name) in urls) {
            try {
                val soup = app.get(url).document
                val home = soup.select("article.item.movies").map {
                    val title = it.selectFirst("div.data > h3 > a")!!.text().substringBefore("(")
                    val link = it.selectFirst("div.poster > a")!!.attr("href")
                    val quality = getQualityFromString(it.selectFirst("span.quality")?.text())
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        TvType.Movie,
                        it.selectFirst("img")!!.attr("src"),
                        null,
                        null,
                        quality = quality
                    )
                }

                items.add(HomePageList(name, home))
            } catch (e: Exception) {
                logError(e)
            }
        }
        try {
            val soup = app.get("$mainUrl/serietv/").document
            val home = soup.select("article.item.tvshows").map {
                val title = it.selectFirst("div.data > h3 > a")!!.text().substringBefore("(")
                val link = it.selectFirst("div.poster > a")!!.attr("href")
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

            items.add(HomePageList("Serie tv", home))
        } catch (e: Exception) {
            logError(e)
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val queryformatted = query.replace(" ", "+")
        val url = "$mainUrl?s=$queryformatted"
        val doc = app.get(url,referer= mainUrl ).document
        return doc.select("div.result-item").map {
            val href = it.selectFirst("div.image > div > a")!!.attr("href")
            val poster = it.selectFirst("div.image > div > a > img")!!.attr("src")
            val name = it.selectFirst("div.details > div.title > a")!!.text().substringBefore("(")
            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                poster
            )

        }
    }

    override suspend fun load(url: String): LoadResponse {
        val page = app.get(url)
        val document = page.document
        val type = if (url.contains("film")) TvType.Movie else TvType.TvSeries
        val title = document.selectFirst("div.data > h1")!!.text().substringBefore("(")
        val description = document.select("#info > div.wp-content > p").html().toString()
        val rating = null
        var year = document.selectFirst(" div.data > div.extra > span.date")!!.text().substringAfter(",")
            .filter { it.isDigit() }
        if (year.length > 4) {
            year = year.dropLast(4)
        }

        val poster = document.selectFirst("div.poster > img")!!.attr("src")

        val recomm = document.select("#single_relacionados >article").map {
            val href = it.selectFirst("a")!!.attr("href")
            val posterUrl = it.selectFirst("a > img")!!.attr("src")
            val name = it.selectFirst("a > img")!!.attr("alt").substringBeforeLast("(")
            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                posterUrl
            )

        }


        if (type == TvType.TvSeries) {

            val episodeList = ArrayList<Episode>()
            document.select("#seasons > div").reversed().map { element ->
                val season = element.selectFirst("div.se-q > span.se-t")!!.text().toInt()
                element.select("div.se-a > ul > li").filter { it.text()!="There are still no episodes this season" }.map{ episode ->
                    val href = episode.selectFirst("div.episodiotitle > a")!!.attr("href")
                    val epNum =episode.selectFirst("div.numerando")!!.text().substringAfter("-").filter { it.isDigit() }.toIntOrNull()
                    val epTitle = episode.selectFirst("div.episodiotitle > a")!!.text()
                    val posterUrl =  episode.selectFirst("div.imagen > img")!!.attr("src")
                    episodeList.add(
                        Episode(
                            href,
                            epTitle,
                            season,
                            epNum,
                            posterUrl,
                        )
                    )
                }
            }
            return TvSeriesLoadResponse(
                title,
                url,
                this.name,
                type,
                episodeList,
                fixUrlNull(poster),
                year.toIntOrNull(),
                description,
                null,
                rating,
                null,
                null,
                mutableListOf(),
                recomm
            )
        } else {
            val actors: List<ActorData> =
                document.select("div.person").filter{it.selectFirst("div.img > a > img")?.attr("src")!!.contains("/no/cast.png").not()}.map { actordata ->
                    val actorName = actordata.selectFirst("div.data > div.name > a")!!.text()
                    val actorImage : String? = actordata.selectFirst("div.img > a > img")?.attr("src")
                    val roleActor = actordata.selectFirst("div.data > div.caracter")!!.text()
                    ActorData(actor = Actor(actorName, image = actorImage), roleString = roleActor )
                }
            return newMovieLoadResponse(
                title,
                url,
                type,
                url
            ) {
                posterUrl = fixUrlNull(poster)
                this.year = year.toIntOrNull()
                this.plot = description
                this.rating = rating
                this.recommendations = recomm
                this.duration = null
                this.actors = actors
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
        val type = if( data.contains("film") ){"movie"} else {"tv"}
        val idpost=doc.select("#player-option-1").attr("data-post")
        val test = app.post("$mainUrl/wp-admin/admin-ajax.php", headers = mapOf(
            "content-type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest",
        ), data = mapOf(
            "action" to "doo_player_ajax",
            "post" to idpost,
            "nume" to "1",
            "type" to type,
        ))

        val url2= Regex("""src='((.|\\n)*?)'""").find(test.text)?.groups?.get(1)?.value.toString()
        val trueUrl = app.get(url2, headers = mapOf("referer" to mainUrl)).url
        loadExtractor(trueUrl, data, subtitleCallback, callback)

        return true
    }
}