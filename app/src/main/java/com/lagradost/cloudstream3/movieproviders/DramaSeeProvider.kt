package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.animeproviders.GogoanimeProvider.Companion.extractVidstream
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

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val headers = mapOf("X-Requested-By" to mainUrl)
        val document = app.get(mainUrl, headers = headers).document
        val mainbody = document.getElementsByTag("body")

        return HomePageResponse(
            mainbody.select("section.block_area.block_area_home")?.map { main ->
                val title = main.select("h2.cat-heading").text() ?: "Main"
                val inner = main.select("div.flw-item") ?: return@map null

                HomePageList(
                    title,
                    inner.mapNotNull {
                        val innerBody = it?.selectFirst("a")
                        // Fetch details
                        val link = fixUrlNull(innerBody?.attr("href")) ?: return@mapNotNull null
                        val image = fixUrlNull(it.select("img").attr("data-src")) ?: ""
                        val name = innerBody?.attr("title") ?: "<Untitled>"
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
        val document = app.get(url).document
        val posters = document.select("div.film-poster")


        return posters.mapNotNull {
            val innerA = it.select("a") ?: return@mapNotNull null
            val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            val title = innerA.attr("title") ?: return@mapNotNull null
            val year =
                Regex(""".*\((\d{4})\)""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val imgSrc = it.select("img")?.attr("data-src") ?: return@mapNotNull null
            val image = fixUrlNull(imgSrc)

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
        val inner = body?.select("div.anis-content")

        // Video details
        val poster = fixUrlNull(inner?.select("img.film-poster-img")?.attr("src")) ?: ""
        //Log.i(this.name, "Result => (imgLinkCode) ${imgLinkCode}")
        val title = inner?.select("h2.film-name.dynamic-name")?.text() ?: ""
        val year = if (title.length > 5) {
            title.substring(title.length - 5)
                .trim().trimEnd(')').toIntOrNull()
        } else {
            null
        }
        //Log.i(this.name, "Result => (year) ${title.substring(title.length - 5)}")
        val descript = body?.firstOrNull()?.select("div.film-description.m-hide")?.text()
        val tags = inner?.select("div.item.item-list > a")
            ?.mapNotNull { it?.text()?.trim() ?: return@mapNotNull null }
        val recs = body.select("div.flw-item")?.mapNotNull {
            val a = it.select("a") ?: return@mapNotNull null
            val aUrl = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val aImg = fixUrlNull(it.select("img")?.attr("data-src"))
            val aName = a.attr("title") ?: return@mapNotNull null
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
        val episodeUrl = body.select("a.btn.btn-radius.btn-primary.btn-play").attr("href")
        val episodeDoc = app.get(episodeUrl).document


        val episodeList = episodeDoc.select("div.ss-list.ss-list-min > a").mapNotNull { ep ->
            val episodeNumber = ep.attr("data-number").toIntOrNull()
            val epLink = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null

//            if (epLink.isNotBlank()) {
//                // Fetch video links
//                val epVidLinkEl = app.get(epLink, referer = mainUrl).document
//                val ajaxUrl = epVidLinkEl.select("div#js-player")?.attr("embed")
//                //Log.i(this.name, "Result => (ajaxUrl) ${ajaxUrl}")
//                if (!ajaxUrl.isNullOrEmpty()) {
//                    val innerPage = app.get(fixUrl(ajaxUrl), referer = epLink).document
//                    val listOfLinks = mutableListOf<String>()
//                    innerPage.select("div.player.active > main > div")?.forEach { em ->
//                        val href = fixUrlNull(em.attr("src")) ?: ""
//                        if (href.isNotBlank()) {
//                            listOfLinks.add(href)
//                        }
//                    }
//
//                    //Log.i(this.name, "Result => (listOfLinks) ${listOfLinks.toJson()}")
//
//                }
//            }
            Episode(
                name = null,
                season = null,
                episode = episodeNumber,
                data = epLink,
                posterUrl = null,
                date = null
            )
        }

        //If there's only 1 episode, consider it a movie.
        if (episodeList.size == 1) {
            return MovieLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.Movie,
                dataUrl = episodeList.first().data,
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
            episodes = episodeList,
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
        println("DATATATAT $data")

        val document = app.get(data).document
        val iframeUrl = document.select("iframe").attr("src")
        val iframe = app.get(iframeUrl)
        val iframeDoc = iframe.document

        argamap({
            iframeDoc.select(".list-server-items > .linkserver")
                .forEach { element ->
                    val status = element.attr("data-status") ?: return@forEach
                    if (status != "1") return@forEach
                    val extractorData = element.attr("data-video") ?: return@forEach
                    loadExtractor(extractorData, iframe.url, subtitleCallback, callback)
                }
        }, {
            val iv = "9262859232435825"
            val secretKey = "93422192433952489752342908585752"
            val secretDecryptKey = "93422192433952489752342908585752"
            extractVidstream(
                iframe.url,
                this.name,
                callback,
                iv,
                secretKey,
                secretDecryptKey,
                isUsingAdaptiveKeys = false,
                isUsingAdaptiveData = true,
                iframeDocument = iframeDoc
            )
        })
        return true
    }
}
