package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import java.util.*

class DubbedAnimeProvider : MainAPI() {
    override var mainUrl = "https://bestdubbedanime.com"
    override var name = "DubbedAnime"
    override val hasQuickSearch = true
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
    )

    data class QueryEpisodeResultRoot(
        @JsonProperty("result")
        val result: QueryEpisodeResult,
    )

    data class QueryEpisodeResult(
        @JsonProperty("anime") val anime: List<EpisodeInfo>,
        @JsonProperty("error") val error: Boolean,
        @JsonProperty("errorMSG") val errorMSG: String?,
    )

    data class EpisodeInfo(
        @JsonProperty("serversHTML") val serversHTML: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("preview_img") val previewImg: String?,
        @JsonProperty("wideImg") val wideImg: String?,
        @JsonProperty("year") val year: String?,
        @JsonProperty("desc") val desc: String?,

        /*
        @JsonProperty("rowid") val rowid: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("skips") val skips: String,
        @JsonProperty("totalEp") val totalEp: Long,
        @JsonProperty("ep") val ep: String,
        @JsonProperty("NextEp") val nextEp: Long,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("showid") val showid: String,
        @JsonProperty("Epviews") val epviews: String,
        @JsonProperty("TotalViews") val totalViews: String,
        @JsonProperty("tags") val tags: String,*/
    )

    private suspend fun parseDocumentTrending(url: String): List<SearchResponse> {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        return document.select("li > a").mapNotNull {
            val href = fixUrl(it.attr("href"))
            val title = it.selectFirst("> div > div.cittx")?.text() ?: return@mapNotNull null
            val poster = fixUrlNull(it.selectFirst("> div > div.imghddde > img")?.attr("src"))
            AnimeSearchResponse(
                title,
                href,
                this.name,
                TvType.Anime,
                poster,
                null,
                EnumSet.of(DubStatus.Dubbed),
            )
        }
    }

    private suspend fun parseDocument(
        url: String,
        trimEpisode: Boolean = false
    ): List<SearchResponse> {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        return document.select("a.grid__link").mapNotNull {
            val href = fixUrl(it.attr("href"))
            val title = it.selectFirst("> div.gridtitlek")?.text() ?: return@mapNotNull null
            val poster =
                fixUrl(it.selectFirst("> img.grid__img")?.attr("src") ?: return@mapNotNull null)
            AnimeSearchResponse(
                title,
                if (trimEpisode) href.removeRange(href.lastIndexOf('/'), href.length) else href,
                this.name,
                TvType.Anime,
                poster,
                null,
                EnumSet.of(DubStatus.Dubbed),
            )
        }
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val trendingUrl = "$mainUrl/xz/trending.php?_=$unixTimeMS"
        val lastEpisodeUrl = "$mainUrl/xz/epgrid.php?p=1&_=$unixTimeMS"
        val recentlyAddedUrl = "$mainUrl/xz/gridgrabrecent.php?p=1&_=$unixTimeMS"
        //val allUrl = "$mainUrl/xz/gridgrab.php?p=1&limit=12&_=$unixTimeMS"

        val listItems = listOf(
            HomePageList("Trending", parseDocumentTrending(trendingUrl)),
            HomePageList("Recently Added", parseDocument(recentlyAddedUrl)),
            HomePageList("Recent Releases", parseDocument(lastEpisodeUrl, true)),
            // HomePageList("All", parseDocument(allUrl))
        )

        return HomePageResponse(listItems)
    }


    private suspend fun getEpisode(slug: String, isMovie: Boolean): EpisodeInfo {
        val url =
            mainUrl + (if (isMovie) "/movies/jsonMovie" else "/xz/v3/jsonEpi") + ".php?slug=$slug&_=$unixTime"
        val response = app.get(url).text
        val mapped = parseJson<QueryEpisodeResultRoot>(response)
        return mapped.result.anime.first()
    }


    private fun getIsMovie(href: String): Boolean {
        return href.contains("movies/")
    }

    private fun getSlug(href: String): String {
        return href.replace("$mainUrl/", "")
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        val url = "$mainUrl/xz/searchgrid.php?p=1&limit=12&s=$query&_=$unixTime"
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val items = document.select("div.grid__item > a")
        if (items.isEmpty()) return emptyList()
        return items.mapNotNull { i ->
            val href = fixUrl(i.attr("href"))
            val title = i.selectFirst("div.gridtitlek")?.text() ?: return@mapNotNull null
            val img = fixUrlNull(i.selectFirst("img.grid__img")?.attr("src"))

            if (getIsMovie(href)) {
                MovieSearchResponse(
                    title, href, this.name, TvType.AnimeMovie, img, null
                )
            } else {
                AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Anime,
                    img,
                    null,
                    EnumSet.of(DubStatus.Dubbed),
                )
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/$query"
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val items = document.select("div.resultinner > a.resulta")
        if (items.isEmpty()) return ArrayList()
        return items.mapNotNull { i ->
            val innerDiv = i.selectFirst("> div.result")
            val href = fixUrl(i.attr("href"))
            val img = fixUrl(innerDiv?.selectFirst("> div.imgkz > img")?.attr("src") ?: return@mapNotNull null)
            val title = innerDiv.selectFirst("> div.titleresults")?.text() ?: return@mapNotNull null

            if (getIsMovie(href)) {
                MovieSearchResponse(
                    title, href, this.name, TvType.AnimeMovie, img, null
                )
            } else {
                AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Anime,
                    img,
                    null,
                    EnumSet.of(DubStatus.Dubbed),
                )
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val serversHTML = (if (data.startsWith(mainUrl)) { // CLASSIC EPISODE
            val slug = getSlug(data)
            getEpisode(slug, false).serversHTML
        } else data).replace("\\", "")

        val hls = ArrayList("hl=\"(.*?)\"".toRegex().findAll(serversHTML).map {
            it.groupValues[1]
        }.toList())
        for (hl in hls) {
            try {
                val sources = app.get("$mainUrl/xz/api/playeri.php?url=$hl&_=$unixTime").text
                val find = "src=\"(.*?)\".*?label=\"(.*?)\"".toRegex().find(sources)
                if (find != null) {
                    val quality = find.groupValues[2]
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            this.name + " " + quality + if (quality.endsWith('p')) "" else 'p',
                            fixUrl(find.groupValues[1]),
                            this.mainUrl,
                            getQualityFromName(quality)
                        )
                    )
                }
            } catch (e: Exception) {
                //IDK
            }
        }
        return true
    }

    override suspend fun load(url: String): LoadResponse {
        if (getIsMovie(url)) {
            val realSlug = url.replace("movies/", "")
            val episode = getEpisode(realSlug, true)
            val poster = episode.previewImg ?: episode.wideImg
            return MovieLoadResponse(
                episode.title,
                realSlug,
                this.name,
                TvType.AnimeMovie,
                episode.serversHTML,
                if (poster == null) null else fixUrl(poster),
                episode.year?.toIntOrNull(),
                episode.desc,
                null
            )
        } else {
            val response = app.get(url).text
            val document = Jsoup.parse(response)
            val title = document.selectFirst("h4")!!.text()
            val descriptHeader = document.selectFirst("div.animeDescript")
            val descript = descriptHeader?.selectFirst("> p")?.text()
            val year = descriptHeader?.selectFirst("> div.distatsx > div.sroverd")
                ?.text()
                ?.replace("Released: ", "")
                ?.toIntOrNull()

            val episodes = document.select("a.epibloks").map {
                val epTitle = it.selectFirst("> div.inwel > span.isgrxx")?.text()
                Episode(fixUrl(it.attr("href")), epTitle)
            }

            val img = fixUrl(document.select("div.fkimgs > img").attr("src"))
            return newAnimeLoadResponse(title, url, TvType.Anime) {
                posterUrl = img
                this.year = year
                addEpisodes(DubStatus.Dubbed, episodes)
                plot = descript
            }
        }
    }
}