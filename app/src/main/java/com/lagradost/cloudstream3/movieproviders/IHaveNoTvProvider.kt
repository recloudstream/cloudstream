package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.net.URLEncoder

class IHaveNoTvProvider : MainAPI() {
    override var mainUrl = "https://ihavenotv.com"
    override var name = "I Have No TV"
    override val hasQuickSearch = false
    override val hasMainPage = true

    override val supportedTypes = setOf(TvType.Documentary)

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        // Uhh, I am too lazy to scrape the "latest documentaries" and "recommended documentaries",
        // so I am just scraping 3 random categories
        val allCategories = listOf(
            "astronomy",
            "brain",
            "creativity",
            "design",
            "economics",
            "environment",
            "health",
            "history",
            "lifehack",
            "math",
            "music",
            "nature",
            "people",
            "physics",
            "science",
            "technology",
            "travel"
        )

        val categories = allCategories.asSequence().shuffled().take(3)
            .toList()  // randomly get 3 categories, because there are too many

        val items = ArrayList<HomePageList>()

        categories.forEach { cat ->
            val link = "$mainUrl/category/$cat"
            val html = app.get(link).text
            val soup = Jsoup.parse(html)

            val searchResults: MutableMap<String, SearchResponse> = mutableMapOf()
            soup.select(".episodesDiv .episode").forEach { res ->
                val poster = res.selectFirst("img")?.attr("src")
                val aTag = if (res.html().contains("/series/")) {
                    res.selectFirst(".episodeMeta > a")
                } else {
                    res.selectFirst("a[href][title]")
                }
                val year = Regex("""•?\s+(\d{4})\s+•""").find(
                    res.selectFirst(".episodeMeta")!!.text()
                )?.destructured?.component1()?.toIntOrNull()

                val title = aTag!!.attr("title")
                val href = fixUrl(aTag.attr("href"))
                searchResults[href] = TvSeriesSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Documentary,//if (href.contains("/series/")) TvType.TvSeries else TvType.Movie,
                    poster,
                    year,
                    null
                )
            }
            items.add(
                HomePageList(
                    capitalizeString(cat),
                    ArrayList(searchResults.values).subList(0, 5)
                )
            ) // just 5 results per category, app crashes when they are too many
        }

        return HomePageResponse(items)
    }

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val url = """$mainUrl/search/${URLEncoder.encode(query, "UTF-8")}"""
        val response = app.get(url).text
        val soup = Jsoup.parse(response)

        val searchResults: MutableMap<String, SearchResponse> = mutableMapOf()

        soup.select(".episodesDiv .episode").forEach { res ->
            val poster = res.selectFirst("img")?.attr("src")
            val aTag = if (res.html().contains("/series/")) {
                res.selectFirst(".episodeMeta > a")
            } else {
                res.selectFirst("a[href][title]")
            }
            val year =
                Regex("""•?\s+(\d{4})\s+•""").find(
                    res.selectFirst(".episodeMeta")!!.text()
                )?.destructured?.component1()
                    ?.toIntOrNull()

            val title = aTag!!.attr("title")
            val href = fixUrl(aTag.attr("href"))
            searchResults[href] = TvSeriesSearchResponse(
                title,
                href,
                this.name,
                TvType.Documentary, //if (href.contains("/series/")) TvType.TvSeries else TvType.Movie,
                poster,
                year,
                null
            )
        }

        return ArrayList(searchResults.values)
    }

    override suspend fun load(url: String): LoadResponse {
        val isSeries = url.contains("/series/")
        val html = app.get(url).text
        val soup = Jsoup.parse(html)

        val container = soup.selectFirst(".container-fluid h1")?.parent()
        val title = if (isSeries) {
            container?.selectFirst("h1")?.text()?.split("•")?.firstOrNull().toString()
        } else soup.selectFirst(".videoDetails")!!.selectFirst("strong")?.text().toString()
        val description = if (isSeries) {
            container?.selectFirst("p")?.text()
        } else {
            soup.selectFirst(".videoDetails > p")?.text()
        }

        var year: Int? = null
        val categories: MutableSet<String> = mutableSetOf()

        val episodes = if (isSeries) {
            container?.select(".episode")?.map { ep ->
                val thumb = ep.selectFirst("img")!!.attr("src")

                val epLink = fixUrl(ep.selectFirst("a[title]")!!.attr("href"))
                val (season, epNum) = if (ep.selectFirst(".episodeMeta > strong") != null &&
                    ep.selectFirst(".episodeMeta > strong")!!.html().contains("S")
                ) {
                    val split = ep.selectFirst(".episodeMeta > strong")?.text()?.split("E")
                    Pair(
                        split?.firstOrNull()?.replace("S", "")?.toIntOrNull(),
                        split?.get(1)?.toIntOrNull()
                    )
                } else Pair<Int?, Int?>(null, null)

                year = Regex("""•?\s+(\d{4})\s+•""").find(
                    ep.selectFirst(".episodeMeta")!!.text()
                )?.destructured?.component1()?.toIntOrNull()

                categories.addAll(
                    ep.select(".episodeMeta > a[href*=\"/category/\"]").map { it.text().trim() })

                newEpisode(epLink) {
                    this.name = ep.selectFirst("a[title]")!!.attr("title")
                    this.season = season
                    this.episode = epNum
                    this.posterUrl = thumb
                    this.description = ep.selectFirst(".episodeSynopsis")?.text()
                }
            }
        } else {
            listOf(MovieLoadResponse(
                title,
                url,
                this.name,
                TvType.Movie,
                url,
                soup.selectFirst("[rel=\"image_src\"]")!!.attr("href"),
                Regex("""•?\s+(\d{4})\s+•""").find(
                    soup.selectFirst(".videoDetails")!!.text()
                )?.destructured?.component1()?.toIntOrNull(),
                description,
                null,
                soup.selectFirst(".videoDetails")!!.select("a[href*=\"/category/\"]")
                    .map { it.text().trim() }
            ))
        }

        val poster = episodes?.firstOrNull().let {
            if (isSeries && it != null) (it as Episode).posterUrl
            else null
        }

        return if (isSeries) TvSeriesLoadResponse(
            title,
            url,
            this.name,
            TvType.TvSeries,
            episodes!!.map { it as Episode },
            poster,
            year,
            description,
            null,
            null,
            categories.toList()
        ) else (episodes?.first() as MovieLoadResponse)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data).text
        val soup = Jsoup.parse(html)

        val iframe = soup.selectFirst("#videoWrap iframe")
        if (iframe != null) {
            loadExtractor(iframe.attr("src"), null, subtitleCallback, callback)
        }
        return true
    }
}
