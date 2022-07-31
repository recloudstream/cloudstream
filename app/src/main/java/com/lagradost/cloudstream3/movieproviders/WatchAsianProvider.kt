package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.animeproviders.GogoanimeProvider.Companion.extractVidstream
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.extractors.helper.AsianEmbedHelper
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class WatchAsianProvider : MainAPI() {
    override var mainUrl = "https://watchasian.cx"
    override var name = "WatchAsian"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val headers = mapOf("X-Requested-By" to mainUrl)
        val doc = app.get(mainUrl, headers = headers).document
        val rowPair = mutableListOf<Pair<String, String>>()
        doc.select("div.block-tab").forEach {
            it?.select("ul.tab > li")?.mapNotNull { row ->
                val link = row?.attr("data-tab") ?: return@mapNotNull null
                val title = row.text() ?: return@mapNotNull null
                Pair(title, link)
            }?.let { it1 ->
                rowPair.addAll(
                    it1
                )
            }
        }

        return HomePageResponse(
            rowPair.mapNotNull { row ->
                val main = (doc.select("div.tab-content.${row.second}")
                    ?: doc.select("div.tab-content.${row.second}.selected"))
                    ?: return@mapNotNull null

                val title = row.first
                val inner = main.select("li") ?: return@mapNotNull null

                HomePageList(
                    title,
                    inner.map {
                        // Get inner div from article
                        val innerBody = it?.selectFirst("a")
                        // Fetch details
                        val link = fixUrlNull(innerBody?.attr("href")) ?: return@map null
                        val image =
                            fixUrlNull(innerBody?.select("img")?.attr("data-original")) ?: ""
                        val name = (innerBody?.selectFirst("h3.title")?.text() ?: innerBody?.text())
                            ?: "<Untitled>"
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
                    }.filterNotNull().distinctBy { c -> c.url })
            }.filter { a -> a.list.isNotEmpty() }
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?type=movies&keyword=$query"
        val document = app.get(url).document.getElementsByTag("body")
            .select("div.block.tab-container > div > ul > li") ?: return listOf()

        return document.mapNotNull {
            val innerA = it?.selectFirst("a") ?: return@mapNotNull null
            val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            val title = it.select("h3.title").text() ?: return@mapNotNull null
            if (title.isEmpty()) {
                return@mapNotNull null
            }
            val year = null
            val imgsrc = innerA.select("img").attr("data-original") ?: return@mapNotNull null
            val image = fixUrlNull(imgsrc)
            //Log.i(this.name, "Result => (img movie) $title / $link")
            MovieSearchResponse(
                title,
                link,
                this.name,
                TvType.Movie,
                image,
                year
            )
        }.distinctBy { a -> a.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val body = app.get(url).document
        // Declare vars
        val isDramaDetail = url.contains("/drama-detail/")
        var poster: String? = null
        var title = ""
        var descript: String? = null
        var year: Int? = null
        var tags: List<String>? = null
        if (isDramaDetail) {
            val main = body.select("div.details")
            val inner = main.select("div.info")
            // Video details
            poster = fixUrlNull(main.select("div.img > img").attr("src"))
            //Log.i(this.name, "Result => (imgLinkCode) ${imgLinkCode}")
            title = inner.select("h1").firstOrNull()?.text() ?: ""
            //Log.i(this.name, "Result => (year) ${title.substring(title.length - 5)}")
            descript = inner.text()

            inner.select("p").forEach { p ->
                val caption =
                    p?.selectFirst("span")?.text()?.trim()?.lowercase()?.removeSuffix(":")?.trim()
                        ?: return@forEach
                when (caption) {
                    "genre" -> {
                        tags = p.select("a").mapNotNull { it?.text()?.trim() }
                    }
                    "released" -> {
                        year = p.select("a").text().trim()?.toIntOrNull()
                    }
                }
            }
        } else {
            poster = body.select("meta[itemprop=\"image\"]")?.attr("content") ?: ""
            title = body.selectFirst("div.block.watch-drama")?.selectFirst("h1")
                ?.text() ?: ""
            year = null
            descript = body.select("meta[name=\"description\"]")?.attr("content")
        }
        //Fallback year from title
        if (year == null) {
            year = if (title.length > 5) {
                title.replace(")", "").replace("(", "").substring(title.length - 5)
                    .trim().trimEnd(')').toIntOrNull()
            } else {
                null
            }
        }

        // Episodes Links
        //Log.i(this.name, "Result => (all eps) ${body.select("ul.list-episode-item-2.all-episode > li")}")
        val episodeList = body.select("ul.list-episode-item-2.all-episode > li").mapNotNull { ep ->
            //Log.i(this.name, "Result => (epA) ${ep.select("a")}")
            val innerA = ep.select("a") ?: return@mapNotNull null
            //Log.i(this.name, "Result => (innerA) ${fixUrlNull(innerA.attr("href"))}")
            val epLink = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null

            val regex = "(?<=episode-).*?(?=.html)".toRegex()
            val count = regex.find(epLink, mainUrl.length)?.value?.toIntOrNull() ?: 0
            //Log.i(this.name, "Result => $epLink (regexYear) ${count}")
            Episode(
                name = null,
                season = null,
                episode = count,
                data = epLink,
                posterUrl = poster,
                date = null
            )
        }
        //If there's only 1 episode, consider it a movie.
        if (episodeList.size == 1) {
            //Clean title
            title = title.trim().removeSuffix("Episode 1")
            val streamlink = getServerLinks(episodeList[0].data)
            //Log.i(this.name, "Result => (streamlink) $streamlink")
            return MovieLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.Movie,
                dataUrl = streamlink,
                posterUrl = poster,
                year = year,
                plot = descript,
                tags = tags
            )
        }
        return TvSeriesLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.AsianDrama,
            episodes = episodeList.reversed(),
            posterUrl = poster,
            year = year,
            plot = descript,
            tags = tags
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = if (data.startsWith(mainUrl)) {
            getServerLinks(data)
        } else {
            data
        }
        var count = 0
        parseJson<List<String>>(links).apmap { item ->
            count++
            val url = fixUrl(item.trim())
            //Log.i(this.name, "Result => (url) $url")
            when {
                url.startsWith("https://asianembed.io") || url.startsWith("https://asianload.io") -> {
                    val iv = "9262859232435825"
                    val secretKey = "93422192433952489752342908585752"
                    extractVidstream(
                        url, this.name, callback, iv, secretKey, secretKey,
                        isUsingAdaptiveKeys = false,
                        isUsingAdaptiveData = false
                    )
                    AsianEmbedHelper.getUrls(url, subtitleCallback, callback)
                }
                url.startsWith("https://embedsito.com") -> {
                    val extractor = XStreamCdn()
                    extractor.domainUrl = "embedsito.com"
                    extractor.getSafeUrl(
                        url,
                        subtitleCallback = subtitleCallback,
                        callback = callback,
                    )
                }
                else -> {
                    loadExtractor(url, mainUrl, subtitleCallback, callback)
                }
            }
        }
        return count > 0
    }

    private suspend fun getServerLinks(url: String): String {
        val moviedoc = app.get(url, referer = mainUrl).document
        return moviedoc.select("div.anime_muti_link > ul > li")
            .mapNotNull {
                fixUrlNull(it?.attr("data-video")) ?: return@mapNotNull null
            }.toJson()
    }
}
