package com.lagradost.cloudstream3.syncproviders

import androidx.fragment.app.FragmentActivity

interface OAuth2API : AuthAPI {
    val key: String
    val redirectUrl: String

    suspend fun handleRedirect(url: String) : Boolean
    fun authenticate(activity: FragmentActivity?)
}