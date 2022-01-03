package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.network.DdosGuardKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.select.Elements

class PinoyMoviesEsProvider : MainAPI() {
    override val name = "Pinoy Movies"
    override val mainUrl = "https://pinoymovies.es/"
    override val lang = "tl"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasDownloadSupport = false
    override val hasMainPage = true
    override val hasQuickSearch = false

    private fun getRowElements(mainbody: Elements, rows: List<Pair<String, String>>, sep: String): MutableList<HomePageList> {
        val all = mutableListOf<HomePageList>()
        for (item in rows) {
            val title = item.first
            val inner = mainbody.select("div${sep}${item.second} > article")
            if (inner != null) {
                val elements: List<SearchResponse> = inner.map {
                    // Get inner div from article
                    var urlTitle = it?.select("div.data.dfeatur")
                    if (urlTitle.isNullOrEmpty()) {
                        urlTitle = it?.select("div.data")
                    }
                    // Fetch details
                    val link = urlTitle?.select("a")?.attr("href") ?: ""
                    val name = urlTitle?.text() ?: ""
                    val year = urlTitle?.select("span")?.text()?.toIntOrNull()
                    //Log.i(this.name, "Result => (link) ${link}")
                    val image = it?.select("div.poster > img")?.attr("data-src")

                    MovieSearchResponse(
                        name,
                        link,
                        this.name,
                        TvType.Movie,
                        image,
                        year,
                        null,
                    )
                }.filter { a -> a.url.isNotEmpty() }
                        .filter { b -> b.name.isNotEmpty() }
                        .distinctBy { c -> c.url }
                if (!elements.isNullOrEmpty()) {
                    all.add(HomePageList(
                            title, elements
                    ))
                }
            }
        }
        return all
    }
    override fun getMainPage(): HomePageResponse {
        val all = ArrayList<HomePageList>()
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)
        val mainbody = document.getElementsByTag("body")
        if (mainbody != null) {
            // All rows will be hardcoded bc of the nature of the site
            val homepage1 = getRowElements(mainbody, listOf(
                Pair("Suggestion", "items.featured"),
                Pair("All Movies", "items.full")
            ), ".")
            if (homepage1.isNotEmpty()) {
                all.addAll(homepage1)
            }
            //2nd rows
            val homepage2 = getRowElements(mainbody, listOf(
                Pair("Action", "genre_action"),
                Pair("Comedy", "genre_comedy"),
                Pair("Romance", "genre_romance"),
                Pair("Horror", "genre_horror")
                //Pair("Rated-R", "genre_rated-r")
            ), "#")
            if (homepage2.isNotEmpty()) {
                all.addAll(homepage2)
            }
        }
        return HomePageResponse(all)
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val html = app.get(url, interceptor = DdosGuardKiller(true)).text
        //Log.i(this.name, "Result => (html) ${Jsoup.parse(html).getElementsByTag("body")}")
        val document = Jsoup.parse(html).select("div#archive-content > article")
        if (document != null) {
            return document.map {
                val urlTitle = it?.select("div.data")
                // Fetch details
                val link = urlTitle?.select("a")?.attr("href") ?: ""
                val title = urlTitle?.text() ?: "<No Title>"
                val year = urlTitle?.select("span.year")?.text()?.toIntOrNull()

                val image = it?.select("div.poster > img")?.attr("src")

                MovieSearchResponse(
                    title,
                    link,
                    this.name,
                    TvType.Movie,
                    image,
                    year
                )
            }
        }
        return listOf()
    }

    override fun load(url: String): LoadResponse {
        val response = app.get(url).text
        val doc = Jsoup.parse(response)
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.sheader")
        // Identify if movie or series
        val isTvSeries = doc?.select("title")?.text()?.lowercase()?.contains("full episode -") ?: false

        // Video details
        val data = inner?.select("div.sheader > div.data")
        val title = data?.select("h1")?.firstOrNull()?.text() ?: "<Untitled>"
        val year = data?.select("span.date")?.text()?.takeLast(4)?.toIntOrNull()

        val descript = body?.select("div#info > div.wp-content")?.text()
        val poster = body?.select("div.poster > img")?.attr("src")

        // Video links
        val linksContainer = body?.select("div#playcontainer")
        val streamlinks = linksContainer?.toString() ?: ""
        //Log.i(this.name, "Result => (streamlinks) ${streamlinks}")

        // Parse episodes if series
        if (isTvSeries) {
            val episodeList = ArrayList<TvSeriesEpisode>()
            val epList = body?.select("div#playeroptions > ul > li")
            //Log.i(this.name, "Result => (epList) ${epList}")
            val epLinks = linksContainer?.select("div > div > div.source-box")
            //Log.i(this.name, "Result => (epLinks) ${epLinks}")
            if (epList != null) {
                for (ep in epList) {
                    val epTitle = ep.select("span.title")?.text() ?: ""
                    if (epTitle.isNotEmpty()) {
                        val epNum = epTitle.lowercase().replace("episode", "").trim().toIntOrNull()
                        //Log.i(this.name, "Result => (epNum) ${epNum}")
                        val href = when (epNum != null && epLinks != null) {
                            true -> epLinks.select("div#source-player-${epNum}")
                                    ?.select("iframe")?.attr("src") ?: ""
                            false -> ""
                        }
                        //Log.i(this.name, "Result => (epLinks href) ${href}")
                        episodeList.add(
                            TvSeriesEpisode(
                                name = name,
                                season = null,
                                episode = epNum,
                                data = href,
                                posterUrl = poster,
                                date = year.toString()
                            )
                        )
                    }
                }
                return TvSeriesLoadResponse(
                    title,
                    url,
                    this.name,
                    TvType.TvSeries,
                    episodeList,
                    poster,
                    year,
                    descript,
                    null,
                    null,
                    null
                )
            }
        }
        return MovieLoadResponse(title, url, this.name, TvType.Movie, streamlinks, poster, year, descript, null, null)
    }

    override fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "about:blank") return false
        if (data.isEmpty()) return false
        val sources = mutableListOf<ExtractorLink>()
        try {
            if (data.contains("playcontainer")) {
                // parse movie servers
                //Log.i(this.name, "Result => (data) ${data}")
                Jsoup.parse(data).select("div")?.map { item ->
                    val url = item.select("iframe")?.attr("src")
                    if (!url.isNullOrEmpty()) {
                        //Log.i(this.name, "Result => (url) ${url}")
                        loadExtractor(url, url, callback)
                    }
                }
            } else {
                // parse single link
                if (data.contains("fembed.com")) {
                    val extractor = FEmbed()
                    extractor.domainUrl = "diasfem.com"
                    val src = extractor.getUrl(data)
                    if (src.isNotEmpty()) {
                        sources.addAll(src)
                    }
                }
            }
            // Invoke sources
            if (sources.isNotEmpty()) {
                for (source in sources) {
                    callback.invoke(source)
                    //Log.i(this.name, "Result => (source) ${source.url}")
                }
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.i(this.name, "Result => (e) ${e}")
        }
        return false
    }
}