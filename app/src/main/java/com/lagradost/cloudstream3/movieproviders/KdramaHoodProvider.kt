package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.XStreamCdn
import com.lagradost.cloudstream3.extractors.helper.AsianEmbedHelper
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class KdramaHoodProvider : MainAPI() {
    override var mainUrl = "https://kdramahood.com"
    override var name = "KDramaHood"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    private data class ResponseDatas(
        @JsonProperty("label") val label: String,
        @JsonProperty("file") val file: String
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/home2").document
        val home = ArrayList<HomePageList>()

        // Hardcoded homepage cause of site implementation
        // Recently added
        val recentlyInner = doc.selectFirst("div.peliculas")
        val recentlyAddedTitle = recentlyInner!!.selectFirst("h1")?.text() ?: "Recently Added"
        val recentlyAdded = recentlyInner.select("div.item_2.items > div.fit.item").mapNotNull {
            val innerA = it.select("div.image > a") ?: return@mapNotNull null
            val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            val image = fixUrlNull(innerA.select("img").attr("src"))

            val innerData = it.selectFirst("div.data")
            val title = innerData!!.selectFirst("h1")?.text() ?: return@mapNotNull null
            val year = try {
                val yearText = innerData.selectFirst("span.titulo_o")
                    ?.text()?.takeLast(11)?.trim()?.take(4) ?: ""
                //Log.i(this.name, "Result => (yearText) $yearText")
                val rex = Regex("\\((\\d+)")
                //Log.i(this.name, "Result => (rex value) ${rex.find(yearText)?.value}")
                rex.find(yearText)?.value?.toIntOrNull()
            } catch (e: Exception) {
                null
            }

            MovieSearchResponse(
                name = title,
                url = link,
                apiName = this.name,
                type = TvType.TvSeries,
                posterUrl = image,
                year = year
            )
        }.distinctBy { it.url } ?: listOf()
        home.add(HomePageList(recentlyAddedTitle, recentlyAdded))
        return HomePageResponse(home.filter { it.list.isNotEmpty() })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val html = app.get(url).document
        val document = html.getElementsByTag("body")
            .select("div.item_1.items > div.item") ?: return listOf()

        return document.mapNotNull {
            if (it == null) {
                return@mapNotNull null
            }
            val innerA = it.selectFirst("div.boxinfo > a") ?: return@mapNotNull null
            val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            val title = innerA.select("span.tt")?.text() ?: return@mapNotNull null

            val year = it.selectFirst("span.year")?.text()?.toIntOrNull()
            val image = fixUrlNull(it.selectFirst("div.image > img")?.attr("src"))

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
        val inner = doc.selectFirst("div.central")

        // Video details
        val title = inner?.selectFirst("h1")?.text() ?: ""
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content")) ?: ""
        //Log.i(this.name, "Result => (poster) ${poster}")
        val info = inner!!.selectFirst("div#info")
        val descript = inner.selectFirst("div.contenidotv > div > p")?.text()
        val year = try {
            val startLink = "https://kdramahood.com/drama-release-year/"
            var res: Int? = null
            info?.select("div.metadatac")?.forEach {
                if (res != null) {
                    return@forEach
                }
                if (it == null) {
                    return@forEach
                }
                val yearLink = it.select("a").attr("href") ?: return@forEach
                if (yearLink.startsWith(startLink)) {
                    res = yearLink.substring(startLink.length).replace("/", "").toIntOrNull()
                }
            }
            res
        } catch (e: Exception) {
            null
        }

        val recs = doc.select("div.sidebartv > div.tvitemrel").mapNotNull {
            val a = it?.select("a") ?: return@mapNotNull null
            val aUrl = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val aImg = a.select("img")
            val aCover = fixUrlNull(aImg.attr("src")) ?: fixUrlNull(aImg.attr("data-src"))
            val aNameYear = a.select("div.datatvrel") ?: return@mapNotNull null
            val aName = aNameYear.select("h4").text() ?: aImg.attr("alt") ?: return@mapNotNull null
            val aYear = aName.trim().takeLast(5).removeSuffix(")").toIntOrNull()
            MovieSearchResponse(
                url = aUrl,
                name = aName,
                type = TvType.Movie,
                posterUrl = aCover,
                year = aYear,
                apiName = this.name
            )
        }

        // Episodes Links
        val episodeList = inner.select("ul.episodios > li")?.mapNotNull { ep ->
            //Log.i(this.name, "Result => (ep) ${ep}")
            val listOfLinks = mutableListOf<String>()
            val count = ep.select("div.numerando")?.text()?.toIntOrNull() ?: 0
            val innerA = ep.select("div.episodiotitle > a") ?: return@mapNotNull null
            //Log.i(this.name, "Result => (innerA) ${innerA}")
            val epLink = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            //Log.i(this.name, "Result => (epLink) ${epLink}")
            if (epLink.isNotBlank()) {
                // Fetch video links
                val epVidLinkEl = app.get(epLink, referer = mainUrl).document
                val epLinksContent = epVidLinkEl.selectFirst("div.player_nav > script")?.html()
                    ?.replace("ifr_target.src =", "<div>")
                    ?.replace("';", "</div>")
                //Log.i(this.name, "Result => (epLinksContent) $epLinksContent")
                if (!epLinksContent.isNullOrEmpty()) {
                    //Log.i(this.name, "Result => (epLinksContent) ${Jsoup.parse(epLinksContent)?.select("div")}")
                    Jsoup.parse(epLinksContent)?.select("div")?.forEach { em ->
                        val href = em?.html()?.trim()?.removePrefix("'") ?: return@forEach
                        //Log.i(this.name, "Result => (ep#$count link) $href")
                        if (href.isNotBlank()) {
                            listOfLinks.add(fixUrl(href))
                        }
                    }
                }
                //Fetch default source and subtitles
                epVidLinkEl.select("div.embed2")?.forEach { defsrc ->
                    if (defsrc == null) {
                        return@forEach
                    }
                    val scriptstring = defsrc.toString()
                    if (scriptstring.contains("sources: [{")) {
                        "(?<=playerInstance2.setup\\()([\\s\\S]*?)(?=\\);)".toRegex()
                            .find(scriptstring)?.value?.let { itemjs ->
                            listOfLinks.add("$mainUrl$itemjs")
                        }
                    }
                }
            }
            Episode(
                name = null,
                season = null,
                episode = count,
                data = listOfLinks.distinct().toJson(),
                posterUrl = poster,
                date = null
            )
        }

        //If there's only 1 episode, consider it a movie.
        if (episodeList?.size == 1) {
            return MovieLoadResponse(
                name = title,
                url = url,
                apiName = this.name,
                type = TvType.Movie,
                dataUrl = episodeList[0].data,
                posterUrl = poster,
                year = year,
                plot = descript,
                recommendations = recs
            )
        }
        return TvSeriesLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            type = TvType.AsianDrama,
            episodes = episodeList?.reversed() ?: emptyList(),
            posterUrl = poster,
            year = year,
            plot = descript,
            recommendations = recs
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var count = 0
        parseJson<List<String>>(data).apmap { item ->
            if (item.isNotBlank()) {
                count++
                if (item.startsWith(mainUrl)) {
                    val text = item.substring(mainUrl.length)
                    //Log.i(this.name, "Result => (text) $text")
                    //Find video files
                    try {
                        "(?<=sources: )([\\s\\S]*?)(?<=])".toRegex().find(text)?.value?.let { vid ->
                            parseJson<List<ResponseDatas>>(vid).forEach { src ->
                                //Log.i(this.name, "Result => (src) ${src.toJson()}")
                                callback(
                                    ExtractorLink(
                                        name = name,
                                        url = src.file,
                                        quality = getQualityFromName(src.label),
                                        referer = mainUrl,
                                        source = name
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        logError(e)
                    }
                    //Find subtitles
                    try {
                        "(?<=tracks: )([\\s\\S]*?)(?<=])".toRegex().find(text)?.value?.let { sub ->
                            val subtext = sub.replace("file:", "\"file\":")
                                .replace("label:", "\"label\":")
                                .replace("kind:", "\"kind\":")
                            parseJson<List<ResponseDatas>>(subtext).forEach { src ->
                                //Log.i(this.name, "Result => (sub) ${src.toJson()}")
                                subtitleCallback(
                                    SubtitleFile(
                                        lang = src.label,
                                        url = src.file
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        logError(e)
                    }

                } else {
                    val url = fixUrl(item.trim())
                    //Log.i(this.name, "Result => (url) $url")
                    when {
                        url.startsWith("https://asianembed.io") -> {
                            AsianEmbedHelper.getUrls(url, subtitleCallback, callback)
                        }
                        url.startsWith("https://embedsito.com") -> {
                            val extractor = XStreamCdn()
                            extractor.domainUrl = "embedsito.com"
                            extractor.getUrl(url).forEach { link ->
                                callback.invoke(link)
                            }
                        }
                        else -> {
                            loadExtractor(url, mainUrl, subtitleCallback, callback)
                        }
                    }
                }
            }
        }
        return count > 0
    }
}