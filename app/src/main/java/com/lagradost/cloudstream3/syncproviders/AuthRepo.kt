package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.NONE_ID
import com.lagradost.cloudstream3.utils.txt

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
    protected suspend fun freshAuth(): AuthData? {
        val data = authData() ?: return null
        if (data.token.isAccessTokenExpired()) {
            val newToken = api.refreshToken(data.token) ?: return null
            val newAuth = AuthData(user = data.user, token = newToken)
            refreshUser(newAuth)
            return newAuth
        }
        return data
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

    suspend fun logout(from: AuthUser) {
        val currentAccounts = AccountManager.accounts(idPrefix)
        val (newAccounts, oldAccounts) = currentAccounts.partition { it.user.id != from.id }
        if (newAccounts.size < currentAccounts.size) {
            AccountManager.updateAccounts(idPrefix, newAccounts.toTypedArray())
            AccountManager.updateAccountsId(idPrefix, 0)
        }

        for (oldAccount in oldAccounts) {
            try {
                api.invalidateToken(oldAccount.token)
            } catch (_: NotImplementedError) {
                // no-op
            } catch (t: Throwable) {
                logError(t)
            }
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