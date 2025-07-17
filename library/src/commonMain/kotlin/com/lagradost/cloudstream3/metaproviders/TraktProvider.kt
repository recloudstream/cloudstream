package com.lagradost.cloudstream3.metaproviders

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
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

    private val traktClientId =
        base64Decode("N2YzODYwYWQzNGI4ZTZmOTdmN2I5MTA0ZWQzMzEwOGI0MmQ3MTdlMTM0MmM2NGMxMTg5NGE1MjUyYTQ3NjE3Zg==")
    private val traktApiUrl = base64Decode("aHR0cHM6Ly9hcGl6LnRyYWt0LnR2")

    override val mainPage = mainPageOf(
        "$traktApiUrl/movies/trending" to "Trending Movies", //Most watched movies right now
        "$traktApiUrl/movies/popular" to "Popular Movies", //The most popular movies for all time
        "$traktApiUrl/shows/trending" to "Trending Shows", //Most watched Shows right now
        "$traktApiUrl/shows/popular" to "Popular Shows", //The most popular Shows for all time
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val apiResponse = getApi("${request.data}?extended=cloud9,full&page=$page")

        val results = parseJson<List<MediaDetails>>(apiResponse).map { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, results)
    }

    private fun MediaDetails.toSearchResponse(): SearchResponse {

        val media = this.media ?: this
        val mediaType = if (media.ids?.tvdb == null) TvType.Movie else TvType.TvSeries
        val poster = media.images?.poster?.firstOrNull()

        if (mediaType == TvType.Movie) {
            return newMovieSearchResponse(
                name = media.title ?: "",
                url = Data(
                    type = mediaType,
                    mediaDetails = media,
                ).toJson(),
                type = TvType.Movie,
            ) {
                posterUrl = fixPath(poster)
            }
        } else {
            return newTvSeriesSearchResponse(
                name = media.title ?: "",
                url = Data(
                    type = mediaType,
                    mediaDetails = media,
                ).toJson(),
                type = TvType.TvSeries,
            ) {
                this.posterUrl = fixPath(poster)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val apiResponse =
            getApi("$traktApiUrl/search/movie,show?extended=cloud9,full&limit=20&page=1&query=$query")

        val results = parseJson<List<MediaDetails>>(apiResponse).map { element ->
            element.toSearchResponse()
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {

        val data = parseJson<Data>(url)
        val mediaDetails = data.mediaDetails
        val moviesOrShows = if (data.type == TvType.Movie) "movies" else "shows"

        val posterUrl = mediaDetails?.images?.poster?.firstOrNull()
        val backDropUrl = mediaDetails?.images?.fanart?.firstOrNull()

        val resActor =
            getApi("$traktApiUrl/$moviesOrShows/${mediaDetails?.ids?.trakt}/people?extended=cloud9,full")

        val actors = parseJson<People>(resActor).cast?.map {
            ActorData(
                Actor(
                    name = it.person?.name!!,
                    image = getWidthImageUrl(it.person.images?.headshot?.firstOrNull(), "w500")
                ),
                roleString = it.character
            )
        }

        val resRelated =
            getApi("$traktApiUrl/$moviesOrShows/${mediaDetails?.ids?.trakt}/related?extended=cloud9,full&limit=20")

        val relatedMedia = parseJson<List<MediaDetails>>(resRelated).map { it.toSearchResponse() }

        val isCartoon =
            mediaDetails?.genres?.contains("animation") == true || mediaDetails?.genres?.contains("anime") == true
        val isAnime =
            isCartoon && (mediaDetails?.language == "zh" || mediaDetails?.language == "ja")
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
                this.posterUrl = getOriginalWidthImageUrl(posterUrl)
                this.year = mediaDetails.year
                this.plot = mediaDetails.overview
                this.score = Score.from10(mediaDetails.rating)
                this.tags = mediaDetails.genres
                this.duration = mediaDetails.runtime
                this.recommendations = relatedMedia
                this.actors = actors
                this.comingSoon = isUpcoming(mediaDetails.released)
                //posterHeaders
                this.backgroundPosterUrl = getOriginalWidthImageUrl(backDropUrl)
                this.contentRating = mediaDetails.certification
                addTrailer(mediaDetails.trailer)
                addImdbId(mediaDetails.ids?.imdb)
                addTMDbId(mediaDetails.ids?.tmdb.toString())
            }
        } else {

            val resSeasons =
                getApi("$traktApiUrl/shows/${mediaDetails?.ids?.trakt.toString()}/seasons?extended=cloud9,full,episodes")
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
                            this.posterUrl = fixPath(episode.images?.screenshot?.firstOrNull())
                            this.rating = episode.rating?.times(10)?.roundToInt()

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
                this.posterUrl = getOriginalWidthImageUrl(posterUrl)
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
                this.backgroundPosterUrl = getOriginalWidthImageUrl(backDropUrl)
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
        ).toString()
    }

    private fun isUpcoming(dateString: String?): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
            unixTimeMS < dateTime
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

    private fun fixPath(url: String?): String? {
        url ?: return null
        return "https://$url"
    }

    private fun getWidthImageUrl(path: String?, width: String): String? {
        if (path == null) return null
        if (!path.contains("image.tmdb.org")) return fixPath(path)
        val fileName = URI(path).path?.substringAfterLast('/') ?: return null
        return "https://image.tmdb.org/t/p/${width}/${fileName}"
    }

    private fun getOriginalWidthImageUrl(path: String?): String? {
        if (path == null) return null
        if (!path.contains("image.tmdb.org")) return fixPath(path)
        return getWidthImageUrl(path, "original")
    }

    data class Data(
        val type: TvType? = null,
        val mediaDetails: MediaDetails? = null,
    )

    data class MediaDetails(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("ids") val ids: Ids? = null,
        @JsonProperty("tagline") val tagline: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("released") val released: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("country") val country: String? = null,
        @JsonProperty("updatedAt") val updatedAt: String? = null,
        @JsonProperty("trailer") val trailer: String? = null,
        @JsonProperty("homepage") val homepage: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("rating") val rating: Double? = null,
        @JsonProperty("votes") val votes: Long? = null,
        @JsonProperty("comment_count") val commentCount: Long? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("languages") val languages: List<String>? = null,
        @JsonProperty("available_translations") val availableTranslations: List<String>? = null,
        @JsonProperty("genres") val genres: List<String>? = null,
        @JsonProperty("certification") val certification: String? = null,
        @JsonProperty("aired_episodes") val airedEpisodes: Int? = null,
        @JsonProperty("first_aired") val firstAired: String? = null,
        @JsonProperty("airs") val airs: Airs? = null,
        @JsonProperty("network") val network: String? = null,
        @JsonProperty("images") val images: Images? = null,
        @JsonProperty("movie") @JsonAlias("show") val media: MediaDetails? = null
    )

    data class Airs(
        @JsonProperty("day") val day: String? = null,
        @JsonProperty("time") val time: String? = null,
        @JsonProperty("timezone") val timezone: String? = null,
    )

    data class Ids(
        @JsonProperty("trakt") val trakt: Int? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("tvdb") val tvdb: Int? = null,
        @JsonProperty("imdb") val imdb: String? = null,
        @JsonProperty("tmdb") val tmdb: Int? = null,
        @JsonProperty("tvrage") val tvrage: String? = null,
    )

    data class Images(
        @JsonProperty("fanart") val fanart: List<String>? = null,
        @JsonProperty("poster") val poster: List<String>? = null,
        @JsonProperty("logo") val logo: List<String>? = null,
        @JsonProperty("clearart") val clearart: List<String>? = null,
        @JsonProperty("banner") val banner: List<String>? = null,
        @JsonProperty("thumb") val thumb: List<String>? = null,
        @JsonProperty("screenshot") val screenshot: List<String>? = null,
        @JsonProperty("headshot") val headshot: List<String>? = null,
    )

    data class People(
        @JsonProperty("cast") val cast: List<Cast>? = null,
    )

    data class Cast(
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("characters") val characters: List<String>? = null,
        @JsonProperty("episode_count") val episodeCount: Long? = null,
        @JsonProperty("person") val person: Person? = null,
        @JsonProperty("images") val images: Images? = null,
    )

    data class Person(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("ids") val ids: Ids? = null,
        @JsonProperty("images") val images: Images? = null,
    )

    data class Seasons(
        @JsonProperty("aired_episodes") val airedEpisodes: Int? = null,
        @JsonProperty("episode_count") val episodeCount: Int? = null,
        @JsonProperty("episodes") val episodes: List<TraktEpisode>? = null,
        @JsonProperty("first_aired") val firstAired: String? = null,
        @JsonProperty("ids") val ids: Ids? = null,
        @JsonProperty("images") val images: Images? = null,
        @JsonProperty("network") val network: String? = null,
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("rating") val rating: Double? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("updated_at") val updatedAt: String? = null,
        @JsonProperty("votes") val votes: Int? = null,
    )

    data class TraktEpisode(
        @JsonProperty("available_translations") val availableTranslations: List<String>? = null,
        @JsonProperty("comment_count") val commentCount: Int? = null,
        @JsonProperty("episode_type") val episodeType: String? = null,
        @JsonProperty("first_aired") val firstAired: String? = null,
        @JsonProperty("ids") val ids: Ids? = null,
        @JsonProperty("images") val images: Images? = null,
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("number_abs") val numberAbs: Int? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("rating") val rating: Double? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("updated_at") val updatedAt: String? = null,
        @JsonProperty("votes") val votes: Int? = null,
    )

    data class LinkData(
        val id: Int? = null,
        val traktId: Int? = null,
        val traktSlug: String? = null,
        val tmdbId: Int? = null,
        val imdbId: String? = null,
        val tvdbId: Int? = null,
        val tvrageId: String? = null,
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