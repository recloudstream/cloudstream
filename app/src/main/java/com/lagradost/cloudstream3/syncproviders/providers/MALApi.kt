package com.lagradost.cloudstream3.syncproviders.providers

import androidx.annotation.StringRes
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.AuthLoginPage
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.DataStore.toKotlinObject
import com.lagradost.cloudstream3.utils.txt
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** max 100 via https://myanimelist.net/apiconfig/references/api/v2#tag/anime */
const val MAL_MAX_SEARCH_LIMIT = 25

class MALApi : SyncAPI() {
    override var name = "MAL"
    override val idPrefix = "mal"

    val key = "1714d6f2f4f7cc19644384f8c4629910"
    private val apiUrl = "https://api.myanimelist.net"
    override val hasOAuth2 = true
    override val redirectUrlIdentifier: String? = "mallogin"
    override val mainUrl = "https://myanimelist.net"
    override val icon = R.drawable.mal_logo
    override val syncIdName = SyncIdName.MyAnimeList
    override val createAccountUrl = "$mainUrl/register.php"

    override val supportedWatchTypes = setOf(
        SyncWatchType.WATCHING,
        SyncWatchType.COMPLETED,
        SyncWatchType.PLANTOWATCH,
        SyncWatchType.DROPPED,
        SyncWatchType.ONHOLD,
        SyncWatchType.NONE
    )

    data class PayLoad(
        val requestId: Int,
        val codeVerifier: String
    )

    override suspend fun login(redirectUrl: String, payload: String?): AuthToken? {
        val payloadData = parseJson<PayLoad>(payload!!)
        val sanitizer = splitRedirectUrl(redirectUrl)
        val state = sanitizer["state"]!!

        if (state != "RequestID${payloadData.requestId}") {
            return null
        }

        val currentCode = sanitizer["code"]!!

        val token = app.post(
            "$mainUrl/v1/oauth2/token",
            data = mapOf(
                "client_id" to key,
                "code" to currentCode,
                "code_verifier" to payloadData.codeVerifier,
                "grant_type" to "authorization_code"
            )
        ).parsed<ResponseToken>()
        return AuthToken(
            accessTokenLifetime = unixTime + token.expiresIn.toLong(),
            refreshToken = token.refreshToken,
            accessToken = token.accessToken
        )
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val user = app.get(
            "$apiUrl/v2/users/@me",
            headers = mapOf(
                "Authorization" to "Bearer ${token?.accessToken ?: return null}"
            ), cacheTime = 0
        ).parsed<MalUser>()
        return AuthUser(
            id = user.id,
            name = user.name,
            profilePicture = user.picture
        )
    }

    override suspend fun search(auth : AuthData?, query: String): List<SyncAPI.SyncSearchResult>? {
        val auth = auth?.token?.accessToken ?: return null
        val url = "$apiUrl/v2/anime?q=$name&limit=$MAL_MAX_SEARCH_LIMIT"
        val res = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer $auth",
            ), cacheTime = 0
        ).parsed<MalSearch>()
        return res.data.map {
            val node = it.node
            SyncAPI.SyncSearchResult(
                node.title,
                this.name,
                node.id.toString(),
                "$mainUrl/anime/${node.id}/",
                node.mainPicture?.large ?: node.mainPicture?.medium
            )
        }
    }

    override fun urlToId(url: String): String? =
        Regex("""/anime/((.*)/|(.*))""").find(url)!!.groupValues.first()

    override suspend fun updateStatus(
        auth : AuthData?,
        id: String,
        newStatus: SyncAPI.AbstractSyncStatus
    ): Boolean {
        return setScoreRequest(
            auth?.token ?: return false,
            id.toIntOrNull() ?: return false,
            fromIntToAnimeStatus(newStatus.status),
            newStatus.score?.toInt(10),
            newStatus.watchedEpisodes
        )
    }

    data class MalAnime(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("main_picture") val mainPicture: MainPicture?,
        @JsonProperty("alternative_titles") val alternativeTitles: AlternativeTitles?,
        @JsonProperty("start_date") val startDate: String?,
        @JsonProperty("end_date") val endDate: String?,
        @JsonProperty("synopsis") val synopsis: String?,
        @JsonProperty("mean") val mean: Double?,
        @JsonProperty("rank") val rank: Int?,
        @JsonProperty("popularity") val popularity: Int?,
        @JsonProperty("num_list_users") val numListUsers: Int?,
        @JsonProperty("num_scoring_users") val numScoringUsers: Int?,
        @JsonProperty("nsfw") val nsfw: String?,
        @JsonProperty("created_at") val createdAt: String?,
        @JsonProperty("updated_at") val updatedAt: String?,
        @JsonProperty("media_type") val mediaType: String?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("genres") val genres: ArrayList<Genres>?,
        @JsonProperty("my_list_status") val myListStatus: MyListStatus?,
        @JsonProperty("num_episodes") val numEpisodes: Int?,
        @JsonProperty("start_season") val startSeason: StartSeason?,
        @JsonProperty("broadcast") val broadcast: Broadcast?,
        @JsonProperty("source") val source: String?,
        @JsonProperty("average_episode_duration") val averageEpisodeDuration: Int?,
        @JsonProperty("rating") val rating: String?,
        @JsonProperty("pictures") val pictures: ArrayList<MainPicture>?,
        @JsonProperty("background") val background: String?,
        @JsonProperty("related_anime") val relatedAnime: ArrayList<RelatedAnime>?,
        @JsonProperty("related_manga") val relatedManga: ArrayList<String>?,
        @JsonProperty("recommendations") val recommendations: ArrayList<Recommendations>?,
        @JsonProperty("studios") val studios: ArrayList<Studios>?,
        @JsonProperty("statistics") val statistics: Statistics?,
    )

    data class Recommendations(
        @JsonProperty("node") val node: Node? = null,
        @JsonProperty("num_recommendations") val numRecommendations: Int? = null
    )

    data class Studios(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null
    )

    data class MyListStatus(
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("score") val score: Int? = null,
        @JsonProperty("num_episodes_watched") val numEpisodesWatched: Int? = null,
        @JsonProperty("is_rewatching") val isRewatching: Boolean? = null,
        @JsonProperty("updated_at") val updatedAt: String? = null
    )

    data class RelatedAnime(
        @JsonProperty("node") val node: Node? = null,
        @JsonProperty("relation_type") val relationType: String? = null,
        @JsonProperty("relation_type_formatted") val relationTypeFormatted: String? = null
    )

    data class Status(
        @JsonProperty("watching") val watching: String? = null,
        @JsonProperty("completed") val completed: String? = null,
        @JsonProperty("on_hold") val onHold: String? = null,
        @JsonProperty("dropped") val dropped: String? = null,
        @JsonProperty("plan_to_watch") val planToWatch: String? = null
    )

    data class Statistics(
        @JsonProperty("status") val status: Status? = null,
        @JsonProperty("num_list_users") val numListUsers: Int? = null
    )

    private fun parseDate(string: String?): Long? {
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(string ?: return null)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun toSearchResult(node: Node?): SyncAPI.SyncSearchResult? {
        return SyncAPI.SyncSearchResult(
            name = node?.title ?: return null,
            apiName = this.name,
            syncId = node.id.toString(),
            url = "$mainUrl/anime/${node.id}",
            posterUrl = node.mainPicture?.large
        )
    }

    override suspend fun load(auth : AuthData?, id: String): SyncAPI.SyncResult? {
        val auth = auth?.token?.accessToken ?: return null
        val internalId = id.toIntOrNull() ?: return null
        val url =
            "$apiUrl/v2/anime/$internalId?fields=id,title,main_picture,alternative_titles,start_date,end_date,synopsis,mean,rank,popularity,num_list_users,num_scoring_users,nsfw,created_at,updated_at,media_type,status,genres,my_list_status,num_episodes,start_season,broadcast,source,average_episode_duration,rating,pictures,background,related_anime,related_manga,recommendations,studios,statistics"

        val res = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer $auth"
            )
        ).text
        return parseJson<MalAnime>(res).let { malAnime ->
            SyncAPI.SyncResult(
                id = internalId.toString(),
                totalEpisodes = malAnime.numEpisodes,
                title = malAnime.title,
                publicScore = Score.from10(malAnime.mean),
                duration = malAnime.averageEpisodeDuration,
                synopsis = malAnime.synopsis,
                airStatus = when (malAnime.status) {
                    "finished_airing" -> ShowStatus.Completed
                    "currently_airing" -> ShowStatus.Ongoing
                    //"not_yet_aired"
                    else -> null
                },
                nextAiring = null,
                studio = malAnime.studios?.mapNotNull { it.name },
                genres = malAnime.genres?.map { it.name },
                trailers = null,
                startDate = parseDate(malAnime.startDate),
                endDate = parseDate(malAnime.endDate),
                recommendations = malAnime.recommendations?.mapNotNull { rec ->
                    val node = rec.node ?: return@mapNotNull null
                    toSearchResult(node)
                },
                nextSeason = malAnime.relatedAnime?.firstOrNull {
                    return@firstOrNull it.relationType == "sequel"
                }?.let { toSearchResult(it.node) },
                prevSeason = malAnime.relatedAnime?.firstOrNull {
                    return@firstOrNull it.relationType == "prequel"
                }?.let { toSearchResult(it.node) },
                actors = null,
            )
        }
    }

    override suspend fun status(auth : AuthData?, id: String): SyncAPI.AbstractSyncStatus? {
        val auth = auth?.token?.accessToken ?: return null

        // https://myanimelist.net/apiconfig/references/api/v2#operation/anime_anime_id_get
        val url =
            "$apiUrl/v2/anime/$id?fields=id,title,num_episodes,my_list_status"
        val data = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer $auth"
            ), cacheTime = 0
        ).parsed<SmallMalAnime>().myListStatus

        return SyncAPI.SyncStatus(
            score = Score.from10(data?.score),
            status = SyncWatchType.fromInternalId(malStatusAsString.indexOf(data?.status)),
            isFavorite = null,
            watchedEpisodes = data?.numEpisodesWatched,
        )
    }

    companion object {
        private val malStatusAsString =
            arrayOf("watching", "completed", "on_hold", "dropped", "plan_to_watch")

        const val MAL_CACHED_LIST: String = "mal_cached_list"

        fun convertToStatus(string: String): MalStatusType {
            return when (string) {
                "watching" -> MalStatusType.Watching
                "completed" -> MalStatusType.Completed
                "on_hold" -> MalStatusType.OnHold
                "dropped" -> MalStatusType.Dropped
                "plan_to_watch" -> MalStatusType.PlanToWatch
                else -> MalStatusType.None
            }
        }

        enum class MalStatusType(var value: Int, @StringRes val stringRes: Int) {
            Watching(0, R.string.type_watching),
            Completed(1, R.string.type_completed),
            OnHold(2, R.string.type_on_hold),
            Dropped(3, R.string.type_dropped),
            PlanToWatch(4, R.string.type_plan_to_watch),
            None(-1, R.string.type_none)
        }

        private fun fromIntToAnimeStatus(inp: SyncWatchType): MalStatusType {//= AniListStatusType.values().first { it.value == inp }
            return when (inp) {
                SyncWatchType.NONE -> MalStatusType.None
                SyncWatchType.WATCHING -> MalStatusType.Watching
                SyncWatchType.COMPLETED -> MalStatusType.Completed
                SyncWatchType.ONHOLD -> MalStatusType.OnHold
                SyncWatchType.DROPPED -> MalStatusType.Dropped
                SyncWatchType.PLANTOWATCH -> MalStatusType.PlanToWatch
                SyncWatchType.REWATCHING -> MalStatusType.Watching
            }
        }

        private fun parseDateLong(string: String?): Long? {
            return try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).parse(
                    string ?: return null
                )?.time?.div(1000)
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun loginRequest(): AuthLoginPage? {
        val codeVerifier = generateCodeVerifier()
        val requestId = ++requestIdCounter
        val codeChallenge = codeVerifier
        val request =
            "$mainUrl/v1/oauth2/authorize?response_type=code&client_id=$key&code_challenge=$codeChallenge&state=RequestID$requestId"

        return AuthLoginPage(
            url = request,
            payload = PayLoad(requestId, codeVerifier).toJson()
        )
    }

    override suspend fun refreshToken(token: AuthToken): AuthToken? {
        val res = app.post(
            "$mainUrl/v1/oauth2/token",
            data = mapOf(
                "client_id" to key,
                "grant_type" to "refresh_token",
                "refresh_token" to token.refreshToken!!
            )
        ).parsed<ResponseToken>()

        return AuthToken(
            accessToken = res.accessToken,
            refreshToken = res.refreshToken,
            accessTokenLifetime = unixTime + res.expiresIn.toLong()
        )
    }

    private var requestIdCounter = 0


    private val allTitles = hashMapOf<Int, MalTitleHolder>()

    data class MalList(
        @JsonProperty("data") val data: List<Data>,
        @JsonProperty("paging") val paging: Paging
    )

    data class MainPicture(
        @JsonProperty("medium") val medium: String,
        @JsonProperty("large") val large: String
    )

    data class Node(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String,
        @JsonProperty("main_picture") val mainPicture: MainPicture?,
        @JsonProperty("alternative_titles") val alternativeTitles: AlternativeTitles?,
        @JsonProperty("media_type") val mediaType: String?,
        @JsonProperty("num_episodes") val numEpisodes: Int?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("start_date") val startDate: String?,
        @JsonProperty("end_date") val endDate: String?,
        @JsonProperty("average_episode_duration") val averageEpisodeDuration: Int?,
        @JsonProperty("synopsis") val synopsis: String?,
        @JsonProperty("mean") val mean: Double?,
        @JsonProperty("genres") val genres: List<Genres>?,
        @JsonProperty("rank") val rank: Int?,
        @JsonProperty("popularity") val popularity: Int?,
        @JsonProperty("num_list_users") val numListUsers: Int?,
        @JsonProperty("num_favorites") val numFavorites: Int?,
        @JsonProperty("num_scoring_users") val numScoringUsers: Int?,
        @JsonProperty("start_season") val startSeason: StartSeason?,
        @JsonProperty("broadcast") val broadcast: Broadcast?,
        @JsonProperty("nsfw") val nsfw: String?,
        @JsonProperty("created_at") val createdAt: String?,
        @JsonProperty("updated_at") val updatedAt: String?
    )

    data class ListStatus(
        @JsonProperty("status") val status: String?,
        @JsonProperty("score") val score: Int,
        @JsonProperty("num_episodes_watched") val numEpisodesWatched: Int,
        @JsonProperty("is_rewatching") val isRewatching: Boolean,
        @JsonProperty("updated_at") val updatedAt: String,
    )

    data class Data(
        @JsonProperty("node") val node: Node,
        @JsonProperty("list_status") val listStatus: ListStatus?,
    ) {
        fun toLibraryItem(): SyncAPI.LibraryItem {
            return SyncAPI.LibraryItem(
                this.node.title,
                "https://myanimelist.net/anime/${this.node.id}/",
                this.node.id.toString(),
                this.listStatus?.numEpisodesWatched,
                this.node.numEpisodes,
                Score.from10(this.listStatus?.score),
                parseDateLong(this.listStatus?.updatedAt),
                "MAL",
                TvType.Anime,
                this.node.mainPicture?.large ?: this.node.mainPicture?.medium,
                null,
                null,
                plot = this.node.synopsis,
                releaseDate = if (this.node.startDate == null) null else try {
                    Date.from(
                        Instant.from(
                            DateTimeFormatter.ofPattern(if (this.node.startDate.length == 4) "yyyy" else if (this.node.startDate.length == 7) "yyyy-MM" else "yyyy-MM-dd")
                                .parse(this.node.startDate)
                        )
                    )
                } catch (_: RuntimeException) {
                    null
                }
            )
        }
    }

    data class Paging(
        @JsonProperty("next") val next: String?
    )

    data class AlternativeTitles(
        @JsonProperty("synonyms") val synonyms: List<String>,
        @JsonProperty("en") val en: String,
        @JsonProperty("ja") val ja: String
    )

    data class Genres(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String
    )

    data class StartSeason(
        @JsonProperty("year") val year: Int,
        @JsonProperty("season") val season: String
    )

    data class Broadcast(
        @JsonProperty("day_of_the_week") val dayOfTheWeek: String?,
        @JsonProperty("start_time") val startTime: String?
    )

    override suspend fun library(auth : AuthData?): LibraryMetadata? {
        val list = getMalAnimeListSmart(auth ?: return null)?.groupBy {
            convertToStatus(it.listStatus?.status ?: "").stringRes
        }?.mapValues { group ->
            group.value.map { it.toLibraryItem() }
        } ?: emptyMap()

        // To fill empty lists when MAL does not return them
        val baseMap =
            MalStatusType.entries.filter { it.value >= 0 }.associate {
                it.stringRes to emptyList<SyncAPI.LibraryItem>()
            }

        return SyncAPI.LibraryMetadata(
            (baseMap + list).map { SyncAPI.LibraryList(txt(it.key), it.value) },
            setOf(
                ListSorting.AlphabeticalA,
                ListSorting.AlphabeticalZ,
                ListSorting.UpdatedNew,
                ListSorting.UpdatedOld,
                ListSorting.ReleaseDateNew,
                ListSorting.ReleaseDateOld,
                ListSorting.RatingHigh,
                ListSorting.RatingLow,
            )
        )
    }

    private suspend fun getMalAnimeListSmart(auth : AuthData): Array<Data>? {
        return if (requireLibraryRefresh) {
            val list = getMalAnimeList(auth.token)
            setKey(MAL_CACHED_LIST, auth.user.id.toString(), list)
            list
        } else {
            getKey<Array<Data>>(MAL_CACHED_LIST, auth.user.id.toString()) as? Array<Data>
        }
    }

    private suspend fun getMalAnimeList(token: AuthToken): Array<Data> {
        var offset = 0
        val fullList = mutableListOf<Data>()
        val offsetRegex = Regex("""offset=(\d+)""")
        while (true) {
            val data: MalList = getMalAnimeListSlice(token, offset) ?: break
            fullList.addAll(data.data)
            offset =
                data.paging.next?.let { offsetRegex.find(it)?.groupValues?.get(1)?.toInt() }
                    ?: break
        }
        return fullList.toTypedArray()
    }

    private suspend fun getMalAnimeListSlice(token: AuthToken, offset: Int = 0): MalList? {
        val user = "@me"
        // Very lackluster docs
        // https://myanimelist.net/apiconfig/references/api/v2#operation/users_user_id_animelist_get
        val url =
            "$apiUrl/v2/users/$user/animelist?fields=list_status,num_episodes,media_type,status,start_date,end_date,synopsis,alternative_titles,mean,genres,rank,num_list_users,nsfw,average_episode_duration,num_favorites,popularity,num_scoring_users,start_season,favorites_info,broadcast,created_at,updated_at&nsfw=1&limit=100&offset=$offset"
        val res = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer ${token.accessToken}",
            ), cacheTime = 0
        ).text
        return res.toKotlinObject()
    }

    private suspend fun setScoreRequest(
        token: AuthToken,
        id: Int,
        status: MalStatusType? = null,
        score: Int? = null,
        numWatchedEpisodes: Int? = null,
    ): Boolean {
        val res = setScoreRequest(
            token,
            id,
            if (status == null) null else malStatusAsString[maxOf(0, status.value)],
            score,
            numWatchedEpisodes
        )

        return if (res.isNullOrBlank()) {
            false
        } else {
            val malStatus = parseJson<MalStatus>(res)
            if (allTitles.containsKey(id)) {
                val currentTitle = allTitles[id]!!
                allTitles[id] = MalTitleHolder(malStatus, id, currentTitle.name)
            } else {
                allTitles[id] = MalTitleHolder(malStatus, id, "")
            }
            true
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun setScoreRequest(
        token: AuthToken,
        id: Int,
        status: String? = null,
        score: Int? = null,
        numWatchedEpisodes: Int? = null,
    ): String? {
        val data = mapOf(
            "status" to status,
            "score" to score?.toString(),
            "num_watched_episodes" to numWatchedEpisodes?.toString()
        ).filterValues { it != null } as Map<String, String>

        return app.put(
            "$apiUrl/v2/anime/$id/my_list_status",
            headers = mapOf(
                "Authorization" to "Bearer ${token.accessToken}"
            ),
            data = data
        ).text
    }


    data class ResponseToken(
        @JsonProperty("token_type") val tokenType: String,
        @JsonProperty("expires_in") val expiresIn: Int,
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("refresh_token") val refreshToken: String,
    )

    data class MalRoot(
        @JsonProperty("data") val data: List<MalDatum>,
    )

    data class MalDatum(
        @JsonProperty("node") val node: MalNode,
        @JsonProperty("list_status") val listStatus: MalStatus,
    )

    data class MalNode(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String,
        /*
        also, but not used
        main_picture ->
            public string medium;
			public string large;
         */
    )

    data class MalStatus(
        @JsonProperty("status") val status: String,
        @JsonProperty("score") val score: Int,
        @JsonProperty("num_episodes_watched") val numEpisodesWatched: Int,
        @JsonProperty("is_rewatching") val isRewatching: Boolean,
        @JsonProperty("updated_at") val updatedAt: String,
    )

    data class MalUser(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("location") val location: String,
        @JsonProperty("joined_at") val joinedAt: String,
        @JsonProperty("picture") val picture: String?,
    )

    data class MalMainPicture(
        @JsonProperty("large") val large: String?,
        @JsonProperty("medium") val medium: String?,
    )

    // Used for getDataAboutId()
    data class SmallMalAnime(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String?,
        @JsonProperty("num_episodes") val numEpisodes: Int,
        @JsonProperty("my_list_status") val myListStatus: MalStatus?,
        @JsonProperty("main_picture") val mainPicture: MalMainPicture?,
    )

    data class MalSearchNode(
        @JsonProperty("node") val node: Node,
    )

    data class MalSearch(
        @JsonProperty("data") val data: List<MalSearchNode>,
        //paging
    )

    data class MalTitleHolder(
        val status: MalStatus,
        val id: Int,
        val name: String,
    )
}
