package com.lagradost.cloudstream3.metaproviders

import com.lagradost.cloudstream3.*
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.util.Locale
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

open class TraktProvider : MainAPI() {
    override var name = "Trakt"
    override val hasMainPage = true
    override val providerType = ProviderType.MetaProvider
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )
    // Override this to control random Posters on every extension reload
    open val randomPosters = false
    private val traktClientId = "d9f434f48b55683a279ffe88ddc68351cc04c9dc9372bd95af5de780b794e770"
    private val tmdbApiKey = "e6333b32409e02a4a6eba6fb7ff866bb"
    private val traktApiUrl = "https://api.trakt.tv"
    private val tmdbApiUrl = "https://api.themoviedb.org/3"

    // You can override mainPage in extension to pass the needed APIs.
    override val mainPage = mainPageOf(
        "$traktApiUrl/movies/trending" to "Trending Movies", //Most watched movies right now
        "$traktApiUrl/movies/popular" to "Popular Movies", //The most popular movies for all time
        "$traktApiUrl/shows/trending" to "Trending Shows", //Most watched Shows right now
        "$traktApiUrl/shows/popular" to "Popular Shows", //The most popular Shows for all time
    )
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val apiResponse = getApi("${request.data}?page=$page")

        val results = parseJson<List<Results>>(apiResponse).map { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, results)
    }
    private suspend fun Results.toSearchResponse(): SearchResponse {

        val ids = this.ids ?: this.media?.ids

        val tmdbId = ids?.tmdb.toString()
        val tvdbId = ids?.tvdb

        val mediaType = if (tvdbId == null) TvType.Movie else TvType.TvSeries

        if (mediaType == TvType.Movie) {
            val images = getImages(tmdbId, true)
            val posterUrl = getPosterUrl(images, randomPosters)

            return newMovieSearchResponse(
                name = this.media?.title ?: this.title!!,
                url = Data(
                    title = this.media?.title ?: this.title!!,
                    movieBool = true,
                    ids = ids,
                    images = images,
                    type = mediaType
                ).toJson(),
                type = TvType.Movie,
            ) {
                this.posterUrl = posterUrl
            }
        } else {
            val images = getImages(tmdbId, false)
            val posterUrl = getPosterUrl(images, randomPosters)

            return newTvSeriesSearchResponse(
                name = this.media?.title ?: this.title!!,
                url = Data(
                    title = this.media?.title ?: this.title!!,
                    movieBool = false,
                    ids = ids,
                    images = images,
                    type = mediaType
                ).toJson(),
                type = TvType.TvSeries,
            ) {
                this.posterUrl = posterUrl
            }
        }
    }
    override suspend fun search(query: String): List<SearchResponse>? {
        val apiResponse = getApi("$traktApiUrl/search/movie,show?query=$query")

        val results = parseJson<List<Results>>(apiResponse).map { element ->
            element.toSearchResponse()
        }

        return results
    }
    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<Data>(url)

        val posterUrl = getOrigPosterUrl(data.images, randomPosters)
        val backDropUrl = getBackdropUrl(data.images)

        val moviesOrShows = if (data.movieBool) "movies" else "shows"
        val res = getApi("$traktApiUrl/$moviesOrShows/${data.ids?.trakt}?extended=full")

        val mediaDetails = parseJson<MediaDetails>(res)

        //Trakt ID, Trakt slug, or IMDB ID
        val peopleDetails = getProfileImages(data.ids?.tmdb.toString(), data.movieBool)

        val actors = peopleDetails.cast?.filter { it.knownForDepartment == "Acting" }?.map {
            ActorData(
                Actor(
                    name = it.name,
                    image = getImageUrl(it.profilePath)
                ),
                roleString = it.character
            )
        }

        val resRelated = getApi("$traktApiUrl/$moviesOrShows/${data.ids?.trakt}/related")

        val relatedMedia = parseJson<List<Results>>(resRelated).map { it.toSearchResponse() }

        val isCartoon = (mediaDetails.genres?.contains("animation") == true || mediaDetails.genres?.contains("anime") == true)
        val isAnime = isCartoon && (mediaDetails.language == "zh" || mediaDetails.language == "ja")
        val isAsian = !isAnime && (mediaDetails.language == "zh" || mediaDetails.language == "ko")
        val isBollywood = mediaDetails.country == "in"

        if (data.type == TvType.Movie) {

            val linkData = LinkData(
                id = mediaDetails.ids?.tmdb,
                imdbId = mediaDetails.ids?.imdb.toString(),
                tvdbId = mediaDetails.ids?.tvdb,
                type = data.type.toString(),
                title = mediaDetails.title,
                year = mediaDetails.year,
                orgTitle = mediaDetails.title,
                isAnime = isAnime,
                //jpTitle = later if needed as it requires another network request,
                airedDate = mediaDetails.released
                    ?: mediaDetails.firstAired,
                isAsian = isAsian,
                isBollywood = isBollywood,
            ).toJson()

            return newMovieLoadResponse(
                name = mediaDetails.title,
                url = data.toJson(),
                dataUrl = linkData.toJson(),
                type = if (isAnime) TvType.AnimeMovie else TvType.Movie,
            ) {
                this.name = mediaDetails.title
                this.apiName = "Trakt"
                this.type = if (isAnime) TvType.AnimeMovie else TvType.Movie
                this.posterUrl = posterUrl
                this.year = mediaDetails.year
                this.plot = mediaDetails.overview
                this.rating = mediaDetails.rating?.times(1000)?.roundToInt()
                this.tags = mediaDetails.genres
                this.duration = mediaDetails.runtime
                addTrailer(mediaDetails.trailer)
                this.recommendations = relatedMedia
                this.actors = actors
                this.comingSoon = isUpcoming(mediaDetails.released)
                //posterHeaders
                this.backgroundPosterUrl = backDropUrl
                this.contentRating = mediaDetails.certification
            }
        } else {

            val resSeasons = getApi("$traktApiUrl/shows/${data.ids?.trakt.toString()}/seasons?extended=episodes")
            val episodes = mutableListOf<Episode>()

            val seasons = parseJson<List<Seasons>>(resSeasons)

            seasons.forEach { season ->
                parseJson<TmdbSeason>(

                    // Using tmdb here because trakt requires a network request for every episode to get its details which make the ext so slow

                    getApi("$tmdbApiUrl/tv/${data.ids?.tmdb.toString()}/season/${season.number}?api_key=$tmdbApiKey")
                ).episodes?.map { episode ->

                    val linkData = LinkData(
                        id = mediaDetails.ids?.tmdb,
                        imdbId = mediaDetails.ids?.imdb.toString(),
                        tvdbId = mediaDetails.ids?.tvdb,
                        type = data.type.toString(),
                        season = episode.seasonNumber,
                        episode = episode.episodeNumber,
                        title = mediaDetails.title,
                        year = mediaDetails.year,
                        orgTitle = mediaDetails.title,
                        isAnime = isAnime,
                        airedYear = mediaDetails.year,
                        lastSeason = seasons.size,
                        epsTitle = episode.name,
                        //jpTitle = later if needed as it requires another network request,
                        date = episode.airDate,
                        airedDate = episode.airDate,
                        isAsian = isAsian,
                        isBollywood = isBollywood,
                        isCartoon = isCartoon
                    ).toJson()

                    episodes.add(
                        Episode(
                            data = linkData.toJson(),
                            name = episode.name  + if (isUpcoming(episode.airDate)) " • [UPCOMING]" else "",
                            season = episode.seasonNumber,
                            episode = episode.episodeNumber,
                            posterUrl = getImageUrl(episode.stillPath),
                            rating = episode.voteAverage?.times(10)?.roundToInt(),
                            description = episode.overview,
                        ).apply {
                            this.addDate(episode.airDate)
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(
                name = mediaDetails.title,
                url = data.toJson(),
                type = if (isAnime) TvType.Anime else TvType.TvSeries,
                episodes = episodes
            ) {
                this.name = mediaDetails.title
                this.apiName = "Trakt"
                this.type = if (isAnime) TvType.Anime else TvType.TvSeries
                this.episodes = episodes
                this.posterUrl = posterUrl
                this.year = mediaDetails.year
                this.plot = mediaDetails.overview
                this.showStatus = getStatus(mediaDetails.status)
                this.rating = mediaDetails.rating?.times(1000)?.roundToInt()
                this.tags = mediaDetails.genres
                this.duration = mediaDetails.runtime
                addTrailer(mediaDetails.trailer)
                this.recommendations = relatedMedia
                this.actors = actors
                //comingSoon
                //posterHeaders
                this.backgroundPosterUrl = backDropUrl
                this.contentRating = mediaDetails.certification
            }
        }
    }
    private suspend fun getApi(url: String) : String {
        return app.get(
            url = url,
            headers = mapOf(
                "Content-Type" to "application/json",
                "trakt-api-version" to "2",
                "trakt-api-key" to traktClientId,
            )
        ).toString()
    }

    private fun getPosterUrl(images: Images, random: Boolean) : String? {

        val imagePath = when {
            random -> {
                images.posters?.filter { it.iso6391 == "en" }?.randomOrNull() ?: images.posters?.randomOrNull()
            }
            else -> {
                images.posters?.firstOrNull { it.iso6391 == "en" } ?: images.posters?.firstOrNull()
            }
        }
            ?: return null
        return "https://image.tmdb.org/t/p/w500${imagePath.filePath}"

    }

    private fun getOrigPosterUrl(images: Images, random: Boolean) : String? {

        val imagePath = when {
            random -> {
                images.posters?.filter { it.iso6391 == null }?.randomOrNull() ?: images.posters?.randomOrNull()
            }
            else -> {
                images.posters?.firstOrNull { it.iso6391 == null } ?: images.posters?.firstOrNull()
            }
        }
            ?: return null
        return "https://image.tmdb.org/t/p/original${imagePath.filePath}"
    }

    private fun getBackdropUrl(images: Images) : String? {
        val imagePath = images.backdrops?.filter { it.iso6391 == null }?.randomOrNull() ?: images.backdrops?.randomOrNull()
        ?: return null
        return "https://image.tmdb.org/t/p/original${imagePath.filePath}"
    }

    private fun getImageUrl(path: String?) : String? {
        if (path == null) return null
        return "https://image.tmdb.org/t/p/w500${path}"
    }

    private suspend fun getProfileImages(castTmdbId: String, movie: Boolean) : Images {
        val imageApiUrl = if (movie) {
            "$tmdbApiUrl/movie/$castTmdbId/credits?api_key=$tmdbApiKey"
        } else {
            "$tmdbApiUrl/tv/$castTmdbId/credits?api_key=$tmdbApiKey"
        }

        val req = app.get(
            url = imageApiUrl,
            headers = mapOf(
                "accept" to "application/json",
            )
        ).toString()
        return parseJson<Images>(req)
    }

    private suspend fun getImages(tmdbId: String, movie: Boolean = true) : Images {

        val imageApiUrl = if (movie) {
            "$tmdbApiUrl/movie/${tmdbId}/images?api_key=${tmdbApiKey}"
        } else {
            "$tmdbApiUrl/tv/${tmdbId}/images?api_key=${tmdbApiKey}"
        }

        val req = app.get(
            url = imageApiUrl,
            headers = mapOf(
                "accept" to "application/json",
            )
        ).toString()

        return parseJson<Images>(req)
    }

    private fun isUpcoming(dateString: String?): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
            System.currentTimeMillis() < dateTime
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    private fun getStatus(t: String?): ShowStatus {
        return when (t) {
            "returning series" -> ShowStatus.Ongoing
            "continuing" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }

    data class Data(
        val title: String,
        val movieBool: Boolean,
        val type: TvType,
        val ids: Ids? = null,
        val images: Images,
    )

    data class Results(
        val watchers: Long? = null,
        val revenue: Long? = null,
        val title: String? = null,
        val year: Int? = null,
        @JsonProperty("movie")
        @JsonAlias("show")
        val media: Media? = null,
        val ids: Ids? = null,
    )

    data class Media(
        val title: String,
        val year: Int,
        val ids: Ids,
    )

    data class MediaDetails(
        val title: String,
        val year: Int? = null,
        val ids: Ids? = null,
        val tagline: String? = null,
        val overview: String? = null,
        val released: String? = null,
        val runtime: Int? = null,
        val country: String? = null,
        @JsonProperty("updated_at")
        val updatedAt: String? = null,
        val trailer: String? = null,
        val homepage: String? = null,
        val status: String? = null,
        val rating: Double? = null,
        val votes: Long? = null,
        @JsonProperty("comment_count")
        val commentCount: Long? = null,
        val language: String? = null,
        val languages: List<String>? = null,
        @JsonProperty("available_translations")
        val availableTranslations: List<String>? = null,
        val genres: List<String>? = null,
        val certification: String? = null,
        @JsonProperty("aired_episodes")
        val airedEpisodes: Int? = null,
        @JsonProperty("first_aired")
        val firstAired: String? = null,
        val airs: Airs? = null,
        val network: String? = null,
    )

    data class Airs(
        val day: String? = null,
        val time: String? = null,
        val timezone: String? = null,
    )

    data class Ids(
        val trakt: Int? = null,
        val slug: String? = null,
        val tvdb: Int? = null,
        val imdb: String? = null,
        val tmdb: Int? = null,
        val tvrage: String? = null,
    )

    data class Images(
        val backdrops: List<Backdrop>? = null,
        val id: Long? = null,
        val logos: List<Logo>? = null,
        val posters: List<Poster>? = null,
        val cast: List<Cast>? = null,
    )

    data class Backdrop(
        @JsonProperty("aspect_ratio")
        val aspectRatio: Double? = null,
        val height: Long? = null,
        @JsonProperty("iso_639_1")
        val iso6391: String? = null,
        @JsonProperty("file_path")
        val filePath: String? = null,
        @JsonProperty("vote_average")
        val voteAverage: Double? = null,
        @JsonProperty("vote_count")
        val voteCount: Long? = null,
        val width: Long? = null,
    )

    data class Logo(
        @JsonProperty("aspect_ratio")
        val aspectRatio: Double? = null,
        val height: Long? = null,
        @JsonProperty("iso_639_1")
        val iso6391: String? = null,
        @JsonProperty("file_path")
        val filePath: String? = null,
        @JsonProperty("vote_average")
        val voteAverage: Double? = null,
        @JsonProperty("vote_count")
        val voteCount: Long? = null,
        val width: Long? = null,
    )

    data class Poster(
        @JsonProperty("aspect_ratio")
        val aspectRatio: Double? = null,
        val height: Long? = null,
        @JsonProperty("iso_639_1")
        val iso6391: String? = null,
        @JsonProperty("file_path")
        val filePath: String? = null,
        @JsonProperty("vote_average")
        val voteAverage: Double? = null,
        @JsonProperty("vote_count")
        val voteCount: Long? = null,
        val width: Long? = null,
    )

    data class Cast(
        val adult: Boolean? = null,
        val gender: Long? = null,
        val id: Long? = null,
        @JsonProperty("known_for_department")
        val knownForDepartment: String? = null,
        val name: String,
        @JsonProperty("original_name")
        val originalName: String? = null,
        val popularity: Double? = null,
        @JsonProperty("profile_path")
        val profilePath: String? = null,
        @JsonProperty("cast_id")
        val castId: Long? = null,
        val character: String? = null,
        @JsonProperty("credit_id")
        val creditId: String? = null,
        val order: Long? = null,
    )

    data class Seasons(
        val number: Int? = null,
        val ids: Ids? = null,
        @JsonProperty("episodes")
        val episodes: List<TraktEpisode>? = null,
    )

    data class TraktEpisode(
        val season: Int? = null,
        val number: Int? = null,
        val title: String? = null,
        val ids: Ids? = null,
    )

    data class TmdbSeason(
        @JsonProperty("_id")
        val id: String? = null,
        @JsonProperty("air_date")
        val airDate: String? = null,
        val episodes: List<TmdbEpisode>? = null,
        val name: String? = null,
        val overview: String? = null,
        @JsonProperty("id")
        val id2: Int? = null,
        @JsonProperty("poster_path")
        val posterPath: String? = null,
        @JsonProperty("season_number")
        val seasonNumber: Int? = null,
        @JsonProperty("vote_average")
        val voteAverage: Double? = null,
    )

    data class TmdbEpisode(
        @JsonProperty("air_date")
        val airDate: String? = null,
        @JsonProperty("episode_number")
        val episodeNumber: Int? = null,
        @JsonProperty("episode_type")
        val episodeType: String? = null,
        val id: Int? = null,
        val name: String? = null,
        val overview: String? = null,
        @JsonProperty("production_code")
        val productionCode: String? = null,
        val runtime: Int? = null,
        @JsonProperty("season_number")
        val seasonNumber: Int? = null,
        @JsonProperty("show_id")
        val showId: Int? = null,
        @JsonProperty("still_path")
        val stillPath: String? = null,
        @JsonProperty("vote_average")
        val voteAverage: Double? = null,
        @JsonProperty("vote_count")
        val voteCount: Int? = null,
        //val crew: List<Crew>,
        //@JsonProperty("guest_stars")
        //val guestStars: List<GuestStar>,
    )

    data class LinkData(
        val id: Int? = null,
        val imdbId: String? = null,
        val tvdbId: Int? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val aniId: String? = null,
        val animeId: String? = null,
        val title: String? = null,
        val year: Int? = null,
        val orgTitle: String? = null,
        val isAnime: Boolean = false,
        val airedYear: Int? = null,
        val lastSeason: Int? = null,
        val epsTitle: String? = null,
        val jpTitle: String? = null,
        val date: String? = null,
        val airedDate: String? = null,
        val isAsian: Boolean = false,
        val isBollywood: Boolean = false,
        val isCartoon: Boolean = false,
    )
}