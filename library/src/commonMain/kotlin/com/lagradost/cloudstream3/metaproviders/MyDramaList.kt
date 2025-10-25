package com.lagradost.cloudstream3.metaproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.BuildConfig
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
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
import okhttp3.Interceptor
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

//Reference: https://mydramalist.github.io/MDL-API/
abstract class MyDramaListAPI : MainAPI() {
    override var name = "MyDramaList"
    override val hasMainPage = true
    override val providerType = ProviderType.MetaProvider
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
    )

    companion object {
        const val TAG = "MyDramaList"
        val API_KEY: String = BuildConfig.MDL_API_KEY
        const val API_HOST = "https://api.mydramalist.com/v1"
        const val SITE_HOST = "https://mydramalist.com"
        private val headerInterceptor = MyDramaListInterceptor()
    }

    /** Automatically adds required api headers */
    private class MyDramaListInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            return chain.proceed(
                chain.request().newBuilder()
                    .removeHeader("user-agent")
                    .addHeader("user-agent", "Dart/3.6 (dart:io)")
                    .addHeader("mdl-api-key", API_KEY)
                    .build()
            )
        }
    }

    override val mainPage = mainPageOf(
        "$API_HOST/titles/trending?type=shows" to "Trending Shows This week",
        "$API_HOST/titles/top_airing?type=shows" to "Top Airing Shows",
        "$API_HOST/titles/upcoming?type=shows" to "Upcoming Shows",
        "$API_HOST/titles/trending?type=movies" to "Trending Movies This week",
        "$API_HOST/titles/top_movies?type=movies" to "Top Movies",
        "$API_HOST/titles/upcoming?type=movies" to "Upcoming Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = app.get(
            url = "${request.data}&limit=20&page=$page&lang=en-US",
            interceptor = headerInterceptor
        ).parsed<SearchResult>().map { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return app.post(
            url = "$API_HOST/search/titles",
            data = mapOf("q" to query),
            interceptor = headerInterceptor
        ).parsed<SearchResult>().map { element ->
            element.toSearchResponse()
        }
    }

    private fun MediaSummary.toSearchResponse(): SearchResponse {

        val mediaType = if (type == "Movie") TvType.Movie else TvType.TvSeries

        if (mediaType == TvType.Movie) {
            return newMovieSearchResponse(
                name = title,
                url = Data(
                    type = mediaType,
                    media = this,
                ).toJson(),
                type = TvType.Movie,
            ) {
                posterUrl = images.poster
            }
        } else {
            return newTvSeriesSearchResponse(
                name = title,
                url = Data(
                    type = mediaType,
                    media = this,
                ).toJson(),
                type = TvType.TvSeries,
            ) {
                this.posterUrl = images.poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<Data>(url)

        return app.get(
            url = "$API_HOST/titles/${data.media?.id}",
            interceptor = headerInterceptor
        ).parsed<Media>().toLoadResponse(data)
    }

    private suspend fun Media.toLoadResponse(data: Data): LoadResponse {

        return if (type == "Movie") {

            val link = LinkData(
                id = id,
                type = "Movie",
                season = 0,
                episode = 0,
                title = title,
                year = mediaYear,
                orgTitle = originalTitle,
                date = released,
            )

            newMovieLoadResponse(
                name = title,
                url = data.toJson(),
                type = TvType.Movie,
                dataUrl = link.toJson(),
            ) {
                this.type = TvType.Movie
            }
        } else {
            newTvSeriesLoadResponse(
                name = this.title,
                url = data.toJson(),
                type = TvType.TvSeries,
                episodes = fetchEpisodes()
            ) {

                this.type = TvType.AsianDrama
                this.showStatus = getStatus(status)
            }
        }.applyMedia(this)
    }

    private suspend fun LoadResponse.applyMedia(media: Media): LoadResponse {
        this.name = media.title
        this.posterUrl = media.images.poster
        this.year = media.mediaYear
        this.plot = media.synopsis
        this.score = Score.from10(media.mediaRating)
        this.tags = media.fixGenres()
        this.duration = media.runtime.toInt()
        this.recommendations = media.fetchRecommendations().map { it.toSearchResponse() }
        this.actors = media.fetchCredits()
        this.comingSoon = isUpcoming(media.airedStart)
        this.backgroundPosterUrl = media.images.poster
        this.contentRating = fixCertification(media.certification)
        addTrailer(
            media.fetchTrailer(),
            addRaw = true,
            headers = mapOf(
                "User-Agent" to "Dart/3.6 (dart:io)"
            )
        )
        return this
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

    private fun getStatus(status: String?): ShowStatus? {
        return when (status) {
            "Airing" -> ShowStatus.Ongoing
            "Finished Airing" -> ShowStatus.Completed
            else -> null
        }
    }

    private fun fixCertification(status: String?): String? {
        return when (status) {
            "G - All Ages" -> "G"
            "13+ - Teens 13 or older" -> "13+"
            "15+ - Teens 15 or older" -> "15+"
            "18+ Restricted (violence & profanity)" -> "18+"
            "Not Yet Rated" -> null
            else -> "null"
        }
    }

    data class Data(
        val type: TvType? = null,
        val media: MediaSummary? = null,
    )

    class SearchResult : ArrayList<MediaSummary>()

    class Recommendations : ArrayList<MediaSummary>()

    data class MediaSummary(
        @JsonProperty("id") val id: Long,
        @JsonProperty("title") val title: String,
        @JsonProperty("original_title") val originalTitle: String,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("rating") val rating: Double? = null,
        @JsonProperty("permalink") val permalink: String? = null,
        @JsonProperty("type") val type: String,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("country") val country: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("images") val images: Images,
    )

    data class Images(
        @JsonProperty("thumb") val thumb: String? = null,
        @JsonProperty("medium") val medium: String? = null,
        @JsonProperty("poster") val poster: String? = null,
    )

    data class Media(
        @JsonProperty("id") val id: Long,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("original_title") val originalTitle: String,
        @JsonProperty("year") val mediaYear: Int,
        @JsonProperty("episodes") val episodes: Long,
        @JsonProperty("rating") val mediaRating: Double,
        @JsonProperty("permalink") val permalink: String? = null,
        @JsonProperty("synopsis") val synopsis: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("country") val country: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("images") val images: Images,
        @JsonProperty("alt_titles") val altTitles: List<String>? = null,
        @JsonProperty("votes") val votes: Long? = null,
        @JsonProperty("aired_start") val airedStart: String? = null,
        @JsonProperty("released") val released: String? = null,
        @JsonProperty("release_dates_fmt") val releaseDatesFmt: String,
        @JsonProperty("genres") val genres: List<Any>? = null,
        @JsonProperty("trailer") val trailer: Trailer?,
        @JsonProperty("watchers") val watchers: Long,
        @JsonProperty("ranked") val ranked: Long,
        @JsonProperty("popularity") val popularity: Long,
        @JsonProperty("runtime") val runtime: Long,
        @JsonProperty("reviews_count") val reviewsCount: Long,
        @JsonProperty("recs_count") val recsCount: Long,
        @JsonProperty("comments_count") val commentsCount: Long,
        @JsonProperty("certification") val certification: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("enable_ads") val enableAds: Boolean,
        @JsonProperty("sources") val sources: List<Source>,
        @JsonProperty("updated_at") val updatedAt: Long,
    ) {
        suspend fun fetchCredits(): List<ActorData> {
            val actors = app.get(
                url = "$API_HOST/titles/$id/credits",
                interceptor = headerInterceptor
            ).parsed<Credits>().cast.map {
                ActorData(
                    Actor(
                        name = it.name,
                        image = it.images.poster
                    ),
                    roleString = it.characterName
                )
            }
            return actors
        }

        suspend fun fetchRecommendations(): Recommendations {
            return app.get(
                url = "$API_HOST/titles/$id/recommendations",
                interceptor = headerInterceptor
            ).parsed<Recommendations>()
        }

        suspend fun fetchTrailer(): String? {
            return app.get(
                url = "$SITE_HOST/v1/trailers/${trailer?.id}",
                interceptor = headerInterceptor
            ).parsedSafe<TrailerRoot>()?.trailer?.trailerDetails?.source
        }

        fun fixGenres(): List<String> {
            return (genres as List<*>).mapNotNull {
                when (it) {
                    is String -> it
                    else -> (it as? Map<String, String>)?.getValue("name")
                }
            }
        }
    }

    private suspend fun Media.fetchEpisodes(): List<Episode> {
        return app.get(
            url = "$API_HOST/titles/${this.id}/episodes",
            interceptor = headerInterceptor
        ).parsed<ShowEpisodes>().map {
            it.episodes
        }.flatten().map { ep ->
            val link = LinkData(
                id = id,
                type = "Series",
                season = 0,
                episode = ep.episodeNumber,
                title = title,
                year = mediaYear,
                orgTitle = originalTitle,
                date = released,
                airedDate = airedStart,
            )
            newEpisode(
                link.toJson()
            ) {
                name = null
                season = null
                episode = ep.episodeNumber
                posterUrl = null
                score = Score.from10(ep.rating)
                description = null
                runTime = null
                addDate(ep.releasedAt)
            }
        }
    }

    data class Genre(
        @JsonProperty("id") val id: Long,
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
    )

    data class Tag(
        @JsonProperty("id") val id: Long,
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
    )

    data class Source(
        @JsonProperty("xid") val xid: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("source") val source: String,
        @JsonProperty("source_type") val sourceType: String,
        @JsonProperty("link") val link: String,
        @JsonProperty("image") val image: String,
    )

    data class Trailer(
        @JsonProperty("id") val id: Long? = null,
    )

    data class Credits(
        @JsonProperty("cast") val cast: List<Cast>,
        @JsonProperty("crew") val crew: List<Crew>,
    )

    data class Cast(
        @JsonProperty("id") val id: Long,
        @JsonProperty("name") val name: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("images") val images: Images,
        @JsonProperty("character_name") val characterName: String,
        @JsonProperty("role") val role: String,
    )

    data class Crew(
        @JsonProperty("id") val id: Long,
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("images") val images: Images,
        @JsonProperty("job") val job: String,
    )

    class ShowEpisodes : ArrayList<ShowEpisodesItem>()

    data class ShowEpisodesItem(
        @JsonProperty("name") val name: String,
        @JsonProperty("release_date") val releaseDate: String,
        @JsonProperty("episodes") val episodes: List<ShowEpisode>,
        @JsonProperty("timezone") val timezone: String,
        @JsonProperty("total") val total: Int,
    )

    data class ShowEpisode(
        @JsonProperty("id") val id: Int,
        @JsonProperty("episode_number") val episodeNumber: Int,
        @JsonProperty("rating") val rating: Double,
        @JsonProperty("votes") val votes: Int,
        @JsonProperty("released_at") val releasedAt: String,
    )

    data class TrailerRoot(
        @JsonProperty("trailer") val trailer: TrailerNode,
    )

    data class TrailerNode(
        @JsonProperty("trailer") val trailerDetails: TrailerDetails,
    )

    data class TrailerDetails(
        @JsonProperty("id") val id: Long,
        @JsonProperty("source") val source: String,
    )

    data class LinkData(
        @JsonProperty("id") val id: Long? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("orgTitle") val orgTitle: String? = null,
        @JsonProperty("lastSeason") val lastSeason: Int? = null,
        @JsonProperty("date") val date: String? = null,
        @JsonProperty("airedDate") val airedDate: String? = null,
    )
}