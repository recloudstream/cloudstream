package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class DramaSeeProvider : MainAPI() {
    override val mainUrl = "https://dramasee.net"
    override val name = "DramaSee"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override fun getMainPage(): HomePageResponse {
        val headers = mapOf("X-Requested-By" to "dramasee.net")
        val html = app.get(mainUrl, headers = headers).text
        val document = Jsoup.parse(html)
        val mainbody = document.getElementsByTag("body")

        return HomePageResponse(
    mainbody?.select("section")?.map { row ->
            val main = row?.select("main")

            val title = main?.select("div.title > div > h2")?.text() ?: "Main"
            val inner = main?.select("li.series-item") ?: return@map null

            HomePageList(
                title,
                inner.mapNotNull {
                // Get inner div from article
                val innerBody = it?.selectFirst("a")
                // Fetch details
                val link = fixUrlNull(innerBody?.attr("href")) ?: return@mapNotNull null
                val image = fixUrlNull(innerBody?.select("img")?.attr("src")) ?: ""
                val name = it?.selectFirst("a.series-name")?.text() ?: "<Untitled>"
                //Log.i(this.name, "Result => (innerBody, image) ${innerBody} / ${image}")
                MovieSearchResponse(
                    name,
                    link,
                    this.name,
                    TvType.TvSeries,
                    image,
                    year = null,
                    id = null,
                )
                }.distinctBy { c -> c.url })
            }?.filterNotNull() ?: listOf()
        )
    }

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val html = app.get(url).document
        val document = html.getElementsByTag("body")
                .select("section > main > ul.series > li") ?: return listOf()

        return document.map {
            val innerA = it?.select("a.series-img")
            val href = innerA?.attr("href") ?: return@map null
            val link = fixUrlNull(href) ?: return@map null
            val title = it?.select("a.series-name")?.text() ?: return@map null
            val year = null
            val imgsrc = innerA?.select("img")?.attr("src") ?: return@map null
            val image = fixUrl(imgsrc)

            MovieSearchResponse(
                title,
                link,
                this.name,
                TvType.Movie,
                image,
                year
            )
        }.filterNotNull()
    }

    override fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.series-info")

        // Video details
        val poster = fixUrlNull(inner?.select("div.img > img")?.attr("src")) ?: ""
        //Log.i(this.name, "Result => (imgLinkCode) ${imgLinkCode}")
        val title = inner?.select("h1.series-name")?.text() ?: ""
        val year = if (title.length > 5) { title.substring(title.length - 5)
            .trim().trimEnd(')').toIntOrNull() } else { null }
        //Log.i(this.name, "Result => (year) ${title.substring(title.length - 5)}")
        val descript = body?.select("div.series-body")?.firstOrNull()
            ?.select("div.js-content")?.text()

        // Episodes Links
        val episodeList = ArrayList<TvSeriesEpisode>()
        val eps = body?.select("ul.episodes > li.episode-item")
        //Log.i(this.name, "Result => (eps) ${eps}")
        if (!eps.isNullOrEmpty()) {
            for (ep in eps) {
                if (ep != null) {
                    val innerA = ep.select("a")
                    val count = innerA.select("span.episode")?.text()?.toIntOrNull() ?: 0
                    val epLink = fixUrlNull(innerA.attr("href")) ?: continue
                    //Log.i(this.name, "Result => (epLink) ${epLink}")
                    if (epLink.isNotEmpty()) {
                        // Fetch video links
                        val epVidLinkEl = app.get(epLink, referer = mainUrl).document
                        val ajaxUrl = epVidLinkEl.select("div#js-player")?.attr("embed")
                        //Log.i(this.name, "Result => (ajaxUrl) ${ajaxUrl}")
                        if (!ajaxUrl.isNullOrEmpty()) {
                            val innerPage = Jsoup.parse(app.get(fixUrl(ajaxUrl), referer = epLink).text)
                            val listOfLinks = mutableListOf<String>()
                            val serverAvail = innerPage?.select("div.player.active > main > div")
                            if (!serverAvail.isNullOrEmpty()) {
                                for (em in serverAvail) {
                                    val href = em.attr("src")
                                    if (!href.isNullOrEmpty()) {
                                        listOfLinks.add(href)
                                    }
                                }
                            }
                            //Log.i(this.name, "Result => (listOfLinks) ${listOfLinks}")
                            episodeList.add(
                                TvSeriesEpisode(
                                    name = "Episode $count",
                                    season = null,
                                    episode = count,
                                    data = listOfLinks.toString(),
                                    posterUrl = poster,
                                    date = null
                                )
                            )
                        }
                    }
                }
            }
        }
        //If there's only 1 episode, consider it a movie.
        if (episodeList.size == 1) {
            return MovieLoadResponse(title, url, this.name, TvType.Movie, episodeList[0].data, poster, year, descript, null, null)
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

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "about:blank") return false
        if (data == "[]") return false
        if (data.isEmpty()) return false

        val urls = data.trim('[').trim(']').split(',')
        if (!urls.isNullOrEmpty()) {
            for (item in urls) {
                if (item.isNotEmpty()) {
                    var url = item.trim()
                    if (url.startsWith("//")) {
                        url = "https:$url"
                    }
                    //Log.i(this.name, "Result => (url) ${url}")
                    if (url.startsWith("https://asianembed.io")) {
                        // Fetch links
                        val doc = app.get(url).document
                        val links = doc.select("div#list-server-more > ul > li.linkserver")
                        if (!links.isNullOrEmpty()) {
                            links.forEach {
                                val datavid = it.attr("data-video") ?: ""
                                //Log.i(this.name, "Result => (datavid) ${datavid}")
                                if (datavid.isNotEmpty()) {
                                    if (datavid.startsWith("https://fembed-hd.com")) {
                                        val extractor = XStreamCdn()
                                        extractor.domainUrl = "fembed-hd.com"
                                        val src = extractor.getUrl(datavid, url)
                                        src.forEach { link ->
                                            callback.invoke(link)
                                        }
                                    } else {
                                        loadExtractor(datavid, url, callback)
                                    }
                                }
                            }
                        }
                    } else if (url.startsWith("https://embedsito.com")) {
                        val extractor = XStreamCdn()
                        extractor.domainUrl = "embedsito.com"
                        val src = extractor.getUrl(url)
                        src.forEach { link ->
                            callback.invoke(link)
                        }
                    } else {
                        loadExtractor(url, url, callback)
                    } // end if
                }
            }
            return true
        }
        return false
    }
}