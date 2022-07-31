package com.lagradost.cloudstream3.movieproviders

import androidx.core.text.parseAsHtml
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor


class AltadefinizioneProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://altadefinizione.tienda"
    override var name = "Altadefinizione"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        Pair("$mainUrl/cerca/anno/2022/page/", "Ultimi Film"),
        Pair("$mainUrl/cerca/openload-quality/HD/page/", "Film in HD"),
        Pair("$mainUrl/cinema/page/", "Ora al cinema")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page

        val soup = app.get(url).document
        val home = soup.select("div.box").map {
            val title = it.selectFirst("img")!!.attr("alt")
            val link = it.selectFirst("a")!!.attr("href")
            val image = mainUrl + it.selectFirst("img")!!.attr("src")
            val quality = getQualityFromString(it.selectFirst("span")!!.text())

            MovieSearchResponse(
                title,
                link,
                this.name,
                TvType.Movie,
                image,
                null,
                null,
                quality,
            )
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.post(
            "$mainUrl/index.php", data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "story" to query,
                "sortby" to "news_read"
            )
        ).document
        return doc.select("div.box").map {
            val title = it.selectFirst("img")!!.attr("alt")
            val link = it.selectFirst("a")!!.attr("href")
            val image = mainUrl + it.selectFirst("img")!!.attr("src")
            val quality = getQualityFromString(it.selectFirst("span")!!.text())

            MovieSearchResponse(
                title,
                link,
                this.name,
                TvType.Movie,
                image,
                null,
                null,
                quality,
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val page = app.get(url)
        val document = page.document
        val title = document.selectFirst(" h1 > a")!!.text().replace("streaming", "")
        val description = document.select("#sfull").toString().substringAfter("altadefinizione")
            .substringBeforeLast("fonte trama").parseAsHtml().toString()
        val rating = null

        val year = document.selectFirst("#details > li:nth-child(2)")!!.childNode(2).toString()
            .filter { it.isDigit() }.toInt()

        val poster = fixUrl(document.selectFirst("div.thumbphoto > img")!!.attr("src"))

        val recomm = document.select("ul.related-list > li").map {
            val href = it.selectFirst("a")!!.attr("href")
            val posterUrl = mainUrl + it.selectFirst("img")!!.attr("src")
            val name = it.selectFirst("img")!!.attr("alt")
            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                posterUrl,
                null
            )

        }


        val actors: List<ActorData> =
            document.select("#staring > a").map {
                ActorData(actor = Actor(it.text()))
            }

        val tags: List<String> = document.select("#details > li:nth-child(1) > a").map { it.text() }

        val trailerurl = document.selectFirst("#showtrailer > div > div > iframe")?.attr("src")

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            posterUrl = fixUrlNull(poster)
            this.year = year
            this.plot = description
            this.rating = rating
            this.recommendations = recomm
            this.duration = null
            this.actors = actors
            this.tags = tags
            addTrailer(trailerurl)
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        if (doc.select("div.guardahd-player").isNullOrEmpty()) {
            val videoUrl =
                doc.select("input").last { it.hasAttr("data-mirror") }.attr("value")
            loadExtractor(videoUrl, data, subtitleCallback, callback)
            doc.select("#mirrors > li > a").forEach {
                loadExtractor(fixUrl(it.attr("data-target")), data, subtitleCallback, callback)
            }
        } else {
            val pagelinks = doc.select("div.guardahd-player").select("iframe").attr("src")
            val docLinks = app.get(pagelinks).document
            docLinks.select("body > div > ul > li").forEach {
                loadExtractor(fixUrl(it.attr("data-link")), data, subtitleCallback, callback)
            }
        }

        return true
    }
}