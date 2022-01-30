package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.Evoload
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.utils.*
import java.util.*

class CinecalidadProvider:MainAPI() {
    override val mainUrl: String
        get() = "https://cinecalidad.lol"
    override val name: String
        get() = "Cinecalidad"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val vpnStatus = VPNStatus.MightBeNeeded //Due to evoload sometimes not loading

    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/ver-serie/", "Series"),
            Pair("$mainUrl/", "Peliculas"),
            Pair("$mainUrl/genero-de-la-pelicula/peliculas-en-calidad-4k/", "4K UHD"),
        )

        for (i in urls) {
            try {
                val soup = app.get(i.first).document
                val home = soup.select(".item.movies").map {
                    val title = it.selectFirst("div.in_title").text()
                    val link = it.selectFirst("a").attr("href")
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        if (link.contains("/ver-pelicula/")) TvType.Movie else TvType.TvSeries,
                        it.selectFirst(".poster.custom img").attr("data-src"),
                        null,
                        null,
                    )
                }

                items.add(HomePageList(i.second, home))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val document = app.get(url).document

        return document.select("article").map {
            val title = it.selectFirst("div.in_title").text()
            val href = it.selectFirst("a").attr("href")
            val image = it.selectFirst(".poster.custom img").attr("data-src")
            val isMovie = href.contains("/ver-pelicula/")

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

        val title = soup.selectFirst(".single_left h1").text()
        val description = soup.selectFirst("div.single_left table tbody tr td p")?.text()?.trim()
        val poster: String? = soup.selectFirst(".alignnone").attr("data-src")
        val episodes = soup.select("div.se-c div.se-a ul.episodios li").map { li ->
            val href = li.selectFirst("a").attr("href")
            val epThumb = li.selectFirst("div.imagen img").attr("data-src") ?: li.selectFirst("div.imagen img").attr("src")

            val name = li.selectFirst(".episodiotitle a").text()
            TvSeriesEpisode(
                name,
                null,
                null,
                href,
                epThumb
            )
        }
        return when (val tvType = if (url.contains("/ver-pelicula/")) TvType.Movie else TvType.TvSeries) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    poster,
                    null,
                    description,
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
                    null,
                    description,
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
        app.get(data).document.select(".dooplay_player_option").apmap {
            val url = it.attr("data-option")
            if (url.startsWith("https://evoload.io")) {
                val extractor = Evoload()
                extractor.getSafeUrl(url)?.forEach { link ->
                    callback.invoke(link)
                }
            } else {
                loadExtractor(url, mainUrl, callback)
            }
        }
        if ((app.get(data).text.contains("en castellano"))) app.get("$data?ref=es").document.select(".dooplay_player_option").apmap {
            val url = it.attr("data-option")
            if (url.startsWith("https://evoload.io")) {
                val extractor = Evoload()
                extractor.getSafeUrl(url)?.forEach { link ->
                    callback.invoke(link)
                }
            } else {
                loadExtractor(url, mainUrl, callback)
            }
        }
        return true
    }
}