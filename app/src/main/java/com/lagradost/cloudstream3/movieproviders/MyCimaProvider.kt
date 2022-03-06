package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class MyCimaProvider : MainAPI() {
    override val lang = "ar"
    override val mainUrl = "https://mycima.tv"
    override val name = "MyCima"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private fun String.getImageURL(): String? {
        return Regex("""--im(age|g):url\((.*?)\);""").find(this)?.groupValues?.getOrNull(2)
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
            .replace("مشاهدة فيلم","")
            .replace("مسلسل","")
            .replace("مترجم","")
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
        listOf("$mainUrl/search/$q", "$mainUrl/search/$q/list/series/").apmap { url ->
            val d = app.get(url).document
            if(d.select("a.hoverable.active").text().contains("الانيمي و الكرتون")) return@apmap null
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
            .replace("مشاهدة فيلم","")
            .replace("مسلسل","")
            .replace("مترجم","")
            .replace("فيلم","")
        // A bit iffy to parse twice like this, but it'll do.
        val duration =
            doc.select("ul.Terms--Content--Single-begin li").firstOrNull {
                it.text().contains("المدة")
            }?.text()?.getIntFromText()

        val synopsis = doc.select("div.StoryMovieContent").text() ?: doc.select("div.PostItemContent").text()

        val tags = doc.select("li:nth-child(3) > p > a").map { it.text() }

        val actors = doc.select("div.List--Teamwork > ul.Inner--List--Teamwork > li")?.mapNotNull {
            val name = it?.selectFirst("a > div.ActorName > span")?.text() ?: return@mapNotNull null
            val image = it.attr("style")
                ?.getImageURL()
                ?: return@mapNotNull null
            Actor(name, image)
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
                addActors(actors)
            }
        } else {
            val episodes = ArrayList<TvSeriesEpisode>()
            val seasons = doc.select("div.List--Seasons--Episodes a").map {
                it.attr("href")
            }
                if(seasons.isNotEmpty()) {
                    seasons.apmap { surl ->
                        if(surl.contains("%d9%85%d8%af%d8%a8%d9%84%d8%ac")) return@apmap
                            val seasonsite = app.get(surl).document
                            val moreButton = seasonsite.select("div.MoreEpisodes--Button")
                            val season = seasonsite.select("div.List--Seasons--Episodes a.selected").text().getIntFromText() ?: 1
                            seasonsite.select("div.Seasons--Episodes div.Episodes--Seasons--Episodes a")
                                        .apmap { episodes.add(TvSeriesEpisode(it.text(), season, it.text().getIntFromText(), it.attr("href"), null, null))}
                            if(moreButton.isNotEmpty()) {
                                val n = seasonsite.select("div.Seasons--Episodes div.Episodes--Seasons--Episodes a").size
                                val totals = seasonsite.select("div.Episodes--Seasons--Episodes a").first().text().getIntFromText()
                                arrayListOf(n, n+40, n+80, n+120, n+160, n+200, n+240, n+280, n+320, n+360)
                                    .apmap { it ->
                                        if(it > totals!!) return@apmap
                                        val ajaxURL = "$mainUrl/AjaxCenter/MoreEpisodes/${moreButton.attr("data-term")}/$it"
                                        val jsonResponse = app.get(ajaxURL)
                                        val json = parseJson<MoreEPS>(jsonResponse.text)
                                        val document = Jsoup.parse(json.output?.replace("""\""", ""))
                                        document.select("a").map { episodes.add(TvSeriesEpisode(it.text(), season, it.text().getIntFromText(), it.attr("href"), null, null)) }
                                }
                            } else return@apmap
                    }
                } else {
                    doc.select("div.Seasons--Episodes div.Episodes--Seasons--Episodes a").map {
                        episodes.add(TvSeriesEpisode(
                            it.text(),
                            doc.select("div.List--Seasons--Episodes a.selected").text().getIntFromText(),
                            it.text().getIntFromText(),
                            it.attr("href"),
                            null,
                            null
                        ))
                    }
                }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().sortedBy { it.episode }) {
                this.duration = duration
                this.posterUrl = posterUrl
                this.tags = tags
                this.year = year
                this.plot = synopsis
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
