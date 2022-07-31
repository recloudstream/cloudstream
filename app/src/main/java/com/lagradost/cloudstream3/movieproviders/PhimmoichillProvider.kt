package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.util.ArrayList

class PhimmoichillProvider : MainAPI() {
    override var mainUrl = "https://phimmoichill.net"
    override var name = "Phimmoichill"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("div.container div.block").forEach { block ->
            val header = fixTitle(block.selectFirst("h2")!!.text())
            val items = block.select("li.item").mapNotNull {
                it.toSearchResult()
            }
            if (items.isNotEmpty()) homePageList.add(HomePageList(header, items))
        }

        return HomePageResponse(homePageList)
    }

    private fun decode(input: String): String? = URLDecoder.decode(input, "utf-8")

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("p,h3")?.text()?.trim().toString()
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = decode(this.selectFirst("img")!!.attr("src").substringAfter("url="))
        val temp = this.select("span.label").text()
        return if (temp.contains(Regex("\\d"))) {
            val episode = Regex("(\\((\\d+))|(\\s(\\d+))").find(temp)?.groupValues?.map { num ->
                num.replace(Regex("\\(|\\s"), "")
            }?.distinct()?.firstOrNull()?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            val quality =
                temp.replace(Regex("(-.*)|(\\|.*)|(?i)(VietSub.*)|(?i)(Thuyết.*)"), "").trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/tim-kiem/$query"
        val document = app.get(link).document

        return document.select("ul.list-film li").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1[itemprop=name]")?.text()?.trim().toString()
        val link = document.select("ul.list-button li:last-child a").attr("href")
        val poster = document.selectFirst("div.image img[itemprop=image]")?.attr("src")
        val tags = document.select("ul.entry-meta.block-film li:nth-child(4) a").map { it.text() }
        val year = document.select("ul.entry-meta.block-film li:nth-child(2) a").text().trim()
            .toIntOrNull()
        val tvType = if (document.select("div.latest-episode").isNotEmpty()
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div#film-content-wrapper").text().trim()
        val trailer =
            document.select("div#trailer script").last()?.data()?.substringAfter("file: \"")
                ?.substringBefore("\",")
        val rating =
            document.select("ul.entry-meta.block-film li:nth-child(7) span").text().toRatingInt()
        val actors = document.select("ul.entry-meta.block-film li:last-child a").map { it.text() }
        val recommendations = document.select("ul#list-film-realted li.item").map {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val docEpisodes = app.get(link).document
            val episodes = docEpisodes.select("ul#list_episodes > li").map {
                val href = it.select("a").attr("href")
                val episode =
                    it.select("a").text().replace(Regex("[^0-9]"), "").trim().toIntOrNull()
                val name = "Episode $episode"
                Episode(
                    data = href,
                    name = name,
                    episode = episode,
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
            newMovieLoadResponse(title, url, TvType.Movie, link) {
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        val key = document.select("div#content script").mapNotNull { script ->
            if (script.data().contains("filmInfo.episodeID =")) {
                val id = script.data().substringAfter("filmInfo.episodeID = parseInt('")
                    .substringBefore("');")
                app.post(
                    url = "$mainUrl/pmplayer.php",
                    data = mapOf("qcao" to id),
                    referer = data,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).text.substringAfterLast("iniPlayers(\"").substringBefore("\",")
            } else {
                null
            }
        }.first()

        listOf(
            Pair("https://so-trym.topphimmoi.org/hlspm/$key", "PMFAST"),
            Pair("https://dash.megacdn.xyz/hlspm/$key", "PMHLS"),
            Pair("https://dash.megacdn.xyz/dast/$key/index.m3u8", "PMBK")
        ).apmap { (link, source) ->
            safeApiCall {
                if (source == "PMBK") {
                    callback.invoke(
                        ExtractorLink(
                            source,
                            source,
                            link,
                            referer = "$mainUrl/",
                            quality = Qualities.P1080.value,
                            isM3u8 = true
                        )
                    )
                } else {
                    val playList = app.get(link, referer = "$mainUrl/")
                        .parsedSafe<ResponseM3u>()?.main?.segments?.map { segment ->
                        PlayListItem(
                            segment.link,
                            (segment.du.toFloat() * 1_000_000).toLong()
                        )
                    }

                    callback.invoke(
                        ExtractorLinkPlayList(
                            source,
                            source,
                            playList ?: return@safeApiCall,
                            referer = "$mainUrl/",
                            quality = Qualities.P1080.value,
                            headers = mapOf(
//                                "If-None-Match" to "*",
                                "Origin" to mainUrl,
                            )
                        )
                    )
                }
            }
        }
        return true
    }

    data class Segment(
        @JsonProperty("du") val du: String,
        @JsonProperty("link") val link: String,
    )

    data class DataM3u(
        @JsonProperty("segments") val segments: List<Segment>?,
    )

    data class ResponseM3u(
        @JsonProperty("2048p") val main: DataM3u?,
    )

}