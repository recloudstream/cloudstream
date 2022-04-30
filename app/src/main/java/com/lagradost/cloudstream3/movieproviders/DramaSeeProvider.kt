package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.animeproviders.GogoanimeProvider.Companion.extractVidstream
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.extractors.helper.AsianEmbedHelper
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class DramaSeeProvider : MainAPI() {
    override var mainUrl = "https://dramasee.net"
    override var name = "DramaSee"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override suspend fun getMainPage(): HomePageResponse {
        val headers = mapOf("X-Requested-By" to "dramasee.net")
        val document = app.get(mainUrl, headers = headers).document
        val mainbody = document.getElementsByTag("body")

        return HomePageResponse(
            mainbody?.select("section")?.map { row ->
                val main = row?.select("main") ?: return@map null
                val title = main.select("div.title > div > h2")?.text() ?: "Main"
                val inner = main.select("li.series-item") ?: return@map null

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
                            TvType.AsianDrama,
                            image,
                            year = null,
                            id = null,
                        )
                    }.distinctBy { c -> c.url })
            }?.filterNotNull() ?: listOf()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val html = app.get(url).document
        val document = html.getElementsByTag("body")
            .select("section > main > ul.series > li") ?: return listOf()

        return document.mapNotNull {
            if (it == null) {
                return@mapNotNull null
            }
            val innerA = it.select("a.series-img") ?: return@mapNotNull null
            val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            val title = it.select("a.series-name")?.text() ?: return@mapNotNull null
            val year = null
            val imgsrc = innerA.select("img")?.attr("src") ?: return@mapNotNull null
            val image = fixUrlNull(imgsrc)

            MovieSearchResponse(
                name = title,
                url = link,
                apiName = this.name,
                type = TvType.Movie,
                posterUrl = image,
                year = year
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.series-info")

        // Video details
        val poster = fixUrlNull(inner?.select("div.img > img")?.attr("src")) ?: ""
        //Log.i(this.name, "Result => (imgLinkCode) ${imgLinkCode}")
        val title = inner?.select("h1.series-name")?.text() ?: ""
        val year = if (title.length > 5) {
            title.substring(title.length - 5)
                .trim().trimEnd(')').toIntOrNull()
        } else {
            null
        }
        //Log.i(this.name, "Result => (year) ${title.substring(title.length - 5)}")
        val seriesBody = body?.select("div.series-body")
        val descript = seriesBody?.firstOrNull()?.select("div.js-content")?.text()
        val tags = seriesBody?.select("div.series-tags > a")
            ?.mapNotNull { it?.text()?.trim() ?: return@mapNotNull null }
        val recs = body?.select("ul.series > li")?.mapNotNull {
            val a = it.select("a.series-img") ?: return@mapNotNull null
            val aUrl = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val aImg = fixUrlNull(a.select("img")?.attr("src"))
            val aName = a.select("img")?.attr("alt") ?: return@mapNotNull null
            val aYear = aName.trim().takeLast(5).removeSuffix(")").toIntOrNull()
            MovieSearchResponse(
                url = aUrl,
                name = aName,
                type = TvType.Movie,
                posterUrl = aImg,
                year = aYear,
                apiName = this.name
            )
        }

        // Episodes Links
        val episodeList = ArrayList<Episode>()
        body?.select("ul.episodes > li")?.forEach { ep ->
            val innerA = ep.select("a") ?: return@forEach
            val count = innerA.select("span.episode")?.text()?.toIntOrNull() ?: 0
            val epLink = fixUrlNull(innerA.attr("href")) ?: return@forEach
            //Log.i(this.name, "Result => (epLink) ${epLink}")
            if (epLink.isNotBlank()) {
                // Fetch video links
                val epVidLinkEl = app.get(epLink, referer = mainUrl).document
                val ajaxUrl = epVidLinkEl.select("div#js-player")?.attr("embed")
                //Log.i(this.name, "Result => (ajaxUrl) ${ajaxUrl}")
                if (!ajaxUrl.isNullOrEmpty()) {
                    val innerPage = app.get(fixUrl(ajaxUrl), referer = epLink).document
                    val listOfLinks = mutableListOf<String>()
                    innerPage.select("div.player.active > main > div")?.forEach { em ->
                        val href = fixUrlNull(em.attr("src")) ?: ""
                        if (href.isNotBlank()) {
                            listOfLinks.add(href)
                        }
                    }

                    //Log.i(this.name, "Result => (listOfLinks) ${listOfLinks.toJson()}")
                    episodeList.add(
                        Episode(
                            name = null,
                            season = null,
                            episode = count,
                            data = listOfLinks.distinct().toJson(),
                            posterUrl = poster,
                            date = null
                        )
                    )
                }
            }
        }

        //If there's only 1 episode, consider it a movie.
        if (episodeList.size == 1) {
            return MovieLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.Movie,
                dataUrl = episodeList[0].data,
                posterUrl = poster,
                year = year,
                plot = descript,
                recommendations = recs,
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
            recommendations = recs,
            tags = tags
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var count = 0
        mapper.readValue<List<String>>(data).apmap { item ->
            if (item.isNotEmpty()) {
                count++
                val url = fixUrl(item.trim())
                //Log.i(this.name, "Result => (url) ${url}")
                when {
                    url.startsWith("https://asianembed.io") || url.startsWith("https://asianload.io") -> {
                        val iv = "9262859232435825"
                        val secretKey = "93422192433952489752342908585752"
                        extractVidstream(url, this.name, callback, iv, secretKey, secretKey,
                            isUsingAdaptiveKeys = false,
                            isUsingAdaptiveData = false
                        )
                        AsianEmbedHelper.getUrls(url, callback)
                    }
                    url.startsWith("https://embedsito.com") -> {
                        val extractor = XStreamCdn()
                        extractor.domainUrl = "embedsito.com"
                        extractor.getSafeUrl(url)?.forEach(callback)
                    }
                    else -> {
                        loadExtractor(url, mainUrl, callback)
                    }
                }
            }
        }
        return count > 0
    }
}
