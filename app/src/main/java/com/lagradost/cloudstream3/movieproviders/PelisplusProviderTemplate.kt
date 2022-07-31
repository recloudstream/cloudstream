package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup


/** Needs to inherit from MainAPI() to
 * make the app know what functions to call
 */

open class PelisplusProviderTemplate : MainAPI() {
    override var lang = "es"
    open val homePageUrlList = listOf<String>()

//    // mainUrl is good to have as a holder for the url to make future changes easier.
//    override val mainUrl: String
//        get() = "https://vidembed.cc"
//
//    // name is for how the provider will be named which is visible in the UI, no real rules for this.
//    override val name: String
//        get() = "VidEmbed"

    // hasQuickSearch defines if quickSearch() should be called, this is only when typing the searchbar
    // gives results on the site instead of bringing you to another page.
    // if hasQuickSearch is true and quickSearch() hasn't been overridden you will get errors.
    // VidEmbed actually has quick search on their site, but the function wasn't implemented.
    override val hasQuickSearch = false

    // If getMainPage() is functional, used to display the homepage in app, an optional, but highly encouraged endevour.
    override val hasMainPage = true

    // Searching returns a SearchResponse, which can be one of the following: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    // Each of the classes requires some different data, but always has some critical things like name, poster and url.

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        // Simply looking at devtools network is enough to spot a request like:
        // https://vidembed.cc/search.html?keyword=neverland where neverland is the query, can be written as below.
        val link = "$mainUrl/search.html?keyword=$query"
        val html = app.get(link).text
        val soup = Jsoup.parse(html)

        return ArrayList(soup.select(".listing.items > .video-block").map { li ->
            // Selects the href in <a href="...">
            val href = fixUrl(li.selectFirst("a")!!.attr("href"))
            val poster = fixUrl(li.selectFirst("img")!!.attr("src"))

            // .text() selects all the text in the element, be careful about doing this while too high up in the html hierarchy
            val title = cleanName(li.selectFirst(".name")!!.text())
            // Use get(0) and toIntOrNull() to prevent any possible crashes, [0] or toInt() will error the search on unexpected values.
            val year = li.selectFirst(".date")?.text()?.split("-")?.get(0)?.toIntOrNull()

            TvSeriesSearchResponse(
                // .trim() removes unwanted spaces in the start and end.
                if (!title.contains("Episode")) title else title.split("Episode")[0].trim(),
                href,
                this.name,
                TvType.TvSeries,
                poster, year,
                // You can't get the episodes from the search bar.
                null
            )
        })
    }


    // Load, like the name suggests loads the info page, where all the episodes and data usually is.
    // Like search you should return either of: AnimeLoadResponse, MovieLoadResponse, TorrentLoadResponse, TvSeriesLoadResponse.
    override suspend fun load(url: String): LoadResponse? {
        // Gets the url returned from searching.
        val html = app.get(url).text
        val soup = Jsoup.parse(html)

        val title = cleanName(soup.selectFirst("h1,h2,h3")!!.text())
        val description = soup.selectFirst(".post-entry")?.text()?.trim()
        val poster = soup.selectFirst("head meta[property=og:image]")!!.attr("content")

        var year : Int? = null
        val episodes = soup.select(".listing.items.lists > .video-block").map { li ->
            val href = fixUrl(li.selectFirst("a")!!.attr("href"))
            val regexseason = Regex("(-[Tt]emporada-(\\d+)-[Cc]apitulo-(\\d+))")
            val aaa = regexseason.find(href)?.destructured?.component1()?.replace(Regex("(-[Tt]emporada-|[Cc]apitulo-)"),"")
            val seasonid = aaa.let { str ->
                str?.split("-")?.mapNotNull { subStr -> subStr.toIntOrNull() }
            }
            val isValid = seasonid?.size == 2
            val episode = if (isValid) seasonid?.getOrNull(1) else null
            val season = if (isValid) seasonid?.getOrNull(0) else null
            val epThumb = fixUrl(li.selectFirst("img")!!.attr("src"))
            val epDate = li.selectFirst(".meta > .date")!!.text()

            if(year == null) {
                year = epDate?.split("-")?.get(0)?.toIntOrNull()
            }

            newEpisode(li.selectFirst("a")!!.attr("href")) {
                this.season = season
                this.episode = episode
                this.posterUrl = epThumb
                addDate(epDate)
            }
        }.reversed()

        // Make sure to get the type right to display the correct UI.
        val tvType = if (episodes.size == 1 && episodes[0].name == title) TvType.Movie else TvType.TvSeries

        return when (tvType) {
            TvType.TvSeries -> {
                TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes,
                    fixUrl(poster),
                    year,
                    description,
                    null,
                    null,
                    null
                )
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    episodes[0].data,
                    fixUrl(poster),
                    year,
                    description,
                    null,
                    null
                )
            }
            else -> null
        }
    }

    // This loads the homepage, which is basically a collection of search results with labels.
    // Optional function, but make sure to enable hasMainPage if you program this.
    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = homePageUrlList
        val homePageList = ArrayList<HomePageList>()
        // .pmap {} is used to fetch the different pages in parallel
        urls.apmap { url ->
            val response = app.get(url, timeout = 20).text
            val document = Jsoup.parse(response)
            document.select("div.main-inner")?.forEach { inner ->
                // Always trim your text unless you want the risk of spaces at the start or end.
                val title = cleanName(inner.select(".widget-title").text())
                val elements = inner.select(".video-block").map {
                    val link = fixUrl(it.select("a").attr("href"))
                    val image = it.select(".picture > img").attr("src").replace("//img", "https://img")
                    val name = cleanName(it.select("div.name").text())
                    val isSeries = (name.contains("Temporada") || name.contains("Capítulo"))

                    if (isSeries) {
                        TvSeriesSearchResponse(
                            name,
                            link,
                            this.name,
                            TvType.TvSeries,
                            image,
                            null,
                            null,
                        )
                    } else {
                        MovieSearchResponse(
                            name,
                            link,
                            this.name,
                            TvType.Movie,
                            image,
                            null,
                            null,
                        )
                    }
                }

                homePageList.add(
                    HomePageList(
                        title, elements
                    )
                )

            }

        }
        return HomePageResponse(homePageList)
    }


    private fun cleanName(input: String): String = input.replace(Regex("([Tt]emporada (\\d+)|[Cc]apítulo (\\d+))|[Tt]emporada|[Cc]apítulo"),"").trim()


    private suspend fun getPelisStream(
        link: String,
        callback: (ExtractorLink) -> Unit) : Boolean {
        val soup = app.get(link).text
        val m3u8regex = Regex("((https:|http:)\\/\\/.*m3u8.*expiry=(\\d+))")
        val m3u8 = m3u8regex.find(soup)?.value ?: return false

        M3u8Helper.generateM3u8(
            name,
            m3u8,
            mainUrl,
            headers = mapOf("Referer" to mainUrl)
        ).forEach (callback)

        return true
    }

    // loadLinks gets the raw .mp4 or .m3u8 urls from the data parameter in the episodes class generated in load()
    // See Episode(...) in this provider.
    // The data are usually links, but can be any other string to help aid loading the links.
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        // These callbacks are functions you should call when you get a link to a subtitle file or media file.
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val info = doc.select("div.tabs-video li").text()
        if (info.contains("Latino")) {
            doc.select(".server-item-1 li").apmap {
                val serverid = fixUrl(it.attr("data-video")).replace("streaming.php","play")
                loadExtractor(serverid, data, subtitleCallback, callback)
                if (serverid.contains("pelisplus.icu")) {
                    getPelisStream(serverid, callback)
                }
            }
        }

        if (info.contains("Subtitulado")) {
            doc.select(".server-item-0 li").apmap {
                val serverid = fixUrl(it.attr("data-video")).replace("streaming.php","play")
                loadExtractor(serverid, data, subtitleCallback, callback)
                if (serverid.contains("pelisplus.icu")) {
                    getPelisStream(serverid, callback)
                }
            }
        }

        if (info.contains("Castellano")) {
            doc.select(".server-item-2 li").apmap {
                val serverid = fixUrl(it.attr("data-video")).replace("streaming.php","play")
                loadExtractor(serverid, data, subtitleCallback, callback)
                if (serverid.contains("pelisplus.icu")) {
                    getPelisStream(serverid, callback)
                }
            }
        }
        return true
    }
}
