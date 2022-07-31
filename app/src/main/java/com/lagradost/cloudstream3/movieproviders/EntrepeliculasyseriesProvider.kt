package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class EntrepeliculasyseriesProvider : MainAPI() {
    override var mainUrl = "https://entrepeliculasyseries.nu"
    override var name = "EntrePeliculasySeries"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val vpnStatus = VPNStatus.MightBeNeeded //Due to evoload sometimes not loading

    override val mainPage = mainPageOf(
        Pair("$mainUrl/series/page/", "Series"),
        Pair("$mainUrl/peliculas/page/", "Peliculas"),
        Pair("$mainUrl/anime/page/", "Animes"),
    )

    override suspend fun getMainPage(
        page: Int,
        request : MainPageRequest
    ): HomePageResponse {
        val url = request.data + page

        val soup = app.get(url).document
        val home = soup.select("ul.list-movie li").map {
            val title = it.selectFirst("a.link-title h2")!!.text()
            val link = it.selectFirst("a")!!.attr("href")
            TvSeriesSearchResponse(
                title,
                link,
                this.name,
                if (link.contains("/pelicula/")) TvType.Movie else TvType.TvSeries,
                it.selectFirst("a.poster img")!!.attr("src"),
                null,
                null,
            )
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val document = app.get(url).document

        return document.select("li.xxx.TPostMv").map {
            val title = it.selectFirst("h2.Title")!!.text()
            val href = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst("img.lazy")!!.attr("data-src")
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
        }.toList()
    }


    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url, timeout = 120).document

        val title = soup.selectFirst("h1.title-post")!!.text()
        val description = soup.selectFirst("p.text-content:nth-child(3)")?.text()?.trim()
        val poster: String? = soup.selectFirst("article.TPost img.lazy")!!.attr("data-src")
        val episodes = soup.select(".TPostMv article").map { li ->
            val href = (li.select("a") ?: li.select(".C a") ?: li.select("article a")).attr("href")
            val epThumb = li.selectFirst("div.Image img")!!.attr("data-src")
            val seasonid = li.selectFirst("span.Year")!!.text().let { str ->
                str.split("x").mapNotNull { subStr -> subStr.toIntOrNull() }
            }
            val isValid = seasonid.size == 2
            val episode = if (isValid) seasonid.getOrNull(1) else null
            val season = if (isValid) seasonid.getOrNull(0) else null
            Episode(
                href,
                null,
                season,
                episode,
                fixUrl(epThumb)
            )
        }
        return when (val tvType =
            if (url.contains("/pelicula/")) TvType.Movie else TvType.TvSeries) {
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
        app.get(data).document.select(".video ul.dropdown-menu li").apmap {
            val servers = it.attr("data-link")
            val doc = app.get(servers).document
            doc.select("input").apmap {
                val postkey = it.attr("value")
                app.post(
                    "https://entrepeliculasyseries.nu/r.php",
                    headers = mapOf(
                        "Host" to "entrepeliculasyseries.nu",
                        "User-Agent" to USER_AGENT,
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Content-Type" to "application/x-www-form-urlencoded",
                        "Origin" to "https://entrepeliculasyseries.nu",
                        "DNT" to "1",
                        "Connection" to "keep-alive",
                        "Referer" to servers,
                        "Upgrade-Insecure-Requests" to "1",
                        "Sec-Fetch-Dest" to "document",
                        "Sec-Fetch-Mode" to "navigate",
                        "Sec-Fetch-Site" to "same-origin",
                        "Sec-Fetch-User" to "?1",
                    ),
                    //params = mapOf(Pair("h", postkey)),
                    data = mapOf(Pair("h", postkey)),
                    allowRedirects = false
                ).okhttpResponse.headers.values("location").apmap {
                    loadExtractor(it, data, subtitleCallback, callback)
                }
            }
        }
        return true
    }
}