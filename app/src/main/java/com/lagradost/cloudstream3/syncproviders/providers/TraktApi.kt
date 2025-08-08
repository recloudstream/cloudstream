package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.syncproviders.AuthLoginPage
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.ui.SyncWatchType

/* https://trakt.docs.apiary.io */
class TraktApi : SyncAPI() {
    override val name = "Trakt"
    override val idPrefix = "trakt"

    override val mainUrl = "https://trakt.tv"
    val api = "https://api.trakt.tv"

    override val supportedWatchTypes: Set<SyncWatchType> = emptySet()

    override val icon = R.drawable.trakt
    override val hasOAuth2 = true
    override val redirectUrlIdentifier = "NONE"
    val redirectUri = "cloudstreamapp://$redirectUrlIdentifier"

    companion object {
        val id: String get() = throw NotImplementedError()
        val secret: String get() = throw NotImplementedError()

        fun getHeaders(token: AuthToken) = mapOf(
            "Authorization" to "Bearer ${token.accessToken}",
            "Content-Type" to "application/json",
            "trakt-api-version" to "2",
            "trakt-api-key" to id,
        )
    }

    data class TokenRoot(
        @JsonProperty("access_token")
        val accessToken: String,
        @JsonProperty("token_type")
        val tokenType: String,
        @JsonProperty("expires_in")
        val expiresIn: Long,
        @JsonProperty("refresh_token")
        val refreshToken: String,
        @JsonProperty("scope")
        val scope: String,
        @JsonProperty("created_at")
        val createdAt: Long,
    )

    data class UserRoot(
        @JsonProperty("username")
        val username: String,
        @JsonProperty("private")
        val private: Boolean?,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("vip")
        val vip: Boolean?,
        @JsonProperty("vip_ep")
        val vipEp: Boolean?,
        @JsonProperty("ids")
        val ids: Ids?,
        @JsonProperty("joined_at")
        val joinedAt: String?,
        @JsonProperty("location")
        val location: String?,
        @JsonProperty("about")
        val about: String?,
        @JsonProperty("gender")
        val gender: String?,
        @JsonProperty("age")
        val age: Long?,
        @JsonProperty("images")
        val images: Images?,
    ) {
        data class Ids(
            @JsonProperty("slug")
            val slug: String,
        )

        data class Images(
            @JsonProperty("avatar")
            val avatar: Avatar,
        )

        data class Avatar(
            @JsonProperty("full")
            val full: String,
        )
    }


    override suspend fun user(token: AuthToken?): AuthUser? {
        if (token == null) return null
        // https://trakt.docs.apiary.io/#reference/users/profile/get-user-profile

        val userData = app.get(
            "$api/users/me?extended=full", headers = getHeaders(token)
        ).parsed<UserRoot>()

        return AuthUser(
            name = userData.name,
            id = userData.username.hashCode(),
            profilePicture = userData.images?.avatar?.full
        )
    }

    override suspend fun login(redirectUrl: String, payload: String?): AuthToken? {
        val sanitizer =
            splitRedirectUrl(redirectUrl)

        if (sanitizer["state"] != payload) {
            return null
        }

        // https://trakt.docs.apiary.io/#reference/authentication-oauth/get-token/exchange-code-for-access_token
        val tokenData = app.post(
            "$api/oauth/token",
            json = mapOf(
                "code" to (sanitizer["code"] ?: throw ErrorLoadingException("No code")),
                "client_id" to id,
                "client_secret" to secret,
                "redirect_uri" to redirectUri,
                "grant_type" to "authorization_code"
            )
        ).parsed<TokenRoot>()

        return AuthToken(
            accessToken = tokenData.accessToken,
            refreshToken = tokenData.refreshToken,
            accessTokenLifetime = unixTime + tokenData.expiresIn
        )
    }

    override suspend fun refreshToken(token: AuthToken): AuthToken? {
        // https://trakt.docs.apiary.io/#reference/authentication-oauth/get-token/exchange-refresh_token-for-access_token
        val tokenData = app.post(
            "$api/oauth/token",
            json = mapOf(
                "refresh_token" to (token.refreshToken
                    ?: throw ErrorLoadingException("No refreshtoken")),
                "client_id" to id,
                "client_secret" to secret,
                "redirect_uri" to redirectUri,
                "grant_type" to "refresh_token",
            )
        ).parsed<TokenRoot>()

        return AuthToken(
            accessToken = tokenData.accessToken,
            refreshToken = tokenData.refreshToken,
            accessTokenLifetime = unixTime + tokenData.expiresIn
        )
    }

    override fun loginRequest(): AuthLoginPage? {
        // https://trakt.docs.apiary.io/#reference/authentication-oauth/authorize/authorize-application
        val codeChallenge = generateCodeVerifier()
        return AuthLoginPage(
            "$mainUrl/oauth/authorize?client_id=$id&response_type=code&redirect_uri=$redirectUri&state=$codeChallenge",
            payload = codeChallenge
        )
    }

    data class RatingRoot(
        @JsonProperty("rated_at")
        val ratedAt: String?,
        @JsonProperty("rating")
        val rating: Int?,
        @JsonProperty("type")
        val type: String,
        @JsonProperty("season")
        val season: Season?,
        @JsonProperty("show")
        val show: Show?,
        @JsonProperty("movie")
        val movie: Movie?,
    ) {
        data class Season(
            @JsonProperty("number")
            val number: Long?,
            @JsonProperty("ids")
            val ids: Ids?,
        )

        data class Show(
            @JsonProperty("title")
            val title: String?,
            @JsonProperty("year")
            val year: Long?,
            @JsonProperty("ids")
            val ids: Ids?,
        )

        data class Movie(
            @JsonProperty("title")
            val title: String?,
            @JsonProperty("year")
            val year: Long?,
            @JsonProperty("ids")
            val ids: Ids?,
        )

        data class Ids(
            @JsonProperty("trakt")
            val trakt: String?,
            @JsonProperty("slug")
            val slug: String?,
            @JsonProperty("tvdb")
            val tvdb: String?,
            @JsonProperty("imdb")
            val imdb: String?,
            @JsonProperty("tmdb")
            val tmdb: String?,
        )
    }


    data class TraktSyncStatus(
        override var status: SyncWatchType = SyncWatchType.NONE,
        override var score: Score?,
        override var watchedEpisodes: Int? = null,
        override var isFavorite: Boolean? = null,
        override var maxEpisodes: Int? = null,
        val type: String,
    ) : AbstractSyncStatus()

    override suspend fun status(token: AuthToken?, id: String): AbstractSyncStatus? {
        if (token == null) return null

        val response = app.get("$api/sync/ratings/all", headers = getHeaders(token))
            .parsed<Array<RatingRoot>>()

        // This is criminally wrong, but there is no api to get the rating directly
        for (x in response) {
            if (x.show?.ids?.trakt == id || x.movie?.ids?.trakt == id || x.season?.ids?.trakt == id) {
                return TraktSyncStatus(score = Score.from10(x.rating), type = x.type)
            }
        }

        return SyncStatus(SyncWatchType.NONE, null, null, null, null)
    }
}