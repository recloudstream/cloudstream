package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
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
    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/", "Peliculas"),
            Pair("$mainUrl/genero-de-la-pelicula/peliculas-en-calidad-4k/", "4K UHD"),
        )

        items.add(HomePageList("Series",app.get("$mainUrl/ver-serie/").document.select(".item.tvshows").map{
            val title = it.selectFirst("div.in_title").text()
            TvSeriesSearchResponse(
                title,
                it.selectFirst("a").attr("href"),
                this.name,
                TvType.TvSeries,
                it.selectFirst(".poster.custom img").attr("data-src"),
                null,
                null,
            )
        }))

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
        val url = "$mainUrl/buscar/?s=${query}"
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
        val description = soup.selectFirst(".single_left > table:nth-child(3) > tbody:nth-child(1) > tr:nth-child(1) > td:nth-child(2) > p")?.text()?.trim()
        val poster: String? = soup.selectFirst(".alignnone").attr("data-src")
        val episodes = soup.select("div.se-c div.se-a ul.episodios li").map { li ->
            val href = li.selectFirst("a").attr("href")
            val epThumb = li.selectFirst("div.imagen img").attr("data-src")
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
        app.get(data).document.select(".ajax_mode .dooplay_player_option").forEach {
            val movieID = it.attr("data-post")
            val serverID = it.attr("data-nume")
            val url = "$mainUrl/wp-json/dooplayer/v2/$movieID/movie/$serverID"
            val urlserver = app.get(url).text
            val serverRegex = Regex("(https:.*?\\\")")
            val videos = serverRegex.findAll(urlserver).map {
                it.value.replace("\\/", "/").replace("\"","")
            }.toList()
            val serversRegex = Regex("(https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=]*))")
            val links = serversRegex.findAll(videos.toString()).map { it.value }.toList()
            for (link in links) {
                for (extractor in extractorApis) {
                    if (link.startsWith(extractor.mainUrl)) {
                        extractor.getSafeUrl(link, data)?.forEach {
                            callback(it)
                        }
                    }
                }
            }
        }
        return true
    }
}