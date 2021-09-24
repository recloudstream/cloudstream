package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder

class AllMoviesForYouProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return when {
                t.contains("series") -> TvType.TvSeries
                t.contains("movies") -> TvType.Movie
                else -> TvType.Movie
            }
        }
    }

    override val mainUrl: String
        get() = "https://allmoviesforyou.co"
    override val name: String
        get() = "AllMoviesForYou"
    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.Movie,
            TvType.TvSeries
        )

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)

        val items = document.select("ul.MovieList > li > article > a")
        val returnValue = ArrayList<SearchResponse>()
        for (item in items) {
            val href = item.attr("href")
            val title = item.selectFirst("> h2.Title").text()
            val img = fixUrl(item.selectFirst("> div.Image > figure > img").attr("data-src"))
            val type = getType(href)
            if (type == TvType.Movie) {
                returnValue.add(MovieSearchResponse(title, href, this.name, type, img, null))
            } else if (type == TvType.TvSeries) {
                returnValue.add(TvSeriesSearchResponse(title, href, this.name, type, img, null, null))
            }
        }
        return returnValue
    }

    private fun getLink(document: Document): List<String>? {
        val list = ArrayList<String>()
        Regex("iframe src=\"(.*?)\"").find(document.html())?.groupValues?.get(1)?.let {
            list.add(it)
        }
        document.select("div.OptionBx")?.forEach { element ->
            val baseElement = element.selectFirst("> a.Button")
            if (element.selectFirst("> p.AAIco-dns")?.text() == "Streamhub") {
                baseElement?.attr("href")?.let { href ->
                    list.add(href)
                }
            }
        }

        return if (list.isEmpty()) null else list
    }

    override fun load(url: String): LoadResponse {
        val type = getType(url)

        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)

        val title = document.selectFirst("h1.Title").text()
        val descipt = document.selectFirst("div.Description > p").text()
        val rating =
            document.selectFirst("div.Vote > div.post-ratings > span")?.text()?.toFloatOrNull()?.times(10)?.toInt()
        val year = document.selectFirst("span.Date")?.text()
        val duration = document.selectFirst("span.Time").text()
        val backgroundPoster = fixUrl(document.selectFirst("div.Image > figure > img").attr("src"))

        if (type == TvType.TvSeries) {
            val list = ArrayList<Pair<Int, String>>()

            document.select("main > section.SeasonBx > div > div.Title > a").forEach { element ->
                val season = element.selectFirst("> span")?.text()?.toIntOrNull()
                val href = element.attr("href")
                if (season != null && season > 0 && !href.isNullOrBlank()) {
                    list.add(Pair(season, fixUrl(href)))
                }
            }
            if (list.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodeList = ArrayList<TvSeriesEpisode>()

            for (season in list) {
                val seasonResponse = khttp.get(season.second)
                val seasonDocument = Jsoup.parse(seasonResponse.text)
                val episodes = seasonDocument.select("table > tbody > tr")
                if (episodes.isNotEmpty()) {
                    episodes.forEach { episode ->
                        val epNum = episode.selectFirst("> td > span.Num")?.text()?.toIntOrNull()
                        val poster = episode.selectFirst("> td.MvTbImg > a > img")?.attr("src")
                        val aName = episode.selectFirst("> td.MvTbTtl > a")
                        val name = aName.text()
                        val href = aName.attr("href")
                        val date = episode.selectFirst("> td.MvTbTtl > span")?.text()
                        episodeList.add(
                            TvSeriesEpisode(
                                name,
                                season.first,
                                epNum,
                                href,
                                if (poster != null) fixUrl(poster) else null,
                                date
                            )
                        )
                    }
                }
            }
            return TvSeriesLoadResponse(
                title,
                url,
                this.name,
                type,
                episodeList,
                backgroundPoster,
                year?.toIntOrNull(),
                descipt,
                null,
                null,
                rating
            )
        } else {
            val data = getLink(document)
                ?: throw ErrorLoadingException("No Links Found")

            return MovieLoadResponse(
                title,
                url,
                this.name,
                type,
                mapper.writeValueAsString(data.filter { it != "about:blank" }),
                backgroundPoster,
                year?.toIntOrNull(),
                descipt,
                null,
                rating,
                duration = duration
            )
        }
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith("$mainUrl/episode/")) {
            val response = khttp.get(data)
            getLink(Jsoup.parse(response.text))?.let { links ->
                for (link in links) {
                    if (link == data) continue
                    loadLinks(link, isCasting, subtitleCallback, callback)
                }
                return true
            }
            return false
        } else if (data.startsWith(mainUrl) && data != mainUrl) {
            val realDataUrl = URLDecoder.decode(data, "application/x-www-form-urlencoded")
            if (data.contains("trdownload")) {
                callback(ExtractorLink(this.name, this.name, realDataUrl, mainUrl, Qualities.Unknown.value))
                return true
            }
            val response = khttp.get(realDataUrl)
            Regex("<iframe.*?src=\"(.*?)\"").find(response.text)?.groupValues?.get(1)?.let { url ->
                loadExtractor(url.trimStart(), realDataUrl, callback)
            }
            return true
        } else {
            val links = mapper.readValue<List<String>>(data)
            for (link in links) {
                loadLinks(link, isCasting, subtitleCallback, callback)
            }
            return true
        }
    }
}
