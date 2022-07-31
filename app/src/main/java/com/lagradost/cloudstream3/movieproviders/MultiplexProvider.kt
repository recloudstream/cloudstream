package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.nodes.Element
import java.util.ArrayList

class MultiplexProvider : MainAPI() {
    override var mainUrl = "https://146.19.24.137"
    override var name = "Multiplex"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("div.col-md-12 > div.home-widget").forEach { block ->
            val header = fixTitle(block.select("h3.homemodule-title").text())
            val items = block.select("div.col-md-125").mapNotNull {
                it.toSearchResult()
            }
            if (items.isNotEmpty()) homePageList.add(HomePageList(header, items))
        }

        document.select("div.container.gmr-maincontent")
            .forEach { block ->
                val header = fixTitle(block.select("h3.homemodule-title").text())
                val items = block.select("article.item").mapNotNull {
                    it.toSearchResult()
                }
                if (items.isNotEmpty()) homePageList.add(HomePageList(header, items))
            }

        document.select("div#idmuvi-rp-2").forEach { block ->
            val header = fixTitle(block.selectFirst("h3.widget-title")?.ownText()!!.trim())
            val items = block.select("div.idmuvi-rp ul li").mapNotNull {
                it.toBottomSearchResult()
            }
            if (items.isNotEmpty()) homePageList.add(HomePageList(header, items))
        }

        return HomePageResponse(homePageList)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("h2.entry-title > a")!!.text().trim()
        val href = this.selectFirst("a")!!.attr("href")
        val posterUrl = fixUrl(this.selectFirst("a > img")?.attr("data-src").toString())
        val quality = getQualityFromString(this.select("div.gmr-quality-item > a").text().trim())
        return if (quality == null) {
            val episode = this.select("div.gmr-numbeps > span").text().toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }

    }

    private fun Element.toBottomSearchResult(): SearchResponse {
        val title = this.selectFirst("a > span.idmuvi-rp-title")!!.text().trim()
        val href = this.selectFirst("a")!!.attr("href")
        val posterUrl = fixUrl(this.selectFirst("a > img")?.attr("data-src").toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val document = app.get(link).document

        return document.select("div#gmr-main-load > article.item").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.selectFirst("h1.entry-title")?.text()?.substringBefore("Season")?.trim()
                .toString()
        val poster =
            fixUrl(document.selectFirst("figure.pull-left > img")?.attr("data-src").toString())
        val tags = document.select("span.gmr-movie-genre:contains(Genre:) > a").map { it.text() }

        val year =
            document.select("span.gmr-movie-genre:contains(Year:) > a").text().trim().toIntOrNull()
        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div[itemprop=description] > p")?.text()?.trim()
        val trailer = document.selectFirst("ul.gmr-player-nav li a.gmr-trailer-popup")?.attr("href")
        val rating =
            document.selectFirst("div.gmr-meta-rating > span[itemprop=ratingValue]")?.text()
                ?.toRatingInt()
        val actors = document.select("div.gmr-moviedata").last()?.select("span[itemprop=actors]")?.map { it.select("a").text() }

        val recommendations = document.select("div.idmuvi-rp ul li").map {
            it.toBottomSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.gmr-listseries > a").map {
                val href = fixUrl(it.attr("href"))
                val episode = it.text().split(" ").last().toIntOrNull()
                val season = it.text().split(" ").first().substringAfter("S").toIntOrNull()
                Episode(
                    href,
                    "Episode $episode",
                    season,
                    episode,
                )
            }
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

    private data class ResponseSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("type") val type: String?,
        @JsonProperty("label") val label: String?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        val id = document.selectFirst("div#muvipro_player_content_id")!!.attr("data-id")
        val server = app.post(
            "$mainUrl/wp-admin/admin-ajax.php",
            data = mapOf("action" to "muvipro_player_content", "tab" to "player1", "post_id" to id)
        ).document.select("iframe").attr("src")

        app.get(server, referer = "$mainUrl/").document.select("script").map { script ->
            if (script.data().contains("var config = {")) {
                val source = script.data().substringAfter("sources: [").substringBefore("],")
                tryParseJson<List<ResponseSource>>("[$source]")?.map { m3u ->
                    val m3uData = app.get(m3u.file, referer = "https://gdriveplayer.link/").text
                    val quality =
                        Regex("\\d{3,4}\\.m3u8").findAll(m3uData).map { it.value }.toList()
                    quality.forEach {
                        callback.invoke(
                            ExtractorLink(
                                source = name,
                                name = name,
                                url = m3u.file.replace("video.m3u8", it),
                                referer = "https://gdriveplayer.link/",
                                quality = getQualityFromName("${it.replace(".m3u8", "")}p"),
                                isM3u8 = true
                            )
                        )
                    }
                }
            }
        }

        return true

    }


}