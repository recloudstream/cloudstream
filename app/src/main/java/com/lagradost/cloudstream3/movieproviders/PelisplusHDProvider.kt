package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class PelisplusHDProvider:MainAPI() {
    override var mainUrl = "https://pelisplushd.net"
    override var name = "PelisplusHD"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val document = app.get(mainUrl).document
        val map = mapOf(
            "Películas" to "#default-tab-1",
            "Series" to "#default-tab-2",
            "Anime" to "#default-tab-3",
            "Doramas" to "#default-tab-4",
        )
        map.forEach {
            items.add(HomePageList(
                it.key,
                document.select(it.value).select("a.Posters-link").map { element ->
                    element.toSearchResult()
                }
            ))
        }
        return HomePageResponse(items)
    }
    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select(".listing-content p").text()
        val href = this.select("a").attr("href")
        val posterUrl = this.select(".Posters-img").attr("src")
        val isMovie = href.contains("/pelicula/")
        return if (isMovie) {
            MovieSearchResponse(
                title,
                href,
                name,
                TvType.Movie,
                posterUrl,
                null
            )
        } else {
            TvSeriesSearchResponse(
                title,
                href,
                name,
                TvType.Movie,
                posterUrl,
                null,
                null
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "https://pelisplushd.net/search?s=${query}"
        val document = app.get(url).document

        return document.select("a.Posters-link").map {
            val title = it.selectFirst(".listing-content p")!!.text()
            val href = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst(".Posters-img")!!.attr("src")
            val isMovie = href.contains("/pelicula/")

            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    null
                )
            } else {
                TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.TvSeries,
                    image,
                    null,
                    null
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document

        val title = soup.selectFirst(".m-b-5")!!.text()
        val description = soup.selectFirst("div.text-large")?.text()?.trim()
        val poster: String? = soup.selectFirst(".img-fluid")!!.attr("src")
        val episodes = soup.select("div.tab-pane .btn").map { li ->
            val href = li.selectFirst("a")!!.attr("href")
            val name = li.selectFirst(".btn-primary.btn-block")!!.text()
            val seasonid = href.replace("/capitulo/","-")
                .replace(Regex("$mainUrl/.*/.*/temporada/"),"").let { str ->
                    str.split("-").mapNotNull { subStr -> subStr.toIntOrNull() }
                }
            val isValid = seasonid.size == 2
            val episode = if (isValid) seasonid.getOrNull(1) else null
            val season = if (isValid) seasonid.getOrNull(0) else null
            Episode(
                href,
                name,
                season,
                episode,
            )
        }

        val year = soup.selectFirst(".p-r-15 .text-semibold")!!.text().toIntOrNull()
        val tvType = if (url.contains("/pelicula/")) TvType.Movie else TvType.TvSeries
        val tags = soup.select(".p-h-15.text-center a span.font-size-18.text-info.text-semibold")
            .map { it?.text()?.trim().toString().replace(", ","") }

        return when (tvType) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    poster,
                    year,
                    description,
                    null,
                    null,
                    tags,
                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    url,
                    poster,
                    year,
                    description,
                    null,
                    tags,
                )
            }
            else -> null
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("div.player > script").map { script ->
            fetchUrls(script.data().replace("https://pelisplushd.net/fembed.php?url=","https://www.fembed.com/v/")).apmap {
                loadExtractor(it, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
