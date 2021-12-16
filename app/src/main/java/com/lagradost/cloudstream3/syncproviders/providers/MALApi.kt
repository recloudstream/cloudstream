package com.lagradost.cloudstream3.syncproviders.providers

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.OAuth2API
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.appString
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.secondsToReadable
import com.lagradost.cloudstream3.syncproviders.OAuth2API.Companion.unixTime
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.AppUtils.splitQuery
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore.toKotlinObject
import java.net.URL
import java.security.SecureRandom
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/** max 100 via https://myanimelist.net/apiconfig/references/api/v2#tag/anime */
const val MAL_MAX_SEARCH_LIMIT = 25

class MALApi(index: Int) : AccountManager(index), SyncAPI {
    override val name = "MAL"
    override val key = "1714d6f2f4f7cc19644384f8c4629910"
    override val redirectUrl = "mallogin"
    override val idPrefix = "mal"
    override val mainUrl = "https://myanimelist.net"
    override val icon: Int
        get() = R.drawable.mal_logo

    override fun logOut() {
        removeAccountKeys()
    }

    override fun loginInfo(): OAuth2API.LoginInfo? {
        //getMalUser(true)?
        getKey<MalUser>(accountId, MAL_USER_KEY)?.let { user ->
            return OAuth2API.LoginInfo(profilePicture = user.picture, name = user.name, accountIndex = accountIndex)
        }
        return null
    }

    override fun search(name: String): List<SyncAPI.SyncSearchResult> {
        val url = "https://api.myanimelist.net/v2/anime?q=$name&limit=$MAL_MAX_SEARCH_LIMIT"
        val auth = getKey<String>(
            accountId,
            MAL_TOKEN_KEY
        ) ?: return emptyList()
        val res = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer " + auth,
            ), cacheTime = 0
        ).text
        return mapper.readValue<MalSearch>(res).data.map {
            val node = it.node
            SyncAPI.SyncSearchResult(
                node.title,
                this.name,
                node.id.toString(),
                "$mainUrl/anime/${node.id}/",
                node.main_picture?.large ?: node.main_picture?.medium
            )
        }
    }

    override fun score(id: String, status : SyncAPI.SyncStatus): Boolean {
        return setScoreRequest(
            id.toIntOrNull() ?: return false,
            fromIntToAnimeStatus(status.status),
            status.score,
            status.watchedEpisodes
        )
    }

    override fun getResult(id: String): SyncAPI.SyncResult? {
        val internalId = id.toIntOrNull() ?: return null
        TODO("Not yet implemented")
    }

    override fun getStatus(id: String): SyncAPI.SyncStatus? {
        val internalId = id.toIntOrNull() ?: return null

        val data = getDataAboutMalId(internalId)?.my_list_status ?: return null
        return SyncAPI.SyncStatus(
            score = data.score,
            status = malStatusAsString.indexOf(data.status),
            isFavorite = null,
            watchedEpisodes = data.num_episodes_watched,
        )
    }

    companion object {
        private val malStatusAsString = arrayOf("watching", "completed", "on_hold", "dropped", "plan_to_watch")

        const val MAL_USER_KEY: String = "mal_user" // user data like profile
        const val MAL_CACHED_LIST: String = "mal_cached_list"
        const val MAL_SHOULD_UPDATE_LIST: String = "mal_should_update_list"
        const val MAL_UNIXTIME_KEY: String = "mal_unixtime" // When token expires
        const val MAL_REFRESH_TOKEN_KEY: String = "mal_refresh_token" // refresh token
        const val MAL_TOKEN_KEY: String = "mal_token" // anilist token for api
    }

    override fun handleRedirect(url: String) {
        try {
            val sanitizer =
                splitQuery(URL(url.replace(appString, "https").replace("/#", "?"))) // FIX ERROR
            val state = sanitizer["state"]!!
            if (state == "RequestID$requestId") {
                val currentCode = sanitizer["code"]!!
                ioSafe {
                    var res = ""
                    try {
                        //println("cc::::: " + codeVerifier)
                        res = app.post(
                            "https://myanimelist.net/v1/oauth2/token",
                            data = mapOf(
                                "client_id" to key,
                                "code" to currentCode,
                                "code_verifier" to codeVerifier,
                                "grant_type" to "authorization_code"
                            )
                        ).text
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (res != "") {
                        switchToNewAccount()
                        storeToken(res)
                        getMalUser()
                        setKey(MAL_SHOULD_UPDATE_LIST, true)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun authenticate() {
        // It is recommended to use a URL-safe string as code_verifier.
        // See section 4 of RFC 7636 for more details.

        val secureRandom = SecureRandom()
        val codeVerifierBytes = ByteArray(96) // base64 has 6bit per char; (8/6)*96 = 128
        secureRandom.nextBytes(codeVerifierBytes)
        codeVerifier =
            Base64.encodeToString(codeVerifierBytes, Base64.DEFAULT).trimEnd('=').replace("+", "-")
                .replace("/", "_").replace("\n", "")
        val codeChallenge = codeVerifier
        val request =
            "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=$key&code_challenge=$codeChallenge&state=RequestID$requestId"
        openBrowser(request)
    }

    private val mapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()!!

    private var requestId = 0
    private var codeVerifier = ""

    private fun storeToken(response: String) {
        try {
            if (response != "") {
                val token = mapper.readValue<ResponseToken>(response)
                setKey(accountId, MAL_UNIXTIME_KEY, (token.expires_in + unixTime))
                setKey(accountId, MAL_REFRESH_TOKEN_KEY, token.refresh_token)
                setKey(accountId, MAL_TOKEN_KEY, token.access_token)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshToken() {
        try {
            val res = app.post(
                "https://myanimelist.net/v1/oauth2/token",
                data = mapOf(
                    "client_id" to key,
                    "grant_type" to "refresh_token",
                    "refresh_token" to getKey(
                        accountId,
                        MAL_REFRESH_TOKEN_KEY
                    )!!
                )
            ).text
            storeToken(res)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
        @JsonProperty("main_picture") val main_picture: MainPicture?,
        @JsonProperty("alternative_titles") val alternative_titles: AlternativeTitles?,
        @JsonProperty("media_type") val media_type: String?,
        @JsonProperty("num_episodes") val num_episodes: Int?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("start_date") val start_date: String?,
        @JsonProperty("end_date") val end_date: String?,
        @JsonProperty("average_episode_duration") val average_episode_duration: Int?,
        @JsonProperty("synopsis") val synopsis: String?,
        @JsonProperty("mean") val mean: Double?,
        @JsonProperty("genres") val genres: List<Genres>?,
        @JsonProperty("rank") val rank: Int?,
        @JsonProperty("popularity") val popularity: Int?,
        @JsonProperty("num_list_users") val num_list_users: Int?,
        @JsonProperty("num_favorites") val num_favorites: Int?,
        @JsonProperty("num_scoring_users") val num_scoring_users: Int?,
        @JsonProperty("start_season") val start_season: StartSeason?,
        @JsonProperty("broadcast") val broadcast: Broadcast?,
        @JsonProperty("nsfw") val nsfw: String?,
        @JsonProperty("created_at") val created_at: String?,
        @JsonProperty("updated_at") val updated_at: String?
    )

    data class ListStatus(
        @JsonProperty("status") val status: String?,
        @JsonProperty("score") val score: Int,
        @JsonProperty("num_episodes_watched") val num_episodes_watched: Int,
        @JsonProperty("is_rewatching") val is_rewatching: Boolean,
        @JsonProperty("updated_at") val updated_at: String,
    )

    data class Data(
        @JsonProperty("node") val node: Node,
        @JsonProperty("list_status") val list_status: ListStatus?,
    )

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
        @JsonProperty("day_of_the_week") val day_of_the_week: String?,
        @JsonProperty("start_time") val start_time: String?
    )

    fun getMalAnimeListCached(): Array<Data>? {
        return getKey(MAL_CACHED_LIST) as? Array<Data>
    }

    fun getMalAnimeListSmart(): Array<Data>? {
        if (getKey<String>(
                accountId,
                MAL_TOKEN_KEY
            ) == null
        ) return null
        return if (getKey(MAL_SHOULD_UPDATE_LIST, true) == true) {
            val list = getMalAnimeList()
            if (list != null) {
                setKey(MAL_CACHED_LIST, list)
                setKey(MAL_SHOULD_UPDATE_LIST, false)
            }
            list
        } else {
            getMalAnimeListCached()
        }
    }

    private fun getMalAnimeList(): Array<Data>? {
        return try {
            checkMalToken()
            var offset = 0
            val fullList = mutableListOf<Data>()
            val offsetRegex = Regex("""offset=(\d+)""")
            while (true) {
                val data: MalList = getMalAnimeListSlice(offset) ?: break
                fullList.addAll(data.data)
                offset = data.paging.next?.let { offsetRegex.find(it)?.groupValues?.get(1)?.toInt() } ?: break
            }
            fullList.toTypedArray()
            //mapper.readValue<MalAnime>(res)
        } catch (e: Exception) {
            null
        }
    }

    fun convertToStatus(string: String): MalStatusType {
        return fromIntToAnimeStatus(malStatusAsString.indexOf(string))
    }

    private fun getMalAnimeListSlice(offset: Int = 0): MalList? {
        val user = "@me"
        val auth = getKey<String>(
            accountId,
            MAL_TOKEN_KEY
        ) ?: return null
        return try {
            // Very lackluster docs
            // https://myanimelist.net/apiconfig/references/api/v2#operation/users_user_id_animelist_get
            val url =
                "https://api.myanimelist.net/v2/users/$user/animelist?fields=list_status,num_episodes,media_type,status,start_date,end_date,synopsis,alternative_titles,mean,genres,rank,num_list_users,nsfw,average_episode_duration,num_favorites,popularity,num_scoring_users,start_season,favorites_info,broadcast,created_at,updated_at&nsfw=1&limit=100&offset=$offset"
            val res = app.get(
                url, headers = mapOf(
                    "Authorization" to "Bearer $auth",
                ), cacheTime = 0
            ).text
            res.toKotlinObject()
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    private fun getDataAboutMalId(id: Int): MalAnime? {
        return try {
            // https://myanimelist.net/apiconfig/references/api/v2#operation/anime_anime_id_get
            val url = "https://api.myanimelist.net/v2/anime/$id?fields=id,title,num_episodes,my_list_status"
            val res = app.get(
                url, headers = mapOf(
                    "Authorization" to "Bearer " + getKey<String>(
                        accountId,
                        MAL_TOKEN_KEY
                    )!!
                ), cacheTime = 0
            ).text
            mapper.readValue<MalAnime>(res)
        } catch (e: Exception) {
            null
        }
    }

    fun setAllMalData() {
        val user = "@me"
        var isDone = false
        var index = 0
        allTitles.clear()
        checkMalToken()
        while (!isDone) {
            val res = app.get(
                "https://api.myanimelist.net/v2/users/$user/animelist?fields=list_status&limit=1000&offset=${index * 1000}",
                headers = mapOf(
                    "Authorization" to "Bearer " + getKey<String>(
                        accountId,
                        MAL_TOKEN_KEY
                    )!!
                ), cacheTime = 0
            ).text
            val values = mapper.readValue<MalRoot>(res)
            val titles = values.data.map { MalTitleHolder(it.list_status, it.node.id, it.node.title) }
            for (t in titles) {
                allTitles[t.id] = t
            }
            isDone = titles.size < 1000
            index++
        }
    }

    fun convertJapanTimeToTimeRemaining(date: String, endDate: String? = null): String? {
        try {
            // No time remaining if the show has already ended
            try {
                endDate?.let {
                    if (SimpleDateFormat("yyyy-MM-dd").parse(it).time < System.currentTimeMillis()) return@convertJapanTimeToTimeRemaining null
                }
            } catch (e: ParseException) {
                logError(e)
            }

            // Unparseable date: "2021 7 4 other null"
            // Weekday: other, date: null
            if (date.contains("null") || date.contains("other")) {
                return null
            }

            val currentDate = Calendar.getInstance()
            val currentMonth = currentDate.get(Calendar.MONTH) + 1
            val currentWeek = currentDate.get(Calendar.WEEK_OF_MONTH)
            val currentYear = currentDate.get(Calendar.YEAR)

            val dateFormat = SimpleDateFormat("yyyy MM W EEEE HH:mm")
            dateFormat.timeZone = TimeZone.getTimeZone("Japan")
            val parsedDate = dateFormat.parse("$currentYear $currentMonth $currentWeek $date") ?: return null
            val timeDiff = (parsedDate.time - System.currentTimeMillis()) / 1000

            // if it has already aired this week add a week to the timer
            val updatedTimeDiff =
                if (timeDiff > -60 * 60 * 24 * 7 && timeDiff < 0) timeDiff + 60 * 60 * 24 * 7 else timeDiff
            return secondsToReadable(updatedTimeDiff.toInt(), "Now")

        } catch (e: Exception) {
            logError(e)
        }
        return null
    }

    private fun checkMalToken() {
        if (unixTime > getKey(
                accountId,
                MAL_UNIXTIME_KEY
            ) ?: 0L
        ) {
            refreshToken()
        }
    }

    private fun getMalUser(setSettings: Boolean = true): MalUser? {
        checkMalToken()
        return try {
            val res = app.get(
                "https://api.myanimelist.net/v2/users/@me",
                headers = mapOf(
                    "Authorization" to "Bearer " + getKey<String>(
                        accountId,
                        MAL_TOKEN_KEY
                    )!!
                ), cacheTime = 0
            ).text

            val user = mapper.readValue<MalUser>(res)
            if (setSettings) {
                setKey(accountId, MAL_USER_KEY, user)
                registerAccount()
            }
            user
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    enum class MalStatusType(var value: Int) {
        Watching(0),
        Completed(1),
        OnHold(2),
        Dropped(3),
        PlanToWatch(4),
        None(-1)
    }

    private fun fromIntToAnimeStatus(inp: Int): MalStatusType {//= AniListStatusType.values().first { it.value == inp }
        return when (inp) {
            -1 -> MalStatusType.None
            0 -> MalStatusType.Watching
            1 -> MalStatusType.Completed
            2 -> MalStatusType.OnHold
            3 -> MalStatusType.Dropped
            4 -> MalStatusType.PlanToWatch
            5 -> MalStatusType.Watching
            else -> MalStatusType.None
        }
    }

    fun setScoreRequest(
        id: Int,
        status: MalStatusType? = null,
        score: Int? = null,
        num_watched_episodes: Int? = null,
    ): Boolean {
        val res = setScoreRequest(
            id,
            if (status == null) null else malStatusAsString[maxOf(0, status.value)],
            score,
            num_watched_episodes
        )
        if (res != "") {
            return try {
                val malStatus = mapper.readValue<MalStatus>(res)
                if (allTitles.containsKey(id)) {
                    val currentTitle = allTitles[id]!!
                    allTitles[id] = MalTitleHolder(malStatus, id, currentTitle.name)
                } else {
                    allTitles[id] = MalTitleHolder(malStatus, id, "")
                }
                true
            } catch (e: Exception) {
                logError(e)
                false
            }
        } else {
            return false
        }
    }

    private fun setScoreRequest(
        id: Int,
        status: String? = null,
        score: Int? = null,
        num_watched_episodes: Int? = null,
    ): String {
        return try {
            app.put(
                "https://api.myanimelist.net/v2/anime/$id/my_list_status",
                headers = mapOf(
                    "Authorization" to "Bearer " + getKey<String>(
                        accountId,
                        MAL_TOKEN_KEY
                    )!!
                ),
                data = mapOf(
                    "status" to status,
                    "score" to score?.toString(),
                    "num_watched_episodes" to num_watched_episodes?.toString()
                )
            ).text
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }


    data class ResponseToken(
        @JsonProperty("token_type") val token_type: String,
        @JsonProperty("expires_in") val expires_in: Int,
        @JsonProperty("access_token") val access_token: String,
        @JsonProperty("refresh_token") val refresh_token: String,
    )

    data class MalRoot(
        @JsonProperty("data") val data: List<MalDatum>,
    )

    data class MalDatum(
        @JsonProperty("node") val node: MalNode,
        @JsonProperty("list_status") val list_status: MalStatus,
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
        @JsonProperty("num_episodes_watched") val num_episodes_watched: Int,
        @JsonProperty("is_rewatching") val is_rewatching: Boolean,
        @JsonProperty("updated_at") val updated_at: String,
    )

    data class MalUser(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("location") val location: String,
        @JsonProperty("joined_at") val joined_at: String,
        @JsonProperty("picture") val picture: String,
    )

    data class MalMainPicture(
        @JsonProperty("large") val large: String?,
        @JsonProperty("medium") val medium: String?,
    )

    // Used for getDataAboutId()
    data class MalAnime(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String?,
        @JsonProperty("num_episodes") val num_episodes: Int,
        @JsonProperty("my_list_status") val my_list_status: MalStatus?,
        @JsonProperty("main_picture") val main_picture: MalMainPicture?,
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