package com.lagradost.cloudstream3.utils.videoskip

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.animeSkipApi
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.AuthLoginRequirement
import com.lagradost.cloudstream3.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.syncproviders.PlainAuthRepo
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest

class AnimeSkipAuth : AuthAPI() {
    override val name = "AnimeSkip"
    override val inAppLoginRequirement: AuthLoginRequirement =
        AuthLoginRequirement(password = true, username = true)
    override val idPrefix = "anime-skip"
    override val hasInApp = true
    override val createAccountUrl = "https://anime-skip.com/account"
    val baseClientId = "as1JgiMbW4wKfmTLWXS79iTDQFll76pk"
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

    data class LoginRoot(
        @JsonProperty("data")
        val data: LoginData,
    )

    data class LoginData(
        @JsonProperty("login")
        val login: Login,
    )

    data class Login(
        @JsonProperty("authToken")
        val authToken: String,
        @JsonProperty("refreshToken")
        val refreshToken: String,
        @JsonProperty("account")
        val account: Account,
    )

    data class ApiRoot(
        @JsonProperty("data")
        val data: ApiData,
    )

    data class ApiData(
        @JsonProperty("myApiClients")
        val myApiClients: List<MyApiClient>,
    )

    data class MyApiClient(
        @JsonProperty("id")
        val id: String,
    )

    data class Account(
        @JsonProperty("profileUrl")
        val profileUrl: String,
        @JsonProperty("username")
        val username: String,
        @JsonProperty("email")
        val email: String,
    )

    data class Payload(
        @JsonProperty("profileUrl")
        val profileUrl: String,
        @JsonProperty("username")
        val username: String,
        @JsonProperty("email")
        val email: String,
        @JsonProperty("clientId")
        val clientId: String,
    )

    override suspend fun user(token: AuthToken?): AuthUser? {
        val payload = parseJson<Payload>(token?.payload ?: return null)
        return AuthUser(
            name = payload.username,
            id = payload.email.hashCode(),
            profilePicture = payload.profileUrl
        )
    }

    override suspend fun login(form: AuthLoginResponse): AuthToken? {
        val hash = md5(form.password ?: return null)
        val emailOrUserName = form.email ?: form.username ?: return null

        val loginQuery = """
    {
      login(usernameEmail: "$emailOrUserName", passwordHash: "$hash") {
        authToken
        refreshToken
        account {
          profileUrl
          username
          email
        }
      }
    }
"""
        val loginRoot = app.post(
            "https://api.anime-skip.com/graphql",
            json = mapOf("query" to loginQuery),
            headers = mapOf(
                "Accept" to "*/*",
                "content-type" to "application/json",
                "X-Client-ID" to baseClientId
            )
        ).parsed<LoginRoot>()

        val authToken = loginRoot.data.login.authToken
        val refreshToken = loginRoot.data.login.refreshToken
        val account = loginRoot.data.login.account

        val clientQuery = """
            {
              myApiClients {
                id
              }
            }
        """.trimIndent()

        val apiRoot = app.post(
            "https://api.anime-skip.com/graphql",
            json = mapOf("query" to clientQuery),
            headers = mapOf(
                "Accept" to "*/*",
                "content-type" to "application/json",
                "Authorization" to "Bearer $authToken",
                "X-Client-ID" to baseClientId
            )
        ).parsed<ApiRoot>()

        val clientId = apiRoot.data.myApiClients.getOrNull(0)?.id
            ?: throw ErrorLoadingException("No API token found")

        val payload = Payload(
            profileUrl = account.profileUrl,
            username = account.username,
            email = account.email,
            clientId = clientId,
        )
        return AuthToken(
            accessToken = authToken,
            refreshToken = refreshToken,
            payload = payload.toJson()
        )
    }
}

class AnimeSkip : SkipAPI() {
    override val name: String = "AniSkip"
    override val supportedTypes: Set<TvType> = setOf(TvType.Anime, TvType.OVA)

    val auth = PlainAuthRepo(animeSkipApi)
    //val clientId = "ZGfO0sMF3eCwLYf8yMSCJjlynwNGRXWE"

    companion object {
        const val MIN_LENGTH: Int = 4

        private val strip = Regex("[ :\\-.!]")

        /** Makes names more uniform to make partial matches more still give a result */
        fun stripName(name: String?): String? =
            name?.replace(strip, "")?.lowercase()

        private val asciiRegex = Regex("[^a-zA-Z0-9 ]")

        /** Makes names more uniform to make partial matches more still give a result */
        fun asciiName(name: String?): String? =
            name?.replace(asciiRegex, "")?.lowercase()
    }

    data class Root(
        @JsonProperty("data")
        val data: Data,
    )

    data class Data(
        @JsonProperty("searchShows")
        val searchShows: List<SearchShow>,
    )

    data class SearchShow(
        @JsonProperty("name")
        val name: String,
        @JsonProperty("originalName")
        val originalName: String?,
        @JsonProperty("seasonCount")
        val seasonCount: Long,
        @JsonProperty("episodeCount")
        val episodeCount: Long,
        @JsonProperty("baseDuration")
        val baseDuration: Double,
        @JsonProperty("episodes")
        val episodes: List<Episode>,
    )

    data class Episode(
        @JsonProperty("number")
        val number: String?,
        @JsonProperty("absoluteNumber")
        val absoluteNumber: String?,
        @JsonProperty("season")
        val season: String?,
        @JsonProperty("timestamps")
        val timestamps: List<Timestamp>,
    )

    data class Timestamp(
        @JsonProperty("at")
        val at: Double,
        @JsonProperty("type")
        val type: Type,
    )

    data class Type(
        @JsonProperty("name")
        val name: String,
    )

    val cache: ConcurrentHashMap<String, Data> = ConcurrentHashMap()

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): List<SkipStamp>? {
        val clientId : String = parseJson<AnimeSkipAuth.Payload>(
            auth.authData()?.token?.payload ?: return null
        ).clientId

        when (data) {
            is AnimeLoadResponse, is TvSeriesLoadResponse -> {
                /** Require episode based anime */
            }

            else -> return null
        }

        val query = """{
  searchShows(search: "${data.name}", limit: 1) {
    name
    originalName
    seasonCount
    episodeCount
    episodes {
      number
      absoluteNumber
      season
      baseDuration
      timestamps {
        at
        type {
          name
        }
      }
    }
  }
}"""
        val root = cache[data.name] ?: run {
            app.post(
                "https://api.anime-skip.com/graphql",
                json = mapOf("query" to query),
                headers = mapOf(
                    "Accept" to "*/*",
                    "content-type" to "application/json",
                    "X-Client-ID" to clientId
                )
            )
                .parsed<Root>().data.also { root ->
                    cache[data.name] = root
                }
        }
        val show = root.searchShows.firstOrNull { show ->
            /** Match ascii */
            val ascii1 = asciiName(data.name)
            val ascii2 = asciiName(show.name)
            if (ascii1 == ascii2 && (ascii1?.length ?: 0) > MIN_LENGTH) {
                return@firstOrNull true
            }

            if (data !is AnimeLoadResponse) {
                return@firstOrNull false
            }

            /** Match original name */
            val strip1 = stripName(show.originalName)
            val strip2 = stripName(data.japName)

            /** Match english name*/
            val ascii3 = stripName(data.engName)
            (strip1 == strip2 && (strip1?.length ?: 0) > MIN_LENGTH) ||
                    (ascii2 == ascii3 && (ascii2?.length ?: 0) > MIN_LENGTH)
        } ?: return null

        val showEpisode = when (data) {
            is AnimeLoadResponse -> {
                val episodeNumber = episode.episode.toString()
                /** For anime, match on number */
                show.episodes.firstOrNull {
                    it.absoluteNumber == episodeNumber
                } ?: show.episodes.firstOrNull {
                    it.number == episodeNumber
                }
            }

            is TvSeriesLoadResponse -> {
                /** For tv-series, match on season + number */
                val seasonNumber = episode.season?.toString()
                val episodeNumber = episode.episode.toString()
                val episodeIndex = episode.totalEpisodeIndex.toString()

                show.episodes.firstOrNull {
                    it.season == seasonNumber && it.number == episodeNumber
                } ?: show.episodes.firstOrNull {
                    it.absoluteNumber == episodeIndex
                }
            }

            else -> null
        } ?: return null

        val result = ArrayList<SkipStamp>()
        var pending: SkipStamp? = null
        for (stamp in showEpisode.timestamps) {
            val startMS = (stamp.at * 1000.0).toLong()
            pending?.let { pending ->
                result.add(pending.copy(endMs = startMS))
            }
            val type = when (stamp.type.name) {
                "Intro", "New Intro" -> SkipType.Intro
                "Credits" -> SkipType.Credits
                "Preview" -> SkipType.Preview
                "Recap" -> SkipType.Recap
                "Mixed Credits" -> SkipType.MixedEnding
                "Filler", "Transition", "Branding", "Canon", "Title Card" -> null
                else -> null
            }
            if (type == null) {
                pending = null
                continue
            }
            pending = SkipStamp(type, startMS, 0L)
        }
        pending?.let { pending ->
            result.add(pending.copy(endMs = episodeDurationMs))
            /** Base duration = fucked */
        }

        return result
    }
}

