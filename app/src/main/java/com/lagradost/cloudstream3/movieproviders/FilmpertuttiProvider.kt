package com.lagradost.cloudstream3.movieproviders

import androidx.core.text.parseAsHtml
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ShortLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element


class FilmpertuttiProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://filmpertutti.photo"
    override var name = "Filmpertutti"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        Pair("$mainUrl/category/film/page/", "Film Popolari"),
        Pair("$mainUrl/category/serie-tv/page/", "Serie Tv Popolari"),
        Pair("$mainUrl/prime-visioni/", "Ultime uscite"),
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = request.data + page

        val soup = app.get(url).document
        val home = soup.select("ul.posts > li").map {
            val title = it.selectFirst("div.title")!!.text().substringBeforeLast("(")
                .substringBeforeLast("[")
            val link = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst("a")!!.attr("data-thumbnail")
            val qualitydata = it.selectFirst("div.hd")
            val quality = if (qualitydata != null) {
                getQualityFromString(qualitydata.text())
            } else {
                null
            }
            newTvSeriesSearchResponse(
                title,
                link
            ) {
                this.posterUrl = image
                this.quality = quality
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val queryformatted = query.replace(" ", "+")
        val url = "$mainUrl/?s=$queryformatted"
        val doc = app.get(url).document
        return doc.select("ul.posts > li").map {
            val title = it.selectFirst("div.title")!!.text().substringBeforeLast("(")
                .substringBeforeLast("[")
            val link = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst("a")!!.attr("data-thumbnail")
            val quality = getQualityFromString(it.selectFirst("div.hd")?.text())

            MovieSearchResponse(
                title,
                link,
                this.name,
                quality = quality,
                posterUrl = image
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val type =
            if (document.selectFirst("a.taxonomy.category")!!.attr("href").contains("serie-tv")
                    .not()
            ) TvType.Movie else TvType.TvSeries
        val title = document.selectFirst("#content > h1")!!.text().substringBeforeLast("(")
            .substringBeforeLast("[")

        val description =
            document.selectFirst("i.fa.fa-file-text-o.fa-fw")?.parent()?.nextSibling()?.toString()
                ?.parseAsHtml().toString()


        val rating = document.selectFirst("div.rating > div.value")?.text()

        val year =
            document.selectFirst("#content > h1")?.text()?.substringAfterLast("(")
                ?.filter { it.isDigit() }?.toIntOrNull()
                ?: description.substringAfter("trasmessa nel").take(6).filter { it.isDigit() }
                    .toIntOrNull() ?: (document.selectFirst("i.fa.fa-calendar.fa-fw")?.parent()
                    ?.nextSibling() as Element?)?.text()?.substringAfterLast(" ")
                    ?.filter { it.isDigit() }?.toIntOrNull()


        val poster = document.selectFirst("div.meta > div > img")?.attr("data-src")


        val trailerurl =
            document.selectFirst("div.youtube-player")?.attr("data-id")?.let { urldata ->
                "https://www.youtube.com/watch?v=$urldata"
            }

        if (type == TvType.TvSeries) {

            val episodeList = ArrayList<Episode>()
            document.select("div.accordion-item").filter { a ->
                a.selectFirst("#season > ul > li.s_title > span")!!.text().isNotEmpty()
            }.map { element ->
                val season =
                    element.selectFirst("#season > ul > li.s_title > span")!!.text().toInt()
                element.select("div.episode-wrap").map { episode ->
                    val href =
                        episode.select("#links > div > div > table > tbody:nth-child(2) > tr")
                            .map { it.selectFirst("a")!!.attr("href") }.toJson()
                    val epNum = episode.selectFirst("li.season-no")!!.text().substringAfter("x")
                        .filter { it.isDigit() }.toIntOrNull()
                    val epTitle = episode.selectFirst("li.other_link > a")?.text()

                    val posterUrl = episode.selectFirst("figure > img")?.attr("data-src")
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
            return newTvSeriesLoadResponse(
                title,
                url, type, episodeList
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                addRating(rating)
                addTrailer(trailerurl)
            }
        } else {

            val urls0 = document.select("div.embed-player")
            val urls = if (urls0.isNotEmpty()) {
                urls0.map { it.attr("data-id") }.toJson()
            } else {
                document.select("#info > ul > li ").mapNotNull { it.selectFirst("a")?.attr("href") }
                    .toJson()
            }

            return newMovieLoadResponse(
                title,
                url,
                type,
                urls
            ) {
                posterUrl = fixUrlNull(poster)
                this.year = year
                this.plot = description
                addRating(rating)
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
        tryParseJson<List<String>>(data)?.apmap { id ->
            val link = ShortLink.unshorten(id).trim().replace("/v/", "/e/").replace("/f/", "/e/")
            loadExtractor(link, data, subtitleCallback, callback)
        }
        return true
    }
}