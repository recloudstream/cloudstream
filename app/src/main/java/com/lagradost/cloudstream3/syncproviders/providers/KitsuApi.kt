package com.lagradost.cloudstream3.syncproviders.providers


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.AuthLoginRequirement
import com.lagradost.cloudstream3.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.ui.SyncWatchType

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

    override suspend fun search(auth: AuthData?, query: String): List<SyncAPI.SyncSearchResult>? {
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

            SyncAPI.SyncSearchResult(
                title,
                this.name,
                it.id.toString(),
                "$mainUrl/anime/${it.id}/",
                attributes.posterImage?.large ?: attributes.posterImage?.medium
            )
        }
    }

    override fun urlToId(url: String): String? =
        Regex("""/anime/((.*)/|(.*))""").find(url)!!.groupValues.first()

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

//    data class MalDatum(
//        @JsonProperty("node") val node: MalNode,
//        @JsonProperty("list_status") val listStatus: MalStatus,
//    )

    data class KitsuNode(
        @JsonProperty("id") val id: String,
        @JsonProperty("attributes") val attributes: KitsuNodeAttributes
        /*
        also, but not used
        main_picture ->
            public string medium;
			public string large;
         */
    )

    data class KitsuNodeAttributes(
        @JsonProperty("titles") val titles: KitsuTitles?,
        @JsonProperty("canonicalTitle") val canonicalTitle: String?,
        @JsonProperty("posterImage") val posterImage: KitsuPosterImage?,
        /* User attributes */
        @JsonProperty("name") val name: String?,
        @JsonProperty("location") val location: String?,
        @JsonProperty("createdAt") val createdAt: String?,
        @JsonProperty("avatar") val avatar: KitsuUserAvatar?
    )

    data class KitsuPosterImage(
        @JsonProperty("large") val large: String?,
        @JsonProperty("medium") val medium: String?,

    )

    data class KitsuTitles(
        @JsonProperty("en_jp") val enJp: String?,
        @JsonProperty("ja_jp") val jaJp: String?
    )

//    data class MalStatus(
//        @JsonProperty("status") val status: String,
//        @JsonProperty("score") val score: Int,
//        @JsonProperty("num_episodes_watched") val numEpisodesWatched: Int,
//        @JsonProperty("is_rewatching") val isRewatching: Boolean,
//        @JsonProperty("updated_at") val updatedAt: String,
//    )

    data class KitsuUserAvatar(
        @JsonProperty("original") val original: String?
    )

    data class MalMainPicture(
        @JsonProperty("large") val large: String?,
        @JsonProperty("medium") val medium: String?,
    )

    // Used for getDataAboutId()
//    data class SmallMalAnime(
//        @JsonProperty("id") val id: Int,
//        @JsonProperty("title") val title: String?,
//        @JsonProperty("num_episodes") val numEpisodes: Int,
//        @JsonProperty("my_list_status") val myListStatus: MalStatus?,
//        @JsonProperty("main_picture") val mainPicture: MalMainPicture?,
//    )

//    data class MalSearchNode(
//        @JsonProperty("node") val node: Node,
//    )

    data class KitsuSearchLinks(
        @JsonProperty("first") val first: String?,
        @JsonProperty("next") val next: String?,
        @JsonProperty("last") val last: String?
    )

    data class KitsuResponse(
        @JsonProperty("links") val links: KitsuSearchLinks?,
        @JsonProperty("data") val data: List<KitsuNode>
        //paging
    )

//    data class MalTitleHolder(
//        val status: MalStatus,
//        val id: Int,
//        val name: String,
//    )
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