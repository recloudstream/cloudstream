package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*

class AnimeflvIOProvider:MainAPI() {
    override var mainUrl = "https://animeflv.io" //Also scrapes from animeid.to
    override var name = "Animeflv.io"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )
    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/series", "Series actualizadas",),
            Pair("$mainUrl/peliculas", "Peliculas actualizadas"),
        )
        items.add(HomePageList("Estrenos", app.get(mainUrl).document.select("div#owl-demo-premiere-movies .pull-left").map{
            val title = it.selectFirst("p")?.text() ?: ""
            AnimeSearchResponse(
                title,
                fixUrl(it.selectFirst("a")?.attr("href") ?: ""),
                this.name,
                TvType.Anime,
                it.selectFirst("img")?.attr("src"),
                it.selectFirst("span.year").toString().toIntOrNull(),
                EnumSet.of(DubStatus.Subbed),
            )
        }))
        urls.apmap { (url, name) ->
            val soup = app.get(url).document
            val home = soup.select("div.item-pelicula").map {
                val title = it.selectFirst(".item-detail p")?.text() ?: ""
                val poster = it.selectFirst("figure img")?.attr("src")
                AnimeSearchResponse(
                    title,
                    fixUrl(it.selectFirst("a")?.attr("href") ?: ""),
                    this.name,
                    TvType.Anime,
                    poster,
                    null,
                    if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
                )
            }

            items.add(HomePageList(name, home))
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val headers = mapOf(
            "Host" to "animeflv.io",
            "User-Agent" to USER_AGENT,
            "X-Requested-With" to "XMLHttpRequest",
            "DNT" to "1",
            "Alt-Used" to "animeflv.io",
            "Connection" to "keep-alive",
            "Referer" to "https://animeflv.io",
        )
        val url = "$mainUrl/search.html?keyword=$query"
        val document = app.get(
            url,
            headers = headers
        ).document
        return document.select(".item-pelicula.pull-left").map {
            val title = it.selectFirst("div.item-detail p")?.text() ?: ""
            val href = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
            var image = it.selectFirst("figure img")?.attr("src") ?: ""
            val isMovie = href.contains("/pelicula/")
            if (image.contains("/static/img/picture.png")) { image = ""}
            if (isMovie) {
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.AnimeMovie,
                    image,
                    null
                )
            } else {
                AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Anime,
                    image,
                    null,
                    EnumSet.of(DubStatus.Subbed),
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        // Gets the url returned from searching.
        val soup = app.get(url).document
        val title = soup.selectFirst(".info-content h1")?.text()
        val description = soup.selectFirst("span.sinopsis")?.text()?.trim()
        val poster: String? = soup.selectFirst(".poster img")?.attr("src")
        val episodes = soup.select(".item-season-episodes a").map { li ->
            val href = fixUrl(li.selectFirst("a")?.attr("href") ?: "")
            val name = li.selectFirst("a")?.text() ?: ""
            Episode(
                href, name,
            )
        }.reversed()

        val year = Regex("(\\d*)").find(soup.select(".info-half").text())

        val tvType = if (url.contains("/pelicula/")) TvType.AnimeMovie else TvType.Anime
        val genre = soup.select(".content-type-a a")
            .map { it?.text()?.trim().toString().replace(", ","") }
        val duration = Regex("""(\d*)""").find(
            soup.select("p.info-half:nth-child(4)").text())

        return when (tvType) {
            TvType.Anime -> {
                return newAnimeLoadResponse(title ?: "", url, tvType) {
                    japName = null
                    engName = title
                    posterUrl = poster
                    this.year = null
                    addEpisodes(DubStatus.Subbed, episodes)
                    plot = description
                    tags = genre

                    showStatus = null
                }
            }
            TvType.AnimeMovie -> {
                MovieLoadResponse(
                    title ?: "",
                    url,
                    this.name,
                    tvType,
                    url,
                    poster,
                    year.toString().toIntOrNull(),
                    description,
                    null,
                    genre,
                    duration.toString().toIntOrNull(),
                )
            }
            else -> null
        }
    }

    data class MainJson (
        @JsonProperty("source") val source: List<Source>,
        @JsonProperty("source_bk") val sourceBk: String?,
        @JsonProperty("track") val track: List<String>?,
        @JsonProperty("advertising") val advertising: List<String>?,
        @JsonProperty("linkiframe") val linkiframe: String?
    )

    data class Source (
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("default") val default: String,
        @JsonProperty("type") val type: String
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("li.tab-video").apmap {
            val url = fixUrl(it.attr("data-video"))
            if (url.contains("animeid")) {
                val ajaxurl = url.replace("streaming.php","ajax.php")
                val ajaxurltext = app.get(ajaxurl).text
                val json = parseJson<MainJson>(ajaxurltext)
                json.source.forEach { source ->
                    if (source.file.contains("m3u8")) {
                        generateM3u8(
                            "Animeflv.io",
                            source.file,
                            "https://animeid.to",
                            headers = mapOf("Referer" to "https://animeid.to")
                        ).apmap {
                            callback(
                                ExtractorLink(
                                    "Animeflv.io",
                                    "Animeflv.io",
                                    it.url,
                                    "https://animeid.to",
                                    getQualityFromName(it.quality.toString()),
                                    it.url.contains("m3u8")
                                )
                            )
                        }
                    } else {
                        callback(
                            ExtractorLink(
                                name,
                                "$name ${source.label}",
                                source.file,
                                "https://animeid.to",
                                Qualities.Unknown.value,
                                isM3u8 = source.file.contains("m3u8")
                            )
                        )
                    }
                }
            }
            loadExtractor(url, data, subtitleCallback, callback)
        }
        return true
    }
}