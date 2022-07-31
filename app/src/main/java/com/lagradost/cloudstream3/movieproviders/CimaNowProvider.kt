package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class CimaNowProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://cimanow.cc"
    override var name = "CimaNow"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = this.attr("href")
        val posterUrl = select("img")?.attr("data-src")
        var title = select("li[aria-label=\"title\"]").html().replace(" <em>.*|\\\\n".toRegex(), "").replace("&nbsp;", "")
        val year = select("li[aria-label=\"year\"]").text().toIntOrNull()
        val tvType = if (url.contains("فيلم|مسرحية|حفلات".toRegex())) TvType.Movie else TvType.TvSeries
        val quality = select("li[aria-label=\"ribbon\"]").first()?.text()?.replace(" |-|1080|720".toRegex(), "")
        val dubEl = select("li[aria-label=\"ribbon\"]:nth-child(2)").isNotEmpty()
        val dubStatus = if(dubEl) select("li[aria-label=\"ribbon\"]:nth-child(2)").text().contains("مدبلج")
        else select("li[aria-label=\"ribbon\"]:nth-child(1)").text().contains("مدبلج")
        if(dubStatus) title = "$title (مدبلج)"
        return MovieSearchResponse(
            "$title ${select("li[aria-label=\"ribbon\"]:contains(الموسم)").text()}",
            url,
            this@CimaNowProvider.name,
            tvType,
            posterUrl,
            year,
            null,
            quality = getQualityFromString(quality)
        )
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {

        val doc = app.get("$mainUrl/home", headers = mapOf("user-agent" to "MONKE")).document
        val pages = doc.select("section").not("section:contains(أختر وجهتك المفضلة)").not("section:contains(تم اضافته حديثاً)").apmap {
            val name = it.select("span").html().replace("<em>.*| <i c.*".toRegex(), "")
            val list = it.select("a").mapNotNull {
                if(it.attr("href").contains("$mainUrl/category/|$mainUrl/الاكثر-مشاهدة/".toRegex())) return@mapNotNull null
                it.toSearchResponse()
            }
            HomePageList(name, list)
        }
        return HomePageResponse(pages)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val result = arrayListOf<SearchResponse>()
        val doc = app.get("$mainUrl/page/1/?s=$query").document
        val paginationElement = doc.select("ul[aria-label=\"pagination\"]")
        doc.select("section article a").map {
            val postUrl = it.attr("href")
            if(it.select("li[aria-label=\"episode\"]").isNotEmpty()) return@map
            if(postUrl.contains("$mainUrl/expired-download/|$mainUrl/افلام-اون-لاين/".toRegex())) return@map
            result.add(it.toSearchResponse()!!)
        }
        if(paginationElement.isNotEmpty()) {
            val max = paginationElement.select("li").not("li.active").last()?.text()?.toIntOrNull()
            if (max != null) {
                if(max > 5) return result.distinct().sortedBy { it.name }
                (2..max!!).toList().apmap {
                    app.get("$mainUrl/page/$it/?s=$query\"").document.select("section article a").map { element ->
                        val postUrl = element.attr("href")
                        if(element.select("li[aria-label=\"episode\"]").isNotEmpty()) return@map
                        if(postUrl.contains("$mainUrl/expired-download/|$mainUrl/افلام-اون-لاين/".toRegex())) return@map
                        result.add(element.toSearchResponse()!!)
                    }
                }
            }
        }
        return result.distinct().sortedBy { it.name }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val posterUrl = doc.select("body > script:nth-child(3)").html().replace(".*,\"image\":\"|\".*".toRegex(),"").ifEmpty { doc.select("meta[property=\"og:image\"]").attr("content") }
        val year = doc.select("article ul:nth-child(1) li a").last()?.text()?.toIntOrNull()
        val title = doc.select("title").text().split(" | ")[0]
        val isMovie = title.contains("فيلم|حفلات|مسرحية".toRegex())
        val youtubeTrailer = doc.select("iframe")?.attr("src")

        val synopsis = doc.select("ul#details li:contains(لمحة) p").text()

        val tags = doc.select("article ul").first()?.select("li")?.map { it.text() }

        val recommendations = doc.select("ul#related li").map { element ->
            MovieSearchResponse(
                apiName = this@CimaNowProvider.name,
                url = element.select("a").attr("href"),
                name = element.select("img:nth-child(2)").attr("alt"),
                posterUrl = element.select("img:nth-child(2)").attr("src")
            )
        }

        return if (isMovie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                "$url/watching"
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.recommendations = recommendations
                this.plot = synopsis
                this.tags = tags
                addTrailer(youtubeTrailer)
            }
        } else {
            val episodes = doc.select("ul#eps li").map { episode ->
                Episode(
                    episode.select("a").attr("href")+"/watching",
                    episode.select("a img:nth-child(2)").attr("alt"),
                    doc.select("span[aria-label=\"season-title\"]").html().replace("<p>.*|\n".toRegex(), "").getIntFromText(),
                    episode.select("a em").text().toIntOrNull(),
                    episode.select("a img:nth-child(2)").attr("src")
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().sortedBy { it.episode }) {
                this.posterUrl = posterUrl
                this.tags = tags
                this.year = year
                this.plot = synopsis
                this.recommendations = recommendations
                addTrailer(youtubeTrailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get("$data").document.select("ul#download [aria-label=\"quality\"]").forEach {
            val name = if(it.select("span").text().contains("فائق السرعة")) "Fast Servers" else "Servers"
            it.select("a").forEach { media ->
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = name,
                        url = media.attr("href"),
                        referer = this.mainUrl,
                        quality = media.text().getIntFromText() ?: Qualities.Unknown.value
                    )
                )
            }
        }
        return true
    }
}
