package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class PeliSmartProvider: MainAPI() {
    override var mainUrl = "https://pelismart.com"
    override var name = "PeliSmart"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    override val vpnStatus = VPNStatus.MightBeNeeded //Due to evoload sometimes not loading

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/peliculas/", "Peliculas"),
            Pair("$mainUrl/series/", "Series"),
            Pair("$mainUrl/documentales/", "Documentales"),
        )

        // has no inf loading
        urls.apmap { (url, name) ->
            try {
                val soup = app.get(url).document
                val home = soup.select(".description-off").map {
                    val title = it.selectFirst("h3.entry-title a")!!.text()
                    val link = it.selectFirst("a")!!.attr("href")
                    TvSeriesSearchResponse(
                        title,
                        link,
                        this.name,
                        if (link.contains("pelicula")) TvType.Movie else TvType.TvSeries,
                        it.selectFirst("div img")!!.attr("src"),
                        null,
                        null,
                    )
                }

                items.add(HomePageList(name, home))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl?s=${query}&post_type=post"
        val document = app.get(url).document

        return document.select(".description-off").map {
            val title = it.selectFirst("h3.entry-title a")!!.text()
            val href = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst("div img")!!.attr("src")
            val isMovie = href.contains("pelicula")

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
        val title = soup.selectFirst(".wpb_wrapper h1")!!.text()
        val description = soup.selectFirst("div.wpb_wrapper p")?.text()?.trim()
        val poster: String? = soup.selectFirst(".vc_single_image-img")!!.attr("src")
        val episodes = soup.select("div.vc_tta-panel-body div a").map { li ->
            val href = li.selectFirst("a")!!.attr("href")
            val preregex = Regex("(\\d+)\\. ")
            val name = li.selectFirst("a")!!.text().replace(preregex,"")
            val regextest = Regex("(temporada-(\\d+)-capitulo-(\\d+)|temporada-(\\d+)-episodio-(\\d+))")
            val test = regextest.find(href)?.destructured?.component1()?.replace(Regex("(temporada-|-)"),"")
            val seasonid = test.let { str ->
                str?.split("episodio","capitulo")?.mapNotNull { subStr -> subStr.toIntOrNull() }
            }
            val isValid = seasonid?.size == 2
            val episode = if (isValid) seasonid?.getOrNull(1) else null
            val season = if (isValid) seasonid?.getOrNull(0) else null
                Episode(
                    href,
                    name,
                    season,
                    episode,
                )
        }
        return when (val tvType = if (episodes.isEmpty()) TvType.Movie else TvType.TvSeries) {
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
        val soup = app.get(data).text
         fetchUrls(soup).apmap {
             val urlc = it.replace("https://pelismart.com/p/1.php?v=","https://evoload.io/e/")
             .replace("https://pelismart.com/p/2.php?v=","https://streamtape.com/e/")
             .replace("https://pelismart.com/p/4.php?v=","https://dood.to/e/")
             .replace("https://pelismarthd.com/p/1.php?v=","https://evoload.io/e/")
             .replace("https://pelismarthd.com/p/2.php?v=","https://streamtape.com/e/")
             .replace("https://pelismarthd.com/p/4.php?v=","https://dood.to/e/")
             loadExtractor(urlc, data, subtitleCallback, callback)
         }
        return true
    }
}