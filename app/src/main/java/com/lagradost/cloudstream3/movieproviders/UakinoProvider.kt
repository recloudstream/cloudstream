package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.*

class UakinoProvider : MainAPI() {
    override var mainUrl = "https://uakino.club"
    override var name = "Uakino"
    override val hasMainPage = true
    override var lang = "uk"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("div.main-section-inner").forEach { block ->
            val header = block.selectFirst("p.sidebar-title")?.text()?.trim().toString()
            val items = block.select("div.owl-item, div.movie-item").map {
                it.toSearchResponse()
            }
            if (items.isNotEmpty()) homePageList.add(HomePageList(header, items))
        }

        return HomePageResponse(homePageList)
    }

    private fun Element.toSearchResponse(): SearchResponse {
        val title = this.selectFirst("a.movie-title")?.text()?.trim().toString()
        val href = this.selectFirst("a.movie-title")?.attr("href").toString()
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            url = mainUrl,
            data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "story" to query.replace(" ", "+")
            )
        ).document
        return document.select("div.movie-item.short-item").map {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1 span.solototle")?.text()?.trim().toString()
        val poster = fixUrl(document.selectFirst("div.film-poster img")?.attr("src").toString())
        val tags = document.select("div.film-info > div:nth-child(4) a").map { it.text() }
        val year = document.select("div.film-info > div:nth-child(2) a").text().toIntOrNull()
        val tvType = if (url.contains(Regex("(/anime-series)|(/seriesss)|(/cartoonseries)"))) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div[itemprop=description]")?.text()?.trim()
        val trailer = document.selectFirst("iframe#pre")?.attr("data-src")
        val rating = document.selectFirst("div.film-info > div:nth-child(8) div.fi-desc")?.text()
            ?.substringBefore("/").toRatingInt()
        val actors = document.select("div.film-info > div:nth-child(6) a").map { it.text() }

        val recommendations = document.select("div#full-slides div.owl-item").map {
            it.toSearchResponse()
        }

        return if (tvType == TvType.TvSeries) {
            val id = url.split("/").last().split("-").first()
            val episodes = app.get("$mainUrl/engine/ajax/playlists.php?news_id=$id&xfield=playlist&time=${Date().time}")
                .parsedSafe<Responses>()?.response.let {
                    Jsoup.parse(it.toString()).select("ul > li").mapNotNull { eps ->
                        val href = fixUrl(eps.attr("data-file"))
                        val name = eps.text().trim()
                        if (href.isNotEmpty()) {
                            Episode(
                                href,
                                name,
                            )
                        } else {
                            null
                        }
                    }
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val links = ArrayList<String>()

        if (data.startsWith("https://ashdi.vip")) {
            links.add(data)
        } else {
            val iframeUrl = app.get(data).document.selectFirst("iframe#pre")?.attr("src")
            if (iframeUrl.isNullOrEmpty()) {
                val id = data.split("/").last().split("-").first()
                app.get("$mainUrl/engine/ajax/playlists.php?news_id=$id&xfield=playlist&time=${Date().time}")
                    .parsedSafe<Responses>()?.response.let {
                        Jsoup.parse(it.toString()).select("ul > li").mapNotNull { mirror ->
                            links.add(fixUrl(mirror.attr("data-file")))
                        }
                    }
            } else {
                links.add(iframeUrl)
            }
        }

        links.apmap { link ->
            safeApiCall {
                app.get(link, referer = "$mainUrl/").document.select("script").map { script ->
                    if (script.data().contains("var player = new Playerjs({")) {
                        val m3uLink =
                            script.data().substringAfterLast("file:\"").substringBefore("\",")
                        M3u8Helper.generateM3u8(
                            source = this.name,
                            streamUrl = m3uLink,
                            referer = "https://ashdi.vip/"
                        ).forEach(callback)
                    }
                }
            }
        }

        return true
    }

    data class Responses(
        @JsonProperty("success") val success: Boolean?,
        @JsonProperty("response") val response: String,
    )

}