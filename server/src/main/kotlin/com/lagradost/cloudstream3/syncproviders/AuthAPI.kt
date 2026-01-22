package com.lagradost.cloudstream3.syncproviders

abstract class AuthAPI {
    open val name: String = "Unknown"
    open val idPrefix: String = "NONE"
    open val icon: String? = null
    open val requiresLogin: Boolean = false
    open val createAccountUrl: String? = null
    open val hasOAuth2: Boolean = false
    open val hasPin: Boolean = false
    open val hasInApp: Boolean = false
    open val inAppLoginRequirement: AuthLoginRequirement? = null

    open fun isValidRedirectUrl(url: String): Boolean = false

    open fun loginRequest(): AuthLoginPage? = null

    open suspend fun login(form: AuthLoginResponse): AuthToken? = null

    open suspend fun login(payload: AuthPinData): AuthToken? = null

    open suspend fun login(redirectUrl: String, payload: String?): AuthToken? = null

    open suspend fun refreshToken(token: AuthToken): AuthToken? = null

    open suspend fun user(token: AuthToken): AuthUser? = null

    open suspend fun pinRequest(): AuthPinData? = null

    open suspend fun invalidateToken(token: AuthToken) {}
}
