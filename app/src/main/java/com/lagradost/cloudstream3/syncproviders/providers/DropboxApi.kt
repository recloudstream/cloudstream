package com.lagradost.cloudstream3.syncproviders.providers

import android.content.Context
import com.lagradost.cloudstream3.syncproviders.OAuth2API

//TODO dropbox sync
class Dropbox : OAuth2API {
    override val idPrefix = "dropbox"
    override val name = "Dropbox"
    override val key = "zlqsamadlwydvb2"
    override val redirectUrl = "dropboxlogin"

    override fun authenticate(context: Context) {
        TODO("Not yet implemented")
    }

    override fun handleRedirect(context: Context,url: String) {
        TODO("Not yet implemented")
    }

    override fun logOut(context: Context) {
        TODO("Not yet implemented")
    }

    override fun loginInfo(context: Context): OAuth2API.LoginInfo? {
        TODO("Not yet implemented")
    }
}