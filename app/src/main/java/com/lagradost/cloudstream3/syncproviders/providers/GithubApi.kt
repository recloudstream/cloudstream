package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager


class GithubApi(index: Int) : InAppAuthAPIManager(index){
    override val idPrefix = "Github"
    override val name = "Github"
    override val icon = R.drawable.ic_github_logo
    override val requiresPassword = true
    override val requiresUsername = true
    override val createAccountUrl = "https://github.com/settings/tokens/new"

    data class GithubOAuthEntity(
        var user: String,
        var pass: String
    )
    companion object {
        const val GITHUB_USER_KEY: String = "github_user" // user data like profile
        var currentSession: GithubOAuthEntity? = null
    }
    private fun getAuthKey(): GithubOAuthEntity? {
        return getKey(accountId, GITHUB_USER_KEY)
    }
    override suspend fun login(data: InAppAuthAPI.LoginData): Boolean {
        switchToNewAccount()
        val username = data.username ?: throw ErrorLoadingException("Requires Username")
        val password = data.password ?: throw ErrorLoadingException("Requires Password")
        try {
            setKey(accountId, GITHUB_USER_KEY, GithubOAuthEntity(username, password))
            registerAccount()
            return true
        } catch (e: Exception) {
            logError(e)
            switchToOldAccount()
        }
        switchToOldAccount()
        return false
    }

    override fun getLatestLoginData(): InAppAuthAPI.LoginData? {
        val current = getAuthKey() ?: return null
        return InAppAuthAPI.LoginData(username = current.user, current.pass)
    }
    override suspend fun initialize() {
        currentSession = getAuthKey() ?: return // just in case the following fails
        setKey(currentSession!!.user, currentSession!!.pass)
    }
    override fun logOut() {
        AcraApplication.removeKey(accountId, GITHUB_USER_KEY)
        removeAccountKeys()
        currentSession = getAuthKey()
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
}