package com.lagradost.cloudstream3.metaproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.uwetrottmann.tmdb2.Tmdb
import com.uwetrottmann.tmdb2.entities.*
import java.util.*

/**
 * episode and season starting from 1
 * they are null if movie
 * */
data class TmdbLink(
    @JsonProperty("imdbID") val imdbID: String?,
    @JsonProperty("tmdbID") val tmdbID: Int?,
    @JsonProperty("episode") val episode: Int?,
    @JsonProperty("season") val season: Int?
)

open class TmdbProvider : MainAPI() {

    // Use the LoadResponse from the metadata provider
    open val useMetaLoadResponse = false
    open val apiName = "TMDB"

    // As some sites doesn't support s0
    open val disableSeasonZero = true

    override val hasMainPage = true
    override val providerType = ProviderType.MetaProvider

    // Fuck it, public private api key because github actions won't co-operate.
    // Please no stealy.
    private val tmdb = Tmdb("e6333b32409e02a4a6eba6fb7ff866bb")

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    private fun getUrl(id: Int?, tvShow: Boolean): String {
        return if (tvShow) "https://www.themoviedb.org/tv/${id ?: -1}"
        else "https://www.themoviedb.org/movie/${id ?: -1}"
    }

    private fun BaseTvShow.toSearchResponse(): TvSeriesSearchResponse {
        return TvSeriesSearchResponse(
            this.name ?: this.original_name,
            getUrl(id, true),
            apiName,
            TvType.TvSeries,
            getImageUrl(this.poster_path),
            this.first_air_date?.let {
                Calendar.getInstance().apply {
                    time = it
                }.get(Calendar.YEAR)
            },
            null,
            this.id
        )
    }

    private fun BaseMovie.toSearchResponse(): MovieSearchResponse {
        return MovieSearchResponse(
            this.title ?: this.original_title,
            getUrl(id, false),
            apiName,
            TvType.TvSeries,
            getImageUrl(this.poster_path),
            this.release_date?.let {
                Calendar.getInstance().apply {
                    time = it
                }.get(Calendar.YEAR)
            },
            this.id,
        )
    }

    private fun TvShow.toLoadResponse(): TvSeriesLoadResponse {
        val episodes = this.seasons?.filter { !disableSeasonZero || (it.season_number ?: 0) != 0 }
            ?.mapNotNull { season ->
                season.episodes?.map { episode ->
                    TvSeriesEpisode(
                        episode.name,
                        episode.season_number,
                        episode.episode_number,
                        TmdbLink(
                            episode.external_ids?.imdb_id ?: this.external_ids?.imdb_id,
                            this.id,
                            episode.episode_number,
                            episode.season_number,
                        ).toJson(),
                        getImageUrl(episode.still_path),
                        episode.air_date?.toString(),
                        episode.rating,
                        episode.overview,
                    )
                } ?: (1..(season.episode_count ?: 1)).map { episodeNum ->
                    TvSeriesEpisode(
                        episode = episodeNum,
                        data = TmdbLink(
                            this.external_ids?.imdb_id,
                            this.id,
                            episodeNum,
                            season.season_number,
                        ).toJson(),
                        season = season.season_number
                    )
                }
            }?.flatten() ?: listOf()

        return TvSeriesLoadResponse(
            this.name ?: this.original_name,
            getUrl(id, true),
            this@TmdbProvider.apiName,
            TvType.TvSeries,
            episodes,
            getImageUrl(this.poster_path),
            this.first_air_date?.let {
                Calendar.getInstance().apply {
                    time = it
                }.get(Calendar.YEAR)
            },
            this.overview,
            null, // this.status
            this.external_ids?.imdb_id,
            this.rating,
            this.genres?.mapNotNull { it.name },
            this.episode_run_time?.average()?.toInt(),
            null,
            this.recommendations?.results?.map { it.toSearchResponse() }
        )
    }

    private fun Movie.toLoadResponse(): MovieLoadResponse {
        return MovieLoadResponse(
            this.title ?: this.original_title,
            getUrl(id, true),
            this@TmdbProvider.apiName,
            TvType.Movie,
            TmdbLink(
                this.imdb_id,
                this.id,
                null,
                null,
            ).toJson(),
            getImageUrl(this.poster_path),
            this.release_date?.let {
                Calendar.getInstance().apply {
                    time = it
                }.get(Calendar.YEAR)
            },
            this.overview,
            null,//this.status
            this.rating,
            this.genres?.mapNotNull { it.name },
            this.runtime,
            null,
            this.recommendations?.results?.map { it.toSearchResponse() }
        )
    }

    override fun getMainPage(): HomePageResponse {

        // SAME AS DISCOVER IT SEEMS
//        val popularSeries = tmdb.tvService().popular(1, "en-US").execute().body()?.results?.map {
//            it.toSearchResponse()
//        } ?: listOf()
//
//        val popularMovies =
//            tmdb.moviesService().popular(1, "en-US", "840").execute().body()?.results?.map {
//                it.toSearchResponse()
//            } ?: listOf()

        val discoverMovies = tmdb.discoverMovie().build().execute().body()?.results?.map {
            it.toSearchResponse()
        } ?: listOf()

        val discoverSeries = tmdb.discoverTv().build().execute().body()?.results?.map {
            it.toSearchResponse()
        } ?: listOf()

        // https://en.wikipedia.org/wiki/ISO_3166-1
        val topMovies =
            tmdb.moviesService().topRated(1, "en-US", "US").execute().body()?.results?.map {
                it.toSearchResponse()
            } ?: listOf()

        val topSeries = tmdb.tvService().topRated(1, "en-US").execute().body()?.results?.map {
            it.toSearchResponse()
        } ?: listOf()

        return HomePageResponse(
            listOf(
//                HomePageList("Popular Series", popularSeries),
//                HomePageList("Popular Movies", popularMovies),
                HomePageList("Popular Movies", discoverMovies),
                HomePageList("Popular Series", discoverSeries),
                HomePageList("Top Movies", topMovies),
                HomePageList("Top Series", topSeries),
            )
        )
    }

    open fun loadFromImdb(imdb: String, seasons: List<TvSeason>): LoadResponse? {
        return null
    }

    open fun loadFromTmdb(tmdb: Int, seasons: List<TvSeason>): LoadResponse? {
        return null
    }

    open fun loadFromImdb(imdb: String): LoadResponse? {
        return null
    }

    open fun loadFromTmdb(tmdb: Int): LoadResponse? {
        return null
    }

    // Possible to add recommendations and such here.
    override fun load(url: String): LoadResponse? {
        // https://www.themoviedb.org/movie/7445-brothers
        // https://www.themoviedb.org/tv/71914-the-wheel-of-time

        val idRegex = Regex("""themoviedb\.org/(.*)/(\d+)""")
        val found = idRegex.find(url)

        val isTvSeries = found?.groupValues?.getOrNull(1).equals("tv", ignoreCase = true)
        val id = found?.groupValues?.getOrNull(2)?.toIntOrNull() ?: return null

        return if (useMetaLoadResponse) {
            return if (isTvSeries) {
                val body = tmdb.tvService().tv(id, "en-US").execute().body()
                body?.toLoadResponse()
            } else {
                val body = tmdb.moviesService().summary(id, "en-US").execute().body()
                body?.toLoadResponse()
            }
        } else {
            loadFromTmdb(id)?.let { return it }
            if (isTvSeries) {
                tmdb.tvService().externalIds(id, "en-US").execute().body()?.imdb_id?.let {
                    val fromImdb = loadFromImdb(it)
                    val result = if (fromImdb == null) {
                        val details = tmdb.tvService().tv(id, "en-US").execute().body()
                        loadFromImdb(it, details?.seasons ?: listOf())
                            ?: loadFromTmdb(id, details?.seasons ?: listOf())
                    } else {
                        fromImdb
                    }

                    result
                }
            } else {
                tmdb.moviesService().externalIds(id, "en-US").execute()
                    .body()?.imdb_id?.let { loadFromImdb(it) }
            }
        }


    }

    override fun search(query: String): List<SearchResponse>? {
        return tmdb.searchService().multi(query, 1, "en-Us", "US", true).execute()
            .body()?.results?.mapNotNull {
                it.movie?.toSearchResponse() ?: it.tvShow?.toSearchResponse()
            }
    }
}