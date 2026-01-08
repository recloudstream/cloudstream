package com.lagradost.cloudstream3.syncproviders.providers


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.AuthLoginRequirement
import com.lagradost.cloudstream3.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.syncproviders.providers.MALApi.Companion.MalStatusType
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.utils.DataStore.toKotlinObject
import com.lagradost.cloudstream3.utils.txt
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

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
    val KITSU_CACHED_LIST: String = "kitsu_cached_list"

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
        val url = "$apiUrl/anime?filter[text]=$query&page[limit]=$KITSU_MAX_SEARCH_LIMIT"
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

    override fun urlToId(url: String): String? =
        Regex("""/anime/((.*)/|(.*))""").find(url)!!.groupValues.first()

    override suspend fun library(auth : AuthData?): LibraryMetadata? {
        val list = getKitsuAnimeListSmart(auth ?: return null)?.groupBy {
            convertToStatus(it.attributes.status ?: "").stringRes
        }?.mapValues { group ->
            group.value.map { it.toLibraryItem(auth.token) }
        } ?: emptyMap()

        // To fill empty lists when MAL does not return them
        val baseMap =
            MalStatusType.entries.filter { it.value >= 0 }.associate {
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
            val list = getKitsuAnimeList(auth.token) ?: return null
            setKey(KITSU_CACHED_LIST, auth.user.id.toString(), list)
            list
        } else {
            getKey<Array<KitsuNode>>(KITSU_CACHED_LIST, auth.user.id.toString()) as? Array<KitsuNode>
        }
    }

    private suspend fun getKitsuAnimeList(token: AuthToken): Array<KitsuNode>? {
        var offset = 0
        val fullList = mutableListOf<KitsuNode>()
        val offsetRegex = Regex("""offset=(\d+)""")
        val userId = user(token)?.id ?: return null
        while (true) {
            val data: KitsuResponse = getKitsuAnimeListSlice(token, userId, offset) ?: break
            fullList.addAll(data.data)
            offset =
                data.links?.next?.let { offsetRegex.find(it)?.groupValues?.get(1)?.toInt() }
                    ?: break
        }
        return fullList.toTypedArray()
    }

    private suspend fun getKitsuAnimeListSlice(token: AuthToken, userId: Int, offset: Int = 0): KitsuResponse? {

        val url =
            "$apiUrl/library-entries?filter[userId]=$userId&filter[kind]=anime&page[limit]=100&page[offset]=$offset"
        val res = app.get(
            url, headers = mapOf(
                "Authorization" to "Bearer ${token.accessToken}",
            ), cacheTime = 0
        ).parsed<KitsuResponse>()
        return res
    }
//    override suspend fun updateStatus(
//        auth: AuthData?,
//        id: String,
//        newStatus: SyncAPI.AbstractSyncStatus
//    ): Boolean {
//        return setScoreRequest(
//            auth?.token ?: return false,
//            id.toIntOrNull() ?: return false,
//            fromIntToAnimeStatus(newStatus.status),
//            newStatus.score?.toInt(10),
//            newStatus.watchedEpisodes
//        )
//    }


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
    ) {
        suspend fun toLibraryItem(token: AuthToken): LibraryItem {

            val animeItem = getAnimeItem(token)

            val numEpisodes = animeItem?.attributes?.episodeCount

            val startDate = animeItem?.attributes?.startDate

            val posterImage = animeItem?.attributes?.posterImage

            val canonicalTitle = animeItem?.attributes?.canonicalTitle
            val titles = animeItem?.attributes?.titles

            val animeId = animeItem?.id

            val synopsis: String? = animeItem?.attributes?.synopsis

            return LibraryItem(
                canonicalTitle ?: titles?.enJp ?: titles?.jaJp.orEmpty(),
                "https://kitsu.app/anime/${animeId}/",
                this.id,
                this.attributes.progress,
                numEpisodes,
                Score.from5(this.attributes.rating),
                parseDateLong(this.attributes.updatedAt),
                "Kitsu",
                TvType.Anime,
                posterImage?.large ?: posterImage?.medium,
                null,
                null,
                plot = synopsis,
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

        private suspend fun getAnimeItem(token: AuthToken): KitsuNode? {

            val url = this.relationships?.anime?.links?.related ?: return null

            val res = app.get(
                url, headers = mapOf(
                    "Authorization" to "Bearer ${token.accessToken}",
                ), cacheTime = 0).parsed<KitsuResponseUnique>()

            return res.data
        }
    }

    data class KitsuNodeAttributes(
        /* General attributes */
        @JsonProperty("titles") val titles: KitsuTitles?,
        @JsonProperty("canonicalTitle") val canonicalTitle: String?,
        @JsonProperty("posterImage") val posterImage: KitsuPosterImage?,
        @JsonProperty("synopsis") val synopsis: String?,
        @JsonProperty("startDate") val startDate: String?,
        @JsonProperty("episodeCount") val episodeCount: Int,
        /* User attributes */
        @JsonProperty("name") val name: String?,
        @JsonProperty("location") val location: String?,
        @JsonProperty("createdAt") val createdAt: String?,
        @JsonProperty("avatar") val avatar: KitsuUserAvatar?,
        /* User list anime attributes */
        @JsonProperty("progress") val progress: Int?,
        @JsonProperty("rating") val rating: String?,
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
        @JsonProperty("data") val data: List<KitsuNode>
    )

    data class KitsuResponseUnique(
        @JsonProperty("data") val data: KitsuNode
    )

    companion object {
        private fun parseDateLong(string: String?): Long? {
            return try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).parse(
                    string ?: return null
                )?.time?.div(1000)
            } catch (e: Exception) {
                null
            }
        }

        private fun convertToStatus(string: String): MalStatusType {
            return when (string) {
                "current" -> MalStatusType.Watching
                "completed" -> MalStatusType.Completed
                "on_hold" -> MalStatusType.OnHold
                "dropped" -> MalStatusType.Dropped
                "planned" -> MalStatusType.PlanToWatch
                else -> MalStatusType.None
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