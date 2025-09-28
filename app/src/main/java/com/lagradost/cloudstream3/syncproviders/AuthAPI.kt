package com.lagradost.cloudstream3.syncproviders

import android.util.Base64
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
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.APP_STRING
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
import com.lagradost.cloudstream3.utils.AppContextUtils.splitQuery
import com.lagradost.cloudstream3.utils.Coroutines.threadSafeListOf
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.txt
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.net.URL
import java.security.SecureRandom
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

/**
 * Stores all information that should be used to authorize access.
 * Be aware that token and user may change independently when a refresh is needed,
 * and as such there should be no strong pairing between the two.
 *
 * Any local set/get key should use user.id.toString(),
 * as token.accessToken (even hashed) is unsecure, and will rotate.
 * */
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

        fun splitRedirectUrl(redirectUrl: String): Map<String, String> {
            return splitQuery(
                URL(
                    redirectUrl.replace(APP_STRING, "https").replace("/#", "?")
                )
            )
        }

        fun generateCodeVerifier(): String {
            // It is recommended to use a URL-safe string as code_verifier.
            // See section 4 of RFC 7636 for more details.
            val secureRandom = SecureRandom()
            val codeVerifierBytes = ByteArray(96) // base64 has 6bit per char; (8/6)*96 = 128
            secureRandom.nextBytes(codeVerifierBytes)
            return Base64.encodeToString(codeVerifierBytes, Base64.DEFAULT).trimEnd('=')
                .replace("+", "-")
                .replace("/", "_").replace("\n", "")
        }
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

    /**
     * An optional security measure to make sure that even if an attacker gets ahold of the token, it will be invalid.
     *
     * Note that this will currently only be called *once* on logout,
     * and as such any network issues it will fail silently, and the token will not be revoked.
     **/
    @Throws
    open suspend fun invalidateToken(token: AuthToken): Nothing = throw NotImplementedError()

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




