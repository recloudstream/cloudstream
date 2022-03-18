package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class MyCimaProvider : MainAPI() {
    override val lang = "ar"
    override var mainUrl = "https://mycima.tv"
    override var name = "MyCima"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    private fun String.getImageURL(): String? {
        return this.replace("--im(age|g):url\\(|\\);".toRegex(), "")
    }

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("div.Thumb--GridItem a")?.attr("href") ?: return null
        val posterUrl = select("span.BG--GridItem")?.attr("data-lazy-style")
            ?.getImageURL()
        val year = select("div.GridItem span.year")?.text()
        val title = select("div.Thumb--GridItem strong").text()
            .replace("$year", "")
            .replace("مشاهدة|فيلم|مسلسل|مترجم".toRegex(), "")
            .replace("( نسخة مدبلجة )"," ( نسخة مدبلجة ) ")
        // If you need to differentiate use the url.
        return MovieSearchResponse(
            title,
            url,
            this@MyCimaProvider.name,
            TvType.TvSeries,
            posterUrl,
            year?.getIntFromText(),
            null,
        )
    }

    override suspend fun getMainPage(): HomePageResponse {
        // Title, Url
        val moviesUrl = listOf(
            "Movies" to "$mainUrl/movies/page/"+(0..25).random(),
            "Series" to "$mainUrl/seriestv/new/page/"+(0..25).random()
        )
        val pages = moviesUrl.apmap {
            val doc = app.get(it.second).document
            val list = doc.select("div.Grid--MycimaPosts div.GridItem").mapNotNull { element ->
                element.toSearchResponse()
            }
            HomePageList(it.first, list)
        }.sortedBy { it.name }
        return HomePageResponse(pages)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ","%20")
        val result = arrayListOf<SearchResponse>()
        listOf("$mainUrl/search/$q",
            "$mainUrl/search/$q/list/series/",
            "$mainUrl/search/$q/list/anime/").apmap { url ->
            val d = app.get(url).document
            d.select("div.Grid--MycimaPosts div.GridItem").mapNotNull {
                if(it.text().contains("اعلان")) return@mapNotNull null
                    it.toSearchResponse()?.let { it1 -> result.add(it1) }
                }
        }
        return result.distinct().sortedBy { it.name }
    }

    data class MoreEPS (
        val output: String
    )

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = doc.select("ol li:nth-child(3)").text().contains("افلام")
        val posterUrl =
            doc.select("mycima.separated--top")?.attr("data-lazy-style")?.getImageURL()
                ?.ifEmpty { doc.select("meta[itemprop=\"thumbnailUrl\"]")?.attr("content") }
                ?.ifEmpty { doc.select("mycima.separated--top")?.attr("style")?.getImageURL() }
        val year = doc.select("div.Title--Content--Single-begin h1 a.unline")?.text()?.getIntFromText()
        val title = doc.select("div.Title--Content--Single-begin h1").text()
            .replace("($year)", "")
            .replace("مشاهدة|فيلم|مسلسل|مترجم|انمي".toRegex(), "")
        // A bit iffy to parse twice like this, but it'll do.
        val duration =
            doc.select("ul.Terms--Content--Single-begin li").firstOrNull {
                it.text().contains("المدة")
            }?.text()?.getIntFromText()

        val synopsis = doc.select("div.StoryMovieContent").text().ifEmpty { doc.select("div.PostItemContent").text() }

        val tags = doc.select("li:nth-child(3) > p > a").map { it.text() }

        val actors = doc.select("div.List--Teamwork > ul.Inner--List--Teamwork > li")?.mapNotNull {
            val name = it?.selectFirst("a > div.ActorName > span")?.text() ?: return@mapNotNull null
            val image = it.attr("style")
                ?.getImageURL()
                ?: return@mapNotNull null
            Actor(name, image)
        }
        val recommendations = doc.select("div.Grid--MycimaPosts div.GridItem")?.mapNotNull { element ->
            element.toSearchResponse()
        }

        return if (isMovie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.tags = tags
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
            }
        } else {
            val episodes = ArrayList<TvSeriesEpisode>()
            val seasons = doc.select("div.List--Seasons--Episodes a").not(".selected").map {
                it.attr("href")
            }
            val moreButton = doc.select("div.MoreEpisodes--Button")
            val season = doc.select("div.List--Seasons--Episodes a.selected").text().getIntFromText()
            doc.select("div.Seasons--Episodes div.Episodes--Seasons--Episodes a")
                .apmap { episodes.add(TvSeriesEpisode(it.text(), season, it.text().getIntFromText(), it.attr("href"), null, null))}
            if(moreButton.isNotEmpty()) {
                val n = doc.select("div.Seasons--Episodes div.Episodes--Seasons--Episodes a").size
                val totals = doc.select("div.Episodes--Seasons--Episodes a").first().text().getIntFromText()
                val mEPS = arrayListOf(n, n+40, n+80, n+120, n+160, n+200, n+240, n+280, n+320, n+360, n+400, n+440, n+480, n+520, n+660, n+700, n+740, n+780, n+820, n+860, n+900, n+940, n+980, n+1020, n+1060, n+1100, n+1140, n+1180, n+1220, totals)
                mEPS.apmap { it ->
                        if (it != null) {
                            if(it > totals!!) return@apmap
                            val ajaxURL = "$mainUrl/AjaxCenter/MoreEpisodes/${moreButton.attr("data-term")}/$it"
                            val jsonResponse = app.get(ajaxURL)
                            val json = parseJson<MoreEPS>(jsonResponse.text)
                            val document = Jsoup.parse(json.output?.replace("""\""", ""))
                            document.select("a").map { episodes.add(TvSeriesEpisode(it.text(), season, it.text().getIntFromText(), it.attr("href"), null, null)) }
                        }
                    }
            }
            if(seasons.isNotEmpty()) {
                    seasons.apmap { surl ->
                        if(surl.contains("%d9%85%d8%af%d8%a8%d9%84%d8%ac")) return@apmap
                            val seasonsite = app.get(surl).document
                            val fmoreButton = seasonsite.select("div.MoreEpisodes--Button")
                            val fseason = seasonsite.select("div.List--Seasons--Episodes a.selected").text().getIntFromText() ?: 1
                            seasonsite.select("div.Seasons--Episodes div.Episodes--Seasons--Episodes a")
                                        .apmap { episodes.add(TvSeriesEpisode(it.text(), fseason, it.text().getIntFromText(), it.attr("href"), null, null))}
                            if(fmoreButton.isNotEmpty()) {
                                val n = seasonsite.select("div.Seasons--Episodes div.Episodes--Seasons--Episodes a").size
                                val totals = seasonsite.select("div.Episodes--Seasons--Episodes a").first().text().getIntFromText()
                                val mEPS = arrayListOf(n, n+40, n+80, n+120, n+160, n+200, n+240, n+280, n+320, n+360, n+400, n+440, n+480, n+520, n+660, n+700, n+740, n+780, n+820, n+860, n+900, n+940, n+980, n+1020, n+1060, n+1100, n+1140, n+1180, n+1220, totals)
                                mEPS.apmap { it ->
                                        if (it != null) {
                                            if(it > totals!!) return@apmap
                                            val ajaxURL = "$mainUrl/AjaxCenter/MoreEpisodes/${fmoreButton.attr("data-term")}/$it"
                                            val jsonResponse = app.get(ajaxURL)
                                            val json = parseJson<MoreEPS>(jsonResponse.text)
                                            val document = Jsoup.parse(json.output?.replace("""\""", ""))
                                            document.select("a").map { episodes.add(TvSeriesEpisode(it.text(), fseason, it.text().getIntFromText(), it.attr("href"), null, null)) }
                                        }
                                }
                            } else return@apmap
                    }
                }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().sortedBy { it.episode }) {
                this.duration = duration
                this.posterUrl = posterUrl
                this.tags = tags
                this.year = year
                this.plot = synopsis
                this.recommendations = recommendations
                addActors(actors)
            }
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document
            .select("ul.List--Download--Mycima--Single:nth-child(2) li").map {
            it.select("a").map { linkElement ->
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name + " - ${linkElement.select("resolution").text().getIntFromText()}p",
                        linkElement.attr("href"),
                        this.mainUrl,
                        2
                    )
                )
            }
        }.flatten()
        return true
    }
}
