package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.extractors.M3u8Manifest
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup

//BE AWARE THAT weboas.is is a clone of lookmovie
class LookMovieProvider : MainAPI() {
    override val hasQuickSearch = true
    override var name = "LookMovie"
    override var mainUrl = "https://lookmovie.io"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    data class LookMovieSearchResult(
        @JsonProperty("backdrop") val backdrop: String?,
        @JsonProperty("imdb_rating") val imdb_rating: String,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("year") val year: String?,
        //  @JsonProperty("flag_quality") val flag_quality: Int?,
    )

    data class LookMovieTokenRoot(
        @JsonProperty("data") val data: LookMovieTokenResult?,
        @JsonProperty("success") val success: Boolean,
    )

    data class LookMovieTokenResult(
        @JsonProperty("accessToken") val accessToken: String,
        @JsonProperty("subtitles") val subtitles: List<LookMovieTokenSubtitle>?,
    )

    data class LookMovieTokenSubtitle(
        @JsonProperty("language") val language: String,
        @JsonProperty("source") val source: String?,
        //@JsonProperty("source_id") val source_id: String,
        //@JsonProperty("kind") val kind: String,
        //@JsonProperty("id") val id: String,
        @JsonProperty("file") val file: String,
    )

    data class LookMovieSearchResultRoot(
        // @JsonProperty("per_page") val per_page: Int?,
        // @JsonProperty("total") val total: Int?,
        @JsonProperty("result") val result: List<LookMovieSearchResult>?,
    )

    data class LookMovieEpisode(
        @JsonProperty("title") var title: String,
        @JsonProperty("index") var index: String,
        @JsonProperty("episode") var episode: String,
        @JsonProperty("id_episode") var idEpisode: Int,
        @JsonProperty("season") var season: String,
    )

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        val movieUrl = "$mainUrl/api/v1/movies/search/?q=$query"
        val movieResponse = app.get(movieUrl).text
        val movies = mapper.readValue<LookMovieSearchResultRoot>(movieResponse).result

        val showsUrl = "$mainUrl/api/v1/shows/search/?q=$query"
        val showsResponse = app.get(showsUrl).text
        val shows = mapper.readValue<LookMovieSearchResultRoot>(showsResponse).result

        val returnValue = ArrayList<SearchResponse>()
        if (!movies.isNullOrEmpty()) {
            for (m in movies) {
                val url = "$mainUrl/movies/view/${m.slug}"
                returnValue.add(
                    MovieSearchResponse(
                        m.title,
                        url,
                        this.name,
                        TvType.Movie,
                        m.poster ?: m.backdrop,
                        m.year?.toIntOrNull()
                    )
                )
            }
        }

        if (!shows.isNullOrEmpty()) {
            for (s in shows) {
                val url = "$mainUrl/shows/view/${s.slug}"
                returnValue.add(
                    MovieSearchResponse(
                        s.title,
                        url,
                        this.name,
                        TvType.TvSeries,
                        s.poster ?: s.backdrop,
                        s.year?.toIntOrNull()
                    )
                )
            }
        }

        return returnValue
    }

    override suspend fun search(query: String): List<SearchResponse> {
        suspend fun search(query: String, isMovie: Boolean): List<SearchResponse> {
            val url = "$mainUrl/${if (isMovie) "movies" else "shows"}/search/?q=$query"
            val response = app.get(url).text
            val document = Jsoup.parse(response)

            val items = document.select("div.flex-wrap-movielist > div.movie-item-style-1")
            return items.map { item ->
                val titleHolder = item.selectFirst("> div.mv-item-infor > h6 > a")
                val href = fixUrl(titleHolder!!.attr("href"))
                val name = titleHolder.text()
                val posterHolder = item.selectFirst("> div.image__placeholder > a")
                val poster = posterHolder!!.selectFirst("> img")?.attr("data-src")
                val year = posterHolder.selectFirst("> p.year")?.text()?.toIntOrNull()
                if (isMovie) {
                    MovieSearchResponse(
                        name, href, this.name, TvType.Movie, poster, year
                    )
                } else
                    TvSeriesSearchResponse(
                        name, href, this.name, TvType.TvSeries, poster, year, null
                    )
            }
        }

        val movieList = search(query, true).toMutableList()
        val seriesList = search(query, false)
        movieList.addAll(seriesList)
        return movieList
    }

    data class LookMovieLinkLoad(val url: String, val extraUrl: String, val isMovie: Boolean)

    private fun addSubtitles(
        subs: List<LookMovieTokenSubtitle>?,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        if (subs == null) return
        subs.forEach {
            if (it.file.endsWith(".vtt"))
                subtitleCallback.invoke(SubtitleFile(it.language, fixUrl(it.file)))
        }
    }

    private suspend fun loadCurrentLinks(url: String, callback: (ExtractorLink) -> Unit) {
        val response = app.get(url.replace("\$unixtime", unixTime.toString())).text
        M3u8Manifest.extractLinks(response).forEach {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    "${this.name} - ${it.second}",
                    fixUrl(it.first),
                    "",
                    getQualityFromName(it.second),
                    true
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
        val localData: LookMovieLinkLoad = mapper.readValue(data)

        if (localData.isMovie) {
            val tokenResponse = app.get(localData.url).text
            val root = mapper.readValue<LookMovieTokenRoot>(tokenResponse)
            val accessToken = root.data?.accessToken ?: return false
            addSubtitles(root.data.subtitles, subtitleCallback)
            loadCurrentLinks(localData.extraUrl.replace("\$accessToken", accessToken), callback)
            return true
        } else {
            loadCurrentLinks(localData.url, callback)
            val subResponse = app.get(localData.extraUrl).text
            val subs = mapper.readValue<List<LookMovieTokenSubtitle>>(subResponse)
            addSubtitles(subs, subtitleCallback)
        }
        return true
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).text
        val document = Jsoup.parse(response)
        val isMovie = url.contains("/movies/")

        val watchHeader = document.selectFirst("div.watch-heading")
        val nameHeader = watchHeader!!.selectFirst("> h1.bd-hd")
        val year = nameHeader!!.selectFirst("> span")?.text()?.toIntOrNull()
        val title = nameHeader.ownText()
        val rating =
            parseRating(watchHeader.selectFirst("> div.movie-rate > div.rate > p > span")!!.text())
        val imgElement = document.selectFirst("div.movie-img > p.movie__poster")
        val img = imgElement?.attr("style")
        var poster = if (img.isNullOrEmpty()) null else "url\\((.*?)\\)".toRegex()
            .find(img)?.groupValues?.get(1)
        if (poster.isNullOrEmpty()) poster = imgElement?.attr("data-background-image")
        val descript = document.selectFirst("p.description-short")!!.text()
        val id = "${if (isMovie) "id_movie" else "id_show"}:(.*?),".toRegex()
            .find(response)?.groupValues?.get(1)
            ?.replace(" ", "")
            ?: return null
        val realSlug = url.replace("$mainUrl/${if (isMovie) "movies" else "shows"}/view/", "")
        val realUrl =
            "$mainUrl/api/v1/security/${if (isMovie) "movie" else "show"}-access?${if (isMovie) "id_movie=$id" else "slug=$realSlug"}&token=1&sk=&step=1"

        if (isMovie) {
            val localData =
                LookMovieLinkLoad(
                    realUrl,
                    "$mainUrl/manifests/movies/json/$id/\$unixtime/\$accessToken/master.m3u8",
                    true
                ).toJson()

            return MovieLoadResponse(
                title,
                url,
                this.name,
                TvType.Movie,
                localData,
                poster,
                year,
                descript,
                rating
            )
        } else {
            val tokenResponse = app.get(realUrl).text
            val root = mapper.readValue<LookMovieTokenRoot>(tokenResponse)
            val accessToken = root.data?.accessToken ?: return null

            val window =
                "window\\['show_storage'] =((.|\\n)*?<)".toRegex().find(response)?.groupValues?.get(
                    1
                )
                    ?: return null
            // val id = "id_show:(.*?),".toRegex().find(response.text)?.groupValues?.get(1) ?: return null
            val season = "seasons:.*\\[((.|\\n)*?)]".toRegex().find(window)?.groupValues?.get(1)
                ?: return null

            fun String.fixSeasonJson(replace: String): String {
                return this.replace("$replace:", "\"$replace\":")
            }

            val json = season
                .replace("\'", "\"")
                .fixSeasonJson("title")
                .fixSeasonJson("id_episode")
                .fixSeasonJson("episode")
                .fixSeasonJson("index")
                .fixSeasonJson("season")
            val realJson = "[" + json.substring(0, json.lastIndexOf(',')) + "]"

            val episodes = mapper.readValue<List<LookMovieEpisode>>(realJson).map {
                val localData =
                    LookMovieLinkLoad(
                        "$mainUrl/manifests/shows/json/$accessToken/\$unixtime/${it.idEpisode}/master.m3u8",
                        "https://lookmovie.io/api/v1/shows/episode-subtitles/?id_episode=${it.idEpisode}",
                        false
                    ).toJson()


                Episode(
                    localData,
                    it.title,
                    it.season.toIntOrNull(),
                    it.episode.toIntOrNull(),
                )
            }.toList()

            return TvSeriesLoadResponse(
                title,
                url,
                this.name,
                TvType.TvSeries,
                ArrayList(episodes),
                poster,
                year,
                descript,
                null,
                rating
            )
        }
    }
}