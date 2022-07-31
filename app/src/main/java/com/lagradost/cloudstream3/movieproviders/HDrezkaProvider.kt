package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.*

class HDrezkaProvider : MainAPI() {
    override var mainUrl = "https://hdrezka19139.org"
    override var name = "HDrezka"
    override val hasMainPage = true
    override var lang = "ru"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {

        val items = ArrayList<HomePageList>()

        listOf(
            Pair("фильмы", "$mainUrl/films/?filter=watching"),
            Pair("сериалы", "$mainUrl/series/?filter=watching"),
            Pair("мультфильмы", "$mainUrl/cartoons/?filter=watching"),
            Pair("аниме", "$mainUrl/animation/?filter=watching"),
        ).apmap { (header, url) ->
            safeApiCall {
                val home = app.get(url).document.select(
                    "div.b-content__inline_items div.b-content__inline_item"
                ).map {
                    it.toSearchResult()
                }
                items.add(HomePageList(fixTitle(header), home))
            }
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title =
            this.selectFirst("div.b-content__inline_item-link > a")?.text()?.trim().toString()
        val href = this.selectFirst("a")?.attr("href").toString()
        val posterUrl = this.select("img").attr("src")
        val type = if (this.select("span.info").isNotEmpty()) TvType.TvSeries else TvType.Movie
        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            val episode =
                this.select("span.info").text().substringAfter(",").replace(Regex("[^0-9]"), "")
                    .toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addDubStatus(
                    dubExist = true,
                    dubEpisodes = episode,
                    subExist = true,
                    subEpisodes = episode
                )
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/search/?do=search&subaction=search&q=$query"
        val document = app.get(link).document

        return document.select("div.b-content__inline_items div.b-content__inline_item").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val id = url.split("/").last().split("-").first()
        val title = (document.selectFirst("div.b-post__origtitle")?.text()?.trim()
            ?: document.selectFirst("div.b-post__title h1")?.text()?.trim()).toString()
        val poster = fixUrlNull(document.selectFirst("div.b-sidecover img")?.attr("src"))
        val tags =
            document.select("table.b-post__info > tbody > tr:nth-child(5) span[itemprop=genre]")
                .map { it.text() }
        val year = document.select("div.film-info > div:nth-child(2) a").text().toIntOrNull()
        val tvType = if (document.select("div#simple-episodes-tabs")
                .isNullOrEmpty()
        ) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("div.b-post__description_text")?.text()?.trim()
        val trailer = app.post(
            "$mainUrl/engine/ajax/gettrailervideo.php",
            data = mapOf("id" to id),
            referer = url
        ).parsedSafe<Trailer>()?.code.let {
            Jsoup.parse(it.toString()).select("iframe").attr("src")
        }
        val rating =
            document.selectFirst("table.b-post__info > tbody > tr:nth-child(1) span.bold")?.text()
                .toRatingInt()
        val actors = document.select("table.b-post__info > tbody > tr:last-child span.item").map {
            Actor(
                it.selectFirst("span[itemprop=name]")?.text().toString(),
                it.selectFirst("span[itemprop=actor]")?.attr("data-photo")
            )
        }

        val recommendations = document.select("div.b-sidelist div.b-content__inline_item").map {
            it.toSearchResult()
        }

        val data = HashMap<String, Any>()
        val server = ArrayList<Map<String, String>>()

        data["id"] = id
        data["favs"] = document.selectFirst("input#ctrl_favs")?.attr("value").toString()
        data["ref"] = url

        return if (tvType == TvType.TvSeries) {
            document.select("ul#translators-list li").map { res ->
                server.add(
                    mapOf(
                        "translator_name" to res.text(),
                        "translator_id" to res.attr("data-translator_id"),
                    )
                )
            }
            val episodes = document.select("div#simple-episodes-tabs ul li").map {
                val season = it.attr("data-season_id").toIntOrNull()
                val episode = it.attr("data-episode_id").toIntOrNull()
                val name = "Episode $episode"

                data["season"] = "$season"
                data["episode"] = "$episode"
                data["server"] = server
                data["action"] = "get_stream"

                Episode(
                    data.toJson(),
                    name,
                    season,
                    episode,
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
            document.select("ul#translators-list li").map { res ->
                server.add(
                    mapOf(
                        "translator_name" to res.text(),
                        "translator_id" to res.attr("data-translator_id"),
                        "camrip" to res.attr("data-camrip"),
                        "ads" to res.attr("data-ads"),
                        "director" to res.attr("data-director")
                    )
                )
            }

            data["server"] = server
            data["action"] = "get_movie"

            newMovieLoadResponse(title, url, TvType.Movie, data.toJson()) {
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

    private fun decryptStreamUrl(data: String): String {

        fun getTrash(arr: List<String>, item: Int): List<String> {
            val trash = ArrayList<List<String>>()
            for (i in 1..item) {
                trash.add(arr)
            }
            return trash.reduce { acc, list ->
                val temp = ArrayList<String>()
                acc.forEach { ac ->
                    list.forEach { li ->
                        temp.add(ac.plus(li))
                    }
                }
                return@reduce temp
            }
        }

        val trashList = listOf("@", "#", "!", "^", "$")
        val trashSet = getTrash(trashList, 2) + getTrash(trashList, 3)
        var trashString = data.replace("#h", "").split("//_//").joinToString("")

        trashSet.forEach {
            val temp = base64Encode(it.toByteArray())
            trashString = trashString.replace(temp, "")
        }

        return base64Decode(trashString)

    }

    private fun cleanCallback(
        source: String,
        url: String,
        quality: String,
        isM3u8: Boolean,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        sourceCallback.invoke(
            ExtractorLink(
                source,
                source,
                url,
                "$mainUrl/",
                getQuality(quality),
                isM3u8,
                headers = mapOf(
                    "Origin" to mainUrl
                )
            )
        )
    }

    private fun getLanguage(str: String): String {
        return when (str) {
            "Русский" -> "Russian"
            "Українська" -> "Ukrainian"
            else -> str
        }
    }

    private fun getQuality(str: String): Int {
        return when (str) {
            "360p" -> Qualities.P240.value
            "480p" -> Qualities.P360.value
            "720p" -> Qualities.P480.value
            "1080p" -> Qualities.P720.value
            "1080p Ultra" -> Qualities.P1080.value
            else -> getQualityFromName(str)
        }
    }

    private fun invokeSources(
        source: String,
        url: String,
        subtitle: String,
        subCallback: (SubtitleFile) -> Unit,
        sourceCallback: (ExtractorLink) -> Unit
    ) {
        decryptStreamUrl(url).split(",").map { links ->
            val quality =
                Regex("\\[([0-9]*p.*?)]").find(links)?.groupValues?.getOrNull(1)
                    .toString().trim()
            links.replace("[$quality]", "").split("or").map { it.trim() }
                .map { link ->

                    if (link.endsWith(".m3u8")) {
                        cleanCallback(
                            "$source (Main)",
                            link,
                            quality,
                            true,
                            sourceCallback,
                        )
                    } else {
                        cleanCallback(
                            "$source (Backup)",
                            link,
                            quality,
                            false,
                            sourceCallback,
                        )
                    }
                }
        }

        subtitle.split(",").map { sub ->
            val language =
                Regex("\\[(.*)]").find(sub)?.groupValues?.getOrNull(1)
                    .toString()
            val link = sub.replace("[$language]", "").trim()
            subCallback.invoke(
                SubtitleFile(
                    getLanguage(language),
                    link
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

        tryParseJson<Data>(data)?.let { res ->
            if (res.server?.isEmpty() == true) {
                val document = app.get(res.ref ?: return@let).document
                document.select("script").map { script ->
                    if (script.data().contains("sof.tv.initCDNMoviesEvents(")) {
                        val dataJson =
                            script.data().substringAfter("false, {").substringBefore("});")
                        tryParseJson<LocalSources>("{$dataJson}")?.let { source ->
                            invokeSources(
                                this.name,
                                source.streams,
                                source.subtitle.toString(),
                                subtitleCallback,
                                callback
                            )
                        }
                    }
                }
            } else {
                res.server?.apmap { server ->
                    suspendSafeApiCall {
                        app.post(
                            url = "$mainUrl/ajax/get_cdn_series/?t=${Date().time}",
                            data = mapOf(
                                "id" to res.id,
                                "translator_id" to server.translator_id,
                                "favs" to res.favs,
                                "is_camrip" to server.camrip,
                                "is_ads" to server.ads,
                                "is_director" to server.director,
                                "season" to res.season,
                                "episode" to res.episode,
                                "action" to res.action,
                            ).filterValues { it != null }.mapValues { it.value as String },
                            referer = res.ref
                        ).parsedSafe<Sources>()?.let { source ->
                            invokeSources(
                                server.translator_name.toString(),
                                source.url,
                                source.subtitle.toString(),
                                subtitleCallback,
                                callback
                            )
                        }
                    }
                }
            }
        }

        return true
    }

    data class LocalSources(
        @JsonProperty("streams") val streams: String,
        @JsonProperty("subtitle") val subtitle: Any?,
    )

    data class Sources(
        @JsonProperty("url") val url: String,
        @JsonProperty("subtitle") val subtitle: Any?,
    )

    data class Server(
        @JsonProperty("translator_name") val translator_name: String?,
        @JsonProperty("translator_id") val translator_id: String?,
        @JsonProperty("camrip") val camrip: String?,
        @JsonProperty("ads") val ads: String?,
        @JsonProperty("director") val director: String?,
    )

    data class Data(
        @JsonProperty("id") val id: String?,
        @JsonProperty("favs") val favs: String?,
        @JsonProperty("server") val server: List<Server>?,
        @JsonProperty("season") val season: String?,
        @JsonProperty("episode") val episode: String?,
        @JsonProperty("action") val action: String?,
        @JsonProperty("ref") val ref: String?,
    )

    data class Trailer(
        @JsonProperty("success") val success: Boolean?,
        @JsonProperty("code") val code: String?,
    )

}