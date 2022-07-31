package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URI

class RebahinProvider : MainAPI() {
    override var mainUrl = "http://167.88.14.149"
    override var name = "Rebahin"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("Featured", "xtab1"),
            Pair("Film Terbaru", "xtab2"),
            Pair("Romance", "xtab3"),
            Pair("Drama", "xtab4"),
            Pair("Action", "xtab5"),
            Pair("Scifi", "xtab6"),
            Pair("Tv Series Terbaru", "stab1"),
            Pair("Anime Series", "stab2"),
            Pair("Drakor Series", "stab3"),
            Pair("West Series", "stab4"),
            Pair("China Series", "stab5"),
            Pair("Japan Series", "stab6"),
        )

        val items = ArrayList<HomePageList>()

        for ((header, tab) in urls) {
            try {
                val home =
                    app.get("$mainUrl/wp-content/themes/indoxxi/ajax-top-$tab.php").document.select(
                        "div.ml-item"
                    ).map {
                        it.toSearchResult()
                    }
                items.add(HomePageList(header, home))
            } catch (e: Exception) {
                logError(e)
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("span.mli-info > h2")!!.text().trim()
        val href = this.selectFirst("a")!!.attr("href")
        val type =
            if (this.select("span.mli-quality").isNotEmpty()) TvType.Movie else TvType.TvSeries
        return if (type == TvType.Movie) {
            val posterUrl = this.select("img").attr("src")
            val quality = getQualityFromString(this.select("span.mli-quality").text().trim())
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            val posterUrl =
                this.select("img").attr("src").ifEmpty { this.select("img").attr("data-original") }
            val episode =
                this.select("div.mli-eps > span").text().replace(Regex("[^0-9]"), "").toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/?s=$query"
        val document = app.get(link).document

        return document.select("div.ml-item").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h3[itemprop=name]")!!.ownText().trim()
        val poster = document.select(".mvic-desc > div.thumb.mvic-thumb").attr("style")
            .substringAfter("url(").substringBeforeLast(")")
        val tags = document.select("span[itemprop=genre]").map { it.text() }

        val year = Regex("([0-9]{4}?)-").find(
            document.selectFirst(".mvici-right > p:nth-child(3)")!!.ownText().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val tvType = if (url.contains("/series/")) TvType.TvSeries else TvType.Movie
        val description = document.select("span[itemprop=reviewBody] > p").text().trim()
        val trailer = fixUrlNull(document.selectFirst("div.modal-body-trailer iframe")?.attr("src"))
        val rating = document.selectFirst("span[itemprop=ratingValue]")?.text()?.toRatingInt()
        val duration = document.selectFirst(".mvici-right > p:nth-child(1)")!!
            .ownText().replace(Regex("[^0-9]"), "").toIntOrNull()
        val actors = document.select("span[itemprop=actor] > a").map { it.select("span").text() }

        val baseLink = fixUrl(document.select("div#mv-info > a").attr("href").toString())

        return if (tvType == TvType.TvSeries) {
            val episodes = app.get(baseLink).document.select("div#list-eps > a").map {
                Pair(it.text(), it.attr("data-iframe"))
            }.groupBy { it.first }.map { eps ->
                Episode(
                    data = eps.value.map { fixUrl(base64Decode(it.second)) }.toString(),
                    name = eps.key,
                    episode = eps.key.filter { it.isDigit() }.toIntOrNull()
                )

            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            val links =
                app.get(baseLink).document.select("div#server-list div.server-wrapper div[id*=episode]")
                    .map {
                        fixUrl(base64Decode(it.attr("data-iframe")))
                    }.toString()
            newMovieLoadResponse(title, url, TvType.Movie, links) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    private suspend fun invokeLokalSource(
        url: String,
        name: String,
        ref: String,
        subCallback: (SubtitleFile) -> Unit,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(
            url,
            allowRedirects = false,
            referer = mainUrl,
            headers = mapOf("Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        ).document

        document.select("script").map { script ->
            if (script.data().contains("sources: [")) {
                val source = tryParseJson<ResponseLocal>(
                    script.data().substringAfter("sources: [").substringBefore("],")
                )
                val m3uData = app.get(source!!.file, referer = ref).text
                val quality = Regex("\\d{3,4}\\.m3u8").findAll(m3uData).map { it.value }.toList()

                quality.forEach {
                    sourceCallback.invoke(
                        ExtractorLink(
                            source = name,
                            name = name,
                            url = source.file.replace("video.m3u8", it),
                            referer = ref,
                            quality = getQualityFromName("${it.replace(".m3u8", "")}p"),
                            isM3u8 = true
                        )
                    )
                }

                val trackJson = script.data().substringAfter("tracks: [").substringBefore("],")
                val track = tryParseJson<List<Tracks>>("[$trackJson]")
                track?.map {
                    subCallback.invoke(
                        SubtitleFile(
                            "Indonesian",
                            (if (it.file.contains(".srt")) it.file else null)!!
                        )
                    )
                }
            }
        }
    }

    private suspend fun invokeKotakAjairSource(
        url: String,
        subCallback: (SubtitleFile) -> Unit,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        val domainUrl = "https://kotakajair.xyz"
        val id = url.trimEnd('/').split("/").last()
        val sources = app.post(
            url = "$domainUrl/api/source/$id",
            data = mapOf("r" to mainUrl, "d" to URI(url).host)
        ).parsed<ResponseKotakAjair>()

        sources.data?.map {
            sourceCallback.invoke(
                ExtractorLink(
                    name,
                    "KotakAjair",
                    fixUrl(it.file),
                    referer = url,
                    quality = getQualityFromName(it.label)
                )
            )
        }
        val userData = sources.player.poster_file.split("/")[2]
        sources.captions?.map {
            subCallback.invoke(
                SubtitleFile(
                    if (it.language.lowercase().contains("eng")) it.language else "Indonesian",
                    "$domainUrl/asset/userdata/$userData/caption/${it.hash}/${it.id}.srt"
                )
            )
        }

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        data.removeSurrounding("[", "]").split(",").map { it.trim() }.apmap { link ->
            safeApiCall {
                when {
                    link.startsWith("http://172.96.161.72") -> invokeLokalSource(
                        link,
                        this.name,
                        "http://172.96.161.72/",
                        subtitleCallback,
                        callback
                    )
                    link.startsWith("https://kotakajair.xyz") -> invokeKotakAjairSource(
                        link,
                        subtitleCallback,
                        callback
                    )
                    else -> {
                        loadExtractor(link, "$mainUrl/", subtitleCallback, callback)
                        if (link.startsWith("https://sbfull.com")) {
                            val response = app.get(
                                link, interceptor = WebViewResolver(
                                    Regex("""\.srt""")
                                )
                            )
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    "Indonesian",
                                    response.url
                                )
                            )
                        }
                    }
                }
            }
        }

        return true
    }

    private data class ResponseLocal(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("type") val type: String?
    )

    private data class Tracks(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
        @JsonProperty("kind") val kind: String?
    )

    private data class Captions(
        @JsonProperty("id") val id: String,
        @JsonProperty("hash") val hash: String,
        @JsonProperty("language") val language: String,
    )

    private data class Data(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
    )

    private data class Player(
        @JsonProperty("poster_file") val poster_file: String,
    )

    private data class ResponseKotakAjair(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("player") val player: Player,
        @JsonProperty("data") val data: List<Data>?,
        @JsonProperty("captions") val captions: List<Captions>?
    )

}

