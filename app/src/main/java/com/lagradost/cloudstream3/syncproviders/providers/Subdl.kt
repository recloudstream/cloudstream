package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.AuthLoginRequirement
import com.lagradost.cloudstream3.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.AuthUser
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.TvType

class SubDlApi : SubtitleAPI() {
    override val name = "SubDL"
    override val idPrefix = "subdl"

    override val icon = R.drawable.subdl_logo_big
    override val hasInApp = true
    override val inAppLoginRequirement = AuthLoginRequirement(password = true, email = true)
    override val requiresLogin = true
    override val createAccountUrl = "https://subdl.com/panel/register"

    companion object {
        const val APIURL = "https://apiold.subdl.com"
        const val APIENDPOINT = "$APIURL/api/v1/subtitles"
        const val DOWNLOADENDPOINT = "https://dl.subdl.com"
    }

    override suspend fun login(form: AuthLoginResponse): AuthToken? {
        val email = form.email ?: return null
        val password = form.password ?: return null
        val tokenResponse = app.post(
            url = "$APIURL/login",
            json = mapOf(
                "email" to email,
                "password" to password
            )
        ).parsed<OAuthTokenResponse>()

        val apiResponse = app.get(
            url = "$APIURL/user/userApi",
            headers = mapOf(
                "Authorization" to "Bearer ${tokenResponse.token}"
            )
        ).parsed<ApiKeyResponse>()

        return AuthToken(accessToken = apiResponse.apiKey, payload = email)
    }

    override suspend fun user(token: AuthToken?): AuthUser? {
        val name = token?.payload ?: return null
        return AuthUser(id = name.hashCode(), name = name)
    }

    override suspend fun search(
        auth : AuthData?,
        query: AbstractSubtitleEntities.SubtitleSearch
    ): List<AbstractSubtitleEntities.SubtitleEntity>? {
        if (auth == null) return null
        val apiKey = auth.token.accessToken ?: return null
        val queryText = query.query
        val epNum = query.epNumber ?: 0
        val seasonNum = query.seasonNumber ?: 0
        val yearNum = query.year ?: 0
        val langSubdlCode = langTagIETF2subdl[query.lang.toString()] ?: query.lang

        val idQuery = when {
            query.imdbId != null -> "&imdb_id=${query.imdbId}"
            query.tmdbId != null -> "&tmdb_id=${query.tmdbId}"
            else -> null
        }

        val epQuery = if (epNum > 0) "&episode_number=$epNum" else ""
        val seasonQuery = if (seasonNum > 0) "&season_number=$seasonNum" else ""
        val yearQuery = if (yearNum > 0) "&year=$yearNum" else ""

        val searchQueryUrl = when (idQuery) {
            //Use imdb/tmdb id to search if its valid
            null -> "$APIENDPOINT?api_key=${apiKey}&film_name=$queryText&languages=$langSubdlCode$epQuery$seasonQuery$yearQuery"
            else -> "$APIENDPOINT?api_key=${apiKey}$idQuery&languages=$langSubdlCode$epQuery$seasonQuery$yearQuery"
        }

        val req = app.get(
            url = searchQueryUrl,
            headers = mapOf(
                "Accept" to "application/json"
            )
        )

        return req.parsedSafe<ApiResponse>()?.subtitles?.map { subtitle ->

            val langTagIETF =
                langTagIETF2subdl.entries.find { it.value == subtitle.lang }?.key ?:
                subtitle.lang
            val resEpNum = subtitle.episode ?: query.epNumber
            val resSeasonNum = subtitle.season ?: query.seasonNumber
            val type = if ((resSeasonNum ?: 0) > 0) TvType.TvSeries else TvType.Movie

            AbstractSubtitleEntities.SubtitleEntity(
                idPrefix = this.idPrefix,
                name = subtitle.releaseName,
                lang = langTagIETF,
                data = "${DOWNLOADENDPOINT}${subtitle.url}",
                type = type,
                source = this.name,
                epNumber = resEpNum,
                seasonNumber = resSeasonNum,
                isHearingImpaired = subtitle.hearingImpaired ?: false,
            )
        }
    }

    override suspend fun SubtitleResource.getResources(
        auth: AuthData?,
        subtitle: AbstractSubtitleEntities.SubtitleEntity
    ) {
        this.addZipUrl(subtitle.data) { name, _ ->
            name
        }
    }

    data class SubtitleOAuthEntity(
        @JsonProperty("userEmail") var userEmail: String,
        @JsonProperty("pass") var pass: String,
        @JsonProperty("name") var name: String? = null,
        @JsonProperty("accessToken") var accessToken: String? = null,
        @JsonProperty("apiKey") var apiKey: String? = null,
    )

    data class OAuthTokenResponse(
        @JsonProperty("token") val token: String,
        @JsonProperty("userData") val userData: UserData? = null,
        @JsonProperty("status") val status: Boolean? = null,
        @JsonProperty("message") val message: String? = null,
    )

    data class UserData(
        @JsonProperty("email") val email: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("country") val country: String,
        @JsonProperty("scStepCode") val scStepCode: String,
        @JsonProperty("scVerified") val scVerified: Boolean,
        @JsonProperty("username") val username: String? = null,
        @JsonProperty("scUsername") val scUsername: String,
    )

    data class ApiKeyResponse(
        @JsonProperty("ok") val ok: Boolean? = false,
        @JsonProperty("api_key") val apiKey: String,
        @JsonProperty("usage") val usage: Usage? = null,
    )

    data class Usage(
        @JsonProperty("total") val total: Long? = 0,
        @JsonProperty("today") val today: Long? = 0,
    )

    data class ApiResponse(
        @JsonProperty("status") val status: Boolean? = null,
        @JsonProperty("results") val results: List<Result>? = null,
        @JsonProperty("subtitles") val subtitles: List<Subtitle>? = null,
    )

    data class Result(
        @JsonProperty("sd_id") val sdId: Int? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("tmdb_id") val tmdbId: Long? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("year") val year: Int? = null,
    )

    data class Subtitle(
        @JsonProperty("release_name") val releaseName: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("lang") val lang: String, // subdl language code
        @JsonProperty("author") val author: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("subtitlePage") val subtitlePage: String? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("language") val language: String? = null, // full language name
        @JsonProperty("hi") val hearingImpaired: Boolean? = null,
    )

    // https://subdl.com/api-files/language_list.json
    // most of it is IETF BPC 47 conformant tag
    // but there are some exceptions
    private val langTagIETF2subdl = mapOf(
        "en-bg" to "BG_EN", // "Bulgarian_English"
        "en-de" to "EN_DE", // "English_German"
        "en-hu" to "HU_EN", // "Hungarian_English"
        "en-nl" to "NL_EN", // "Dutch_English"
        "pt-br" to "BR_PT", // "Brazillian Portuguese"
        "zh-hant" to "ZH_BG", // "Big 5 code" -> traditional Chinese (?_?)
        // "ar" to "AR", // "Arabic"
        // "az" to "AZ", // "Azerbaijani"
        // "be" to "BE", // "Belarusian"
        // "bg" to "BG", // "Bulgarian"
        // "bn" to "BN", // "Bengali"
        // "bs" to "BS", // "Bosnian"
        // "ca" to "CA", // "Catalan"
        // "cs" to "CS", // "Czech"
        // "da" to "DA", // "Danish"
        // "de" to "DE", // "German"
        // "el" to "EL", // "Greek"
        // "en" to "EN", // "English"
        // "eo" to "EO", // "Esperanto"
        // "es" to "ES", // "Spanish"
        // "et" to "ET", // "Estonian"
        // "fa" to "FA", // "Farsi_Persian"
        // "fi" to "FI", // "Finnish"
        // "fr" to "FR", // "French"
        // "he" to "HE", // "Hebrew"
        // "hi" to "HI", // "Hindi"
        // "hr" to "HR", // "Croatian"
        // "hu" to "HU", // "Hungarian"
        // "id" to "ID", // "Indonesian"
        // "is" to "IS", // "Icelandic"
        // "it" to "IT", // "Italian"
        // "ja" to "JA", // "Japanese"
        // "ka" to "KA", // "Georgian"
        // "kl" to "KL", // "Greenlandic"
        // "ko" to "KO", // "Korean"
        // "ku" to "KU", // "Kurdish"
        // "lt" to "LT", // "Lithuanian"
        // "lv" to "LV", // "Latvian"
        // "mk" to "MK", // "Macedonian"
        // "ml" to "ML", // "Malayalam"
        // "mni" to "MNI", // "Manipuri"
        // "ms" to "MS", // "Malay"
        // "my" to "MY", // "Burmese"
        // "nl" to "NL", // "Dutch"
        // "no" to "NO", // "Norwegian"
        // "pl" to "PL", // "Polish"
        // "pt" to "PT", // "Portuguese"
        // "ro" to "RO", // "Romanian"
        // "ru" to "RU", // "Russian"
        // "si" to "SI", // "Sinhala"
        // "sk" to "SK", // "Slovak"
        // "sl" to "SL", // "Slovenian"
        // "sq" to "SQ", // "Albanian"
        // "sr" to "SR", // "Serbian"
        // "sv" to "SV", // "Swedish"
        // "ta" to "TA", // "Tamil"
        // "te" to "TE", // "Telugu"
        // "th" to "TH", // "Thai"
        // "tl" to "TL", // "Tagalog"
        // "tr" to "TR", // "Turkish"
        // "uk" to "UK", // "Ukranian"
        // "ur" to "UR", // "Urdu"
        // "vi" to "VI", // "Vietnamese"
        // "zh" to "ZH", // "Chinese BG code"
    )
}
