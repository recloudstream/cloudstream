package com.lagradost.cloudstream4.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream4.state.UniqueItem
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid


/**
 * UI -> Viewmodel
 *
 * The response from the UI about an action on a ImmutableSearchResponse */
@Immutable
data class SearchResponseAction(
    val response: ImmutableSearchResponse,
    val operation: SearchResponseOperation,
)

/**
 * UI -> ViewModel
 *
 * Action on ImmutableSearchResponse */
enum class SearchResponseOperation {
    /** Open the item in a result view */
    Open,

    /** Show a popup of metadata */
    Metadata,
}

@Immutable
data class ImmutableWatchPosition(
    val position: Duration,
    val duration: Duration,
)

@Immutable
data class ImmutableWatchEpisode(
    val parentId: Int,
    val episodeId: Int?,
    val episode: Int?,
    val season: Int?,
)

@Immutable
data class ImmutableSearchResponse(
    override val uuid: Uuid = Uuid.random(),
    val name: String,
    val url: String,
    val apiName: String,
    val type: TvType? = null,
    val posterUrl: String? = null,
    val backgroundPosterUrl: String? = null,
    val posterHeaders: PersistentMap<String, String> = persistentMapOf(),
    val id: Int? = null,
    val quality: SearchQuality? = null,
    val score: Score? = null,
    val year: Int? = null,
    val dubStatus: PersistentSet<DubStatus> = persistentSetOf(),
    val episodes: PersistentMap<DubStatus, Int> = persistentMapOf(),
    val language: String? = null,
    val updateTime: Long? = null,
    val plot: String? = null,
    val duration: Duration? = null,
    val comingSoon: Boolean? = null,
    val logoUrl: String? = null,
    val contentRating: String? = null,
    val tags: PersistentList<String> = persistentListOf(),
    val actors: PersistentList<ActorData> = persistentListOf(),
    val recommendations: PersistentList<ImmutableSearchResponse> = persistentListOf(),
    val watchPosition: ImmutableWatchPosition? = null,
    val watchEpisode: ImmutableWatchEpisode? = null,
) : UniqueItem {
    @Composable
    fun imageRequest(): ImageRequest {
        val context = LocalPlatformContext.current
        return remember(context, posterUrl, backgroundPosterUrl) {
            ImageRequest(context)
        }
    }

    @Composable
    fun backgroundImageRequest(): ImageRequest {
        val context = LocalPlatformContext.current
        return remember(context, posterUrl, backgroundPosterUrl) {
            BackgroundImageRequest(context)
        }
    }

    fun BackgroundImageRequest(context: PlatformContext): ImageRequest =
        ImageRequest.Builder(context).data(
            if (backgroundPosterUrl.isNullOrBlank()) {
                posterUrl
            } else {
                backgroundPosterUrl
            }
        ).httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
            posterHeaders.forEach { (key, value) ->
                headerBuilder[key] = value
            }
        }.build()).crossfade(true).build()

    fun ImageRequest(context: PlatformContext): ImageRequest = ImageRequest.Builder(context).data(
        if (posterUrl.isNullOrBlank()) {
            backgroundPosterUrl
        } else {
            posterUrl
        }
    ).httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
        posterHeaders.forEach { (key, value) ->
            headerBuilder[key] = value
        }
    }.build()).crossfade(true).build()

    companion object {
        val previewMovie = ImmutableSearchResponse(
            id = 0,
            name = "Hello, World!",
            url = "url",
            type = TvType.Movie,
            apiName = "Preview",
            duration = 123.minutes,
            quality = SearchQuality.HD,
            score = Score.from10(4.2),
            year = 1984,
            plot = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor " +
                    "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis " +
                    "nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. " +
                    "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu " +
                    "fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in " +
                    "culpa qui officia deserunt mollit anim id est laborum.",
            watchPosition = ImmutableWatchPosition(position = 42.4.minutes, duration = 123.minutes),
            watchEpisode = ImmutableWatchEpisode(
                episode = 0,
                season = null,
                parentId = 0,
                episodeId = null
            )
        )
        val previewTvSeries = ImmutableSearchResponse(
            id = 1,
            name = "Hello, Tv!",
            url = "url",
            type = TvType.TvSeries,
            apiName = "Preview",
            duration = 22.minutes,
            quality = SearchQuality.HD,
            score = Score.from10(9.2),
            year = 1337,
            plot = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor " +
                    "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis " +
                    "nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. " +
                    "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu " +
                    "fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in " +
                    "culpa qui officia deserunt mollit anim id est laborum.",
            watchPosition = ImmutableWatchPosition(position = 5.4.minutes, duration = 22.minutes),
            watchEpisode = ImmutableWatchEpisode(
                episode = 1,
                season = 2,
                parentId = 1,
                episodeId = 2
            )
        )

        fun from(response: SearchResponse, id: Int? = null): ImmutableSearchResponse {
            val animeResponse = response as? AnimeSearchResponse
            val tvResponse = response as? TvSeriesSearchResponse
            val liveResponse = response as? LiveSearchResponse
            return ImmutableSearchResponse(
                name = response.name,
                url = response.url,
                apiName = response.apiName,
                type = response.type,
                posterUrl = response.posterUrl,
                posterHeaders = response.posterHeaders?.toPersistentMap() ?: persistentMapOf(),
                id = id ?: response.id,
                quality = response.quality,
                score = response.score,
                year = animeResponse?.year ?: tvResponse?.year,
                dubStatus = animeResponse?.dubStatus?.toPersistentSet() ?: persistentSetOf(),
                episodes = animeResponse?.episodes?.toPersistentMap() ?: tvResponse?.episodes?.let {
                    persistentMapOf(
                        DubStatus.None to it
                    )
                } ?: persistentMapOf(),
                language = liveResponse?.lang)
        }

        private fun getLoadResponseIdFromUrl(url: String, apiName: String): Int {
            return url.replace(getApiFromNameNull(apiName)?.mainUrl ?: "", "").replace("/", "")
                .hashCode()
        }

        fun from(response: LoadResponse): ImmutableSearchResponse {
            return ImmutableSearchResponse(
                name = response.name,
                url = response.url,
                apiName = response.apiName,
                id = getLoadResponseIdFromUrl(response.uniqueUrl, response.apiName),
                year = response.year,
                plot = response.plot,
                duration = response.duration?.minutes,
                comingSoon = response.comingSoon,
                logoUrl = response.logoUrl,
                contentRating = response.contentRating,
                score = response.score,
                posterUrl = response.posterUrl,
                backgroundPosterUrl = response.backgroundPosterUrl,
                posterHeaders = response.posterHeaders?.toPersistentMap() ?: persistentMapOf(),
                tags = response.tags?.toPersistentList() ?: persistentListOf(),
                actors = response.actors?.toPersistentList() ?: persistentListOf(),
                type = response.type,
                recommendations = response.recommendations?.map { from(it) }?.toPersistentList()
                    ?: persistentListOf()
            )
        }
    }
}

@Immutable
data class ImmutableHomePageList(
    /** Unique order of the item */
    val order: Int,
    /** Unique name */
    val name: String,
    /** Unique identifier */
    override val uuid: Uuid = Uuid.random(),
    val isEnabled : Boolean = true,
    val page: Int,
    val hasNext: Boolean,
    val isLoading: Boolean,
    val isHorizontalImages: Boolean,
    val items: PersistentList<ImmutableSearchResponse>,
) : UniqueItem
