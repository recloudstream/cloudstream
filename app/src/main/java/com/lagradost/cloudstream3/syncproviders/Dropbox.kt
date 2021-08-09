package com.lagradost.cloudstream3.syncproviders

//TODO dropbox sync
class Dropbox : OAuth2Interface {
    override val key: String
        get() = "zlqsamadlwydvb2"
    override val redirectUrl: String
        get() = "dropboxlogin"

    override fun handleRedirect(url: String) {

    }
}