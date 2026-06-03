package com.lagradost.cloudstream3.metaproviders

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
import com.lagradost.cloudstream3.isUpcoming
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.Interceptor
import okhttp3.Response

// Reference: https://mydramalist.github.io/MDL-API/
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

    @Serializable
    data class Data(
        @SerialName("type") val type: TvType? = null,
        @SerialName("media") val media: MediaSummary? = null,
    )

    @Serializable
    class SearchResult : ArrayList<MediaSummary>()

    @Serializable
    class Recommendations : ArrayList<MediaSummary>()

    @Serializable
    data class MediaSummary(
        @SerialName("id") val id: Long,
        @SerialName("title") val title: String,
        @SerialName("original_title") val originalTitle: String,
        @SerialName("year") val year: Int? = null,
        @SerialName("rating") val rating: Double? = null,
        @SerialName("permalink") val permalink: String? = null,
        @SerialName("type") val type: String,
        @SerialName("media_type") val mediaType: String? = null,
        @SerialName("country") val country: String? = null,
        @SerialName("language") val language: String? = null,
        @SerialName("images") val images: Images,
    )

    @Serializable
    data class Images(
        @SerialName("thumb") val thumb: String? = null,
        @SerialName("medium") val medium: String? = null,
        @SerialName("poster") val poster: String? = null,
    )

    @Serializable
    data class Media(
        @SerialName("id") val id: Long,
        @SerialName("slug") val slug: String,
        @SerialName("title") val title: String,
        @SerialName("original_title") val originalTitle: String,
        @SerialName("year") val mediaYear: Int,
        @SerialName("episodes") val episodes: Long,
        @SerialName("rating") val mediaRating: Double,
        @SerialName("permalink") val permalink: String? = null,
        @SerialName("synopsis") val synopsis: String? = null,
        @SerialName("type") val type: String? = null,
        @SerialName("media_type") val mediaType: String? = null,
        @SerialName("country") val country: String? = null,
        @SerialName("language") val language: String? = null,
        @SerialName("images") val images: Images,
        @SerialName("alt_titles") val altTitles: List<String>? = null,
        @SerialName("votes") val votes: Long? = null,
        @SerialName("aired_start") val airedStart: String? = null,
        @SerialName("released") val released: String? = null,
        @SerialName("release_dates_fmt") val releaseDatesFmt: String,
        @SerialName("genres") val genres: List<JsonElement>? = null,
        @SerialName("trailer") val trailer: Trailer?,
        @SerialName("watchers") val watchers: Long,
        @SerialName("ranked") val ranked: Long,
        @SerialName("popularity") val popularity: Long,
        @SerialName("runtime") val runtime: Long,
        @SerialName("reviews_count") val reviewsCount: Long,
        @SerialName("recs_count") val recsCount: Long,
        @SerialName("comments_count") val commentsCount: Long,
        @SerialName("certification") val certification: String,
        @SerialName("status") val status: String,
        @SerialName("enable_ads") val enableAds: Boolean,
        @SerialName("sources") val sources: List<Source>,
        @SerialName("updated_at") val updatedAt: Long,
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
            return (genres as? List<*>)?.mapNotNull { item ->
                when (item) {
                    is String -> item
                    is Map<*, *> -> item["name"] as? String
                    else -> null
                }
            } ?: emptyList()
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

    @Serializable
    data class Genre(
        @SerialName("id") val id: Long,
        @SerialName("name") val name: String,
        @SerialName("slug") val slug: String,
    )

    @Serializable
    data class Tag(
        @SerialName("id") val id: Long,
        @SerialName("name") val name: String,
        @SerialName("slug") val slug: String,
    )

    @Serializable
    data class Source(
        @SerialName("xid") val xid: String,
        @SerialName("name") val name: String,
        @SerialName("source") val source: String,
        @SerialName("source_type") val sourceType: String,
        @SerialName("link") val link: String,
        @SerialName("image") val image: String,
    )

    @Serializable
    data class Trailer(
        @SerialName("id") val id: Long? = null,
    )

    @Serializable
    data class Credits(
        @SerialName("cast") val cast: List<Cast>,
        @SerialName("crew") val crew: List<Crew>,
    )

    @Serializable
    data class Cast(
        @SerialName("id") val id: Long,
        @SerialName("name") val name: String,
        @SerialName("url") val url: String,
        @SerialName("slug") val slug: String,
        @SerialName("images") val images: Images,
        @SerialName("character_name") val characterName: String,
        @SerialName("role") val role: String,
    )

    @Serializable
    data class Crew(
        @SerialName("id") val id: Long,
        @SerialName("name") val name: String,
        @SerialName("slug") val slug: String,
        @SerialName("images") val images: Images,
        @SerialName("job") val job: String,
    )

    @Serializable
    class ShowEpisodes : ArrayList<ShowEpisodesItem>()

    @Serializable
    data class ShowEpisodesItem(
        @SerialName("name") val name: String,
        @SerialName("release_date") val releaseDate: String,
        @SerialName("episodes") val episodes: List<ShowEpisode>,
        @SerialName("timezone") val timezone: String,
        @SerialName("total") val total: Int,
    )

    @Serializable
    data class ShowEpisode(
        @SerialName("id") val id: Int,
        @SerialName("episode_number") val episodeNumber: Int,
        @SerialName("rating") val rating: Double,
        @SerialName("votes") val votes: Int,
        @SerialName("released_at") val releasedAt: String,
    )

    @Serializable
    data class TrailerRoot(
        @SerialName("trailer") val trailer: TrailerNode,
    )

    @Serializable
    data class TrailerNode(
        @SerialName("trailer") val trailerDetails: TrailerDetails,
    )

    @Serializable
    data class TrailerDetails(
        @SerialName("id") val id: Long,
        @SerialName("source") val source: String,
    )

    @Serializable
    data class LinkData(
        @SerialName("id") val id: Long? = null,
        @SerialName("type") val type: String? = null,
        @SerialName("season") val season: Int? = null,
        @SerialName("episode") val episode: Int? = null,
        @SerialName("title") val title: String? = null,
        @SerialName("year") val year: Int? = null,
        @SerialName("orgTitle") val orgTitle: String? = null,
        @SerialName("lastSeason") val lastSeason: Int? = null,
        @SerialName("date") val date: String? = null,
        @SerialName("airedDate") val airedDate: String? = null,
    )
}
