package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

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
        get() = "https://allmoviesforyou.co/"
    override val name: String
        get() = "AllMoviesForYou"
    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.Movie,
        //    TvType.TvSeries
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
               // returnValue.add(TvSeriesSearchResponse(title, href, this.name, type, img, null, null))
            }
        }
        return returnValue
    }

    override fun load(url: String): LoadResponse {
        val type = getType(url)
        if(type == TvType.TvSeries) throw ErrorLoadingException("TvSeries not implemented yet")
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        val title = document.selectFirst("h1.Title").text()
        val descipt = document.selectFirst("div.Description > p").text()
        val rating =
            document.selectFirst("div.Vote > div.post-ratings > span")?.text()?.toFloatOrNull()?.times(10)?.toInt()
        val year = document.selectFirst("span.Date").text()
        val duration = document.selectFirst("span.Time").text()
        val backgroundPoster = fixUrl(document.selectFirst("div.Image > figure > img").attr("src"))

        val data = Regex("iframe src=\"(.*?)\"").find(response.text)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("No Links Found")

        return MovieLoadResponse(
            title,
            url,
            this.name,
            type,
            data,
            backgroundPoster,
            year.toIntOrNull(),
            descipt,
            null,
            rating,
            duration = duration
        )
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith(mainUrl) && data != mainUrl) {
            val response = khttp.get(data.replace("&#038;","&"))
            Regex("<iframe.*?src=\"(.*?)\"").find(response.text)?.groupValues?.get(1)?.let { url ->
                loadExtractor(url.trimStart(), data, callback)
            }
            return true
        }

        return false
    }
}