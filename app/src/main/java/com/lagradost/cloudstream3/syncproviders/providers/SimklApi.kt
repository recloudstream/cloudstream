package com.lagradost.cloudstream3.syncproviders.providers

import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.getKeys
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.debugPrint
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.Interceptor
import okhttp3.Response
import java.math.BigInteger
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class SimklApi(index: Int) : AccountManager(index), SyncAPI {
    override var name = "Simkl"
    override val key = "simkl-key"
    override val redirectUrl = "simkl"
    override val idPrefix = "simkl"
    override var requireLibraryRefresh = true
    override var mainUrl = "https://api.simkl.com"
    override val icon = R.drawable.simkl_logo
    override val requiresLogin = false
    override val createAccountUrl = "$mainUrl/signup"
    override val syncIdName = SyncIdName.Simkl
    private val token: String?
        get() = getKey<String>(accountId, SIMKL_TOKEN_KEY).also {
            debugAssert({ it == null }) { "No ${this.name} token!" }
        }

    /** Automatically adds simkl auth headers */
    private val interceptor = HeaderInterceptor()

    /**
     * This is required to override the reported last activity as simkl activites
     * may not always update based on testing.
     */
    private var lastScoreTime = -1L

    private object SimklCache {
        private const val SIMKL_CACHE_KEY = "SIMKL_API_CACHE"

        enum class CacheTimes(val value: String) {
            OneMonth("30d"),
            ThirtyMinutes("30m")
        }

        private class SimklCacheWrapper<T>(
            @JsonProperty("obj") val obj: T?,
            @JsonProperty("validUntil") val validUntil: Long,
            @JsonProperty("cacheTime") val cacheTime: Long = unixTime,
        ) {
            /** Returns true if cache is newer than cacheDays */
            fun isFresh(): Boolean {
                return validUntil > unixTime
            }

            fun remainingTime(): Duration {
                val unixTime = unixTime
                return if (validUntil > unixTime) {
                    (validUntil - unixTime).toDuration(DurationUnit.SECONDS)
                } else {
                    Duration.ZERO
                }
            }
        }

        fun cleanOldCache() {
            getKeys(SIMKL_CACHE_KEY)?.forEach {
                val isOld = AcraApplication.getKey<SimklCacheWrapper<Any>>(it)?.isFresh() == false
                if (isOld) {
                    removeKey(it)
                }
            }
        }

        fun <T> setKey(path: String, value: T, cacheTime: Duration) {
            debugPrint { "Set cache: $SIMKL_CACHE_KEY/$path for ${cacheTime.inWholeDays} days or ${cacheTime.inWholeSeconds} seconds." }
            setKey(
                SIMKL_CACHE_KEY,
                path,
                // Storing as plain sting is required to make generics work.
                SimklCacheWrapper(value, unixTime + cacheTime.inWholeSeconds).toJson()
            )
        }

        /**
         * Gets cached object, if object is not fresh returns null and removes it from cache
         */
        inline fun <reified T : Any> getKey(path: String): T? {
            // Required for generic otherwise "LinkedHashMap cannot be cast to MediaObject"
            val type = mapper.typeFactory.constructParametricType(
                SimklCacheWrapper::class.java,
                T::class.java
            )
            val cache = getKey<String>(SIMKL_CACHE_KEY, path)?.let {
                mapper.readValue<SimklCacheWrapper<T>>(it, type)
            }

            return if (cache?.isFresh() == true) {
                debugPrint {
                    "Cache hit at: $SIMKL_CACHE_KEY/$path. " +
                            "Remains fresh for ${cache.remainingTime().inWholeDays} days or ${cache.remainingTime().inWholeSeconds} seconds."
                }
                cache.obj
            } else {
                debugPrint { "Cache miss at: $SIMKL_CACHE_KEY/$path" }
                removeKey(SIMKL_CACHE_KEY, path)
                null
            }
        }
    }

    companion object {
        private const val clientId: String = BuildConfig.SIMKL_CLIENT_ID
        private const val clientSecret: String = BuildConfig.SIMKL_CLIENT_SECRET
        private var lastLoginState = ""

        const val SIMKL_TOKEN_KEY: String = "simkl_token"
        const val SIMKL_USER_KEY: String = "simkl_user"
        const val SIMKL_CACHED_LIST: String = "simkl_cached_list"
        const val SIMKL_CACHED_LIST_TIME: String = "simkl_cached_time"

        /** 2014-09-01T09:10:11Z -> 1409562611 */
        private const val simklDateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        fun getUnixTime(string: String?): Long? {
            return try {
                SimpleDateFormat(simklDateFormat).apply {
                    this.timeZone = TimeZone.getTimeZone("UTC")
                }.parse(
                    string ?: return null
                )?.toInstant()?.epochSecond
            } catch (e: Exception) {
                logError(e)
                return null
            }
        }

        /** 1409562611 -> 2014-09-01T09:10:11Z */
        fun getDateTime(unixTime: Long?): String? {
            return try {
                SimpleDateFormat(simklDateFormat).apply {
                    this.timeZone = TimeZone.getTimeZone("UTC")
                }.format(
                    Date.from(
                        Instant.ofEpochSecond(
                            unixTime ?: return null
                        )
                    )
                )
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Set of sync services simkl is compatible with.
         * Add more as required: https://simkl.docs.apiary.io/#reference/search/id-lookup/get-items-by-id
         */
        enum class SyncServices(val originalName: String) {
            Simkl("simkl"),
            Imdb("imdb"),
            Tmdb("tmdb"),
            AniList("anilist"),
            Mal("mal"),
        }

        /**
         * The ID string is a way to keep a collection of services in one single ID using a map
         * This adds a database service (like imdb) to the string and returns the new string.
         */
        fun addIdToString(idString: String?, database: SyncServices, id: String?): String? {
            if (id == null) return idString
            return (readIdFromString(idString) + mapOf(database to id)).toJson()
        }

        /** Read the id string to get all other ids */
        fun readIdFromString(idString: String?): Map<SyncServices, String> {
            return tryParseJson(idString) ?: return emptyMap()
        }

        fun getPosterUrl(poster: String): String {
            return "https://wsrv.nl/?url=https://simkl.in/posters/${poster}_m.webp"
        }

        private fun getUrlFromId(id: Int): String {
            return "https://simkl.com/shows/$id"
        }

        enum class SimklListStatusType(
            var value: Int,
            @StringRes val stringRes: Int,
            val originalName: String?
        ) {
            Watching(0, R.string.type_watching, "watching"),
            Completed(1, R.string.type_completed, "completed"),
            Paused(2, R.string.type_on_hold, "hold"),
            Dropped(3, R.string.type_dropped, "dropped"),
            Planning(4, R.string.type_plan_to_watch, "plantowatch"),
            ReWatching(5, R.string.type_re_watching, "watching"),
            None(-1, R.string.none, null);

            companion object {
                fun fromString(string: String): SimklListStatusType? {
                    return SimklListStatusType.values().firstOrNull {
                        it.originalName == string
                    }
                }
            }
        }

        // -------------------
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        data class TokenRequest(
            @JsonProperty("code") val code: String,
            @JsonProperty("client_id") val client_id: String = clientId,
            @JsonProperty("client_secret") val client_secret: String = clientSecret,
            @JsonProperty("redirect_uri") val redirect_uri: String = "$appString://simkl",
            @JsonProperty("grant_type") val grant_type: String = "authorization_code"
        )

        data class TokenResponse(
            /** No expiration date */
            val access_token: String,
            val token_type: String,
            val scope: String
        )
        // -------------------

        /** https://simkl.docs.apiary.io/#reference/users/settings/receive-settings */
        data class SettingsResponse(
            val user: User
        ) {
            data class User(
                val name: String,
                /** Url */
                val avatar: String
            )
        }

        // -------------------
        data class ActivitiesResponse(
            val all: String?,
            val tv_shows: UpdatedAt,
            val anime: UpdatedAt,
            val movies: UpdatedAt,
        ) {
            data class UpdatedAt(
                val all: String?,
                val removed_from_list: String?,
                val rated_at: String?,
            )
        }

        /** https://simkl.docs.apiary.io/#reference/tv/episodes/get-tv-show-episodes */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        data class EpisodeMetadata(
            @JsonProperty("title") val title: String?,
            @JsonProperty("description") val description: String?,
            @JsonProperty("season") val season: Int?,
            @JsonProperty("episode") val episode: Int,
            @JsonProperty("img") val img: String?
        ) {
            companion object {
                fun convertToEpisodes(list: List<EpisodeMetadata>?): List<MediaObject.Season.Episode>? {
                    return list?.map {
                        MediaObject.Season.Episode(it.episode)
                    }
                }

                fun convertToSeasons(list: List<EpisodeMetadata>?): List<MediaObject.Season>? {
                    return list?.filter { it.season != null }?.groupBy {
                        it.season
                    }?.mapNotNull { (season, episodes) ->
                        convertToEpisodes(episodes)?.let { MediaObject.Season(season!!, it) }
                    }?.ifEmpty { null }
                }
            }
        }

        /**
         * https://simkl.docs.apiary.io/#introduction/about-simkl-api/standard-media-objects
         * Useful for finding shows from metadata
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        open class MediaObject(
            @JsonProperty("title") val title: String?,
            @JsonProperty("year") val year: Int?,
            @JsonProperty("ids") val ids: Ids?,
            @JsonProperty("total_episodes") val total_episodes: Int? = null,
            @JsonProperty("status") val status: String? = null,
            @JsonProperty("poster") val poster: String? = null,
            @JsonProperty("type") val type: String? = null,
            @JsonProperty("seasons") val seasons: List<Season>? = null,
            @JsonProperty("episodes") val episodes: List<Season.Episode>? = null
        ) {
            fun hasEnded(): Boolean {
                return status == "released" || status == "ended"
            }

            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            data class Season(
                @JsonProperty("number") val number: Int,
                @JsonProperty("episodes") val episodes: List<Episode>
            ) {
                data class Episode(@JsonProperty("number") val number: Int)
            }

            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            data class Ids(
                @JsonProperty("simkl") val simkl: Int?,
                @JsonProperty("imdb") val imdb: String? = null,
                @JsonProperty("tmdb") val tmdb: String? = null,
                @JsonProperty("mal") val mal: String? = null,
                @JsonProperty("anilist") val anilist: String? = null,
            ) {
                companion object {
                    fun fromMap(map: Map<SyncServices, String>): Ids {
                        return Ids(
                            simkl = map[SyncServices.Simkl]?.toIntOrNull(),
                            imdb = map[SyncServices.Imdb],
                            tmdb = map[SyncServices.Tmdb],
                            mal = map[SyncServices.Mal],
                            anilist = map[SyncServices.AniList]
                        )
                    }
                }
            }

            fun toSyncSearchResult(): SyncAPI.SyncSearchResult? {
                return SyncAPI.SyncSearchResult(
                    this.title ?: return null,
                    "Simkl",
                    this.ids?.simkl?.toString() ?: return null,
                    getUrlFromId(this.ids.simkl),
                    this.poster?.let { getPosterUrl(it) },
                    if (this.type == "movie") TvType.Movie else TvType.TvSeries
                )
            }
        }

        class SimklScoreBuilder private constructor() {
            data class Builder(
                private var url: String? = null,
                private var interceptor: Interceptor? = null,
                private var ids: MediaObject.Ids? = null,
                private var score: Int? = null,
                private var status: Int? = null,
                private var addEpisodes: Pair<List<MediaObject.Season>?, List<MediaObject.Season.Episode>?>? = null,
                private var removeEpisodes: Pair<List<MediaObject.Season>?, List<MediaObject.Season.Episode>?>? = null,
                // Required for knowing if the status should be overwritten
                private var onList: Boolean = false
            ) {
                fun interceptor(interceptor: Interceptor) = apply { this.interceptor = interceptor }
                fun apiUrl(url: String) = apply { this.url = url }
                fun ids(ids: MediaObject.Ids) = apply { this.ids = ids }
                fun score(score: Int?, oldScore: Int?) = apply {
                    if (score != oldScore) {
                        this.score = score
                    }
                }

                fun status(newStatus: Int?, oldStatus: Int?) = apply {
                    onList = oldStatus != null
                    // Only set status if its new
                    if (newStatus != oldStatus) {
                        this.status = newStatus
                    } else {
                        this.status = null
                    }
                }

                fun episodes(
                    allEpisodes: List<EpisodeMetadata>?,
                    newEpisodes: Int?,
                    oldEpisodes: Int?,
                ) = apply {
                    if (allEpisodes == null || newEpisodes == null) return@apply

                    fun getEpisodes(rawEpisodes: List<EpisodeMetadata>) =
                        if (rawEpisodes.any { it.season != null }) {
                            EpisodeMetadata.convertToSeasons(rawEpisodes) to null
                        } else {
                            null to EpisodeMetadata.convertToEpisodes(rawEpisodes)
                        }

                    // Do not add episodes if there is no change
                    if (newEpisodes > (oldEpisodes ?: 0)) {
                        this.addEpisodes = getEpisodes(allEpisodes.take(newEpisodes))

                        // Set to watching if episodes are added and there is no current status
                        if (!onList) {
                            status = SimklListStatusType.Watching.value
                        }
                    }
                    if ((oldEpisodes ?: 0) > newEpisodes) {
                        this.removeEpisodes = getEpisodes(allEpisodes.drop(newEpisodes))
                    }
                }

                suspend fun execute(): Boolean {
                    val time = getDateTime(unixTime)

                    return if (this.status == SimklListStatusType.None.value) {
                        app.post(
                            "$url/sync/history/remove",
                            json = StatusRequest(
                                shows = listOf(HistoryMediaObject(ids = ids)),
                                movies = emptyList()
                            ),
                            interceptor = interceptor
                        ).isSuccessful
                    } else {
                        val statusResponse = status?.let { setStatus ->
                            val newStatus =
                                SimklListStatusType.values()
                                    .firstOrNull { it.value == setStatus }?.originalName
                                    ?: SimklListStatusType.Watching.originalName!!

                            app.post(
                                "${this.url}/sync/add-to-list",
                                json = StatusRequest(
                                    shows = listOf(
                                        StatusMediaObject(
                                            null,
                                            null,
                                            ids,
                                            newStatus,
                                        )
                                    ), movies = emptyList()
                                ),
                                interceptor = interceptor
                            ).isSuccessful
                        } ?: true

                        val episodeRemovalResponse = removeEpisodes?.let { (seasons, episodes) ->
                            app.post(
                                "${this.url}/sync/history/remove",
                                json = StatusRequest(
                                    shows = listOf(
                                        HistoryMediaObject(
                                            ids = ids,
                                            seasons = seasons,
                                            episodes = episodes
                                        )
                                    ),
                                    movies = emptyList()
                                ),
                                interceptor = interceptor
                            ).isSuccessful
                        } ?: true

                        val historyResponse =
                            // Only post if there are episodes or score to upload
                            if (addEpisodes != null || score != null) {
                                app.post(
                                    "${this.url}/sync/history",
                                    json = StatusRequest(
                                        shows = listOf(
                                            HistoryMediaObject(
                                                null,
                                                null,
                                                ids,
                                                addEpisodes?.first,
                                                addEpisodes?.second,
                                                score,
                                                score?.let { time },
                                            )
                                        ), movies = emptyList()
                                    ),
                                    interceptor = interceptor
                                ).isSuccessful
                            } else {
                                true
                            }

                        statusResponse && episodeRemovalResponse && historyResponse
                    }
                }
            }
        }

        suspend fun getEpisodes(
            simklId: Int?,
            type: String?,
            episodes: Int?,
            hasEnded: Boolean?
        ): Array<EpisodeMetadata>? {
            if (simklId == null) return null

            val cacheKey = "Episodes/$simklId"
            val cache = SimklCache.getKey<Array<EpisodeMetadata>>(cacheKey)

            // Return cached result if its higher or equal the amount of episodes.
            if (cache != null && cache.size >= (episodes ?: 0)) {
                return cache
            }

            // There is always one season in Anime -> no request necessary
            if (type == "anime" && episodes != null) {
                return episodes.takeIf { it > 0 }?.let {
                    (1..it).map { episode ->
                        EpisodeMetadata(
                            null, null, null, episode, null
                        )
                    }.toTypedArray()
                }
            }
            val url = when (type) {
                "anime" -> "https://api.simkl.com/anime/episodes/$simklId"
                "tv" -> "https://api.simkl.com/tv/episodes/$simklId"
                "movie" -> return null
                else -> return null
            }

            debugPrint { "Requesting episodes from $url" }
            return app.get(url, params = mapOf("client_id" to clientId))
                .parsedSafe<Array<EpisodeMetadata>>()?.also {
                    val cacheTime =
                        if (hasEnded == true) SimklCache.CacheTimes.OneMonth.value else SimklCache.CacheTimes.ThirtyMinutes.value

                    // 1 Month cache
                    SimklCache.setKey(cacheKey, it, Duration.parse(cacheTime))
                }
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        class HistoryMediaObject(
            @JsonProperty("title") title: String? = null,
            @JsonProperty("year") year: Int? = null,
            @JsonProperty("ids") ids: Ids? = null,
            @JsonProperty("seasons") seasons: List<Season>? = null,
            @JsonProperty("episodes") episodes: List<Season.Episode>? = null,
            @JsonProperty("rating") val rating: Int? = null,
            @JsonProperty("rated_at") val rated_at: String? = null,
        ) : MediaObject(title, year, ids, seasons = seasons, episodes = episodes)

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        class RatingMediaObject(
            @JsonProperty("title") title: String?,
            @JsonProperty("year") year: Int?,
            @JsonProperty("ids") ids: Ids?,
            @JsonProperty("rating") val rating: Int,
            @JsonProperty("rated_at") val rated_at: String? = getDateTime(unixTime)
        ) : MediaObject(title, year, ids)

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        class StatusMediaObject(
            @JsonProperty("title") title: String?,
            @JsonProperty("year") year: Int?,
            @JsonProperty("ids") ids: Ids?,
            @JsonProperty("to") val to: String,
            @JsonProperty("watched_at") val watched_at: String? = getDateTime(unixTime)
        ) : MediaObject(title, year, ids)

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        data class StatusRequest(
            @JsonProperty("movies") val movies: List<MediaObject>,
            @JsonProperty("shows") val shows: List<MediaObject>
        )

        /** https://simkl.docs.apiary.io/#reference/sync/get-all-items/get-all-items-in-the-user's-watchlist */
        data class AllItemsResponse(
            @JsonProperty("shows")
            val shows: List<ShowMetadata> = emptyList(),
            @JsonProperty("anime")
            val anime: List<ShowMetadata> = emptyList(),
            @JsonProperty("movies")
            val movies: List<MovieMetadata> = emptyList(),
        ) {
            companion object {
                fun merge(first: AllItemsResponse?, second: AllItemsResponse?): AllItemsResponse {

                    // Replace the first item with the same id, or add the new item
                    fun <T> MutableList<T>.replaceOrAddItem(newItem: T, predicate: (T) -> Boolean) {
                        for (i in this.indices) {
                            if (predicate(this[i])) {
                                this[i] = newItem
                                return
                            }
                        }
                        this.add(newItem)
                    }

                    //
                    fun <T : Metadata> merge(
                        first: List<T>?,
                        second: List<T>?
                    ): List<T> {
                        return (first?.toMutableList() ?: mutableListOf()).apply {
                            second?.forEach { secondShow ->
                                this.replaceOrAddItem(secondShow) {
                                    it.getIds().simkl == secondShow.getIds().simkl
                                }
                            }
                        }
                    }

                    return AllItemsResponse(
                        merge(first?.shows, second?.shows),
                        merge(first?.anime, second?.anime),
                        merge(first?.movies, second?.movies),
                    )
                }
            }

            interface Metadata {
                val last_watched_at: String?
                val status: String?
                val user_rating: Int?
                val last_watched: String?
                val watched_episodes_count: Int?
                val total_episodes_count: Int?

                fun getIds(): ShowMetadata.Show.Ids
                fun toLibraryItem(): SyncAPI.LibraryItem
            }

            data class MovieMetadata(
                override val last_watched_at: String?,
                override val status: String,
                override val user_rating: Int?,
                override val last_watched: String?,
                override val watched_episodes_count: Int?,
                override val total_episodes_count: Int?,
                val movie: ShowMetadata.Show
            ) : Metadata {
                override fun getIds(): ShowMetadata.Show.Ids {
                    return this.movie.ids
                }

                override fun toLibraryItem(): SyncAPI.LibraryItem {
                    return SyncAPI.LibraryItem(
                        this.movie.title,
                        "https://simkl.com/tv/${movie.ids.simkl}",
                        movie.ids.simkl.toString(),
                        this.watched_episodes_count,
                        this.total_episodes_count,
                        this.user_rating?.times(10),
                        getUnixTime(last_watched_at) ?: 0,
                        "Simkl",
                        TvType.Movie,
                        this.movie.poster?.let { getPosterUrl(it) },
                        null,
                        null,
                        movie.ids.simkl,
                    )
                }
            }

            data class ShowMetadata(
                @JsonProperty("last_watched_at") override val last_watched_at: String?,
                @JsonProperty("status") override val status: String,
                @JsonProperty("user_rating") override val user_rating: Int?,
                @JsonProperty("last_watched") override val last_watched: String?,
                @JsonProperty("watched_episodes_count") override val watched_episodes_count: Int?,
                @JsonProperty("total_episodes_count") override val total_episodes_count: Int?,
                @JsonProperty("show") val show: Show
            ) : Metadata {
                override fun getIds(): Show.Ids {
                    return this.show.ids
                }

                override fun toLibraryItem(): SyncAPI.LibraryItem {
                    return SyncAPI.LibraryItem(
                        this.show.title,
                        "https://simkl.com/tv/${show.ids.simkl}",
                        show.ids.simkl.toString(),
                        this.watched_episodes_count,
                        this.total_episodes_count,
                        this.user_rating?.times(10),
                        getUnixTime(last_watched_at) ?: 0,
                        "Simkl",
                        TvType.Anime,
                        this.show.poster?.let { getPosterUrl(it) },
                        null,
                        null,
                        show.ids.simkl
                    )
                }

                data class Show(
                    @JsonProperty("title") val title: String,
                    @JsonProperty("poster") val poster: String?,
                    @JsonProperty("year") val year: Int?,
                    @JsonProperty("ids") val ids: Ids,
                ) {
                    data class Ids(
                        @JsonProperty("simkl") val simkl: Int,
                        @JsonProperty("slug") val slug: String?,
                        @JsonProperty("imdb") val imdb: String?,
                        @JsonProperty("zap2it") val zap2it: String?,
                        @JsonProperty("tmdb") val tmdb: String?,
                        @JsonProperty("offen") val offen: String?,
                        @JsonProperty("tvdb") val tvdb: String?,
                        @JsonProperty("mal") val mal: String?,
                        @JsonProperty("anidb") val anidb: String?,
                        @JsonProperty("anilist") val anilist: String?,
                        @JsonProperty("traktslug") val traktslug: String?
                    ) {
                        fun matchesId(database: SyncServices, id: String): Boolean {
                            return when (database) {
                                SyncServices.Simkl -> this.simkl == id.toIntOrNull()
                                SyncServices.AniList -> this.anilist == id
                                SyncServices.Mal -> this.mal == id
                                SyncServices.Tmdb -> this.tmdb == id
                                SyncServices.Imdb -> this.imdb == id
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Appends api keys to the requests
     **/
    private inner class HeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            debugPrint { "${this@SimklApi.name} made request to ${chain.request().url}" }
            return chain.proceed(
                chain.request()
                    .newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("simkl-api-key", clientId)
                    .build()
            )
        }
    }

    private suspend fun getUser(): SettingsResponse.User? {
        return suspendSafeApiCall {
            app.post("$mainUrl/users/settings", interceptor = interceptor)
                .parsedSafe<SettingsResponse>()?.user
        }
    }

    /**
     * Useful to get episodes on demand to prevent unnecessary requests.
     */
    class SimklEpisodeConstructor(
        private val simklId: Int?,
        private val type: String?,
        private val totalEpisodeCount: Int?,
        private val hasEnded: Boolean?
    ) {
        suspend fun getEpisodes(): Array<EpisodeMetadata>? {
            return getEpisodes(simklId, type, totalEpisodeCount, hasEnded)
        }
    }

    class SimklSyncStatus(
        override var status: SyncWatchType,
        override var score: Int?,
        val oldScore: Int?,
        override var watchedEpisodes: Int?,
        val episodeConstructor: SimklEpisodeConstructor,
        override var isFavorite: Boolean? = null,
        override var maxEpisodes: Int? = null,
        /** Save seen episodes separately to know the change from old to new.
         * Required to remove seen episodes if count decreases */
        val oldEpisodes: Int,
        val oldStatus: String?
    ) : SyncAPI.AbstractSyncStatus()

    override suspend fun getStatus(id: String): SyncAPI.AbstractSyncStatus? {
        val realIds = readIdFromString(id)

        // Key which assumes all ids are the same each time :/
        // This could be some sort of reference system to make multiple IDs
        // point to the same key.
        val idKey =
            realIds.toList().map { "${it.first.originalName}=${it.second}" }.sorted().joinToString()

        val cachedObject = SimklCache.getKey<MediaObject>(idKey)
        val searchResult: MediaObject = cachedObject
            ?: (searchByIds(realIds)?.firstOrNull()?.also { result ->
                val cacheTime =
                    if (result.hasEnded()) SimklCache.CacheTimes.OneMonth.value else SimklCache.CacheTimes.ThirtyMinutes.value
                SimklCache.setKey(idKey, result, Duration.parse(cacheTime))
            }) ?: return null

        val episodeConstructor = SimklEpisodeConstructor(
            searchResult.ids?.simkl,
            searchResult.type,
            searchResult.total_episodes,
            searchResult.hasEnded()
        )

        val foundItem = getSyncListSmart()?.let { list ->
            listOf(list.shows, list.anime, list.movies).flatten().firstOrNull { show ->
                realIds.any { (database, id) ->
                    show.getIds().matchesId(database, id)
                }
            }
        }

        if (foundItem != null) {
            return SimklSyncStatus(
                status = foundItem.status?.let { SyncWatchType.fromInternalId(SimklListStatusType.fromString(it)?.value) }
                    ?: return null,
                score = foundItem.user_rating,
                watchedEpisodes = foundItem.watched_episodes_count,
                maxEpisodes = searchResult.total_episodes,
                episodeConstructor = episodeConstructor,
                oldEpisodes = foundItem.watched_episodes_count ?: 0,
                oldScore = foundItem.user_rating,
                oldStatus = foundItem.status
            )
        } else {
            return SimklSyncStatus(
                status = SyncWatchType.fromInternalId(SimklListStatusType.None.value) ,
                score = 0,
                watchedEpisodes = 0,
                maxEpisodes = if (searchResult.type == "movie") 0 else searchResult.total_episodes,
                episodeConstructor = episodeConstructor,
                oldEpisodes = 0,
                oldStatus = null,
                oldScore = null
            )
        }
    }

    override suspend fun score(id: String, status: SyncAPI.AbstractSyncStatus): Boolean {
        val parsedId = readIdFromString(id)
        lastScoreTime = unixTime
        val simklStatus = status as? SimklSyncStatus

        val builder = SimklScoreBuilder.Builder()
            .apiUrl(this.mainUrl)
            .score(status.score, simklStatus?.oldScore)
            .status(status.status.internalId, (status as? SimklSyncStatus)?.oldStatus?.let { oldStatus ->
                SimklListStatusType.values().firstOrNull {
                    it.originalName == oldStatus
                }?.value
            })
            .interceptor(interceptor)
            .ids(MediaObject.Ids.fromMap(parsedId))


        // Get episodes only when required
        val episodes = simklStatus?.episodeConstructor?.getEpisodes()

        // All episodes if marked as completed
        val watchedEpisodes = if (status.status.internalId == SimklListStatusType.Completed.value) {
            episodes?.size
        } else {
            status.watchedEpisodes
        }

        builder.episodes(episodes?.toList(), watchedEpisodes, simklStatus?.oldEpisodes)

        requireLibraryRefresh = true
        return builder.execute()
    }


    /** See https://simkl.docs.apiary.io/#reference/search/id-lookup/get-items-by-id */
    suspend fun searchByIds(serviceMap: Map<SyncServices, String>): Array<MediaObject>? {
        if (serviceMap.isEmpty()) return emptyArray()

        return app.get(
            "$mainUrl/search/id",
            params = mapOf("client_id" to clientId) + serviceMap.map { (service, id) ->
                service.originalName to id
            }
        ).parsedSafe()
    }

    override suspend fun search(name: String): List<SyncAPI.SyncSearchResult>? {
        return app.get(
            "$mainUrl/search/", params = mapOf("client_id" to clientId, "q" to name)
        ).parsedSafe<Array<MediaObject>>()?.mapNotNull { it.toSyncSearchResult() }
    }

    override fun authenticate(activity: FragmentActivity?) {
        lastLoginState = BigInteger(130, SecureRandom()).toString(32)
        val url =
            "https://simkl.com/oauth/authorize?response_type=code&client_id=$clientId&redirect_uri=$appString://${redirectUrl}&state=$lastLoginState"
        openBrowser(url, activity)
    }

    override fun loginInfo(): AuthAPI.LoginInfo? {
        return getKey<SettingsResponse.User>(accountId, SIMKL_USER_KEY)?.let { user ->
            AuthAPI.LoginInfo(
                name = user.name,
                profilePicture = user.avatar,
                accountIndex = accountIndex
            )
        }
    }

    override fun logOut() {
        requireLibraryRefresh = true
        removeAccountKeys()
    }

    override suspend fun getResult(id: String): SyncAPI.SyncResult? {
        return null
    }

    private suspend fun getSyncListSince(since: Long?): AllItemsResponse? {
        val params = getDateTime(since)?.let {
            mapOf("date_from" to it)
        } ?: emptyMap()

        // Can return null on no change.
        return app.get(
            "$mainUrl/sync/all-items/",
            params = params,
            interceptor = interceptor
        ).parsedSafe()
    }

    private suspend fun getActivities(): ActivitiesResponse? {
        return app.post("$mainUrl/sync/activities", interceptor = interceptor).parsedSafe()
    }

    private fun getSyncListCached(): AllItemsResponse? {
        return getKey(accountId, SIMKL_CACHED_LIST)
    }

    private suspend fun getSyncListSmart(): AllItemsResponse? {
        if (token == null) return null

        val activities = getActivities()
        val lastCacheUpdate = getKey<Long>(accountId, SIMKL_CACHED_LIST_TIME)
        val lastRemoval = listOf(
            activities?.tv_shows?.removed_from_list,
            activities?.anime?.removed_from_list,
            activities?.movies?.removed_from_list
        ).maxOf {
            getUnixTime(it) ?: -1
        }
        val lastRealUpdate =
            listOf(
                activities?.tv_shows?.all,
                activities?.anime?.all,
                activities?.movies?.all,
            ).maxOf {
                getUnixTime(it) ?: -1
            }

        debugPrint { "Cache times: lastCacheUpdate=$lastCacheUpdate, lastRemoval=$lastRemoval, lastRealUpdate=$lastRealUpdate" }
        val list = if (lastCacheUpdate == null || lastCacheUpdate < lastRemoval) {
            debugPrint { "Full list update in ${this.name}." }
            setKey(accountId, SIMKL_CACHED_LIST_TIME, lastRemoval)
            getSyncListSince(null)
        } else if (lastCacheUpdate < lastRealUpdate || lastCacheUpdate < lastScoreTime) {
            debugPrint { "Partial list update in ${this.name}." }
            setKey(accountId, SIMKL_CACHED_LIST_TIME, lastCacheUpdate)
            AllItemsResponse.merge(getSyncListCached(), getSyncListSince(lastCacheUpdate))
        } else {
            debugPrint { "Cached list update in ${this.name}." }
            getSyncListCached()
        }
        debugPrint { "List sizes: movies=${list?.movies?.size}, shows=${list?.shows?.size}, anime=${list?.anime?.size}" }

        setKey(accountId, SIMKL_CACHED_LIST, list)

        return list
    }


    override suspend fun getPersonalLibrary(): SyncAPI.LibraryMetadata? {
        val list = getSyncListSmart() ?: return null

        val baseMap =
            SimklListStatusType.values()
                .filter { it.value >= 0 && it.value != SimklListStatusType.ReWatching.value }
                .associate {
                    it.stringRes to emptyList<SyncAPI.LibraryItem>()
                }

        val syncMap = listOf(list.anime, list.movies, list.shows)
            .flatten()
            .groupBy {
                it.status
            }
            .mapNotNull { (status, list) ->
                val stringRes =
                    status?.let { SimklListStatusType.fromString(it)?.stringRes }
                        ?: return@mapNotNull null
                val libraryList = list.map { it.toLibraryItem() }
                stringRes to libraryList
            }.toMap()

        return SyncAPI.LibraryMetadata(
            (baseMap + syncMap).map { SyncAPI.LibraryList(txt(it.key), it.value) }, setOf(
                ListSorting.AlphabeticalA,
                ListSorting.AlphabeticalZ,
                ListSorting.UpdatedNew,
                ListSorting.UpdatedOld,
                ListSorting.RatingHigh,
                ListSorting.RatingLow,
            )
        )
    }

    override fun getIdFromUrl(url: String): String {
        val simklUrlRegex = Regex("""https://simkl\.com/[^/]*/(\d+).*""")
        return simklUrlRegex.find(url)?.groupValues?.get(1) ?: ""
    }

    override suspend fun handleRedirect(url: String): Boolean {
        val uri = url.toUri()
        val state = uri.getQueryParameter("state")
        // Ensure consistent state
        if (state != lastLoginState) return false
        lastLoginState = ""

        val code = uri.getQueryParameter("code") ?: return false
        val token = app.post(
            "$mainUrl/oauth/token", json = TokenRequest(code)
        ).parsedSafe<TokenResponse>() ?: return false

        switchToNewAccount()
        setKey(accountId, SIMKL_TOKEN_KEY, token.access_token)

        val user = getUser()
        if (user == null) {
            removeKey(accountId, SIMKL_TOKEN_KEY)
            switchToOldAccount()
            return false
        }

        setKey(accountId, SIMKL_USER_KEY, user)
        registerAccount()
        requireLibraryRefresh = true

        return true
    }
}
