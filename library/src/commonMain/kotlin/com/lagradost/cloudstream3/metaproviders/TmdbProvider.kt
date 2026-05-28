package com.lagradost.cloudstream3.metaproviders

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * episode and season starting from 1
 * they are null if movie
 */
@Serializable
data class TmdbLink(
    @SerialName("imdbID") val imdbID: String?,
    @SerialName("tmdbID") val tmdbID: Int?,
    @SerialName("episode") val episode: Int?,
    @SerialName("season") val season: Int?,
    @SerialName("movieName") val movieName: String? = null,
)

open class TmdbProvider : MainAPI() {
    // This should always be false, but might as well make it easier for forks
    open val includeAdult = false

    // Use the LoadResponse from the metadata provider
    open val useMetaLoadResponse = false
    open val apiName = "TMDB"

    // As some sites don't support s0
    open val disableSeasonZero = true

    override val hasMainPage = true
    override val providerType = ProviderType.MetaProvider

    private val tmdbApiKey = "e6333b32409e02a4a6eba6fb7ff866bb"
    private val tmdbApiUrl = "https://api.themoviedb.org/3"

    @Serializable
    data class TmdbIds(
        @SerialName("imdb_id") val imdbId: String? = null,
        @SerialName("tvdb_id") val tvdbId: Int? = null,
    )

    @Serializable
    data class TmdbGenre(
        @SerialName("id") val id: Int? = null,
        @SerialName("name") val name: String? = null,
    )

    @Serializable
    data class TmdbCastMember(
        @SerialName("name") val name: String? = null,
        @SerialName("character") val character: String? = null,
        @SerialName("profile_path") val profilePath: String? = null,
    )

    @Serializable
    data class TmdbCredits(
        @SerialName("cast") val cast: List<TmdbCastMember>? = null,
    )

    @Serializable
    data class TmdbVideo(
        @SerialName("key") val key: String? = null,
        @SerialName("site") val site: String? = null,
        @SerialName("type") val type: String? = null,
    )

    @Serializable
    data class TmdbVideos(
        @SerialName("results") val results: List<TmdbVideo>? = null,
    )

    // Shared between movie and tv search results
    @Serializable
    data class TmdbSearchResult(
        @SerialName("id") val id: Int? = null,
        @SerialName("title") val title: String? = null,           // movies
        @SerialName("original_title") val originalTitle: String? = null,
        @SerialName("name") val name: String? = null,             // tv
        @SerialName("original_name") val originalName: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("media_type") val mediaType: String? = null,  // for multi-search
    ) {
        val isTv get() = name != null || mediaType == "tv"
        val displayTitle get() = title ?: originalTitle ?: name ?: originalName ?: ""
        val year get() = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
    }

    @Serializable
    data class TmdbPageResult<T>(
        @SerialName("results") val results: List<T>? = null,
        @SerialName("total_pages") val totalPages: Int? = null,
        @SerialName("total_results") val totalResults: Int? = null,
    )

    @Serializable
    data class TmdbMultiResult(
        @SerialName("results") val results: List<TmdbSearchResult>? = null,
    )

    @Serializable
    data class TmdbEpisode(
        @SerialName("id") val id: Int? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("overview") val overview: String? = null,
        @SerialName("episode_number") val episodeNumber: Int? = null,
        @SerialName("season_number") val seasonNumber: Int? = null,
        @SerialName("still_path") val stillPath: String? = null,
        @SerialName("air_date") val airDate: String? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        @SerialName("external_ids") val externalIds: TmdbIds? = null,
    )

    @Serializable
    data class TmdbSeasonDetail(
        @SerialName("season_number") val seasonNumber: Int? = null,
        @SerialName("episodes") val episodes: List<TmdbEpisode>? = null,
    )

    @Serializable
    data class TmdbSeasonSummary(
        @SerialName("season_number") val seasonNumber: Int? = null,
        @SerialName("episode_count") val episodeCount: Int? = null,
    )

    @Serializable
    data class TmdbContentRating(
        @SerialName("iso_3166_1") val country: String? = null,
        @SerialName("rating") val rating: String? = null,
    )

    @Serializable
    data class TmdbContentRatings(
        @SerialName("results") val results: List<TmdbContentRating>? = null,
    )

    @Serializable
    data class TmdbReleaseDateEntry(
        @SerialName("certification") val certification: String? = null,
        @SerialName("type") val type: Int? = null,
    )

    @Serializable
    data class TmdbReleaseDateResult(
        @SerialName("iso_3166_1") val country: String? = null,
        @SerialName("release_dates") val releaseDates: List<TmdbReleaseDateEntry>? = null,
    )

    @Serializable
    data class TmdbReleaseDates(
        @SerialName("results") val results: List<TmdbReleaseDateResult>? = null,
    )

    @Serializable
    data class TmdbTvDetail(
        @SerialName("id") val id: Int? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("original_name") val originalName: String? = null,
        @SerialName("overview") val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        @SerialName("genres") val genres: List<TmdbGenre>? = null,
        @SerialName("episode_run_time") val episodeRunTime: List<Int>? = null,
        @SerialName("seasons") val seasons: List<TmdbSeasonSummary>? = null,
        @SerialName("external_ids") val externalIds: TmdbIds? = null,
        @SerialName("videos") val videos: TmdbVideos? = null,
        @SerialName("credits") val credits: TmdbCredits? = null,
        @SerialName("recommendations") val recommendations: TmdbPageResult<TmdbSearchResult>? = null,
        @SerialName("similar") val similar: TmdbPageResult<TmdbSearchResult>? = null,
        @SerialName("content_ratings") val contentRatings: TmdbContentRatings? = null,
    ) {
        val displayTitle get() = name ?: originalName ?: ""
        val year get() = firstAirDate?.take(4)?.toIntOrNull()
    }

    @Serializable
    data class TmdbMovieDetail(
        @SerialName("id") val id: Int? = null,
        @SerialName("title") val title: String? = null,
        @SerialName("original_title") val originalTitle: String? = null,
        @SerialName("overview") val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        @SerialName("genres") val genres: List<TmdbGenre>? = null,
        @SerialName("runtime") val runtime: Int? = null,
        @SerialName("imdb_id") val imdbId: String? = null,
        @SerialName("external_ids") val externalIds: TmdbIds? = null,
        @SerialName("videos") val videos: TmdbVideos? = null,
        @SerialName("credits") val credits: TmdbCredits? = null,
        @SerialName("recommendations") val recommendations: TmdbPageResult<TmdbSearchResult>? = null,
        @SerialName("similar") val similar: TmdbPageResult<TmdbSearchResult>? = null,
        @SerialName("release_dates") val releaseDates: TmdbReleaseDates? = null,
    ) {
        val displayTitle get() = title ?: originalTitle ?: ""
        val year get() = releaseDate?.take(4)?.toIntOrNull()
    }

    private fun getImageUrl(link: String?): String? {
        link ?: return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500$link" else link
    }

    private fun getUrl(id: Int?, tvShow: Boolean): String {
        return if (tvShow) "https://www.themoviedb.org/tv/${id ?: -1}"
        else "https://www.themoviedb.org/movie/${id ?: -1}"
    }

    private suspend fun getApi(path: String, extraParams: Map<String, String> = emptyMap()): String {
        val params = buildMap {
            put("api_key", tmdbApiKey)
            putAll(extraParams)
        }
        return app.get(
            url = "$tmdbApiUrl$path",
            params = params,
        ).text
    }

    private fun TmdbSearchResult.toSearchResponse() = if (isTv) {
        newTvSeriesSearchResponse(
            name = displayTitle,
            url = getUrl(id, true),
            type = TvType.TvSeries,
            fix = false,
        ) {
            this.id = this@toSearchResponse.id
            this.posterUrl = getImageUrl(posterPath)
            this.score = Score.from10(voteAverage)
            this.year = this@toSearchResponse.year
        }
    } else {
        newMovieSearchResponse(
            name = displayTitle,
            url = getUrl(id, false),
            type = TvType.Movie,
            fix = false,
        ) {
            this.id = this@toSearchResponse.id
            this.posterUrl = getImageUrl(posterPath)
            this.score = Score.from10(voteAverage)
            this.year = this@toSearchResponse.year
        }
    }

    private fun List<TmdbCastMember?>?.toActors(): List<Pair<Actor, String?>>? {
        return this?.mapNotNull {
            it ?: return@mapNotNull null
            Pair(
                Actor(it.name ?: return@mapNotNull null, getImageUrl(it.profilePath)),
                it.character
            )
        }
    }

    private fun TmdbVideos?.toTrailers(): List<String>? {
        val skipTypes = setOf("Opening Credits", "Featurette")
        return this?.results
            ?.filter { it.type !in skipTypes }
            ?.sortedBy { it.type }
            ?.mapNotNull {
                when (it.site?.trim()?.lowercase()) {
                    "youtube" -> "https://www.youtube.com/watch?v=${it.key}"
                    else -> null
                }
            }
    }

    open suspend fun fetchContentRating(id: Int?, country: String): String? {
        id ?: return null
        // Try TV content ratings first
        val tvRating = parseJson<TmdbContentRatings>(
            getApi("/tv/$id/content_ratings")
        ).results?.firstOrNull { it.country == country }?.rating
        if (tvRating != null) return tvRating

        // Fall back to movie release dates
        return parseJson<TmdbReleaseDates>(
            getApi("/movie/$id/release_dates")
        ).results?.firstOrNull { it.country == country }
            ?.releaseDates?.firstOrNull { !it.certification.isNullOrBlank() }
            ?.certification
    }

    private suspend fun TmdbTvDetail.toLoadResponse(): TvSeriesLoadResponse {
        val episodes = mutableListOf<Episode>()
        val validSeasons = seasons?.filter { !disableSeasonZero || (it.seasonNumber ?: 0) != 0 }
            ?: emptyList()

        for (season in validSeasons) {
            val seasonNum = season.seasonNumber ?: continue
            val fullSeason = parseJson<TmdbSeasonDetail>(
                getApi("/tv/$id/season/$seasonNum", mapOf("append_to_response" to "external_ids"))
            )
            fullSeason.episodes?.forEach { episode ->
                episodes += newEpisode(
                    TmdbLink(
                        episode.externalIds?.imdbId ?: externalIds?.imdbId,
                        id,
                        episode.episodeNumber,
                        episode.seasonNumber,
                        displayTitle,
                    ).toJson()
                ) {
                    this.name = episode.name
                    this.season = episode.seasonNumber
                    this.episode = episode.episodeNumber
                    this.score = Score.from10(episode.voteAverage)
                    this.description = episode.overview
                    this.posterUrl = getImageUrl(episode.stillPath)
                    this.addDate(episode.airDate)
                }
            }
        }

        return newTvSeriesLoadResponse(
            displayTitle,
            getUrl(id, true),
            TvType.TvSeries,
            episodes,
        ) {
            posterUrl = getImageUrl(posterPath)
            this.year = this@toLoadResponse.year
            plot = overview
            addImdbId(externalIds?.imdbId)
            tags = genres?.mapNotNull { it.name }
            duration = episodeRunTime?.average()?.toInt()
            score = Score.from10(voteAverage)
            addTrailer(videos.toTrailers())
            recommendations = (this@toLoadResponse.recommendations
                ?: this@toLoadResponse.similar)?.results?.map { it.toSearchResponse() }
            addActors(credits?.cast?.toList().toActors())
            contentRating = contentRatings?.results?.firstOrNull { it.country == "US" }?.rating
                ?: fetchContentRating(id, "US")
        }
    }

    private suspend fun TmdbMovieDetail.toLoadResponse(): MovieLoadResponse {
        return newMovieLoadResponse(
            displayTitle,
            getUrl(id, false),
            TvType.Movie,
            TmdbLink(
                imdbId ?: externalIds?.imdbId,
                id,
                null,
                null,
                displayTitle,
            ).toJson()
        ) {
            posterUrl = getImageUrl(posterPath)
            this.year = this@toLoadResponse.year
            plot = overview
            addImdbId(imdbId ?: externalIds?.imdbId)
            tags = genres?.mapNotNull { it.name }
            duration = runtime
            score = Score.from10(voteAverage)
            addTrailer(videos.toTrailers())
            recommendations = (this@toLoadResponse.recommendations
                ?: this@toLoadResponse.similar)?.results?.map { it.toSearchResponse() }
            addActors(credits?.cast?.toList().toActors())
            contentRating = releaseDates?.results
                ?.firstOrNull { it.country == "US" }
                ?.releaseDates?.firstOrNull { !it.certification.isNullOrBlank() }
                ?.certification
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var discoverMovies: List<MovieSearchResponse> = listOf()
        var discoverSeries: List<TvSeriesSearchResponse> = listOf()
        var topMovies: List<MovieSearchResponse> = listOf()
        var topSeries: List<TvSeriesSearchResponse> = listOf()

        runAllAsync(
            {
                discoverMovies = parseJson<TmdbPageResult<TmdbSearchResult>>(
                    getApi("/discover/movie", mapOf("page" to "$page"))
                ).results?.map { it.toSearchResponse() as MovieSearchResponse } ?: listOf()
            },
            {
                discoverSeries = parseJson<TmdbPageResult<TmdbSearchResult>>(
                    getApi("/discover/tv", mapOf("page" to "$page"))
                ).results?.map { it.toSearchResponse() as TvSeriesSearchResponse } ?: listOf()
            },
            {
                topMovies = parseJson<TmdbPageResult<TmdbSearchResult>>(
                    getApi("/movie/top_rated", mapOf("page" to "$page", "language" to "en-US", "region" to "US"))
                ).results?.map { it.toSearchResponse() as MovieSearchResponse } ?: listOf()
            },
            {
                topSeries = parseJson<TmdbPageResult<TmdbSearchResult>>(
                    getApi("/tv/top_rated", mapOf("page" to "$page", "language" to "en-US"))
                ).results?.map { it.toSearchResponse() as TvSeriesSearchResponse } ?: listOf()
            },
        )

        return newHomePageResponse(
            listOf(
                HomePageList("Popular Movies", discoverMovies),
                HomePageList("Popular Series", discoverSeries),
                HomePageList("Top Movies", topMovies),
                HomePageList("Top Series", topSeries),
            )
        )
    }

    open fun loadFromImdb(imdb: String, seasons: List<TmdbSeasonSummary>): LoadResponse? = null
    open fun loadFromTmdb(tmdbId: Int, seasons: List<TmdbSeasonSummary>): LoadResponse? = null
    open fun loadFromImdb(imdb: String): LoadResponse? = null
    open fun loadFromTmdb(tmdbId: Int): LoadResponse? = null

    override suspend fun load(url: String): LoadResponse? {
        // https://www.themoviedb.org/movie/7445-brothers
        // https://www.themoviedb.org/tv/71914-the-wheel-of-time
        val idRegex = Regex("""themoviedb\.org/(.*)/(\d+)""")
        val found = idRegex.find(url)

        val isTvSeries = found?.groupValues?.getOrNull(1).equals("tv", ignoreCase = true)
        val id = found?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: throw ErrorLoadingException("No id found")

        return if (useMetaLoadResponse) {
            if (isTvSeries) {
                val detail = parseJson<TmdbTvDetail>(
                    getApi(
                        "/tv/$id",
                        mapOf(
                            "language" to "en-US",
                            "append_to_response" to "external_ids,videos,credits,recommendations,similar,content_ratings",
                        )
                    )
                )
                detail.toLoadResponse()
            } else {
                val detail = parseJson<TmdbMovieDetail>(
                    getApi(
                        "/movie/$id",
                        mapOf(
                            "language" to "en-US",
                            "append_to_response" to "external_ids,videos,credits,recommendations,similar,release_dates",
                        )
                    )
                )
                detail.toLoadResponse()
            }
        } else {
            loadFromTmdb(id)?.let { return it }
            if (isTvSeries) {
                val externalIds = parseJson<TmdbIds>(getApi("/tv/$id/external_ids"))
                val imdbId = externalIds.imdbId
                if (imdbId != null) {
                    val fromImdb = loadFromImdb(imdbId)
                    if (fromImdb != null) return fromImdb
                }
                val seasons = parseJson<TmdbTvDetail>(getApi("/tv/$id")).seasons ?: listOf()
                if (imdbId != null) {
                    loadFromImdb(imdbId, seasons) ?: loadFromTmdb(id, seasons)
                } else {
                    loadFromTmdb(id, seasons)
                }
            } else {
                val imdbId = parseJson<TmdbMovieDetail>(getApi("/movie/$id")).imdbId
                if (imdbId != null) loadFromImdb(imdbId) else null
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        return parseJson<TmdbMultiResult>(
            getApi(
                "/search/multi",
                mapOf(
                    "query" to query,
                    "page" to "$page",
                    "language" to "en-US",
                    "include_adult" to "$includeAdult",
                )
            )
        ).results?.mapNotNull {
            if (it.mediaType == "person") null else it.toSearchResponse()
        }?.toNewSearchResponseList()
    }
}
