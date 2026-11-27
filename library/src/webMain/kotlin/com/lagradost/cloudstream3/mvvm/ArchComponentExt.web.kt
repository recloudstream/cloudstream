package com.lagradost.cloudstream3.mvvm

actual fun <T> platformThrowAbleToResource(throwable: Throwable): Resource<T> {
    val message = throwable.message.orEmpty()
    return when {
        message.contains("timeout", ignoreCase = true) -> {
            Resource.Failure(
                true,
                "Connection Timeout\nPlease try again later."
            )
        }

        message.contains("NetworkError", ignoreCase = true) ||
        message.contains("Failed to fetch", ignoreCase = true) ||
        message.contains("ERR_NAME_NOT_RESOLVED", ignoreCase = true) -> {
            Resource.Failure(
                true,
                "Cannot connect to server, try again later.\n${throwable.message}"
            )
        }

        message.contains("SSL", ignoreCase = true) ||
        message.contains("certificate", ignoreCase = true) -> {
            Resource.Failure(
                true,
                (throwable.message ?: "SSL error") + "\nTry a VPN or DNS."
            )
        }

        else -> safeFail(throwable)
    }
}
