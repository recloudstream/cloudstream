package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.text
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
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

    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.Movie,
            TvType.TvSeries,
        )

    override val vpnStatus: VPNStatus
        get() = VPNStatus.MightBeNeeded

    override fun getMainPage(): HomePageResponse? {
        val response = app.get(mainUrl).text
        val document = Jsoup.parse(response)
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
        if (returnList.size <= 0) return null

        return HomePageResponse(returnList)
        //section.section > div.container > div.owl-carousel
    }

    override fun quickSearch(query: String): List<SearchResponse> {
        val url = "$mainUrl/en/quick-search?q=$query"
        val response = app.get(url).text
        val document = Jsoup.parse(response)
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

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/en/popular/movies-tvshows-collections?q=$query"
        val response = app.get(url).text
        val document = Jsoup.parse(response)
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

    private fun loadLink(
        data: String,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val response = app.get(data).text
        val url = "<source src='(.*?)'".toRegex().find(response)?.groupValues?.get(1)
        if (url != null) {
            callback.invoke(ExtractorLink(this.name, this.name, url, mainUrl, Qualities.Unknown.value, false))
        }
        return url != null
    }

    private fun loadSubs(url: String, subtitleCallback: (SubtitleFile) -> Unit) {
        if (url.isEmpty()) return

        val response = app.get(fixUrl(url)).text
        val document = Jsoup.parse(response)

        val items = document.select("div.list-group > a.list-group-item")
        for (item in items) {
            val hash = item.attr("hash") ?: continue
            val languageCode = item.attr("languagecode") ?: continue
            if (hash.isEmpty()) continue
            if (languageCode.isEmpty()) continue

            subtitleCallback.invoke(
                SubtitleFile(
                    SubtitleHelper.fromTwoLettersToLanguage(languageCode) ?: languageCode,
                    "$mainUrl/subtitles/$hash"
                )
            )
        }
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (isCasting) return false
        val pairData = mapper.readValue<Pair<String, String>>(data)
        val url = pairData.second

        val isMovie = url.contains("/web-sources/")
        if (isMovie) {
            val isSucc = loadLink(url, callback)
            val subUrl = pairData.first
            loadSubs(subUrl, subtitleCallback)

            return isSucc
        } else if (url.contains("/episode/")) {
            val response = app.get(url, params = mapOf("preview" to "1")).text
            val document = Jsoup.parse(response)
            // val qSub = document.select("subtitle-content")
            val subUrl = document.select("subtitle-content")?.attr("data-url") ?: ""

            val subData = fixUrl(document.selectFirst("content").attr("data-url") ?: return false)
            val isSucc = if (subData.contains("/web-sources/")) {
                loadLink(subData, callback)
            } else false

            loadSubs(subUrl, subtitleCallback)
            return isSucc
        }
        return false
    }

    override fun load(url: String): LoadResponse {
        val response = app.get(if (url.endsWith("?preview=1")) url else "$url?preview=1").text
        val document = Jsoup.parse(response)
        var title = document?.selectFirst("h2.breadcrumbs-custom-title > a")?.text()
            ?: throw ErrorLoadingException("Service might be unavailable")

        val metaInfo = document.select("div.post-info-meta > ul.post-info-meta-list > li")
        val year = metaInfo?.get(0)?.selectFirst("> span.small-text")?.text()?.takeLast(4)?.toIntOrNull()
        val rating = parseRating(metaInfo?.get(1)?.selectFirst("> span.small-text")?.text()?.replace("/ 10", ""))
        val duration = metaInfo?.get(2)?.selectFirst("> span.small-text")?.text()
        val imdbUrl = metaInfo?.get(3)?.selectFirst("> a")?.attr("href")
        val trailer = metaInfo?.get(4)?.selectFirst("> a")?.attr("href")
        val poster = document.selectFirst("div.slider-image > a > img").attr("src")
        val descriptHeader = document.selectFirst("article.post-info")
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

        val isTvShow = url.contains("/tvshow/")
        if (isTvShow) {
            val episodes = document.select("#seasons-accordion .card-body > .tour-modern")
                ?: throw ErrorLoadingException("No Episodes found")
            val parsedEpisodes = episodes.withIndex().map { (index, item) ->
                val epPoster = item.selectFirst("img").attr("src")
                val main = item.selectFirst(".tour-modern-main")
                val titleHeader = main.selectFirst("a")
                val titleName = titleHeader.text()
                val href = fixUrl(titleHeader.attr("href"))
                val gValues =
                    Regex(""".*?[\w\s]+ ([0-9]+)(?::[\w\s]+)?\s-\s(?:Episode )?([0-9]+)?(?:: )?(.*)""").find(titleName)?.destructured
                val season = gValues?.component1()?.toIntOrNull()
                var episode = gValues?.component2()?.toIntOrNull()
                if (episode == null) {
                    episode = index + 1
                }
                val epName =
                    if (gValues?.component3()?.isNotEmpty() == true) gValues.component3() else "Episode $episode"
                val infoHeaders = main.select("span.small-text")
                val date = infoHeaders?.get(0)?.text()
                val ratingText = infoHeaders?.get(1)?.text()?.replace("/ 10", "")
                val epRating = if (ratingText == null) null else parseRating(ratingText)
                val epDescript = main.selectFirst("p")?.text()

                TvSeriesEpisode(
                    epName,
                    season,
                    episode,
                    mapper.writeValueAsString(Pair("", href)),
                    epPoster,
                    date,
                    epRating,
                    epDescript
                )
            }
            return TvSeriesLoadResponse(
                title,
                url,
                this.name,
                TvType.TvSeries,
                ArrayList(parsedEpisodes),
                poster,
                year,
                descript,
                null,
                imdbUrlToIdNullable(imdbUrl),
                rating,
                tags,
                duration,
                trailer
            )
        } else {

            //https://trailers.to/en/subtitle-details/2086212/jungle-cruise-2021?imdbId=tt0870154&season=0&episode=0
            //https://trailers.to/en/movie/2086212/jungle-cruise-2021

            val subUrl = if (imdbUrl != null) {
                val imdbId = imdbUrlToId(imdbUrl)
                url.replace("/movie/", "/subtitle-details/") + "?imdbId=$imdbId&season=0&episode=0"
            } else ""

            val data = mapper.writeValueAsString(
                Pair(
                    subUrl,
                    fixUrl(
                        document.selectFirst("content")?.attr("data-url")
                            ?: throw ErrorLoadingException("Link not found")
                    )
                )
            )
            return MovieLoadResponse(
                title,
                url,
                this.name,
                TvType.Movie,
                data,
                poster,
                year,
                descript,
                imdbUrlToIdNullable(imdbUrl),
                rating,
                tags,
                duration,
                trailer
            )
        }
    }
}
