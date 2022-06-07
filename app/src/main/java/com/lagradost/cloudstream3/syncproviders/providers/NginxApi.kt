package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.movieproviders.NginxProvider
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager

class NginxApi(index: Int) : InAppAuthAPIManager(index) {
    override val name = "Nginx"
    override val idPrefix = "nginx"
    override val icon = R.drawable.nginx
    override val requiresUsername = true
    override val requiresPassword = true
    override val requiresServer = true
    override val createAccountUrl = "https://www.sarlays.com/use-nginx-with-cloudstream/"

    companion object {
        const val NGINX_USER_KEY: String = "nginx_user"
    }

    override fun getLatestLoginData(): InAppAuthAPI.LoginData? {
        return getKey(accountId, NGINX_USER_KEY)
    }

    override fun loginInfo(): AuthAPI.LoginInfo? {
        val data = getLatestLoginData() ?: return null
        return AuthAPI.LoginInfo(name = data.username ?: data.server, accountIndex = accountIndex)
    }

    override suspend fun login(data: InAppAuthAPI.LoginData): Boolean {
        if (data.server.isNullOrBlank()) return false // we require a server
        switchToNewAccount()
        setKey(accountId, NGINX_USER_KEY, data)
        registerAccount()
        initialize()
        return true
    }

    override fun logOut() {
        removeAccountKeys()
        initializeData()
    }

    private fun initializeData() {
        val data = getLatestLoginData() ?: run {
            NginxProvider.overrideUrl = null
            NginxProvider.loginCredentials = null
            return
        }
        NginxProvider.overrideUrl = data.server?.removeSuffix("/")
        NginxProvider.loginCredentials = "${data.username ?: ""}:${data.password ?: ""}"
    }

    override suspend fun initialize() {
        initializeData()
    }
}