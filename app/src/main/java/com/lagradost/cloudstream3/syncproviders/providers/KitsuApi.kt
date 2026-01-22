package com.lagradost.cloudstream3.syncproviders.providers


import androidx.annotation.StringRes
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.AuthLoginRequirement
import com.lagradost.cloudstream3.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.txt
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.withIndex
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.collections.set

const val KITSU_MAX_SEARCH_LIMIT = 20

class KitsuApi: SyncAPI() {
    override var name = "Kitsu"
    override val idPrefix = "kitsu"

    private val apiUrl = "https://kitsu.io/api/edge"
    private val oauthUrl = "https://kitsu.io/api/oauth"
    override val hasInApp = true
    override val mainUrl = "https://kitsu.app"
    override val icon = R.drawable.kitsu_icon
    override val syncIdName = SyncIdName.Kitsu
    override val createAccountUrl = mainUrl

    override val supportedWatchTypes = setOf(
        SyncWatchType.WATCHING,
        SyncWatchType.COMPLETED,
        SyncWatchType.PLANTOWATCH,
        SyncWatchType.DROPPED,
        SyncWatchType.ONHOLD,
        SyncWatchType.NONE
    )

    override val inAppLoginRequirement = AuthLoginRequirement(
        password = true,
        email = true
    )

    override suspend fun login(form: AuthLoginResponse): AuthToken? {
        val username = form.email ?: return null
        val password = form.password ?: return null

        val grantType = "password"

        val token = app.post(
            "$oauthUrl/token",
            data = mapOf(
                "grant_type" to grantType,
                "username" to username,
                "password" to password
            )
        ).parsed<ResponseToken>()
        return AuthToken(
            accessTokenLifetime = unixTime + token.expiresIn.toLong(),
            refreshToken = token.refreshToken,
            accessToken = token.accessToken,
        )
    }

    override suspend fun refreshToken(token: AuthToken): AuthToken {
        val res = app.post(
            "$oauthUrl/token",
            data = mapOf(
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

    override suspend fun user(token: AuthToken?): AuthUser? {
        val user = app.get(
            "$apiUrl/users?filter[self]=true",
            headers = mapOf(
                "Authorization" to "Bearer ${token?.accessToken ?: return null}"
            ), cacheTime = 0
        ).parsed<KitsuResponse>()

        if (user.data.isEmpty()) {
           return null
        }

        return AuthUser(
            id = user.data[0].id.toInt(),
            name = user.data[0].attributes.name,
            profilePicture = user.data[0].attributes.avatar?.original
        )
    }

    override suspend fun search(auth: AuthData?, query: String): List<SyncSearchResult>? {
        val auth = auth?.token?.accessToken ?: return null
        val animeSelectedFields = arrayOf("titles","canonicalTitle","posterImage","episodeCount")
        val url = "$apiUrl/anime?filter[text]=$query&page[limit]=$KITSU_MAX_SEARCH_LIMIT&fields[anime]=${animeSelectedFields.joinToString(",")}"
        val res = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer $auth",
            ), cacheTime = 0
        ).parsed<KitsuResponse>()
        return res.data.map {
            val attributes = it.attributes

            val title = attributes.canonicalTitle ?: attributes.titles?.enJp ?: attributes.titles?.jaJp ?: "No title"

            SyncSearchResult(
                title,
                this.name,
                it.id,
                "$mainUrl/anime/${it.id}/",
                attributes.posterImage?.large ?: attributes.posterImage?.medium
            )
        }
    }

    override suspend fun load(auth : AuthData?, id: String): SyncResult? {
        val auth = auth?.token?.accessToken ?: return null
        if (id.toIntOrNull() == null) {
            return null
        }

        data class KitsuResponse(
            @field:JsonProperty(value = "data")
            val data: KitsuNode,
        )

        val url =
            "$apiUrl/anime/$id"

        val anime = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer $auth"
            )
        ).parsed<KitsuResponse>().data.attributes

        return SyncResult(
            id = id,
            totalEpisodes = anime.episodeCount,
            title = anime.canonicalTitle ?: anime.titles?.enJp ?: anime.titles?.jaJp.orEmpty(),
            publicScore =  Score.from(anime.ratingTwenty.toString(), 20),
            duration = anime.episodeLength,
            synopsis = anime.synopsis,
            airStatus = when(anime.status) {
                "finished" -> ShowStatus.Completed
                "current" -> ShowStatus.Ongoing
                else -> null
            },
            nextAiring = null,
            studio = null,
            genres = null,
            trailers = null,
            startDate = LocalDate.parse(anime.startDate).toEpochDay(),
            endDate = LocalDate.parse(anime.endDate).toEpochDay(),
            recommendations = null,
            nextSeason =null,
            prevSeason = null,
            actors = null,
        )

    }

    override suspend fun status(auth : AuthData?, id: String): AbstractSyncStatus? {
        val accessToken = auth?.token?.accessToken ?: return null
        val userId = auth.user.id

        val selectedFields = arrayOf("status","ratingTwenty", "progress")

        val url =
            "$apiUrl/library-entries?filter[userId]=$userId&filter[animeId]=$id&fields[libraryEntries]=${selectedFields.joinToString(",")}"

        val anime = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer $accessToken"
            )
        ).parsed<KitsuResponse>().data.firstOrNull()?.attributes

        if (anime == null) {
            return SyncStatus(
                score = null,
                status = SyncWatchType.NONE,
                isFavorite = null,
                watchedEpisodes = null
            )
        }

        return SyncStatus(
            score = Score.from(anime.ratingTwenty.toString(), 20),
            status = SyncWatchType.fromInternalId(kitsuStatusAsString.indexOf(anime.status)),
            isFavorite = null,
            watchedEpisodes = anime.progress,
        )
    }
    suspend fun getAnimeIdByTitle(title: String): String? {

        val animeSelectedFields = arrayOf("titles","canonicalTitle")
        val url = "$apiUrl/anime?filter[text]=$title&page[limit]=$KITSU_MAX_SEARCH_LIMIT&fields[anime]=${animeSelectedFields.joinToString(",")}"
        val res = app.get(url).parsed<KitsuResponse>()

        return res.data.firstOrNull()?.id

    }

    override fun urlToId(url: String): String? =
        Regex("""/anime/((.*)/|(.*))""").find(url)?.groupValues?.first()

    override suspend fun updateStatus(
        auth : AuthData?,
        id: String,
        newStatus: AbstractSyncStatus
    ): Boolean {

        return setScoreRequest(
            auth ?: return false,
            id.toIntOrNull() ?: return false,
            fromIntToAnimeStatus(newStatus.status),
            newStatus.score?.toInt(20),
            newStatus.watchedEpisodes
        )
    }

    private suspend fun setScoreRequest(
        auth : AuthData,
        id: Int,
        status: KitsuStatusType? = null,
        score: Int? = null,
        numWatchedEpisodes: Int? = null,
    ): Boolean {

        val libraryEntryId = getAnimeLibraryEntryId(auth, id)

        // Exists entry for anime in library
        if (libraryEntryId != null) {

            // Delete anime from library
            if (status == null || status == KitsuStatusType.None) {

                val res = app.delete(
                    "$apiUrl/library-entries/$libraryEntryId",
                    headers = mapOf(
                        "Authorization" to "Bearer ${auth.token.accessToken}"
                    ),
                )

                return res.isSuccessful

            }

            return setScoreRequest(
                auth,
                libraryEntryId,
                kitsuStatusAsString[maxOf(0, status.value)],
                score,
                numWatchedEpisodes
            )

        }

        val data = mapOf(
            "data" to mapOf(
                "type" to "libraryEntries",
                "attributes" to mapOf(
                    "ratingTwenty" to score,
                    "progress" to numWatchedEpisodes,
                    "status" to if (status == null) null else kitsuStatusAsString[maxOf(0, status.value)],
                ),
                "relationships" to mapOf(
                    "anime" to mapOf(
                        "data" to mapOf(
                            "type" to "anime",
                            "id" to id.toString()
                        )
                    ),
                    "user" to mapOf(
                        "data" to mapOf(
                            "type" to "users",
                            "id" to auth.user.id
                        )
                    )
                )
            )
        )

        val res = app.post(
            "$apiUrl/library-entries",
            headers = mapOf(
                "content-type" to "application/vnd.api+json",
                "Authorization" to "Bearer ${auth.token.accessToken}"
            ),
            requestBody = data.toJson().toRequestBody()
        )

        return res.isSuccessful

    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun setScoreRequest(
        auth : AuthData,
        id: Int,
        status: String? = null,
        score: Int? = null,
        numWatchedEpisodes: Int? = null,
    ):  Boolean {
        val data = mapOf(
            "data" to mapOf(
                "type" to "libraryEntries",
                "id" to id.toString(),
                "attributes" to mapOf(
                    "ratingTwenty" to score,
                    "progress" to numWatchedEpisodes,
                    "status" to status
                )
            )
        )

        val res = app.patch(
            "$apiUrl/library-entries/$id",
            headers = mapOf(
                "content-type" to "application/vnd.api+json",
                "Authorization" to "Bearer ${auth.token.accessToken}"
            ),
            requestBody = data.toJson().toRequestBody()
        )

        return res.isSuccessful

    }

    private suspend fun getAnimeLibraryEntryId(auth: AuthData, id: Int): Int? {

        val userId = auth.user.id

        val res = app.get(
            "$apiUrl/library-entries?filter[userId]=$userId&filter[animeId]=$id",
            headers = mapOf(
                "Authorization" to "Bearer ${auth.token.accessToken}"
            ),
        ).parsed<KitsuResponse>().data.firstOrNull() ?: return null

        return res.id.toInt()

    }

    override suspend fun library(auth : AuthData?): LibraryMetadata? {
        val list = getKitsuAnimeListSmart(auth ?: return null)?.groupBy {
            convertToStatus(it.attributes.status ?: "").stringRes
        }?.mapValues { group ->
            group.value.map { it.toLibraryItem() }
        } ?: emptyMap()

        // To fill empty lists when Kitsu does not return them
        val baseMap =
            KitsuStatusType.entries.filter { it.value >= 0 }.associate {
                it.stringRes to emptyList<LibraryItem>()
            }

        return LibraryMetadata(
            (baseMap + list).map { LibraryList(txt(it.key), it.value) },
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

    private suspend fun getKitsuAnimeListSmart(auth : AuthData): Array<KitsuNode>? {
        return if (requireLibraryRefresh) {
            val list = getKitsuAnimeList(auth.token, auth.user.id)
            setKey(KITSU_CACHED_LIST, auth.user.id.toString(), list)
            list
        } else {
            getKey<Array<KitsuNode>>(KITSU_CACHED_LIST, auth.user.id.toString()) as? Array<KitsuNode>
        }
    }

    private suspend fun getKitsuAnimeList(token: AuthToken, userId: Int): Array<KitsuNode> {

        val animeSelectedFields = arrayOf("titles","canonicalTitle","posterImage","synopsis","startDate","episodeCount")
        val libraryEntriesSelectedFields = arrayOf("progress","rating","updatedAt", "status")
        val limit = 500
        var url = "$apiUrl/library-entries?filter[userId]=$userId&filter[kind]=anime&include=anime&page[limit]=$limit&page[offset]=0&fields[anime]=${animeSelectedFields.joinToString(",")}&fields[libraryEntries]=${libraryEntriesSelectedFields.joinToString(",")}"

        val fullList = mutableListOf<KitsuNode>()

        while (true) {

            val data: KitsuResponse = getKitsuAnimeListSlice(token, url)

            data.data.forEachIndexed { index, value ->
                value.anime = data.included?.get(index)
            }

            fullList.addAll(data.data)

            url = data.links?.next ?: break
        }


        return fullList.toTypedArray()
    }

    private suspend fun getKitsuAnimeListSlice(token: AuthToken, url: String): KitsuResponse {
        val res = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer ${token.accessToken}",
            )
        ).parsed<KitsuResponse>()
        return res
    }


    data class ResponseToken(
        @JsonProperty("token_type") val tokenType: String,
        @JsonProperty("expires_in") val expiresIn: Int,
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("refresh_token") val refreshToken: String,
    )

    data class KitsuNode(
        @JsonProperty("id") val id: String,
        @JsonProperty("attributes") val attributes: KitsuNodeAttributes,
        /* User list anime node */
        @JsonProperty("relationships") val relationships: KitsuRelationships?,
        var anime: KitsuAnimeData?
    ) {
        fun toLibraryItem(): LibraryItem {

            val animeItem = this.anime

            val numEpisodes = animeItem?.attributes?.episodeCount

            val startDate = animeItem?.attributes?.startDate

            val posterImage = animeItem?.attributes?.posterImage

            val canonicalTitle = animeItem?.attributes?.canonicalTitle
            val titles = animeItem?.attributes?.titles

            val animeId = animeItem?.id

            val description: String? = animeItem?.attributes?.synopsis

            return LibraryItem(
                canonicalTitle ?: titles?.enJp ?: titles?.jaJp.orEmpty(),
                "https://kitsu.app/anime/${animeId}/",
                this.id,
                this.attributes.progress,
                numEpisodes,
                Score.from(this.attributes.ratingTwenty.toString(), 20),
                parseDateLong(this.attributes.updatedAt),
                "Kitsu",
                TvType.Anime,
                posterImage?.large ?: posterImage?.medium,
                null,
                null,
                plot = description,
                releaseDate = if (startDate == null) null else try {
                    Date.from(
                        Instant.from(
                            DateTimeFormatter.ofPattern(if (startDate.length == 4) "yyyy" else if (startDate.length == 7) "yyyy-MM" else "yyyy-MM-dd")
                                .parse(startDate)
                        )
                    )
                } catch (_: RuntimeException) {
                    null
                }
            )
        }

    }

    data class KitsuAnimeAttributes(
        @JsonProperty("titles") val titles: KitsuTitles?,
        @JsonProperty("canonicalTitle") val canonicalTitle: String?,
        @JsonProperty("posterImage") val posterImage: KitsuPosterImage?,
        @JsonProperty("synopsis") val synopsis: String?,
        @JsonProperty("startDate") val startDate: String?,
        @JsonProperty("endDate") val endDate: String?,
        @JsonProperty("episodeCount") val episodeCount: Int?,
        @JsonProperty("episodeLength") val episodeLength: Int?,
    )

    data class KitsuAnimeData(
        @JsonProperty("id") val id: String,
        @JsonProperty("attributes") val attributes: KitsuAnimeAttributes,
    )


    data class KitsuNodeAttributes(
        /* General attributes */
        @JsonProperty("titles") val titles: KitsuTitles?,
        @JsonProperty("canonicalTitle") val canonicalTitle: String?,
        @JsonProperty("posterImage") val posterImage: KitsuPosterImage?,
        @JsonProperty("synopsis") val synopsis: String?,
        @JsonProperty("startDate") val startDate: String?,
        @JsonProperty("endDate") val endDate: String?,
        @JsonProperty("episodeCount") val episodeCount: Int?,
        @JsonProperty("episodeLength") val episodeLength: Int?,
        /* User attributes */
        @JsonProperty("name") val name: String?,
        @JsonProperty("location") val location: String?,
        @JsonProperty("createdAt") val createdAt: String?,
        @JsonProperty("avatar") val avatar: KitsuUserAvatar?,
        /* User list anime attributes */
        @JsonProperty("progress") val progress: Int?,
        @JsonProperty("ratingTwenty") val ratingTwenty: Float?,
        @JsonProperty("updatedAt") val updatedAt: String?,
        @JsonProperty("status") val status: String?,
    )

    data class KitsuRelationships(
        @JsonProperty("anime") val anime: KitsuRelationshipsAnime?
    )

    data class KitsuRelationshipsAnime(
        @JsonProperty("links") val links: KitsuLinks?
    )

    data class KitsuPosterImage(
        @JsonProperty("large") val large: String?,
        @JsonProperty("medium") val medium: String?,
    )

    data class KitsuTitles(
        @JsonProperty("en_jp") val enJp: String?,
        @JsonProperty("ja_jp") val jaJp: String?
    )

    data class KitsuUserAvatar(
        @JsonProperty("original") val original: String?
    )

    data class KitsuLinks(
        /* Pagination */
        @JsonProperty("first") val first: String?,
        @JsonProperty("next") val next: String?,
        @JsonProperty("last") val last: String?,
        /* Relationships */
        @JsonProperty("related") val related: String?
    )

    data class KitsuResponse(
        @JsonProperty("links") val links: KitsuLinks?,
        @JsonProperty("data") val data: List<KitsuNode>,
        /* When requesting related info (User library entry -> anime) */
        @JsonProperty("included") val included: List<KitsuAnimeData>?,
    )


    companion object {

        const val KITSU_CACHED_LIST: String = "kitsu_cached_list"
        private fun parseDateLong(string: String?): Long? {
            return try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).parse(
                    string ?: return null
                )?.time?.div(1000)
            } catch (e: Exception) {
                null
            }
        }

        private val kitsuStatusAsString =
            arrayOf("current", "completed", "on_hold", "dropped", "planned")
        private fun fromIntToAnimeStatus(inp: SyncWatchType): KitsuStatusType {
            return when (inp) {
                SyncWatchType.NONE ->  KitsuStatusType.None
                SyncWatchType.WATCHING ->  KitsuStatusType.Watching
                SyncWatchType.COMPLETED ->  KitsuStatusType.Completed
                SyncWatchType.ONHOLD ->  KitsuStatusType.OnHold
                SyncWatchType.DROPPED ->  KitsuStatusType.Dropped
                SyncWatchType.PLANTOWATCH ->  KitsuStatusType.PlanToWatch
                SyncWatchType.REWATCHING ->  KitsuStatusType.Watching
            }
        }

        enum class KitsuStatusType(var value: Int, @StringRes val stringRes: Int) {
            Watching(0, R.string.type_watching),
            Completed(1, R.string.type_completed),
            OnHold(2, R.string.type_on_hold),
            Dropped(3, R.string.type_dropped),
            PlanToWatch(4, R.string.type_plan_to_watch),
            None(-1, R.string.type_none)
        }

        private fun convertToStatus(string: String): KitsuStatusType {
            return when (string) {
                "current" ->  KitsuStatusType.Watching
                "completed" ->  KitsuStatusType.Completed
                "on_hold" ->  KitsuStatusType.OnHold
                "dropped" ->  KitsuStatusType.Dropped
                "planned" ->  KitsuStatusType.PlanToWatch
                else ->  KitsuStatusType.None
            }
        }
    }
}

// modified code from from https://github.com/saikou-app/saikou/blob/main/app/src/main/java/ani/saikou/others/Kitsu.kt
// GNU General Public License v3.0 https://github.com/saikou-app/saikou/blob/main/LICENSE.md
object Kitsu {
    private suspend fun getKitsuData(query: String): KitsuResponse {
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Connection" to "keep-alive",
            "DNT" to "1",
            "Origin" to "https://kitsu.io"
        )

        return app.post(
            "https://kitsu.io/api/graphql",
            headers = headers,
            data = mapOf("query" to query)
        ).parsed()
    }

    private val cache: MutableMap<Pair<String, String>, Map<Int, KitsuResponse.Node>> =
        mutableMapOf()

    var isEnabled = true

    suspend fun getEpisodesDetails(
        malId: String?,
        anilistId: String?,
        isResponseRequired: Boolean = true, // overrides isEnabled
    ): Map<Int, KitsuResponse.Node>? {
        if (!isResponseRequired && !isEnabled) return null
        if (anilistId != null) {
            try {
                val map = getKitsuEpisodesDetails(anilistId, "ANILIST_ANIME")
                if (!map.isNullOrEmpty()) return map
            } catch (e: Exception) {
                logError(e)
            }
        }
        if (malId != null) {
            try {
                val map = getKitsuEpisodesDetails(malId, "MYANIMELIST_ANIME")
                if (!map.isNullOrEmpty()) return map
            } catch (e: Exception) {
                logError(e)
            }
        }
        return null
    }

    @Throws
    suspend fun getKitsuEpisodesDetails(id: String, site: String): Map<Int, KitsuResponse.Node>? {
        require(id.isNotBlank()) {
            "Black id"
        }

        require(site.isNotBlank()) {
            "invalid site"
        }

        if (cache.containsKey(id to site)) {
            return cache[id to site]
        }

        val query =
            """
query {
  lookupMapping(externalId: $id, externalSite: $site) {
    __typename
    ... on Anime {
      id
      episodes(first: 2000) {
        nodes {
          number
          titles {
            canonical
          }
          description
          thumbnail {
            original {
              url
            }
          }
        }
      }
    }
  }
}"""
        val result = getKitsuData(query)
        val map = (result.data?.lookupMapping?.episodes?.nodes ?: return null).mapNotNull { ep ->
            val num = ep?.num ?: return@mapNotNull null
            num to ep
        }.toMap()
        if (map.isNotEmpty()) {
            cache[id to site] = map
        }
        return map
    }

    data class KitsuResponse(
        val data: Data? = null
    ) {
        data class Data(
            val lookupMapping: LookupMapping? = null
        )

        data class LookupMapping(
            val id: String? = null,
            val episodes: Episodes? = null
        )

        data class Episodes(
            val nodes: List<Node?>? = null
        )

        data class Node(
            @JsonProperty("number")
            val num: Int? = null,
            val titles: Titles? = null,
            val description: Description? = null,
            val thumbnail: Thumbnail? = null
        )

        data class Description(
            val en: String? = null
        )

        data class Thumbnail(
            val original: Original? = null
        )

        data class Original(
            val url: String? = null
        )

        data class Titles(
            val canonical: String? = null
        )
    }
}