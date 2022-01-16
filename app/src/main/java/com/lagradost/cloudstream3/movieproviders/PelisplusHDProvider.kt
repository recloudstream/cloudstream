package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import java.util.*

class PelisplusHDProvider:MainAPI() {
    override val mainUrl: String
        get() = "https://pelisplushd.net"
    override val name: String
        get() = "PelisplusHD"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )
    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/peliculas", "Peliculas"),
            Pair("$mainUrl/series", "Series"),
            Pair("$mainUrl/generos/dorama", "Doramas"),
            Pair("$mainUrl/animes", "Animes"),
        )
        for (i in urls) {
            try {
                val response = app.get(i.first)
                val soup = Jsoup.parse(response.text)
                val home = soup.select("a.Posters-link").map {
                    val title = it.selectFirst(".listing-content p").text()
                    val link = it.selectFirst("a").attr("href")
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        if (link.contains("/pelicula/")) TvType.Movie else TvType.TvSeries,
                        it.selectFirst(".Posters-img").attr("src"),
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
        val url = "https://pelisplushd.net/search?s=${query}"
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select("a.Posters-link").map {
            val title = it.selectFirst(".listing-content p").text()
            val href = it.selectFirst("a").attr("href")
            val image = it.selectFirst(".Posters-img").attr("src")
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
        val html = app.get(url).text
        val soup = Jsoup.parse(html)

        val title = soup.selectFirst(".m-b-5").text()
        val description = soup.selectFirst("div.text-large")?.text()?.trim()
        val poster: String? = soup.selectFirst(".img-fluid").attr("src")
        val episodes = soup.select("div.tab-pane .btn").map { li ->
            val href = li.selectFirst("a").attr("href")
            TvSeriesEpisode(
                null,
                null,
                null,
                href,
            )
        }

        val year = soup.selectFirst(".p-r-15 .text-semibold").text().toIntOrNull()
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
                    ShowStatus.Ongoing,
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
        val html = app.get(data).text
        val soup = Jsoup.parse(html)
        val selector = soup.selectFirst("div.player > script").toString()
        val linkRegex = Regex("""(https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&\/\/=]*))""")
        val links = linkRegex.findAll(selector).map {
            it.value.replace("https://pelisplushd.net/fembed.php?url=","https://www.fembed.com/v/")
        }.toList()
        for (link in links) {
            for (extractor in extractorApis) {
                if (link.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(link, data)?.forEach {
                        callback(it)
                    }
                }
            }
        }
        return true
    }
}