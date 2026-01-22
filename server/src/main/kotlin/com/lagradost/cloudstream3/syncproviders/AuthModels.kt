package com.lagradost.cloudstream3.syncproviders

data class AuthLoginPage(
    val url: String,
    val payload: String? = null,
)

data class AuthToken(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accessTokenLifetime: Long? = null,
    val refreshTokenLifetime: Long? = null,
    val payload: String? = null,
) {
    fun isAccessTokenExpired(marginSec: Long = 10L): Boolean {
        val lifetime = accessTokenLifetime ?: return false
        return (System.currentTimeMillis() / 1000) + marginSec >= lifetime
    }
}

data class AuthUser(
    val name: String? = null,
    val id: Int = 0,
    val profilePicture: String? = null,
    val profilePictureHeaders: Map<String, String>? = null,
)

data class AuthData(
    val user: AuthUser,
    val token: AuthToken,
)

data class AuthPinData(
    val deviceCode: String = "",
    val userCode: String = "",
    val verificationUrl: String = "",
    val expiresIn: Int = 0,
    val interval: Int = 0,
)

data class AuthLoginRequirement(
    val password: Boolean = false,
    val username: Boolean = false,
    val email: Boolean = false,
    val server: Boolean = false,
)

data class AuthLoginResponse(
    val username: String? = null,
    val password: String? = null,
    val email: String? = null,
    val server: String? = null,
)
