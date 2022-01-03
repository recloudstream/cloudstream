package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FEmbed
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.lang.Exception

class PinoyMoviePediaProvider : MainAPI() {
    override val name = "Pinoy Moviepedia"
    override val mainUrl = "https://pinoymoviepedia.ru"
    override val lang = "tl"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasDownloadSupport = true
    override val hasMainPage = true
    override val hasQuickSearch = false

    override fun getMainPage(): HomePageResponse {
        val all = ArrayList<HomePageList>()
        val html = app.get(mainUrl).text
        val document = Jsoup.parse(html)
        val mainbody = document.getElementsByTag("body")
        // All rows will be hardcoded bc of the nature of the site
        val rows: List<Pair<String, String>> = listOf(
            Pair("Latest Movies", "featured-titles"),
            Pair("Movies", "dt-movies"),
            Pair("Digitally Restored", "genre_digitally-restored"),
            Pair("Action", "genre_action"),
            Pair("Romance", "genre_romance"),
            Pair("Comedy", "genre_comedy"),
            Pair("Family", "genre_family")
            //Pair("Adult +18", "genre_pinay-sexy-movies")
        )
        for (item in rows) {
            val title = item.first
            val inner = mainbody?.select("div#${item.second} > article")
            if (inner != null) {
                val elements: List<SearchResponse> = inner.map {
                    // Get inner div from article
                    val urlTitle = it?.select("div.data")
                    // Fetch details
                    val link = urlTitle?.select("a")?.attr("href") ?: ""
                    val name = urlTitle?.text() ?: ""
                    val image = it?.select("div.poster > img")?.attr("src")
                    // Get Year from Title
                    val rex = Regex("\\((\\d+)")
                    val yearRes = rex.find(name)?.value ?: ""
                    val year = yearRes.replace("(", "").toIntOrNull()

                    val tvType = TvType.Movie
                    MovieSearchResponse(
                        name,
                        link,
                        this.name,
                        tvType,
                        image,
                        year,
                        null,
                    )
                }.filter { a -> a.url.isNotEmpty() }
                        .filter { b -> b.name.isNotEmpty() }
                        .distinctBy { c -> c.url }
                // Add
                all.add(
                    HomePageList(
                        title, elements
                    )
                )
            }
        }
        return HomePageResponse(all)
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query}"
        val html = app.get(url).text
        val document = Jsoup.parse(html).select("div.search-page")?.firstOrNull()
            ?.select("div.result-item")
        if (document != null) {
            return document.map {
                val inner = it.select("article")
                val details = inner.select("div.details")
                val href = details?.select("div.title > a")?.attr("href") ?: ""

                val title = details?.select("div.title")?.text() ?: ""
                val link: String = when (href != "") {
                    true -> fixUrl(href)
                    false -> ""
                }
                val year = details?.select("div.meta > span.year")?.text()?.toIntOrNull()
                val image = inner.select("div.image > div > a > img")?.attr("src")

                MovieSearchResponse(
                    title,
                    link,
                    this.name,
                    TvType.Movie,
                    image,
                    year
                )
            }.filter { a -> a.url.isNotEmpty() }
                    .filter { b -> b.name.isNotEmpty() }
                    .distinctBy { c -> c.url }
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
        val poster = inner?.select("div.poster > img")?.attr("src")
        val title = inner?.select("div.data > h1")?.firstOrNull()?.text() ?: ""
        val descript = body?.select("div#info > div.wp-content")?.text()
        val rex = Regex("\\((\\d+)")
        val yearRes = rex.find(title)?.value ?: ""
        //Log.i(this.name, "Result => (yearRes) ${yearRes}")
        val year = yearRes.replace("(", "").toIntOrNull()

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
                                "Episode $epNum",
                                null,
                                epNum,
                                href,
                                poster,
                                null
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

        // parse movie servers
        if (data.contains("playcontainer")) {
            Jsoup.parse(data).select("div")?.map { item ->
                val url = item.select("iframe")?.attr("src")
                if (!url.isNullOrEmpty()) {
                    //Log.i(this.name, "Result => (url) ${url}")
                    loadExtractor(url, url, callback)
                }
            }
        } else {
            // parse single link
            try {
                if (data.contains("fembed.com")) {
                    val extractor = FEmbed()
                    extractor.domainUrl = "diasfem.com"
                    val src = extractor.getUrl(data)
                    if (src.isNotEmpty()) {
                        sources.addAll(src)
                    }
                }
            } catch (e: Exception) {
                Log.i(this.name, "Result => (exception) $e")
            }
        }
        // Invoke sources
        if (sources.isNotEmpty()) {
            for (source in sources) {
                callback.invoke(source)
                //Log.i(this.name, "Result => (source) ${source.url}")
                return true
            }
        }
        return false
    }
}