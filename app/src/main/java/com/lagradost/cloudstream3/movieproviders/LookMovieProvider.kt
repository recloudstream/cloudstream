package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup

class LookMovieProvider : MainAPI() {
    override val hasQuickSearch: Boolean
        get() = true

    override val name: String
        get() = "LookMovie"

    override val mainUrl: String
        get() = "https://lookmovie.io"

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
        //@JsonProperty("source") val source: String,
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

    override fun quickSearch(query: String): ArrayList<SearchResponse>? {
        val movieUrl = "$mainUrl/api/v1/movies/search/?q=$query"
        val movieResponse = khttp.get(movieUrl)
        val movies = mapper.readValue<LookMovieSearchResultRoot>(movieResponse.text).result

        val showsUrl = "$mainUrl/api/v1/shows/search/?q=$query"
        val showsResponse = khttp.get(showsUrl)
        val shows = mapper.readValue<LookMovieSearchResultRoot>(showsResponse.text).result

        val returnValue = ArrayList<SearchResponse>()
        if (!movies.isNullOrEmpty()) {
            for (m in movies) {
                val url = "$mainUrl/movies/view/${m.slug}"
                returnValue.add(MovieSearchResponse(m.title,
                    url,
                    url,//m.slug,
                    this.name,
                    TvType.Movie,
                    m.poster ?: m.backdrop,
                    m.year?.toIntOrNull()))
            }
        }

        if (!shows.isNullOrEmpty()) {
            for (s in shows) {
                val url = "$mainUrl/shows/view/${s.slug}"
                returnValue.add(MovieSearchResponse(s.title,
                    url,
                    url,//s.slug,
                    this.name,
                    TvType.TvSeries,
                    s.poster ?: s.backdrop,
                    s.year?.toIntOrNull()))
            }
        }

        return returnValue
    }

    override fun search(query: String): ArrayList<SearchResponse> {
        fun search(query: String, isMovie: Boolean): ArrayList<SearchResponse> {
            val url = "$mainUrl/${if (isMovie) "movies" else "shows"}/search/?q=$query"
            val response = khttp.get(url)
            val document = Jsoup.parse(response.text)

            val items = document.select("div.flex-wrap-movielist > div.movie-item-style-1")
            val returnValue = ArrayList<SearchResponse>()
            items.forEach { item ->
                val titleHolder = item.selectFirst("> div.mv-item-infor > h6 > a")
                val href = fixUrl(titleHolder.attr("href"))
                val name = titleHolder.text()
                val posterHolder = item.selectFirst("> div.image__placeholder > a")
                val poster = posterHolder.selectFirst("> img")?.attr("data-src")
                val year = posterHolder.selectFirst("> p.year")?.text()?.toIntOrNull()

                returnValue.add(if (isMovie) {
                    MovieSearchResponse(
                        name, href, href, this.name, TvType.Movie, poster, year
                    )
                } else
                    TvSeriesSearchResponse(
                        name, href, href, this.name, TvType.TvSeries, poster, year, null
                    )
                )
            }
            return returnValue
        }

        val movieList = search(query, true)
        val seriesList = search(query, false)
        movieList.addAll(seriesList)
        return movieList
    }

    override fun loadLinks(data: String, isCasting: Boolean, callback: (ExtractorLink) -> Unit): Boolean {
        val response = khttp.get(data.replace("\$unixtime", unixTime.toString()))

        "\"(.*?)\":\"(.*?)\"".toRegex().findAll(response.text).forEach {
            var quality = it.groupValues[1].replace("auto", "Auto")
            if (quality != "Auto") quality += "p"
            val url = it.groupValues[2]
            callback.invoke(ExtractorLink(this.name, "${this.name} - $quality", url, "", getQualityFromName(quality),true))
        }
        return true
    }

    override fun load(slug: String): LoadResponse? {
        val response = khttp.get(slug)
        val document = Jsoup.parse(response.text)
        val isMovie = slug.contains("/movies/")

        val watchHeader = document.selectFirst("div.watch-heading")
        val nameHeader = watchHeader.selectFirst("> h1.bd-hd")
        val year = nameHeader.selectFirst("> span")?.text()?.toIntOrNull()
        val name = nameHeader.ownText()
        val rating = parseRating(watchHeader.selectFirst("> div.movie-rate > div.rate > p > span").text())
        val imgElement = document.selectFirst("div.movie-img > p.movie__poster")
        val img = imgElement?.attr("style")
        var poster = if (img.isNullOrEmpty()) null else "url\\((.*?)\\)".toRegex().find(img)?.groupValues?.get(1)
        if (poster.isNullOrEmpty()) poster = imgElement?.attr("data-background-image")
        val descript = document.selectFirst("p.description-short").text()
        val id = "${if (isMovie) "id_movie" else "id_show"}:(.*?),".toRegex().find(response.text)?.groupValues?.get(1)
            ?.replace(" ", "")
            ?: return null
        val realSlug = slug.replace("$mainUrl/${if (isMovie) "movies" else "shows"}/view/", "")
        val realUrl =
            "$mainUrl/api/v1/security/${if (isMovie) "movie" else "show"}-access?${if (isMovie) "id_movie=$id" else "slug=$realSlug"}&token=1&sk=&step=1"

        val tokenResponse = khttp.get(realUrl)
        val root = mapper.readValue<LookMovieTokenRoot>(tokenResponse.text)
        val accessToken = root.data?.accessToken ?: return null

        //https://lookmovie.io/api/v1/security/show-access?slug=9140554-loki-2021&token=&sk=null&step=1
        //https://lookmovie.io/api/v1/security/movie-access?id_movie=11582&token=1&sk=&step=1

        if (isMovie) {
            return MovieLoadResponse(name,
                slug,
                this.name,
                TvType.Movie,
                "$mainUrl/manifests/movies/json/$id/\$unixtime/$accessToken/master.m3u8",
                poster,
                year,
                descript,
                null,
                rating)
        } else {
            val window =
                "window\\[\\'show_storage\\'\\] =((.|\\n)*?\\<)".toRegex().find(response.text)?.groupValues?.get(1)
                    ?: return null
            // val id = "id_show:(.*?),".toRegex().find(response.text)?.groupValues?.get(1) ?: return null
            val season = "seasons:.*\\[((.|\\n)*?)]".toRegex().find(window)?.groupValues?.get(1) ?: return null
            fun String.fixSeasonJson(replace: String): String {
                return this.replace("$replace:", "\"$replace\":")
            }

            //https://lookmovie.io/api/v1/security/show-access?slug=9140554-loki-2021&token=&sk=null&step=1
            //https://lookmovie.io/manifests/shows/json/TGv3dO0pcwomftMrywOnmw/1624571222/128848/master.m3u8
            //https://lookmovie.io/api/v1/shows/episode-subtitles/?id_episode=128848

            val json = season
                .replace("\'", "\"")
                .fixSeasonJson("title")
                .fixSeasonJson("id_episode")
                .fixSeasonJson("episode")
                .fixSeasonJson("index")
                .fixSeasonJson("season")
            val realJson = "[" + json.substring(0, json.lastIndexOf(',')) + "]"

            val episodes = mapper.readValue<List<LookMovieEpisode>>(realJson).map {
                TvSeriesEpisode(it.title,
                    it.season.toIntOrNull(),
                    it.episode.toIntOrNull(),
                    "$mainUrl/manifests/shows/json/$accessToken/\$unixtime/${it.idEpisode}/master.m3u8")
            }.toList()

            return TvSeriesLoadResponse(name,
                slug,
                this.name,
                TvType.TvSeries,
                ArrayList(episodes),
                poster,
                year,
                descript,
                null,
                null,
                rating)
        }
        //watch-heading
    }
}