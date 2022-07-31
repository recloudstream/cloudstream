package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.util.*


class AnimefenixProvider:MainAPI() {

    override var mainUrl = "https://animefenix.com"
    override var name = "Animefenix"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    fun getDubStatus(title: String): DubStatus {
        return if (title.contains("Latino") || title.contains("Castellano"))
            DubStatus.Dubbed
        else DubStatus.Subbed
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/", "Animes"),
            Pair("$mainUrl/animes?type[]=movie&order=default", "Peliculas", ),
            Pair("$mainUrl/animes?type[]=ova&order=default", "OVA's", ),
        )

        val items = ArrayList<HomePageList>()

        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl).document.select(".capitulos-grid div.item").map {
                    val title = it.selectFirst("div.overtitle")?.text()
                    val poster = it.selectFirst("a img")?.attr("src")
                    val epRegex = Regex("(-(\\d+)\$|-(\\d+)\\.(\\d+))")
                    val url = it.selectFirst("a")?.attr("href")?.replace(epRegex,"")
                        ?.replace("/ver/","/")
                    val epNum = it.selectFirst(".is-size-7")?.text()?.replace("Episodio ","")?.toIntOrNull()
                    newAnimeSearchResponse(title!!, url!!) {
                        this.posterUrl = poster
                        addDubStatus(getDubStatus(title), epNum)
                    }
                })
        )

        urls.apmap { (url, name) ->
            val response = app.get(url)
            val soup = Jsoup.parse(response.text)
            val home = soup.select(".list-series article").map {
                val title = it.selectFirst("h3 a")?.text()
                val poster = it.selectFirst("figure img")?.attr("src")
                AnimeSearchResponse(
                    title!!,
                    it.selectFirst("a")?.attr("href") ?: "",
                    this.name,
                    TvType.Anime,
                    poster,
                    null,
                    if (title.contains("Latino")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
                )
            }

            items.add(HomePageList(name, home))
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/animes?q=$query").document.select(".list-series article").map {
            val title = it.selectFirst("h3 a")?.text()
            val href = it.selectFirst("a")?.attr("href")
            val image = it.selectFirst("figure img")?.attr("src")
            AnimeSearchResponse(
                title!!,
                href!!,
                this.name,
                TvType.Anime,
                fixUrl(image ?: ""),
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(
                    DubStatus.Subbed),
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = Jsoup.parse(app.get(url, timeout = 120).text)
        val poster = doc.selectFirst(".image > img")?.attr("src")
        val title = doc.selectFirst("h1.title.has-text-orange")?.text()
        val description = doc.selectFirst("p.has-text-light")?.text()
        val genres = doc.select(".genres a").map { it.text() }
        val status = when (doc.selectFirst(".is-narrow-desktop a.button")?.text()) {
            "Emisión" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        val episodes = doc.select(".anime-page__episode-list li").map {
            val name = it.selectFirst("span")?.text()
            val link = it.selectFirst("a")?.attr("href")
            Episode(link!!, name)
        }.reversed()
        val type = if (doc.selectFirst("ul.has-text-light")?.text()
            !!.contains("Película") && episodes.size == 1
        ) TvType.AnimeMovie else TvType.Anime
        return newAnimeLoadResponse(title!!, url, type) {
            japName = null
            engName = title
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            plot = description
            tags = genres
            showStatus = status
        }
    }

    private fun cleanStreamID(input: String): String = input.replace(Regex("player=.*&amp;code=|&"),"")

    data class Amazon (
        @JsonProperty("file") var file  : String? = null,
        @JsonProperty("type") var type  : String? = null,
        @JsonProperty("label") var label : String? = null
    )

    private fun cleanExtractor(
        source: String,
        name: String,
        url: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback(
            ExtractorLink(
                source,
                name,
                url,
                "",
                Qualities.Unknown.value,
                false
            )
        )
        return true
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val soup = app.get(data).document
        val script = soup.selectFirst(".player-container script")?.data()
        if (script!!.contains("var tabsArray =")) {
            val sourcesRegex = Regex("player=.*&amp;code(.*)&")
            val test = sourcesRegex.findAll(script).toList()
            test.apmap {
                val codestream = it.value
                val links = when {
                    codestream.contains("player=2&amp") -> "https://embedsito.com/v/"+cleanStreamID(codestream)
                    codestream.contains("player=3&amp") -> "https://www.mp4upload.com/embed-"+cleanStreamID(codestream)+".html"
                    codestream.contains("player=6&amp") -> "https://www.yourupload.com/embed/"+cleanStreamID(codestream)
                    codestream.contains("player=12&amp") -> "http://ok.ru/videoembed/"+cleanStreamID(codestream)
                    codestream.contains("player=4&amp") -> "https://sendvid.com/"+cleanStreamID(codestream)
                    codestream.contains("player=9&amp") -> "AmaNormal https://www.animefenix.com/stream/amz.php?v="+cleanStreamID(codestream)
                    codestream.contains("player=11&amp") -> "AmazonES https://www.animefenix.com/stream/amz.php?v="+cleanStreamID(codestream)
                    codestream.contains("player=22&amp") -> "Fireload https://www.animefenix.com/stream/fl.php?v="+cleanStreamID(codestream)

                    else -> ""
                }
                loadExtractor(links, data, subtitleCallback, callback)

                argamap({
                    if (links.contains("AmaNormal")) {
                        val doc = app.get(links.replace("AmaNormal ","")).document
                        doc.select("script").map { script ->
                            if (script.data().contains("sources: [{\"file\"")) {
                                val text = script.data().substringAfter("sources:").substringBefore("]").replace("[","")
                                val json = parseJson<Amazon>(text)
                                if (json.file != null) {
                                    cleanExtractor(
                                        "Amazon",
                                        "Amazon ${json.label}",
                                        json.file!!,
                                        callback
                                    )
                                }
                            }
                        }
                    }

                    if (links.contains("AmazonES")) {
                        val amazonES = links.replace("AmazonES ", "")
                        val doc = app.get("$amazonES&ext=es").document
                        doc.select("script").map { script ->
                            if (script.data().contains("sources: [{\"file\"")) {
                                val text = script.data().substringAfter("sources:").substringBefore("]").replace("[","")
                                val json = parseJson<Amazon>(text)
                                if (json.file != null) {
                                    cleanExtractor(
                                        "AmazonES",
                                        "AmazonES ${json.label}",
                                        json.file!!,
                                        callback
                                    )
                                }
                            }
                        }
                    }
                    if (links.contains("Fireload")) {
                        val doc = app.get(links.replace("Fireload ", "")).document
                        doc.select("script").map { script ->
                            if (script.data().contains("sources: [{\"file\"")) {
                                val text = script.data().substringAfter("sources:").substringBefore("]").replace("[","")
                                val json = parseJson<Amazon>(text)
                                val testurl = if (json.file?.contains("fireload") == true) {
                                    app.get("https://${json.file}").text
                                } else null
                                if (testurl?.contains("error") == true) {
                                    //
                                } else if (json.file?.contains("fireload") == true) {
                                    cleanExtractor(
                                        "Fireload",
                                        "Fireload ${json.label}",
                                        "https://"+json.file!!,
                                        callback
                                    )
                                }
                            }
                        }
                    }
                })
            }
        }
        return true
    }
}