package com.lagradost.cloudstream3.syncproviders.providers

import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.OAuth2API

//TODO dropbox sync
class Dropbox : OAuth2API {
    override val idPrefix = "dropbox"
    override var name = "Dropbox"
    override val key = "zlqsamadlwydvb2"
    override val redirectUrl = "dropboxlogin"
    override val requiresLogin = true
    override val createAccountUrl: String? = null

    override val icon: Int
        get() = TODO("Not yet implemented")

    override fun authenticate(activity: FragmentActivity?) {
        TODO("Not yet implemented")
    }

    override suspend fun handleRedirect(url: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun logOut() {
        TODO("Not yet implemented")
    }

    override fun loginInfo(): AuthAPI.LoginInfo? {
        TODO("Not yet implemented")
    }
}