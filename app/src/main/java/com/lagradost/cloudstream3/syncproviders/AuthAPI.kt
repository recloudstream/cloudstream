package com.lagradost.cloudstream3.syncproviders

interface AuthAPI {
    val name: String
    val icon: Int?

    val requiresLogin: Boolean

    val createAccountUrl : String?

    // don't change this as all keys depend on it
    val idPrefix: String

    // if this returns null then you are not logged in
    fun loginInfo(): LoginInfo?
    fun logOut()

    class LoginInfo(
        val profilePicture: String? = null,
        val name: String?,
        val accountIndex: Int,
    )
}