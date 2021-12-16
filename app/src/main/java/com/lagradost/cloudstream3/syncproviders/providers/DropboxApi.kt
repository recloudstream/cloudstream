package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.OAuth2API

//TODO dropbox sync
class Dropbox : OAuth2API {
    override val idPrefix = "dropbox"
    override val name = "Dropbox"
    override val key = "zlqsamadlwydvb2"
    override val redirectUrl = "dropboxlogin"

    override fun authenticate() {
        TODO("Not yet implemented")
    }

    override fun handleRedirect(url: String) {
        TODO("Not yet implemented")
    }

    override fun logOut() {
        TODO("Not yet implemented")
    }

    override fun loginInfo(): OAuth2API.LoginInfo? {
        TODO("Not yet implemented")
    }
}