package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

// referer = https://trailers.to, USERAGENT ALSO REQUIRED
class TrailersToProvider : MainAPI() {
    override val mainUrl: String
        get() = "https://trailers.to"
    override val name: String
        get() = "Trailers.to"

    override val hasQuickSearch: Boolean
        get() = true

    override val hasMainPage: Boolean
        get() = true

    override val hasChromecastSupport: Boolean
        get() = false

    override fun getMainPage(): HomePageResponse? {
        val response = khttp.get(mainUrl)
        val document = Jsoup.parse(response.text)
        val returnList = ArrayList<HomePageList>()
        val docs = document.select("section.section > div.container")
        for (doc in docs) {
            val epList = doc.selectFirst("> div.owl-carousel") ?: continue
            val title = doc.selectFirst("> div.text-center > h2").text()
            val list = epList.select("> div.item > div.box-nina")
            val isMovieType = title.contains("Movie")
            val currentList = list.mapNotNull { head ->
                val hrefItem = head.selectFirst("> div.box-nina-media > a")
                val href = fixUrl(hrefItem.attr("href"))
                val img = hrefItem.selectFirst("> img")
                val posterUrl = img.attr("src")
                val name = img.attr("alt")
                return@mapNotNull if (isMovieType) MovieSearchResponse(
                    name,
                    href,
                    this.name,
                    TvType.Movie,
                    posterUrl,
                    null
                ) else TvSeriesSearchResponse(
                    name,
                    href,
                    this.name,
                    TvType.TvSeries,
                    posterUrl,
                    null, null
                )
            }
            if (currentList.isNotEmpty()) {
                returnList.add(HomePageList(title, currentList))
            }
        }
        if(returnList.size <= 0) return null

        return HomePageResponse(returnList)
        //section.section > div.container > div.owl-carousel
    }

    override fun quickSearch(query: String): ArrayList<SearchResponse> {
        val url = "$mainUrl/en/quick-search?q=$query"
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        val items = document.select("div.group-post-minimal > a.post-minimal")
        if (items.isNullOrEmpty()) return ArrayList()

        val returnValue = ArrayList<SearchResponse>()
        for (item in items) {
            val href = fixUrl(item.attr("href"))
            val poster = item.selectFirst("> div.post-minimal-media > img").attr("src")
            val header = item.selectFirst("> div.post-minimal-main")
            val name = header.selectFirst("> span.link-black").text()
            val info = header.select("> p")
            val year = info?.get(1)?.text()?.toIntOrNull()
            val isTvShow = href.contains("/tvshow/")

            returnValue.add(
                if (isTvShow) {
                    TvSeriesSearchResponse(name, href, this.name, TvType.TvSeries, poster, year, null)
                } else {
                    MovieSearchResponse(name, href, this.name, TvType.Movie, poster, year)
                }
            )
        }
        return returnValue
    }

    override fun search(query: String): ArrayList<SearchResponse> {
        val url = "$mainUrl/en/popular/movies-tvshows-collections?q=$query"
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        val items = document.select("div.col-lg-8 > article.list-item")
        if (items.isNullOrEmpty()) return ArrayList()
        val returnValue = ArrayList<SearchResponse>()
        for (item in items) {
            val poster = item.selectFirst("> div.tour-modern-media > a.tour-modern-figure > img").attr("src")
            val infoDiv = item.selectFirst("> div.tour-modern-main")
            val nameHeader = infoDiv.select("> h5.tour-modern-title > a").last()
            val name = nameHeader.text()
            val href = fixUrl(nameHeader.attr("href"))
            val year = infoDiv.selectFirst("> div > span.small-text")?.text()?.takeLast(4)?.toIntOrNull()
            val isTvShow = href.contains("/tvshow/")

            returnValue.add(
                if (isTvShow) {
                    TvSeriesSearchResponse(name, href, this.name, TvType.TvSeries, poster, year, null)
                } else {
                    MovieSearchResponse(name, href, this.name, TvType.Movie, poster, year)
                }
            )
        }
        return returnValue
    }

    private fun loadLink(data: String, callback: (ExtractorLink) -> Unit): Boolean {
        val response = khttp.get(data)
        val url = "<source src='(.*?)'".toRegex().find(response.text)?.groupValues?.get(1)
        if (url != null) {
            callback.invoke(ExtractorLink(this.name, this.name, url, mainUrl, Qualities.Unknown.value, false))
            return true
        }
        return false
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (isCasting) return false
        val isMovie = data.contains("/web-sources/")
        if (isMovie) {
            return loadLink(data, callback)
        } else if (data.contains("/episode/")) {
            val response = khttp.get(data)
            val document = Jsoup.parse(response.text)
            val subData = fixUrl(document.selectFirst("content").attr("data-url") ?: return false)
            if (subData.contains("/web-sources/")) {
                return loadLink(subData, callback)
            }
        }
        return false
    }

    override fun load(slug: String): LoadResponse? {
        val response = khttp.get(slug)
        val document = Jsoup.parse(response.text)
        val metaInfo = document.select("div.post-info-meta > ul.post-info-meta-list > li")
        val year = metaInfo?.get(0)?.selectFirst("> span.small-text")?.text()?.takeLast(4)?.toIntOrNull()
        val rating = parseRating(metaInfo?.get(1)?.selectFirst("> span.small-text")?.text()?.replace("/ 10", ""))
        val duration = metaInfo?.get(2)?.selectFirst("> span.small-text")?.text()
        val imdbUrl = metaInfo?.get(3)?.selectFirst("> a")?.attr("href")
        val trailer = metaInfo?.get(4)?.selectFirst("> a")?.attr("href")
        val poster = document.selectFirst("div.slider-image > a > img").attr("src")
        val descriptHeader = document.selectFirst("article.post-info")
        var title = document.selectFirst("h2.breadcrumbs-custom-title > a").text()
        title = title.substring(0, title.length - 6) // REMOVE YEAR

        val descript = descriptHeader.select("> div > p").text()
        val table = descriptHeader.select("> table.post-info-table > tbody > tr > td")
        var generes: List<String>? = null
        for (i in 0 until table.size / 2) {
            val header = table[i * 2].text()
            val info = table[i * 2 + 1]
            when (header) {
                "Genre" -> generes = info.text().split(",")
            }
        }
        val tags = if (generes == null) null else ArrayList(generes)

        val isTvShow = slug.contains("/tvshow/")
        if (isTvShow) {
            val episodes = document.select("article.tour-modern") ?: return null
            val parsedEpisodes = episodes.map { item ->
                val epPoster = item.selectFirst("> div.tour-modern-media > a.tour-modern-figure > img").attr("src")
                val main = item.selectFirst("> div.tour-modern-main")
                val titleHeader = main.selectFirst("> h5.tour-modern-title > a")
                val titleName = titleHeader.text()
                val href = fixUrl(titleHeader.attr("href"))
                val gValues = ".*?Season ([0-9]*).*Episode ([0-9]*): (.*)".toRegex().find(titleName)?.groupValues
                val season = gValues?.get(1)?.toIntOrNull()
                val episode = gValues?.get(2)?.toIntOrNull()
                val epName = gValues?.get(3)
                val infoHeaders = main.select("> div > span.small-text")
                val date = infoHeaders?.get(0)?.text()
                val ratingText = infoHeaders?.get(1)?.text()?.replace("/ 10", "")
                val epRating = if (ratingText == null) null else parseRating(ratingText)
                val epDescript = main.selectFirst("> p")?.text()
                TvSeriesEpisode(epName, season, episode, href, epPoster, date, epRating, epDescript)
            }
            return TvSeriesLoadResponse(
                title,
                slug,
                this.name,
                TvType.TvSeries,
                ArrayList(parsedEpisodes),
                poster,
                year,
                descript,
                null,
                imdbUrl,
                rating,
                tags,
                duration,
                trailer
            )
        } else {
            val data = fixUrl(document.selectFirst("content").attr("data-url") ?: return null)
            return MovieLoadResponse(
                title,
                slug,
                this.name,
                TvType.Movie,
                data,
                poster,
                year,
                descript,
                imdbUrl,
                rating,
                tags,
                duration,
                trailer
            )
        }
    }
}