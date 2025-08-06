package com.lagradost.cloudstream3.syncproviders

import androidx.annotation.WorkerThread
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.NONE_ID
import com.lagradost.cloudstream3.syncproviders.providers.Addic7ed
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.LocalList
import com.lagradost.cloudstream3.syncproviders.providers.MALApi
import com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi
import com.lagradost.cloudstream3.syncproviders.providers.SubDlApi
import com.lagradost.cloudstream3.syncproviders.providers.SubSourceApi
import com.lagradost.cloudstream3.ui.SyncWatchType
import com.lagradost.cloudstream3.ui.library.ListSorting
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.txt
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.Date
import java.util.concurrent.TimeUnit

data class AuthLoginPage(
    /** The website to open to authenticate */
    val url: String,
    /**
     * State/control code to verify against the redirectUrl to make sure the request is valid.
     * This parameter will be saved, and then used in AuthAPI::login.
     * */
    val payload: String? = null,
)

data class AuthToken(
    /**
     * This is the general access tokens/api token representing a logged in user.
     *
     * `Access tokens are the thing that applications use to make API requests on behalf of a user.`
     * */
    @JsonProperty("accessToken")
    val accessToken: String? = null,
    /**
     * For OAuth a special refresh token is issues to refresh the access token.
     * */
    @JsonProperty("refreshToken")
    val refreshToken: String? = null,
    /** In UnixTime (sec) when it expires */
    @JsonProperty("accessTokenLifetime")
    val accessTokenLifetime: Long? = null,
    /** In UnixTime (sec) when it expires */
    @JsonProperty("refreshTokenLifetime")
    val refreshTokenLifetime: Long? = null,
    /** Sometimes AuthToken needs to be customized to store e.g. username/password,
     * this acts as a catch all to store text or JSON data. */
    @JsonProperty("payload")
    val payload: String? = null,
) {
    fun isAccessTokenExpired(marginSec: Long = 10L) =
        accessTokenLifetime != null && (System.currentTimeMillis() / 1000) + marginSec >= accessTokenLifetime

    fun isRefreshTokenExpired(marginSec: Long = 10L) =
        refreshTokenLifetime != null && (System.currentTimeMillis() / 1000) + marginSec >= refreshTokenLifetime
}

data class AuthUser(
    /** Account display-name, can also be email if name does not exist */
    @JsonProperty("name")
    val name: String?,
    /** Unique account identifier,
     * if a subsequent login is done then it will be refused if another account with the same id exists*/
    @JsonProperty("id")
    val id: Int,
    /** Profile picture URL */
    @JsonProperty("profilePicture")
    val profilePicture: String? = null,
    /** Profile picture Headers of the URL */
    @JsonProperty("profilePictureHeader")
    val profilePictureHeaders: Map<String, String>? = null
)

data class AuthData(
    @JsonProperty("user")
    val user: AuthUser,
    @JsonProperty("token")
    val token: AuthToken,
)

data class AuthPinData(
    val deviceCode: String,
    val userCode: String,
    /** QR Code url */
    val verificationUrl: String,
    /** In seconds */
    val expiresIn: Int,
    /** Check if the code has been verified interval */
    val interval: Int,
)

/** The login field requirements to display to the user */
data class AuthLoginRequirement(
    val password: Boolean = false,
    val username: Boolean = false,
    val email: Boolean = false,
    val server: Boolean = false,
)

/** What the user responds to the AuthLoginRequirement */
data class AuthLoginResponse(
    @JsonProperty("password")
    val password: String?,
    @JsonProperty("username")
    val username: String?,
    @JsonProperty("email")
    val email: String?,
    @JsonProperty("server")
    val server: String?,
)

/** Stateless Authentication class used for all personalized content */
abstract class AuthAPI {
    open val name: String = "NONE"
    open val idPrefix: String = "NONE"

    /** Drawable icon of the service */
    open val icon: Int? = null

    /** If this service requires an account to use */
    open val requiresLogin: Boolean = true

    /** Link to a website for creating a new account */
    open val createAccountUrl: String? = null

    /** The sensitive redirect URL from OAuth should contain "/redirectUrlIdentifier" to trigger the login */
    open val redirectUrlIdentifier: String? = null

    /** Has OAuth2 login support, including login, loginRequest and refreshToken */
    open val hasOAuth2: Boolean = false

    /** Has on device pin support, aka login with a QR code */
    open val hasPin: Boolean = false

    /** Has in app login support, aka login with a dialog */
    open val hasInApp: Boolean = false

    /** The requirements to login in app */
    open val inAppLoginRequirement: AuthLoginRequirement? = null

    companion object {
        val unixTime: Long
            get() = System.currentTimeMillis() / 1000L
        val unixTimeMs: Long
            get() = System.currentTimeMillis()
    }

    /** Is this url a valid redirect url for this service? */
    @Throws
    open fun isValidRedirectUrl(url: String): Boolean =
        redirectUrlIdentifier != null && url.contains("/$redirectUrlIdentifier")

    /** OAuth2 login from a valid redirectUrl, and payload given in loginRequest */
    @Throws
    open suspend fun login(redirectUrl: String, payload: String?): AuthToken? =
        throw NotImplementedError()

    /** OAuth2 login request, asking the service to provide a url to open in the browser */
    @Throws
    open fun loginRequest(): AuthLoginPage? = throw NotImplementedError()

    /** Pin login request, asking the service to provide an verificationUrl to display with a QR code */
    @Throws
    open suspend fun pinRequest(): AuthPinData? = throw NotImplementedError()

    /** OAuth2 token refresh, this ensures that all token passed to other functions will be valid */
    @Throws
    open suspend fun refreshToken(token: AuthToken): AuthToken? = throw NotImplementedError()

    /** Pin login, this will be called periodically while logging in to check if the pin has been verified by the user */
    @Throws
    open suspend fun login(payload: AuthPinData): AuthToken? = throw NotImplementedError()

    /** In app login */
    @Throws
    open suspend fun login(form: AuthLoginResponse): AuthToken? = throw NotImplementedError()

    /** Get the visible user account */
    @Throws
    open suspend fun user(token: AuthToken?): AuthUser? = throw NotImplementedError()

    @Throws
    @Deprecated("Please the the new api for AuthAPI", level = DeprecationLevel.WARNING)
    fun toRepo(): AuthRepo = when (this) {
        is SubtitleAPI -> SubtitleRepo(this)
        is SyncAPI -> SyncRepo(this)
        else -> throw NotImplementedError("Unknown inheritance from AuthAPI")
    }

    @Deprecated("Please the the new api for AuthAPI", level = DeprecationLevel.WARNING)
    fun loginInfo(): LoginInfo? {
        return this.toRepo().authUser()?.let { user ->
            LoginInfo(
                profilePicture = user.profilePicture,
                name = user.name,
                accountIndex = -1,
            )
        }
    }

    @Deprecated("Please the the new api for AuthAPI", level = DeprecationLevel.WARNING)
    suspend fun getPersonalLibrary(): SyncAPI.LibraryMetadata? {
        return (this.toRepo() as? SyncRepo)?.library()?.getOrThrow()
    }

    @Deprecated("Please the the new api for AuthAPI", level = DeprecationLevel.WARNING)
    class LoginInfo(
        val profilePicture: String? = null,
        val name: String?,
        val accountIndex: Int,
    )
}

/**
 * Stateless synchronization class, used for syncing status about a specific movie/show.
 *
 * All non-null `AuthToken` will be non-expired when each function is called.
 */
abstract class SyncAPI : AuthAPI() {
    /**
     * Set this to true if the user updates something on the list like watch status or score
     **/
    open var requireLibraryRefresh: Boolean = true
    open val mainUrl: String = "NONE"

    /**
     * Allows certain providers to open pages from
     * library links.
     **/
    open val syncIdName: SyncIdName? = null

    /** Modify the current status of an item */
    @Throws
    @WorkerThread
    open suspend fun updateStatus(
        token: AuthToken?,
        id: String,
        newStatus: AbstractSyncStatus
    ): Boolean = throw NotImplementedError()

    /** Get the current status of an item */
    @Throws
    @WorkerThread
    open suspend fun status(token: AuthToken?, id: String): AbstractSyncStatus? =
        throw NotImplementedError()

    /** Get metadata about an item */
    @Throws
    @WorkerThread
    open suspend fun load(token: AuthToken?, id: String): SyncResult? = throw NotImplementedError()

    /** Search this service for any results for a given query */
    @Throws
    @WorkerThread
    open suspend fun search(token: AuthToken?, query: String): List<SyncSearchResult>? =
        throw NotImplementedError()

    /** Get the current library/bookmarks of this service */
    @Throws
    @WorkerThread
    open suspend fun library(token: AuthToken?): LibraryMetadata? = throw NotImplementedError()

    /** Helper function, may be used in the future */
    @Throws
    open fun urlToId(url: String): String? = null

    data class SyncSearchResult(
        override val name: String,
        override val apiName: String,
        var syncId: String,
        override val url: String,
        override var posterUrl: String?,
        override var type: TvType? = null,
        override var quality: SearchQuality? = null,
        override var posterHeaders: Map<String, String>? = null,
        override var id: Int? = null,
        override var score: Score? = null,
    ) : SearchResponse

    abstract class AbstractSyncStatus {
        abstract var status: SyncWatchType
        abstract var score: Score?
        abstract var watchedEpisodes: Int?
        abstract var isFavorite: Boolean?
        abstract var maxEpisodes: Int?
    }

    data class SyncStatus(
        override var status: SyncWatchType,
        override var score: Score?,
        override var watchedEpisodes: Int?,
        override var isFavorite: Boolean? = null,
        override var maxEpisodes: Int? = null,
    ) : AbstractSyncStatus()

    data class SyncResult(
        /**Used to verify*/
        var id: String,

        var totalEpisodes: Int? = null,

        var title: String? = null,
        var publicScore: Score? = null,
        /**In minutes*/
        var duration: Int? = null,
        var synopsis: String? = null,
        var airStatus: ShowStatus? = null,
        var nextAiring: NextAiring? = null,
        var studio: List<String>? = null,
        var genres: List<String>? = null,
        var synonyms: List<String>? = null,
        var trailers: List<String>? = null,
        var isAdult: Boolean? = null,
        var posterUrl: String? = null,
        var backgroundPosterUrl: String? = null,

        /** In unixtime */
        var startDate: Long? = null,
        /** In unixtime */
        var endDate: Long? = null,
        var recommendations: List<SyncSearchResult>? = null,
        var nextSeason: SyncSearchResult? = null,
        var prevSeason: SyncSearchResult? = null,
        var actors: List<ActorData>? = null,
    )

    data class Page(
        val title: UiText, var items: List<LibraryItem>
    ) {
        fun sort(method: ListSorting?, query: String? = null) {
            items = when (method) {
                ListSorting.Query ->
                    if (query != null) {
                        items.sortedBy {
                            -FuzzySearch.partialRatio(
                                query.lowercase(), it.name.lowercase()
                            )
                        }
                    } else items

                ListSorting.RatingHigh -> items.sortedBy { -(it.personalRating?.toInt(100) ?: 0) }
                ListSorting.RatingLow -> items.sortedBy { (it.personalRating?.toInt(100) ?: 0) }
                ListSorting.AlphabeticalA -> items.sortedBy { it.name }
                ListSorting.AlphabeticalZ -> items.sortedBy { it.name }.reversed()
                ListSorting.UpdatedNew -> items.sortedBy { it.lastUpdatedUnixTime?.times(-1) }
                ListSorting.UpdatedOld -> items.sortedBy { it.lastUpdatedUnixTime }
                ListSorting.ReleaseDateNew -> items.sortedByDescending { it.releaseDate }
                ListSorting.ReleaseDateOld -> items.sortedBy { it.releaseDate }
                else -> items
            }
        }
    }

    data class LibraryMetadata(
        val allLibraryLists: List<LibraryList>,
        val supportedListSorting: Set<ListSorting>
    )

    data class LibraryList(
        val name: UiText,
        val items: List<LibraryItem>
    )

    data class LibraryItem(
        override val name: String,
        override val url: String,
        /**
         * Unique unchanging string used for data storage.
         * This should be the actual id when you change scores and status
         * since score changes from library might get added in the future.
         **/
        val syncId: String,
        val episodesCompleted: Int?,
        val episodesTotal: Int?,
        val personalRating: Score?,
        val lastUpdatedUnixTime: Long?,
        override val apiName: String,
        override var type: TvType?,
        override var posterUrl: String?,
        override var posterHeaders: Map<String, String>?,
        override var quality: SearchQuality?,
        val releaseDate: Date?,
        override var id: Int? = null,
        val plot: String? = null,
        override var score: Score? = null,
        val tags: List<String>? = null
    ) : SearchResponse
}

/**
 * Stateless subtitle class for external subtitles.
 *
 * All non-null `AuthToken` will be non-expired when each function is called.
 */
abstract class SubtitleAPI : AuthAPI() {
    @WorkerThread
    @Throws
    open suspend fun search(token: AuthToken?, query: SubtitleSearch): List<SubtitleEntity>? =
        throw NotImplementedError()

    @WorkerThread
    @Throws
    open suspend fun load(token: AuthToken?, data: SubtitleEntity): String? =
        throw NotImplementedError()

    @WorkerThread
    @Throws
    open suspend fun SubtitleResource.getResources(token: AuthToken?, data: SubtitleEntity) {
        this.addUrl(load(token, data))
    }

    @WorkerThread
    @Throws
    suspend fun getResource(token: AuthToken?, data: SubtitleEntity): SubtitleResource {
        return SubtitleResource().apply {
            this.getResources(token, data)
        }
    }
}

/** Safe abstraction for AuthAPI that provides both a catching interface, and automatic token management. */
abstract class AuthRepo(open val api: AuthAPI) {
    fun isValidRedirectUrl(url: String) = safe { api.isValidRedirectUrl(url) } ?: false
    val idPrefix get() = api.idPrefix
    val name get() = api.name
    val icon get() = api.icon
    val requiresLogin get() = api.requiresLogin
    val createAccountUrl get() = api.createAccountUrl
    val hasOAuth2 get() = api.hasOAuth2
    val hasPin get() = api.hasPin
    val hasInApp get() = api.hasInApp
    val inAppLoginRequirement get() = api.inAppLoginRequirement
    val isAvailable get() = !api.requiresLogin || authUser() != null

    companion object {
        private val oauthPayload: MutableMap<String, String?> = mutableMapOf()
    }

    @Throws
    protected suspend fun freshToken(): AuthToken? {
        val data = authData() ?: return null
        if (data.token.isAccessTokenExpired()) {
            val newToken = api.refreshToken(data.token) ?: return null
            refreshUser(AuthData(user = data.user, token = newToken))
            return newToken
        }
        return data.token
    }

    @Throws
    fun openOAuth2Page(): Boolean {
        val page = api.loginRequest() ?: return false
        synchronized(oauthPayload) {
            oauthPayload.put(idPrefix, page.payload)
        }
        openBrowser(page.url)
        return true
    }

    fun openOAuth2PageWithToast() {
        try {
            if (!openOAuth2Page()) {
                showToast(txt(R.string.authenticated_user_fail, api.name))
            }
        } catch (t: Throwable) {
            logError(t)
            if (t is ErrorLoadingException && t.message != null) {
                showToast(t.message)
                return
            }
            showToast(txt(R.string.authenticated_user_fail, api.name))
        }
    }

    fun logout(from: AuthUser) {
        val currentAccounts = AccountManager.accounts(idPrefix)
        val newAccounts = currentAccounts.filter { it.user.id != from.id }.toTypedArray()
        if (newAccounts.size < currentAccounts.size) {
            AccountManager.updateAccounts(idPrefix, newAccounts)
            AccountManager.updateAccountsId(idPrefix, 0)
        }
    }

    fun refreshUser(newAuth: AuthData) {
        val currentAccounts = AccountManager.accounts(idPrefix)
        val newAccounts = currentAccounts.map {
            if (it.user.id == newAuth.user.id) {
                newAuth
            } else {
                it
            }
        }.toTypedArray()
        AccountManager.updateAccounts(idPrefix, newAccounts)
    }

    fun authData(): AuthData? = synchronized(AccountManager.cachedAccountIds) {
        AccountManager.cachedAccountIds[idPrefix]?.let { id ->
            AccountManager.cachedAccounts[idPrefix]?.firstOrNull { data -> data.user.id == id }
        }
    }

    fun authToken(): AuthToken? = authData()?.token

    fun authUser(): AuthUser? = authData()?.user

    val accounts
        get() = synchronized(AccountManager.cachedAccounts) {
            AccountManager.cachedAccounts[idPrefix] ?: emptyArray()
        }
    var accountId
        get() = synchronized(AccountManager.cachedAccountIds) {
            AccountManager.cachedAccountIds[idPrefix] ?: NONE_ID
        }
        set(value) {
            AccountManager.updateAccountsId(idPrefix, value)
        }

    @Throws
    suspend fun pinRequest() =
        api.pinRequest()

    @Throws
    private suspend fun setupLogin(token: AuthToken): Boolean {
        val user = api.user(token) ?: return false

        val newAccount = AuthData(
            token = token,
            user = user,
        )

        val currentAccounts = AccountManager.accounts(idPrefix)
        if (currentAccounts.any { it.user.id == newAccount.user.id }) {
            throw ErrorLoadingException("Already logged into this account")
        }

        val newAccounts = currentAccounts + newAccount
        AccountManager.updateAccounts(idPrefix, newAccounts)
        AccountManager.updateAccountsId(idPrefix, user.id)
        if (this is SyncRepo) {
            requireLibraryRefresh = true
        }
        return true
    }

    @Throws
    suspend fun login(form: AuthLoginResponse): Boolean {
        return setupLogin(api.login(form) ?: return false)
    }

    @Throws
    suspend fun login(payload: AuthPinData): Boolean {
        return setupLogin(api.login(payload) ?: return false)
    }

    @Throws
    suspend fun login(redirectUrl: String): Boolean {
        return setupLogin(
            api.login(
                redirectUrl,
                synchronized(oauthPayload) { oauthPayload[api.idPrefix] }) ?: return false
        )
    }
}

/** Stateless safe abstraction of SyncAPI */
class SyncRepo(override val api: SyncAPI) : AuthRepo(api) {
    val syncIdName = api.syncIdName
    var requireLibraryRefresh: Boolean
        get() = api.requireLibraryRefresh
        set(value) {
            api.requireLibraryRefresh = value
        }

    suspend fun updateStatus(id: String, newStatus: SyncAPI.AbstractSyncStatus): Result<Boolean> =
        runCatching {
            val status = api.updateStatus(freshToken() ?: return@runCatching false, id, newStatus)
            requireLibraryRefresh = true
            status
        }

    suspend fun status(id: String): Result<SyncAPI.AbstractSyncStatus?> = runCatching {
        api.status(freshToken(), id)
    }

    suspend fun load(id: String): Result<SyncAPI.SyncResult?> = runCatching {
        api.load(freshToken(), id)
    }

    suspend fun library(): Result<SyncAPI.LibraryMetadata?> = runCatching {
        api.library(freshToken())
    }
}

/** Stateless safe abstraction of SubtitleAPI */
class SubtitleRepo(override val api: SubtitleAPI) : AuthRepo(api) {
    companion object {
        data class SavedSearchResponse(
            val unixTime: Long,
            val response: List<SubtitleEntity>,
            val query: SubtitleSearch
        )

        data class SavedResourceResponse(
            val unixTime: Long,
            val response: SubtitleResource,
            val query: SubtitleEntity
        )

        // maybe make this a generic struct? right now there is a lot of boilerplate
        private val searchCache = threadSafeListOf<SavedSearchResponse>()
        private var searchCacheIndex: Int = 0
        private val resourceCache = threadSafeListOf<SavedResourceResponse>()
        private var resourceCacheIndex: Int = 0
        const val CACHE_SIZE = 20
    }

    @WorkerThread
    suspend fun getResource(data: SubtitleEntity): Resource<SubtitleResource> = safeApiCall {
        synchronized(resourceCache) {
            for (item in resourceCache) {
                // 20 min save
                if (item.query == data && (unixTime - item.unixTime) < 60 * 20) {
                    return@safeApiCall item.response
                }
            }
        }

        val returnValue = api.getResource(freshToken(), data)
        synchronized(resourceCache) {
            val add = SavedResourceResponse(unixTime, returnValue, data)
            if (resourceCache.size > CACHE_SIZE) {
                resourceCache[resourceCacheIndex] = add // rolling cache
                resourceCacheIndex = (resourceCacheIndex + 1) % CACHE_SIZE
            } else {
                resourceCache.add(add)
            }
        }
        returnValue
    }

    @WorkerThread
    suspend fun search(query: SubtitleSearch): Resource<List<SubtitleEntity>> {
        return safeApiCall {
            synchronized(searchCache) {
                for (item in searchCache) {
                    // 120 min save
                    if (item.query == query && (unixTime - item.unixTime) < 60 * 120) {
                        return@safeApiCall item.response
                    }
                }
            }

            val returnValue =
                api.search(freshToken(), query) ?: throw ErrorLoadingException("Null subtitles")

            // only cache valid return values
            if (returnValue.isNotEmpty()) {
                val add = SavedSearchResponse(unixTime, returnValue, query)
                synchronized(searchCache) {
                    if (searchCache.size > CACHE_SIZE) {
                        searchCache[searchCacheIndex] = add // rolling cache
                        searchCacheIndex = (searchCacheIndex + 1) % CACHE_SIZE
                    } else {
                        searchCache.add(add)
                    }
                }
            }
            returnValue
        }
    }
}

abstract class AccountManager {
    companion object {
        const val NONE_ID : Int = -1
        val malApi = MALApi()
        val aniListApi = AniListApi()
        val simklApi = SimklApi()
        val localListApi = LocalList()

        val openSubtitlesApi = OpenSubtitlesApi()
        val addic7ed = Addic7ed()
        val subDlApi = SubDlApi()
        val subSourceApi = SubSourceApi()

        var cachedAccounts: MutableMap<String, Array<AuthData>>
        var cachedAccountIds: MutableMap<String, Int>

        const val ACCOUNT_TOKEN = "auth_tokens"
        const val ACCOUNT_IDS = "auth_ids"

        fun accounts(prefix: String): Array<AuthData> {
            require(prefix != "NONE")
            return getKey<Array<AuthData>>(ACCOUNT_TOKEN, "${prefix}/${DataStoreHelper.currentAccount}") ?: arrayOf()
        }

        fun updateAccounts(prefix: String, array: Array<AuthData>) {
            require(prefix != "NONE")
            setKey(ACCOUNT_TOKEN, "${prefix}/${DataStoreHelper.currentAccount}", array)
            synchronized(cachedAccounts) {
                cachedAccounts[prefix] = array
            }
        }

        fun updateAccountsId(prefix: String, id: Int) {
            require(prefix != "NONE")
            setKey(ACCOUNT_IDS, "${prefix}/${DataStoreHelper.currentAccount}", id)
            synchronized(cachedAccountIds) {
                cachedAccountIds[prefix] = id
            }
        }

        val allApis = arrayOf(
            SyncRepo(malApi),
            SyncRepo(aniListApi),
            SyncRepo(simklApi),
            SyncRepo(localListApi),

            SubtitleRepo(openSubtitlesApi),
            SubtitleRepo(addic7ed),
            SubtitleRepo(subDlApi),
            SubtitleRepo(subSourceApi)
        )

        fun updateAccountIds() {
            val ids = mutableMapOf<String, Int>()
            for (api in allApis) {
                ids.put(api.idPrefix, getKey<Int>(ACCOUNT_IDS, "${api.idPrefix}/${DataStoreHelper.currentAccount}" , NONE_ID) ?: NONE_ID)
            }
            synchronized(cachedAccountIds) {
                cachedAccountIds = ids
            }
        }

        init {
            val data = mutableMapOf<String, Array<AuthData>>()
            val ids = mutableMapOf<String, Int>()
            for (api in allApis) {
                data.put(api.idPrefix, accounts(api.idPrefix))
                ids.put(api.idPrefix, getKey<Int>(ACCOUNT_IDS, "${api.idPrefix}/${DataStoreHelper.currentAccount}" , NONE_ID) ?: NONE_ID)
            }
            cachedAccounts = data
            cachedAccountIds = ids
        }

        // I do not want to place this in the init block as JVM initialization order is weird, and it may cause exceptions
        // accessing other classes
        fun initMainAPI() {
            LoadResponse.malIdPrefix = malApi.idPrefix
            LoadResponse.aniListIdPrefix = aniListApi.idPrefix
            LoadResponse.simklIdPrefix = simklApi.idPrefix
        }

        val subtitleProviders = arrayOf(
            SubtitleRepo(openSubtitlesApi),
            SubtitleRepo(addic7ed),
            SubtitleRepo(subDlApi),
            SubtitleRepo(subSourceApi)
        )
        val syncApis = arrayOf(
            SyncRepo(malApi),
            SyncRepo(aniListApi),
            SyncRepo(simklApi),
            SyncRepo(localListApi)
        )

        const val APP_STRING = "cloudstreamapp"
        const val APP_STRING_REPO = "cloudstreamrepo"
        const val APP_STRING_PLAYER = "cloudstreamplayer"

        // Instantly start the search given a query
        const val APP_STRING_SEARCH = "cloudstreamsearch"

        // Instantly resume watching a show
        const val APP_STRING_RESUME_WATCHING = "cloudstreamcontinuewatching"

        fun secondsToReadable(seconds: Int, completedValue: String): String {
            var secondsLong = seconds.toLong()
            val days = TimeUnit.SECONDS
                .toDays(secondsLong)
            secondsLong -= TimeUnit.DAYS.toSeconds(days)

            val hours = TimeUnit.SECONDS
                .toHours(secondsLong)
            secondsLong -= TimeUnit.HOURS.toSeconds(hours)

            val minutes = TimeUnit.SECONDS
                .toMinutes(secondsLong)
            secondsLong -= TimeUnit.MINUTES.toSeconds(minutes)
            if (minutes < 0) {
                return completedValue
            }
            //println("$days $hours $minutes")
            return "${if (days != 0L) "$days" + "d " else ""}${if (hours != 0L) "$hours" + "h " else ""}${minutes}m"
        }
    }
}

