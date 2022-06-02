package com.lagradost.cloudstream3.movieproviders

import androidx.core.text.parseAsHtml
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

class AltadefinizioneProvider : MainAPI() {
    override val lang = "it"
    override var mainUrl = "https://altadefinizione.limo"
    override var name = "Altadefinizione"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie
    )

    override suspend fun getMainPage(): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/azione/", "Azione"),
            Pair("$mainUrl/avventura/", "Avventura"),
        )
        for ((url, name) in urls) {
            try {
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

                items.add(HomePageList(name, home))
            } catch (e: Exception) {
                logError(e)
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.post("$mainUrl/index.php?do=search", data = mapOf(
            "subaction" to "search",
            "story" to query
        )).document
        return doc.select("div.box").map {
            val title = it.selectFirst("img")!!.attr("alt")
            val link = it.selectFirst("a")!!.attr("href")
            val image = mainUrl+it.selectFirst("img")!!.attr("src")
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
        val title = document.selectFirst(" h1 > a")!!.text()
        val description = document.select("#sfull").toString().substringAfter("altadefinizione").substringBeforeLast("fonte trama").parseAsHtml().toString()
        val rating = null

        val year = document.selectFirst("#details > li:nth-child(2)")!!.childNode(2).toString().filter { it.isDigit() }.toInt()

        val poster = fixUrl(document.selectFirst("div.thumbphoto > img")!!.attr("src"))

        val recomm = document.select("ul.related-list > li").map {
            val href = it.selectFirst("a")!!.attr("href")
            val posterUrl = mainUrl + it.selectFirst("img")!!.attr("src")
            val name =  it.selectFirst("img")!!.attr("alt")
            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                posterUrl,
                null
            )

        }

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

            }
        }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        if (doc.select("div.guardahd-player").isNullOrEmpty()){
            val videoUrl = doc.select("input").filter { it.hasAttr("data-mirror") }.last().attr("value")
            loadExtractor(videoUrl, data, callback)
            doc.select("#mirrors > li > a").forEach {
                loadExtractor(fixUrl(it.attr("data-target")), data, callback)
            }
        }
        else{
            val pagelinks = doc.select("div.guardahd-player").select("iframe").attr("src")
            val docLinks = app.get(pagelinks).document
            docLinks.select("body > div > ul > li").forEach {
                loadExtractor(fixUrl(it.attr("data-link")), data, callback)
            }
        }



        return true
    }
}