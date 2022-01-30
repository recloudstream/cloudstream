package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class PinoyHDXyzProvider : MainAPI() {
    override val name = "Pinoy-HD"
    override val mainUrl = "https://www.pinoy-hd.xyz"
    override val lang = "tl"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasDownloadSupport = true
    override val hasMainPage = true
    override val hasQuickSearch = false


    override suspend fun getMainPage(): HomePageResponse {
        val all = ArrayList<HomePageList>()
        val document = app.get(mainUrl, referer = mainUrl).document
        val mainbody = document.getElementsByTag("body")

        mainbody?.select("div.section-cotent.col-md-12.bordert")?.forEach { row ->
            val title = row?.select("div.title-section.tt")?.text() ?: "<Row>"
            val inner = row?.select("li.img_frame.preview-tumb7")
            if (inner != null) {
                val elements: List<SearchResponse> = inner.map {
                    // Get inner div from article
                    val innerBody = it?.select("a")?.firstOrNull()
                    // Fetch details
                    val name = it?.text() ?: ""
                    val link = innerBody?.attr("href") ?: ""
                    val imgsrc = innerBody?.select("img")?.attr("src")
                    val image = when (!imgsrc.isNullOrEmpty()) {
                        true -> "${mainUrl}${imgsrc}"
                        false -> null
                    }
                    //Log.i(this.name, "Result => (innerBody, image) ${innerBody} / ${image}")
                    // Get Year from Link
                    val rex = Regex("_(\\d+)_")
                    val yearRes = rex.find(link)?.value ?: ""
                    val year = yearRes.replace("_", "").toIntOrNull()
                    //Log.i(this.name, "Result => (yearRes, year) ${yearRes} / ${year}")
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

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/?q=${query.replace(" ", "+")}"
        val document = app.get(url).document.select("div.portfolio-thumb")
        return document?.mapNotNull {
            if (it == null) {
                return@mapNotNull null
            }
            val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = it.text() ?: ""
            val year = null
            val image = null // site provides no image on search page

            MovieSearchResponse(
                title,
                link,
                this.name,
                TvType.Movie,
                image,
                year
            )
        }?.distinctBy { c -> c.url } ?: listOf()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.info")

        // Video details
        val imgLinkCode = inner?.select("div.portfolio-tumb.ph-link > img")?.attr("src")
        val poster = if (!imgLinkCode.isNullOrEmpty()) { "${mainUrl}${imgLinkCode}" } else { null }
        //Log.i(this.name, "Result => (imgLinkCode) ${imgLinkCode}")
        val title = inner?.select("td.trFon2.entt")?.firstOrNull()?.text() ?: "<Untitled>"
        var yearRes = inner?.select("td.trFon2")?.toString()
        val year = if (!yearRes.isNullOrEmpty()) {
            if (yearRes.contains("var year =")) {
                yearRes = yearRes.substring(yearRes.indexOf("var year =") + "var year =".length)
                //Log.i(this.name, "Result => (yearRes) $yearRes")
                yearRes = yearRes.substring(0, yearRes.indexOf(';'))
                    .trim().removeSurrounding("'")
            }
            yearRes.toIntOrNull()
        } else { null }

        var descript = body?.select("div.eText")?.text()
        if (!descript.isNullOrEmpty()) {
            try {
                descript = descript.substring(0, descript.indexOf("_x_Polus1"))
                    .replace("_x_Polus1", "")
            } catch (e: java.lang.Exception) {  }
        }

        // Try looking for episodes, for series
        val episodeList = ArrayList<TvSeriesEpisode>()
        val bodyText = body?.select("div.section-cotent1.col-md-12")?.select("section")
            ?.select("script")?.toString() ?: ""
        //Log.i(this.name, "Result => (bodyText) ${bodyText}")

        "(?<=ses=\\(')(.*)(?='\\).split)".toRegex().find(bodyText)?.groupValues?.get(0).let {
            if (!it.isNullOrEmpty()) {
                var count = 0
                it.split(", ").forEach { ep ->
                    count++
                    val listEpStream = listOf(ep.trim()).toJson()
                    //Log.i(this.name, "Result => (ep $count) $listEpStream")
                    episodeList.add(
                        TvSeriesEpisode(
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

        // Video links for Movie
        val listOfLinks: MutableList<String> = mutableListOf()
        body?.select("div.tabcontent > iframe")?.forEach {
            val linkMain = it?.attr("src")
            if (!linkMain.isNullOrEmpty()) {
                listOfLinks.add(linkMain)
                //Log.i(this.name, "Result => (linkMain) $linkMain")
            }
        }
        body?.select("div.tabcontent.hide > iframe")?.forEach {
            val linkMain = it?.attr("src")
            if (!linkMain.isNullOrEmpty()) {
                listOfLinks.add(linkMain)
                //Log.i(this.name, "Result => (linkMain hide) $linkMain")
            }
        }

        val extraLinks = body?.select("div.tabcontent.hide")?.text()
        listOfLinks.addAll(fetchUrls(extraLinks))

        val streamLinks = listOfLinks.distinct().toJson()
        //Log.i(this.name, "Result => (streamLinks) streamLinks")
        return MovieLoadResponse(title, url, this.name, TvType.Movie, streamLinks, poster, year, descript, null, null)
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
                val url = item.trim()
                loadExtractor(url, mainUrl, callback)
                count++
            }
        }
        return count > 0
    }
}