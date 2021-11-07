package com.lagradost.cloudstream3.syncproviders

import android.content.Context

//TODO dropbox sync
class Dropbox : OAuth2Interface {
    override val name: String
        get() = "Dropbox"
    override val key: String
        get() = "zlqsamadlwydvb2"
    override val redirectUrl: String
        get() = "dropboxlogin"

    override fun authenticate(context: Context) {
        TODO("Not yet implemented")
    }

    override fun handleRedirect(context: Context,url: String) {
        TODO("Not yet implemented")
    }

    override fun logOut(context: Context) {
        TODO("Not yet implemented")
    }

    override fun loginInfo(context: Context): OAuth2Interface.LoginInfo? {
        TODO("Not yet implemented")
    }
}