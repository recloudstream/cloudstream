package com.lagradost.cloudstream3.syncproviders

interface OAuth2Interface {
    val key : String
    val redirectUrl : String
    fun handleRedirect(url : String)
}