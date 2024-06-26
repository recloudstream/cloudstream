package com.lagradost.cloudstream3.syncproviders

import androidx.fragment.app.FragmentActivity

interface OAuth2API : AuthAPI {
    val key: String
    val redirectUrl: String
    val supportDeviceAuth: Boolean

    suspend fun handleRedirect(url: String) : Boolean
    fun authenticate(activity: FragmentActivity?)
    suspend fun getDevicePin() : PinAuthData? {
        return null
    }

    suspend fun handleDeviceAuth(pinAuthData: PinAuthData) : Boolean {
        return false
    }

    data class PinAuthData(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val expiresIn: Int,
        val interval: Int,
    )
}