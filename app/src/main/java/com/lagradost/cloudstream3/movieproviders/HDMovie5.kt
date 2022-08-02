package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class HDMovie5 : MainAPI() {
    override var mainUrl = "https://hdmovie2.click"
    override var name = "HDMovie"
    override var lang = "hi"

    override val hasQuickSearch = true
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val mainPage = mainPageOf(
        "$mainUrl/genre/tv-movie/page/" to "TV Movie",
        "$mainUrl/genre/tv-show/page/" to "TV- Show",
        "$mainUrl/genre/hindi-dubbed/page/" to "Hindi Dubbed",
        "$mainUrl/genre/netflix/page/" to "NETFLIX",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val home = app.get(request.data + page).document.select("article.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3 > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = this.selectFirst("img")?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            addPoster(posterUrl)
        }
    }

    private data class QuickSearchResponse(
        val title: String,
        val url: String,
        val img: String,
        val extra: Extra
    ) {
        data class Extra(
            val date: String
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return app.get("$mainUrl/wp-json/dooplay/search/?keyword=$query&nonce=ddbde04d9c")
            .parsed<Map<String, QuickSearchResponse>>().map {
                val res = it.value
                MovieSearchResponse(
                    res.title,
                    res.url,
                    this.name,
                    TvType.Movie,
                    res.img,
                    res.extra.date.toIntOrNull()
                )
            }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?s=$query").document.select(".search-page>div.result-item").map {
            val image = it.select(".image")
            MovieSearchResponse(
                image.select("img").attr("alt"),
                image.select("a").attr("href"),
                this.name,
                TvType.Movie,
                image.select("img").attr("src"),
                it.select(".year").text().toIntOrNull()
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val info = doc.select(".sheader")
        val links = doc.select("#playeroptionsul>li")
        val data = links.joinToString(",") { it.attr("data-post") }
        return MovieLoadResponse(
            info.select(".data>h1").text(),
            url,
            this.name,
            TvType.Movie,
            data,
            info.select(".poster>img").attr("src"),
            info.select(".date").text().substringAfter(", ").toIntOrNull(),
            doc.select(".wp-content>p").let { it.getOrNull(it.size - 1)?.text() },
            (doc.select("#repimdb>strong").text().toFloatOrNull()?.times(1000))?.toInt(),
            info.select(".sgeneros>a").map { it.text() },
            info.select(".runtime").text().substringBefore(" Min.").toIntOrNull(),
            mutableListOf(),
            doc.select("#single_relacionados>article>a").map {
                val img = it.select("img")
                MovieSearchResponse(
                    img.attr("alt"),
                    it.attr("href"),
                    this.name,
                    TvType.Movie,
                    img.attr("src")
                )
            },
            doc.select("#cast>.persons>.person").mapNotNull {
                if (it.attr("itemprop") != "director") {
                    ActorData(
                        Actor(
                            it.select("meta").attr("content"),
                            it.select("img").attr("src")
                        )
                    )
                } else null
            },
        )
    }

    private data class PlayerAjaxResponse(
        @JsonProperty("embed_url")
        val embedURL: String? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return data.split(",").apmapIndexed { index, it ->
            val p = app.post(
                "$mainUrl/wp-admin/admin-ajax.php",
                data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to it,
                    "nume" to "${index + 1}",
                    "type" to "movie"
                )
            )
            val html = p.parsedSafe<PlayerAjaxResponse>()?.embedURL ?: return@apmapIndexed false
            val doc = Jsoup.parse(html)
            val link = doc.select("iframe").attr("src")
            loadExtractor(httpsify(link), "$mainUrl/", subtitleCallback, callback)
        }.contains(true)
    }
}
