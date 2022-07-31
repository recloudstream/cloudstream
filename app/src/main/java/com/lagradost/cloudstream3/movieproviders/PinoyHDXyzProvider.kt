package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class PinoyHDXyzProvider : MainAPI() {
    override var name = "Pinoy-HD"
    override var mainUrl = "https://www.pinoy-hd.xyz"
    override var lang = "tl"
    override val supportedTypes = setOf(TvType.AsianDrama)
    override val hasDownloadSupport = true
    override val hasMainPage = true
    override val hasQuickSearch = false

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val all = ArrayList<HomePageList>()
        val document = app.get(mainUrl, referer = mainUrl).document
        val mainbody = document.getElementsByTag("body")

        mainbody.select("div.section-cotent.col-md-12.bordert").forEach { row ->
            val title = row?.select("div.title-section.tt")?.text() ?: "<Row>"
            val elements = row?.select("li.img_frame.preview-tumb7")?.mapNotNull {
                // Get inner div from article
                val innerBody = it?.selectFirst("a") ?: return@mapNotNull null
                // Fetch details
                val name = it.text().trim()
                if (name.isBlank()) {
                    return@mapNotNull null
                }

                val link = innerBody.attr("href") ?: return@mapNotNull null
                val image = fixUrlNull(innerBody.select("img").attr("src"))
                //Log.i(this.name, "Result => (innerBody, image) ${innerBody} / ${image}")
                // Get Year from Link
                val rex = Regex("_(\\d+)_")
                val year = rex.find(link)?.value?.replace("_", "")?.toIntOrNull()
                //Log.i(this.name, "Result => (yearRes, year) ${yearRes} / ${year}")
                MovieSearchResponse(
                    name = name,
                    url = link,
                    apiName = this.name,
                    type = TvType.Movie,
                    posterUrl = image,
                    year = year
                )
            }?.distinctBy { c -> c.url } ?: listOf()
            // Add to Homepage
            if (elements.isNotEmpty()) {
                all.add(
                    HomePageList(
                        title, elements
                    )
                )
            }
        }
        return HomePageResponse(all)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/?q=${query.replace(" ", "+")}"
        val document = app.get(url).document.select("div.portfolio-thumb")
        return document.mapNotNull {
            if (it == null) {
                return@mapNotNull null
            }
            val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = it.text() ?: ""
            val year = null
            val image = null // site provides no image on search page

            MovieSearchResponse(
                name = title,
                url = link,
                apiName = this.name,
                type = TvType.Movie,
                posterUrl = image,
                year = year
            )
        }.distinctBy { c -> c.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val body = doc.getElementsByTag("body")
        val inner = body.select("div.info")

        // Video links
        val listOfLinks: MutableList<String> = mutableListOf()

        // Video details
        var title = ""
        var year: Int? = null
        var tags: List<String>? = null
        val poster = fixUrlNull(inner.select("div.portfolio-tumb.ph-link > img").attr("src"))
        //Log.i(this.name, "Result => (imgLinkCode) ${imgLinkCode}")
        inner.select("table").select("tr").forEach {
            val td = it?.select("td") ?: return@forEach
            val caption = td[0].text().lowercase()
            //Log.i(this.name, "Result => (caption) $caption")
            when (caption) {
                "name" -> {
                    title = td[1].text()
                }
                "year" -> {
                    var yearRes = td[1].toString()
                    year = if (yearRes.isNotBlank()) {
                        if (yearRes.contains("var year =")) {
                            yearRes =
                                yearRes.substring(yearRes.indexOf("var year =") + "var year =".length)
                            //Log.i(this.name, "Result => (yearRes) $yearRes")
                            yearRes = yearRes.substring(0, yearRes.indexOf(';'))
                                .trim().removeSurrounding("'")
                        }
                        yearRes.toIntOrNull()
                    } else {
                        null
                    }
                }
                "genre" -> {
                    tags = td[1].select("a").mapNotNull { tag ->
                        tag?.text()?.trim() ?: return@mapNotNull null
                    }.filter { a -> a.isNotBlank() }
                }
            }
        }

        var descript = body.select("div.eText").text()
        if (!descript.isNullOrEmpty()) {
            try {
                descript = "(undefined_x_Polus+[.\\d+])".toRegex().replace(descript, "")
                descript = "(_x_Polus+[.\\d+])".toRegex().replace(descript, "")
                descript = descript.trim().removeSuffix("undefined").trim()
            } catch (e: java.lang.Exception) {
            }
        }
        // Add links hidden in description
        listOfLinks.addAll(fetchUrls(descript))
        listOfLinks.forEach { link ->
            //Log.i(this.name, "Result => (hidden link) $link")
            descript = descript.replace(link, "")
        }

        // Try looking for episodes, for series
        val episodeList = ArrayList<Episode>()
        val bodyText = body.select("div.section-cotent1.col-md-12").select("section")
            .select("script").toString()
        //Log.i(this.name, "Result => (bodyText) ${bodyText}")

        "(?<=ses=\\(')(.*)(?='\\).split)".toRegex().find(bodyText)?.groupValues?.get(0).let {
            if (!it.isNullOrEmpty()) {
                var count = 0
                it.split(", ").forEach { ep ->
                    count++
                    val listEpStream = listOf(ep.trim()).toJson()
                    //Log.i(this.name, "Result => (ep $count) $listEpStream")
                    episodeList.add(
                        Episode(
                            name = null,
                            season = null,
                            episode = count,
                            data = listEpStream,
                            posterUrl = null,
                            date = null
                        )
                    )
                }
            }
        }
        if (episodeList.size > 0) {
            return TvSeriesLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.AsianDrama,
                episodes = episodeList,
                posterUrl = poster,
                year = year,
                plot = descript,
                tags = tags
            )
        }

        // Video links for Movie
        body.select("div.tabcontent > iframe").forEach {
            val linkMain = it?.attr("src")
            if (!linkMain.isNullOrEmpty()) {
                listOfLinks.add(linkMain)
                //Log.i(this.name, "Result => (linkMain) $linkMain")
            }
        }
        body.select("div.tabcontent.hide > iframe").forEach {
            val linkMain = it?.attr("src")
            if (!linkMain.isNullOrEmpty()) {
                listOfLinks.add(linkMain)
                //Log.i(this.name, "Result => (linkMain hide) $linkMain")
            }
        }

        val extraLinks = body.select("div.tabcontent.hide").text()
        listOfLinks.addAll(fetchUrls(extraLinks))

        val streamLinks = listOfLinks.distinct().toJson()
        //Log.i(this.name, "Result => (streamLinks) streamLinks")
        return MovieLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.Movie,
            dataUrl = streamLinks,
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
        var count = 0
        parseJson<List<String>>(data).forEach { item ->
            val url = item.trim()
            if (url.isNotBlank()) {
                if (loadExtractor(url, mainUrl, subtitleCallback, callback)) {
                    count++
                }
            }
        }
        return count > 0
    }
}