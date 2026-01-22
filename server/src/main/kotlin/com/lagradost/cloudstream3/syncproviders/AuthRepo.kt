package com.lagradost.cloudstream3.syncproviders

open class AuthRepo(open val api: AuthAPI) {
    val idPrefix: String
        get() = api.idPrefix
    val name: String
        get() = api.name
    val icon: String?
        get() = api.icon
    val requiresLogin: Boolean
        get() = api.requiresLogin
    val createAccountUrl: String?
        get() = api.createAccountUrl
    val hasOAuth2: Boolean
        get() = api.hasOAuth2
    val hasPin: Boolean
        get() = api.hasPin
    val hasInApp: Boolean
        get() = api.hasInApp
    val inAppLoginRequirement: AuthLoginRequirement?
        get() = api.inAppLoginRequirement

    open suspend fun freshAuth(): AuthData? = authData()

    open fun authData(): AuthData? = null

    open fun authToken(): AuthToken? = authData()?.token

    open fun authUser(): AuthUser? = authData()?.user
}
