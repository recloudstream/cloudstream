package com.lagradost.cloudstream3.syncproviders.providers

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.removeKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.subtitles.AbstractSubApi
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager
import com.lagradost.cloudstream3.utils.AppUtils
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class OpenSubtitlesApi(index: Int) : InAppAuthAPIManager(index), AbstractSubApi {
    override val idPrefix = "opensubtitles"
    override val name = "OpenSubtitles"
    override val icon = R.drawable.open_subtitles_icon
    override val requiresPassword = true
    override val requiresUsername = true
    override val createAccountUrl = "https://www.opensubtitles.com/en/users/sign_up"

    companion object {
        const val OPEN_SUBTITLES_USER_KEY: String = "open_subtitles_user" // user data like profile
        const val apiKey = "uyBLgFD17MgrYmA0gSXoKllMJBelOYj2"
        const val host = "https://api.opensubtitles.com/api/v1"
        const val TAG = "OPENSUBS"
        const val coolDownDuration: Long = 1000L * 30L // CoolDown if 429 error code in ms
        var currentCoolDown: Long = 0L
        var currentSession: SubtitleOAuthEntity? = null
    }

    private fun canDoRequest(): Boolean {
        return unixTimeMs > currentCoolDown
    }

    private fun throwIfCantDoRequest() {
        if (!canDoRequest()) {
            throw ErrorLoadingException("Too many requests wait for ${(currentCoolDown - unixTimeMs) / 1000L}s")
        }
    }

    private fun throwGotTooManyRequests() {
        currentCoolDown = unixTimeMs + coolDownDuration
        throw ErrorLoadingException("Too many requests")
    }

    private fun getAuthKey(): SubtitleOAuthEntity? {
        return getKey(accountId, OPEN_SUBTITLES_USER_KEY)
    }

    private fun setAuthKey(data: SubtitleOAuthEntity?) {
        if (data == null) removeKey(accountId, OPEN_SUBTITLES_USER_KEY)
        currentSession = data
        setKey(accountId, OPEN_SUBTITLES_USER_KEY, data)
    }

    override fun loginInfo(): AuthAPI.LoginInfo? {
        getAuthKey()?.let { user ->
            return AuthAPI.LoginInfo(
                profilePicture = null,
                name = user.user,
                accountIndex = accountIndex
            )
        }
        return null
    }

    override fun getLatestLoginData(): InAppAuthAPI.LoginData? {
        val current = getAuthKey() ?: return null
        return InAppAuthAPI.LoginData(username = current.user, current.pass)
    }

    /*
        Authorize app to connect to API, using username/password.
        Required to run at startup.
        Returns OAuth entity with valid access token.
    */
    override suspend fun initialize() {
        currentSession = getAuthKey() ?: return // just in case the following fails
        initLogin(currentSession?.user ?: return, currentSession?.pass ?: return)
    }

    override fun logOut() {
        setAuthKey(null)
        removeAccountKeys()
        currentSession = getAuthKey()
    }

    private suspend fun initLogin(username: String, password: String): Boolean {
        //Log.i(TAG, "DATA = [$username] [$password]")
        val response = app.post(
            url = "$host/login",
            headers = mapOf(
                "Api-Key" to apiKey,
                "Content-Type" to "application/json"
            ),
            data = mapOf(
                "username" to username,
                "password" to password
            )
        )
        //Log.i(TAG, "Responsecode = ${response.code}")
        //Log.i(TAG, "Result => ${response.text}")

        if (response.isSuccessful) {
            AppUtils.tryParseJson<OAuthToken>(response.text)?.let { token ->
                setAuthKey(
                    SubtitleOAuthEntity(
                        user = username,
                        pass = password,
                        access_token = token.token ?: run {
                            return false
                        })
                )
            }
            return true
        }
        return false
    }

    override suspend fun login(data: InAppAuthAPI.LoginData): Boolean {
        val username = data.username ?: throw ErrorLoadingException("Requires Username")
        val password = data.password ?: throw ErrorLoadingException("Requires Password")
        switchToNewAccount()
        try {
            if (initLogin(username, password)) {
                registerAccount()
                return true
            }
        } catch (e: Exception) {
            logError(e)
            switchToOldAccount()
        }
        switchToOldAccount()
        return false
    }

    /**
     * Some languages do not use the normal country codes on OpenSubtitles
     * */
    private val languageExceptions = mapOf<String, String>(
//        "pt" to "pt-PT",
//        "pt" to "pt-BR"
    )
    private fun fixLanguage(language: String?) : String? {
        return languageExceptions[language] ?: language
    }
    // O(n) but good enough, BiMap did not want to work properly
    private fun fixLanguageReverse(language: String?) : String? {
        return languageExceptions.entries.firstOrNull { it.value == language }?.key ?: language
    }

    /**
     * Fetch subtitles using token authenticated on previous method (see authorize).
     * Returns list of Subtitles which user can select to download (see load).
     * */
    override suspend fun search(query: AbstractSubtitleEntities.SubtitleSearch): List<AbstractSubtitleEntities.SubtitleEntity>? {
        throwIfCantDoRequest()
        val fixedLang = fixLanguage(query.lang)

        val imdbId = query.imdb ?: 0
        val queryText = query.query
        val epNum = query.epNumber ?: 0
        val seasonNum = query.seasonNumber ?: 0
        val yearNum = query.year ?: 0
        val epQuery = if (epNum > 0) "&episode_number=$epNum" else ""
        val seasonQuery = if (seasonNum > 0) "&season_number=$seasonNum" else ""
        val yearQuery = if (yearNum > 0) "&year=$yearNum" else ""

        val searchQueryUrl = when (imdbId > 0) {
            //Use imdb_id to search if its valid
            true -> "$host/subtitles?imdb_id=$imdbId&languages=${fixedLang}$yearQuery$epQuery$seasonQuery"
            false -> "$host/subtitles?query=${queryText}&languages=${fixedLang}$yearQuery$epQuery$seasonQuery"
        }

        val req = app.get(
            url = searchQueryUrl,
            headers = mapOf(
                Pair("Api-Key", apiKey),
                Pair("Content-Type", "application/json")
            )
        )
        Log.i(TAG, "Search Req => ${req.text}")
        if (!req.isSuccessful) {
            if (req.code == 429)
                throwGotTooManyRequests()
            return null
        }

        val results = mutableListOf<AbstractSubtitleEntities.SubtitleEntity>()

        AppUtils.tryParseJson<Results>(req.text)?.let {
            it.data?.forEach { item ->
                val attr = item.attributes ?: return@forEach
                val featureDetails = attr.featDetails
                //Use filename as name, if its valid
                val filename = attr.files?.firstNotNullOfOrNull { subfile ->
                    subfile.fileName
                }
                //Use any valid name/title in hierarchy
                val name = filename ?: featureDetails?.movieName ?: featureDetails?.title
                ?: featureDetails?.parentTitle ?: attr.release ?: query.query
                val lang = fixLanguageReverse(attr.language)?: ""
                val resEpNum = featureDetails?.episodeNumber ?: query.epNumber
                val resSeasonNum = featureDetails?.seasonNumber ?: query.seasonNumber
                val year = featureDetails?.year ?: query.year
                val type = if ((resSeasonNum ?: 0) > 0) TvType.TvSeries else TvType.Movie
                val isHearingImpaired = attr.hearing_impaired ?: false
                //Log.i(TAG, "Result id/name => ${item.id} / $name")
                item.attributes?.files?.forEach { file ->
                    val resultData = file.fileId?.toString() ?: ""
                    //Log.i(TAG, "Result file => ${file.fileId} / ${file.fileName}")
                    results.add(
                        AbstractSubtitleEntities.SubtitleEntity(
                            idPrefix = this.idPrefix,
                            name = name,
                            lang = lang,
                            data = resultData,
                            type = type,
                            source = this.name,
                            epNumber = resEpNum,
                            seasonNumber = resSeasonNum,
                            year = year,
                            isHearingImpaired = isHearingImpaired
                        )
                    )
                }
            }
        }
        return results
    }

    /*
        Process data returned from search.
        Returns string url for the subtitle file.
    */
    override suspend fun load(data: AbstractSubtitleEntities.SubtitleEntity): String? {
        throwIfCantDoRequest()

        val req = app.post(
            url = "$host/download",
            headers = mapOf(
                Pair(
                    "Authorization",
                    "Bearer ${currentSession?.access_token ?: throw ErrorLoadingException("No access token active in current session")}"
                ),
                Pair("Api-Key", apiKey),
                Pair("Content-Type", "application/json"),
                Pair("Accept", "*/*")
            ),
            data = mapOf(
                Pair("file_id", data.data)
            )
        )
        Log.i(TAG, "Request result  => (${req.code}) ${req.text}")
        //Log.i(TAG, "Request headers => ${req.headers}")
        if (req.isSuccessful) {
            AppUtils.tryParseJson<ResultDownloadLink>(req.text)?.let {
                val link = it.link ?: ""
                Log.i(TAG, "Request load link => $link")
                return link
            }
        } else {
            if (req.code == 429)
                throwGotTooManyRequests()
        }
        return null
    }


    data class SubtitleOAuthEntity(
        var user: String,
        var pass: String,
        var access_token: String,
    )

    data class OAuthToken(
        @JsonProperty("token") var token: String? = null,
        @JsonProperty("status") var status: Int? = null
    )

    data class Results(
        @JsonProperty("data") var data: List<ResultData>? = listOf()
    )

    data class ResultData(
        @JsonProperty("id") var id: String? = null,
        @JsonProperty("type") var type: String? = null,
        @JsonProperty("attributes") var attributes: ResultAttributes? = ResultAttributes()
    )

    data class ResultAttributes(
        @JsonProperty("subtitle_id") var subtitleId: String? = null,
        @JsonProperty("language") var language: String? = null,
        @JsonProperty("release") var release: String? = null,
        @JsonProperty("url") var url: String? = null,
        @JsonProperty("files") var files: List<ResultFiles>? = listOf(),
        @JsonProperty("feature_details") var featDetails: ResultFeatureDetails? = ResultFeatureDetails(),
        @JsonProperty("hearing_impaired") var hearing_impaired: Boolean? = null,
    )

    data class ResultFiles(
        @JsonProperty("file_id") var fileId: Int? = null,
        @JsonProperty("file_name") var fileName: String? = null
    )

    data class ResultDownloadLink(
        @JsonProperty("link") var link: String? = null,
        @JsonProperty("file_name") var fileName: String? = null,
        @JsonProperty("requests") var requests: Int? = null,
        @JsonProperty("remaining") var remaining: Int? = null,
        @JsonProperty("message") var message: String? = null,
        @JsonProperty("reset_time") var resetTime: String? = null,
        @JsonProperty("reset_time_utc") var resetTimeUtc: String? = null
    )

    data class ResultFeatureDetails(
        @JsonProperty("year") var year: Int? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("movie_name") var movieName: String? = null,
        @JsonProperty("imdb_id") var imdbId: Int? = null,
        @JsonProperty("tmdb_id") var tmdbId: Int? = null,
        @JsonProperty("season_number") var seasonNumber: Int? = null,
        @JsonProperty("episode_number") var episodeNumber: Int? = null,
        @JsonProperty("parent_imdb_id") var parentImdbId: Int? = null,
        @JsonProperty("parent_title") var parentTitle: String? = null,
        @JsonProperty("parent_tmdb_id") var parentTmdbId: Int? = null,
        @JsonProperty("parent_feature_id") var parentFeatureId: Int? = null
    )
}
