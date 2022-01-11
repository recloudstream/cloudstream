package com.lagradost.cloudstream3.movieproviders

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.lang.Exception

class PinoyHDXyzProvider : MainAPI() {
    override val name = "Pinoy-HD"
    override val mainUrl = "https://www.pinoy-hd.xyz"
    override val lang = "tl"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasDownloadSupport = true
    override val hasMainPage = true
    override val hasQuickSearch = false


    override fun getMainPage(): HomePageResponse {
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

    override fun search(query: String): List<SearchResponse> {
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

    override fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val body = doc.getElementsByTag("body")
        val inner = body?.select("div.info")

        // Video details
        val tvtype = when (url.contains("/pinoy_tv_series/", ignoreCase = true)) {
            true -> TvType.TvSeries
            false -> TvType.Movie
        }
        val imgLinkCode = inner?.select("div.portfolio-tumb.ph-link > img")?.attr("src")
        val poster = if (!imgLinkCode.isNullOrEmpty()) { "${mainUrl}${imgLinkCode}" } else { null }
        //Log.i(this.name, "Result => (imgLinkCode) ${imgLinkCode}")
        val title = inner?.select("td.trFon2.entt")?.firstOrNull()?.text() ?: "<Untitled>"
        var yearRes = inner?.select("td.trFon2")?.toString()
        if (!yearRes.isNullOrEmpty()) {
            if (yearRes.contains("var year =")) {
                yearRes = yearRes.substring(yearRes.indexOf("var year ="))
                yearRes = yearRes.substring(0, yearRes.indexOf(';')).replace("var year =", "")
                    .trim().trim('\'')
            }
        }
        //Log.i(this.name, "Result => (yearRes) ${yearRes}")
        val year = yearRes?.toIntOrNull()

        var descript = body?.select("div.eText")?.text()
        if (!descript.isNullOrEmpty()) {
            try {
                descript = descript.substring(0, descript.indexOf("_x_Polus1"))
                    .replace("_x_Polus1", "")
            } catch (e: java.lang.Exception) {  }
        }

        // Video links
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

        var extraLinks = body?.select("div.tabcontent.hide")?.text()
        if (!extraLinks.isNullOrEmpty()) {
            try {
                extraLinks = extraLinks.substring(extraLinks.indexOf("_x_Polus1"))
                extraLinks = extraLinks.trim().substring("_x_Polus1".length)
                extraLinks = extraLinks.substring(0, extraLinks.indexOf("<script>"))
                extraLinks.split("_x_Polus").forEach { item ->
                    if (item.contains("https://")) {
                        val lnkurl = item.substring(item.indexOf("https://")).trim()
                        listOfLinks.add(lnkurl)
                        //Log.i(this.name, "Result => (lnkurl) $lnkurl")
                    }
                }
            } catch (e: Exception) { }
        }

        // Parse episodes if series
        if (tvtype == TvType.TvSeries) {
            val indexStart = "ses=("
            val episodeList = ArrayList<TvSeriesEpisode>()
            val bodyText = body?.select("div.section-cotent1.col-md-12")?.select("section")
                ?.select("script")?.toString() ?: ""
            //Log.i(this.name, "Result => (bodyText) ${bodyText}")
            if (bodyText.contains(indexStart)) {
                var epListText = bodyText.substring(bodyText.indexOf(indexStart))
                if (epListText.isNotEmpty()) {
                    epListText = epListText.substring(indexStart.length, epListText.indexOf(")"))
                        .trim().trim('\'')
                    //Log.i(this.name, "Result => (epListText) ${epListText}")
                    var count = 0
                    epListText.split(',').forEach { ep ->
                        count++
                        val listEpStream = listOf(ep.trim()).toJson()
                        //Log.i(this.name, "Result => (ep $count) $listEpStream")
                        episodeList.add(
                            TvSeriesEpisode(
                                name = null,
                                season = null,
                                episode = count,
                                data = listEpStream,
                                posterUrl = poster,
                                date = null
                            )
                        )
                    }
                    return TvSeriesLoadResponse(
                        title,
                        url,
                        this.name,
                        tvtype,
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
        }
        val streamLinks = listOfLinks.distinct().toJson()
        return MovieLoadResponse(title, url, this.name, tvtype, streamLinks, poster, year, descript, null, null)
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isEmpty()) return false
        if (data == "about:blank") return false
        if (data == "[]") return false

        mapper.readValue<List<String>>(data).forEach { item ->
            if (item.isNotEmpty()) {
                val url = item.trim()
                loadExtractor(url, mainUrl, callback)
            }
        }
        return true
    }
}