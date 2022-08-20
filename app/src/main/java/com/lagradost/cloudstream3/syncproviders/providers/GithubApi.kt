package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.subtitles.AbstractSubApi
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager


class GithubApi(index: Int) : InAppAuthAPIManager(index), AbstractSubApi {
    override val idPrefix = "Github"
    override val name = "Github"
    override val icon = R.drawable.ic_github_logo
    override val requiresPassword = true
    override val requiresUsername = true
    override val createAccountUrl = "https://github.com/settings/tokens/new"

    data class GithubOAuthEntity(
        var repository: String,
        var access_token: String,
    )
    private fun getAuthKey(): GithubOAuthEntity? {
        return getKey(accountId, OpenSubtitlesApi.OPEN_SUBTITLES_USER_KEY)
    }
    override fun loginInfo(): AuthAPI.LoginInfo? {
        getAuthKey()?.let { user ->
            return AuthAPI.LoginInfo(
                profilePicture = null,
                name = user.repository,
                accountIndex = accountIndex
            )
        }
        return null
    }
    override fun getLatestLoginData(): InAppAuthAPI.LoginData? {
        val current = getAuthKey() ?: return null
        return InAppAuthAPI.LoginData(username = current.repository, current.access_token)
    }

}