package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.util.*


class AnimekisaProvider : MainAPI() {
    override var mainUrl = "https://animekisa.in"
    override var name = "Animekisa"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    data class Response(
        @JsonProperty("html") val html: String
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/ajax/list/views?type=all", "All animes"),
            Pair("$mainUrl/ajax/list/views?type=day", "Trending now"),
            Pair("$mainUrl/ajax/list/views?type=week", "Trending by week"),
            Pair("$mainUrl/ajax/list/views?type=month", "Trending by month"),
        )

        val items = urls.mapNotNull  {
            suspendSafeApiCall {
                val home = Jsoup.parse(
                    parseJson<Response>(
                        app.get(
                            it.first
                        ).text
                    ).html
                ).select("div.flw-item").mapNotNull secondMap@ {
                    val title = it.selectFirst("h3.title a")?.text() ?: return@secondMap null
                    val link = it.selectFirst("a")?.attr("href")  ?: return@secondMap null
                    val poster = it.selectFirst("img.lazyload")?.attr("data-src")
                    AnimeSearchResponse(
                        title,
                        link,
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        if (title.contains("(DUB)") || title.contains("(Dub)")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),
                    )
                }
                HomePageList(name, home)
            }
        }

        if (items.isEmpty()) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/search/?keyword=$query").document.select("div.flw-item")
            .mapNotNull {
                val title = it.selectFirst("h3 a")?.text() ?: ""
                val url = it.selectFirst("a.film-poster-ahref")?.attr("href")
                    ?.replace("watch/", "anime/")?.replace(
                        Regex("(-episode-(\\d+)/\$|-episode-(\\d+)\$|-episode-full|-episode-.*-.(/|))"),
                        ""
                    ) ?: return@mapNotNull null
                val poster = it.selectFirst(".film-poster img")?.attr("data-src")
                AnimeSearchResponse(
                    title,
                    url,
                    this.name,
                    TvType.Anime,
                    poster,
                    null,
                    if (title.contains("(DUB)") || title.contains("(Dub)")) EnumSet.of(
                        DubStatus.Dubbed
                    ) else EnumSet.of(DubStatus.Subbed),
                )
            }.toList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst(".mb-2 img")?.attr("src")
            ?: doc.selectFirst("head meta[property=og:image]")?.attr("content")
        val title = doc.selectFirst("h1.heading-name a")!!.text()
        val description = doc.selectFirst("div.description p")?.text()?.trim()
        val genres = doc.select("div.row-line a").map { it.text() }
        val test = if (doc.selectFirst("div.dp-i-c-right").toString()
                .contains("Airing")
        ) ShowStatus.Ongoing else ShowStatus.Completed
        val episodes = doc.select("div.tab-content ul li.nav-item").mapNotNull {
            val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            Episode(link)
        }
        val type = if (doc.selectFirst(".dp-i-stats").toString()
                .contains("Movies")
        ) TvType.AnimeMovie else TvType.Anime
        return newAnimeLoadResponse(title, url, type) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = test
            plot = description
            tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("#servers-list ul.nav li a").apmap {
            val server = it.attr("data-embed")
            loadExtractor(server, data, subtitleCallback, callback)
        }
        return true
    }
}