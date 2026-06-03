package com.lagradost.cloudstream3.metaproviders

import com.lagradost.api.BuildConfig
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.isUpcoming
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

open class TraktProvider : MainAPI() {
    override var name = "Trakt"
    override val hasMainPage = true
    override val providerType = ProviderType.MetaProvider
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )

    private val traktApiUrl = "https://api.trakt.tv"
    private val traktClientId: String = BuildConfig.TRAKT_CLIENT_ID

    override val mainPage = mainPageOf(
        "$traktApiUrl/movies/trending" to "Trending Movies", //Most watched movies right now
        "$traktApiUrl/movies/popular" to "Popular Movies", //The most popular movies for all time
        "$traktApiUrl/shows/trending" to "Trending Shows", //Most watched Shows right now
        "$traktApiUrl/shows/popular" to "Popular Shows", //The most popular Shows for all time
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val apiResponse = getApi("${request.data}?extended=full,images&page=$page")

        val results = parseJson<List<MediaDetails>>(apiResponse).map { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, results)
    }

    private fun MediaDetails.toSearchResponse(): SearchResponse {

        val media = this.media ?: this
        val mediaType = if (media.ids?.tvdb == null) TvType.Movie else TvType.TvSeries
        val poster = media.images?.poster?.firstOrNull()
        return if (mediaType == TvType.Movie) {
            newMovieSearchResponse(
                name = media.title ?: "",
                url = Data(
                    type = mediaType,
                    mediaDetails = media,
                ).toJson(),
                type = TvType.Movie,
            ) {
                score = Score.from10(media.rating)
                posterUrl = fixPath(poster)
            }
        } else {
            newTvSeriesSearchResponse(
                name = media.title ?: "",
                url = Data(
                    type = mediaType,
                    mediaDetails = media,
                ).toJson(),
                type = TvType.TvSeries,
            ) {
                score = Score.from10(media.rating)
                this.posterUrl = fixPath(poster)
            }
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val apiResponse =
            getApi("$traktApiUrl/search/movie,show?extended=full,images&limit=20&page=$page&query=$query")

        return newSearchResponseList(parseJson<List<MediaDetails>>(apiResponse).map { element ->
            element.toSearchResponse()
        })
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<Data>(url)
        val mediaDetails = data.mediaDetails
        val moviesOrShows = if (data.type == TvType.Movie) "movies" else "shows"

        val posterUrl = fixPath(mediaDetails?.images?.poster?.firstOrNull())
        val backDropUrl = fixPath(mediaDetails?.images?.fanart?.firstOrNull())

        val resActor =
            getApi("$traktApiUrl/$moviesOrShows/${mediaDetails?.ids?.trakt}/people?extended=full,images")

        val actors = parseJson<People>(resActor).cast?.map {
            ActorData(
                Actor(
                    name = it.person?.name!!,
                    image = fixPath(it.person.images?.headshot?.firstOrNull())
                ),
                roleString = it.character
            )
        }

        val resRelated =
            getApi("$traktApiUrl/$moviesOrShows/${mediaDetails?.ids?.trakt}/related?extended=full,images&limit=20")

        val relatedMedia = parseJson<List<MediaDetails>>(resRelated).map { it.toSearchResponse() }

        val isCartoon =
            mediaDetails?.genres?.contains("animation") == true || mediaDetails?.genres?.contains("anime") == true
        val isAnime =
            isCartoon && (mediaDetails.language == "zh" || mediaDetails.language == "ja")
        val isAsian = !isAnime && (mediaDetails?.language == "zh" || mediaDetails?.language == "ko")
        val isBollywood = mediaDetails?.country == "in"
        val uniqueUrl = data.mediaDetails?.ids?.trakt?.toJson() ?: data.toJson()

        if (data.type == TvType.Movie) {

            val linkData = LinkData(
                id = mediaDetails?.ids?.tmdb,
                traktId = mediaDetails?.ids?.trakt,
                traktSlug = mediaDetails?.ids?.slug,
                tmdbId = mediaDetails?.ids?.tmdb,
                imdbId = mediaDetails?.ids?.imdb.toString(),
                tvdbId = mediaDetails?.ids?.tvdb,
                tvrageId = mediaDetails?.ids?.tvrage,
                type = data.type.toString(),
                title = mediaDetails?.title,
                year = mediaDetails?.year,
                orgTitle = mediaDetails?.title,
                isAnime = isAnime,
                //jpTitle = later if needed as it requires another network request,
                airedDate = mediaDetails?.released
                    ?: mediaDetails?.firstAired,
                isAsian = isAsian,
                isBollywood = isBollywood,
            ).toJson()

            return newMovieLoadResponse(
                name = mediaDetails?.title!!,
                url = data.toJson(),
                dataUrl = linkData.toJson(),
                type = if (isAnime) TvType.AnimeMovie else TvType.Movie,
            ) {
                this.uniqueUrl = uniqueUrl
                this.name = mediaDetails.title
                this.type = if (isAnime) TvType.AnimeMovie else TvType.Movie
                this.posterUrl = posterUrl
                this.year = mediaDetails.year
                this.plot = mediaDetails.overview
                this.score = Score.from10(mediaDetails.rating)
                this.tags = mediaDetails.genres
                this.duration = mediaDetails.runtime
                this.recommendations = relatedMedia
                this.actors = actors
                this.comingSoon = isUpcoming(mediaDetails.released)
                //posterHeaders
                this.backgroundPosterUrl = backDropUrl
                this.contentRating = mediaDetails.certification
                addTrailer(mediaDetails.trailer)
                addImdbId(mediaDetails.ids?.imdb)
                addTMDbId(mediaDetails.ids?.tmdb.toString())
            }
        } else {

            val resSeasons =
                getApi("$traktApiUrl/shows/${mediaDetails?.ids?.trakt.toString()}/seasons?extended=full,images,episodes")
            val episodes = mutableListOf<Episode>()
            val seasons = parseJson<List<Seasons>>(resSeasons)
            var nextAir: NextAiring? = null

            seasons.forEach { season ->

                season.episodes?.map { episode ->

                    val linkData = LinkData(
                        id = mediaDetails?.ids?.tmdb,
                        traktId = mediaDetails?.ids?.trakt,
                        traktSlug = mediaDetails?.ids?.slug,
                        tmdbId = mediaDetails?.ids?.tmdb,
                        imdbId = mediaDetails?.ids?.imdb.toString(),
                        tvdbId = mediaDetails?.ids?.tvdb,
                        tvrageId = mediaDetails?.ids?.tvrage,
                        type = data.type.toString(),
                        season = episode.season,
                        episode = episode.number,
                        title = mediaDetails?.title,
                        year = mediaDetails?.year,
                        orgTitle = mediaDetails?.title,
                        isAnime = isAnime,
                        airedYear = mediaDetails?.year,
                        lastSeason = seasons.size,
                        epsTitle = episode.title,
                        //jpTitle = later if needed as it requires another network request,
                        date = episode.firstAired,
                        airedDate = episode.firstAired,
                        isAsian = isAsian,
                        isBollywood = isBollywood,
                        isCartoon = isCartoon
                    ).toJson()

                    episodes.add(
                        newEpisode(linkData.toJson()) {
                            this.name = episode.title
                            this.season = episode.season
                            this.episode = episode.number
                            this.description = episode.overview
                            this.runTime = episode.runtime
                            this.posterUrl = fixPath( episode.images?.screenshot?.firstOrNull())
                            this.score = Score.from10(episode.rating)

                            this.addDate(episode.firstAired, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                            if (nextAir == null && this.date != null && this.date!! > unixTimeMS && this.season != 0) {
                                nextAir = NextAiring(
                                    episode = this.episode!!,
                                    unixTime = this.date!!.div(1000L),
                                    season = if (this.season == 1) null else this.season,
                                )
                            }
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(
                name = mediaDetails?.title!!,
                url = data.toJson(),
                type = if (isAnime) TvType.Anime else TvType.TvSeries,
                episodes = episodes
            ) {
                this.uniqueUrl = uniqueUrl
                this.name = mediaDetails.title
                this.type = if (isAnime) TvType.Anime else TvType.TvSeries
                this.episodes = episodes
                this.posterUrl = posterUrl
                this.year = mediaDetails.year
                this.plot = mediaDetails.overview
                this.showStatus = getStatus(mediaDetails.status)
                this.score = Score.from10(mediaDetails.rating)
                this.tags = mediaDetails.genres
                this.duration = mediaDetails.runtime
                this.recommendations = relatedMedia
                this.actors = actors
                this.comingSoon = isUpcoming(mediaDetails.released)
                //posterHeaders
                this.nextAiring = nextAir
                this.backgroundPosterUrl = backDropUrl
                this.contentRating = mediaDetails.certification
                addTrailer(mediaDetails.trailer)
                addImdbId(mediaDetails.ids?.imdb)
                addTMDbId(mediaDetails.ids?.tmdb.toString())
            }
        }
    }

    private suspend fun getApi(url: String): String {
        return app.get(
            url = url,
            headers = mapOf(
                "Content-Type" to "application/json",
                "trakt-api-version" to "2",
                "trakt-api-key" to traktClientId,
            )
        ).text
    }

    private fun getStatus(t: String?): ShowStatus {
        return when (t) {
            "returning series" -> ShowStatus.Ongoing
            "continuing" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }

    private fun fixPath(url: String?): String? {
        url ?: return null
        return "https://$url"
    }

    @Serializable
    data class Data(
        @SerialName("type") val type: TvType? = null,
        @SerialName("mediaDetails") val mediaDetails: MediaDetails? = null,
    )

    @OptIn(ExperimentalSerializationApi::class) // JsonNames is an experimental annotation for now
    @Serializable
    data class MediaDetails(
        @SerialName("title") val title: String? = null,
        @SerialName("year") val year: Int? = null,
        @SerialName("ids") val ids: Ids? = null,
        @SerialName("tagline") val tagline: String? = null,
        @SerialName("overview") val overview: String? = null,
        @SerialName("released") val released: String? = null,
        @SerialName("runtime") val runtime: Int? = null,
        @SerialName("country") val country: String? = null,
        @SerialName("updatedAt") val updatedAt: String? = null,
        @SerialName("trailer") val trailer: String? = null,
        @SerialName("homepage") val homepage: String? = null,
        @SerialName("status") val status: String? = null,
        @SerialName("rating") val rating: Double? = null,
        @SerialName("votes") val votes: Long? = null,
        @SerialName("comment_count") val commentCount: Long? = null,
        @SerialName("language") val language: String? = null,
        @SerialName("languages") val languages: List<String>? = null,
        @SerialName("available_translations") val availableTranslations: List<String>? = null,
        @SerialName("genres") val genres: List<String>? = null,
        @SerialName("certification") val certification: String? = null,
        @SerialName("aired_episodes") val airedEpisodes: Int? = null,
        @SerialName("first_aired") val firstAired: String? = null,
        @SerialName("airs") val airs: Airs? = null,
        @SerialName("network") val network: String? = null,
        @SerialName("images") val images: Images? = null,
        @SerialName("movie") @JsonNames("show") val media: MediaDetails? = null
    )

    @Serializable
    data class Airs(
        @SerialName("day") val day: String? = null,
        @SerialName("time") val time: String? = null,
        @SerialName("timezone") val timezone: String? = null,
    )

    @Serializable
    data class Ids(
        @SerialName("trakt") val trakt: Int? = null,
        @SerialName("slug") val slug: String? = null,
        @SerialName("tvdb") val tvdb: Int? = null,
        @SerialName("imdb") val imdb: String? = null,
        @SerialName("tmdb") val tmdb: Int? = null,
        @SerialName("tvrage") val tvrage: String? = null,
    )

    @Serializable
    data class Images(
        @SerialName("poster") val poster: List<String>? = null,
        @SerialName("fanart") val fanart: List<String>? = null,
        @SerialName("logo") val logo: List<String>? = null,
        @SerialName("clearart") val clearArt: List<String>? = null,
        @SerialName("banner") val banner: List<String>? = null,
        @SerialName("thumb") val thumb: List<String>? = null,
        @SerialName("screenshot") val screenshot: List<String>? = null,
        @SerialName("headshot") val headshot: List<String>? = null,
    )

    @Serializable
    data class People(
        @SerialName("cast") val cast: List<Cast>? = null,
    )

    @Serializable
    data class Cast(
        @SerialName("character") val character: String? = null,
        @SerialName("characters") val characters: List<String>? = null,
        @SerialName("episode_count") val episodeCount: Long? = null,
        @SerialName("person") val person: Person? = null,
        @SerialName("images") val images: Images? = null,
    )

    @Serializable
    data class Person(
        @SerialName("name") val name: String? = null,
        @SerialName("ids") val ids: Ids? = null,
        @SerialName("images") val images: Images? = null,
    )

    @Serializable
    data class Seasons(
        @SerialName("aired_episodes") val airedEpisodes: Int? = null,
        @SerialName("episode_count") val episodeCount: Int? = null,
        @SerialName("episodes") val episodes: List<TraktEpisode>? = null,
        @SerialName("first_aired") val firstAired: String? = null,
        @SerialName("ids") val ids: Ids? = null,
        @SerialName("images") val images: Images? = null,
        @SerialName("network") val network: String? = null,
        @SerialName("number") val number: Int? = null,
        @SerialName("overview") val overview: String? = null,
        @SerialName("rating") val rating: Double? = null,
        @SerialName("title") val title: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("votes") val votes: Int? = null,
    )

    @Serializable
    data class TraktEpisode(
        @SerialName("available_translations") val availableTranslations: List<String>? = null,
        @SerialName("comment_count") val commentCount: Int? = null,
        @SerialName("episode_type") val episodeType: String? = null,
        @SerialName("first_aired") val firstAired: String? = null,
        @SerialName("ids") val ids: Ids? = null,
        @SerialName("images") val images: Images? = null,
        @SerialName("number") val number: Int? = null,
        @SerialName("number_abs") val numberAbs: Int? = null,
        @SerialName("overview") val overview: String? = null,
        @SerialName("rating") val rating: Double? = null,
        @SerialName("runtime") val runtime: Int? = null,
        @SerialName("season") val season: Int? = null,
        @SerialName("title") val title: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("votes") val votes: Int? = null,
    )

    @Serializable
    data class LinkData(
        @SerialName("id") val id: Int? = null,
        @SerialName("trakt_id") val traktId: Int? = null,
        @SerialName("trakt_slug") val traktSlug: String? = null,
        @SerialName("tmdb_id") val tmdbId: Int? = null,
        @SerialName("imdb_id") val imdbId: String? = null,
        @SerialName("tvdb_id") val tvdbId: Int? = null,
        @SerialName("tvrage_id") val tvrageId: String? = null,
        @SerialName("type") val type: String? = null,
        @SerialName("season") val season: Int? = null,
        @SerialName("episode") val episode: Int? = null,
        @SerialName("ani_id") val aniId: String? = null,
        @SerialName("anime_id") val animeId: String? = null,
        @SerialName("title") val title: String? = null,
        @SerialName("year") val year: Int? = null,
        @SerialName("org_title") val orgTitle: String? = null,
        @SerialName("is_anime") val isAnime: Boolean = false,
        @SerialName("aired_year") val airedYear: Int? = null,
        @SerialName("last_season") val lastSeason: Int? = null,
        @SerialName("eps_title") val epsTitle: String? = null,
        @SerialName("jp_title") val jpTitle: String? = null,
        @SerialName("date") val date: String? = null,
        @SerialName("aired_date") val airedDate: String? = null,
        @SerialName("is_asian") val isAsian: Boolean = false,
        @SerialName("is_bollywood") val isBollywood: Boolean = false,
        @SerialName("is_cartoon") val isCartoon: Boolean = false,
    )
}
