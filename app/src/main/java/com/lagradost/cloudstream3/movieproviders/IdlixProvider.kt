package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element
import java.net.URI
import java.util.ArrayList

class IdlixProvider : MainAPI() {
    override var mainUrl = "https://94.103.82.88"
    override var name = "Idlix"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document

        val homePageList = ArrayList<HomePageList>()

        document.select("div.items").forEach { block ->
            val header =
                fixTitle(block.previousElementSibling()?.previousElementSibling()?.select("header > h2")
                    ?.text()!!.trim())
            val items = block.select("article.item").mapNotNull {
                it.toSearchResult()
            }
            if (items.isNotEmpty()) homePageList.add(HomePageList(header, items))
        }

        return HomePageResponse(homePageList)
    }

    private fun getProperLink(uri: String): String {
        return when {
            uri.contains("/episode/") -> {
                var title = uri.substringAfter("$mainUrl/episode/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvseries/$title"
            }
            uri.contains("/season/") -> {
                var title = uri.substringAfter("$mainUrl/season/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvseries/$title"
            }
            else -> {
                uri
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("h3 > a")!!.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
        val href = getProperLink(this.selectFirst("h3 > a")!!.attr("href"))
        val posterUrl = this.select("div.poster > img").attr("src").toString()
        val quality = getQualityFromString(this.select("span.quality").text())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/search/$query"
        val document = app.get(link).document

        return document.select("div.result-item").map {
            val title = it.selectFirst("div.title > a")!!.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
            val href = getProperLink(it.selectFirst("div.title > a")!!.attr("href"))
            val posterUrl = it.selectFirst("img")!!.attr("src").toString()
            newMovieSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("div.data > h1")?.text()?.replace(Regex("\\(\\d{4}\\)"), "")?.trim().toString()
        val poster = document.select("div.poster > img").attr("src").toString()
        val tags = document.select("div.sgeneros > a").map { it.text() }

        val year = Regex(",\\s?(\\d+)").find(
            document.select("span.date").text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        val tvType = if (document.select("ul#section > li:nth-child(1)").text().contains("Episodes")
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div.wp-content > p").text().trim()
        val trailer = document.selectFirst("div.embed iframe")?.attr("src")
        val rating =
            document.selectFirst("span.dt_rating_vgs")?.text()?.toRatingInt()
        val actors = document.select("div.persons > div[itemprop=actor]").map {
            Actor(it.select("meta[itemprop=name]").attr("content"), it.select("img").attr("src"))
        }

        val recommendations = document.select("div.owl-item").map {
            val recName =
                it.selectFirst("a")!!.attr("href").toString().removeSuffix("/").split("/").last()
            val recHref = it.selectFirst("a")!!.attr("href")
            val recPosterUrl = it.selectFirst("img")?.attr("src").toString()
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("ul.episodios > li").map {
                val href = it.select("a").attr("href")
                val name = fixTitle(it.select("div.episodiotitle > a").text().trim())
                val image = it.select("div.imagen > img").attr("src")
                val episode = it.select("div.numerando").text().replace(" ", "").split("-").last()
                    .toIntOrNull()
                val season = it.select("div.numerando").text().replace(" ", "").split("-").first()
                    .toIntOrNull()
                Episode(
                    href,
                    name,
                    season,
                    episode,
                    image
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    private fun getLanguage(str: String): String {
        return when {
            str.lowercase().contains("indonesia") || str.lowercase().contains("bahasa") -> "Indonesian"
            else -> str
        }
    }

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String?,
    )

    data class ResponseSource(
        @JsonProperty("hls") val hls: Boolean,
        @JsonProperty("videoSource") val videoSource: String,
        @JsonProperty("securedLink") val securedLink: String?,
    )

    data class Tracks(
        @JsonProperty("kind") val kind: String?,
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
    )

    private suspend fun invokeLokalSource(
        url: String,
        subCallback: (SubtitleFile) -> Unit,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = "$mainUrl/").document
        val hash = url.split("/").last().substringAfter("data=")

        val m3uLink = app.post(
            url = "https://jeniusplay.com/player/index.php?data=$hash&do=getVideo",
            data = mapOf("hash" to hash, "r" to "$mainUrl/"),
            referer = url,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsed<ResponseSource>().videoSource

        M3u8Helper.generateM3u8(
            this.name,
            m3uLink,
            url,
        ).forEach(sourceCallback)


        document.select("script").map { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
                val subData = getAndUnpack(script.data()).substringAfter("\"tracks\":[").substringBefore("],")
                tryParseJson<List<Tracks>>("[$subData]")?.map { subtitle ->
                    subCallback.invoke(
                        SubtitleFile(
                            getLanguage(subtitle.label!!),
                            subtitle.file
                        )
                    )
                }
            }
        }
    }

    data class ResponseLaviolaSource(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String?,
    )

    private suspend fun invokeLaviolaSource(
        url: String,
        subCallback: (SubtitleFile) -> Unit,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = "$mainUrl/").document
        val baseName = "Laviola"
        val baseUrl = "https://laviola.live/"
        document.select("script").map { script ->
            if (script.data().contains("var config = {")) {
                val data = script.data().substringAfter("sources: [").substringBefore("],")
                tryParseJson<List<ResponseLaviolaSource>>("[$data]")?.map { m3u ->
                    val m3uData = app.get(m3u.file, referer = baseUrl).text
                    val quality =
                        Regex("\\d{3,4}\\.m3u8").findAll(m3uData).map { it.value }.toList()
                    quality.forEach {
                        sourceCallback.invoke(
                            ExtractorLink(
                                source = baseName,
                                name = baseName,
                                url = m3u.file.replace("video.m3u8", it),
                                referer = baseUrl,
                                quality = getQualityFromName("${it.replace(".m3u8", "")}p"),
                                isM3u8 = true
                            )
                        )
                    }
                }

                val subData = script.data().substringAfter("tracks: [").substringBefore("],")
                tryParseJson<List<Tracks>>("[$subData]")?.map { subtitle ->
                    subCallback.invoke(
                        SubtitleFile(
                            getLanguage(subtitle.label!!),
                            (if (subtitle.kind!!.contains("captions")) subtitle.file else null)!!
                        )
                    )
                }
            }
        }
    }

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

    private data class ResponseCdn(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("player") val player: Player,
        @JsonProperty("data") val data: List<Data>?,
        @JsonProperty("captions") val captions: List<Captions>?
    )

    private suspend fun invokeCdnSource(
        url: String,
        subCallback: (SubtitleFile) -> Unit,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        val domainUrl = "https://cdnplayer.online"
        val id = url.trimEnd('/').split("/").last()
        val sources = app.post(
            url = "$domainUrl/api/source/$id",
            data = mapOf("r" to mainUrl, "d" to URI(url).host)
        ).parsed<ResponseCdn>()

        sources.data?.map {
            sourceCallback.invoke(
                ExtractorLink(
                    name,
                    "Cdnplayer",
                    fixUrl(it.file),
                    referer = url,
                    quality = getQualityFromName(it.label)
                )
            )
        }
        val userData = sources.player.poster_file.split("/")[2]
        sources.captions?.map { subtitle ->
            subCallback.invoke(
                SubtitleFile(
                    getLanguage(subtitle.language),
                    "$domainUrl/asset/userdata/$userData/caption/${subtitle.hash}/${subtitle.id}.srt"
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

        val document = app.get(data).document
        val id = document.select("meta#dooplay-ajax-counter").attr("data-postid")
        val type = if (data.contains("/movie/")) "movie" else "tv"

        document.select("ul#playeroptionsul > li").map {
            it.attr("data-nume")
        }.apmap { nume ->
            safeApiCall {
                var source = app.post(
                    url = "$mainUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to id,
                        "nume" to nume,
                        "type" to type
                    )
                ).parsed<ResponseHash>().embed_url

                when {
                    source.startsWith("https://jeniusplay.com") -> invokeLokalSource(
                        source,
                        subtitleCallback,
                        callback
                    )
                    source.startsWith("https://laviola.live") -> invokeLaviolaSource(
                        source,
                        subtitleCallback,
                        callback
                    )
                    source.startsWith("https://cdnplayer.online") -> invokeCdnSource(
                        source,
                        subtitleCallback,
                        callback
                    )
                    else -> {
                        if (source.startsWith("https://uservideo.xyz")) {
                            source = app.get(source).document.select("iframe").attr("src")
                        }
                        loadExtractor(source, data, subtitleCallback, callback)
                    }
                }
            }
        }

        return true
    }


}