package com.lagradost.cloudstream3.mvvm

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.bumptech.glide.load.HttpException
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.ErrorLoadingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

const val DEBUG_EXCEPTION = "THIS IS A DEBUG EXCEPTION!"

class DebugException(message: String) : Exception("$DEBUG_EXCEPTION\n$message")

inline fun debugException(message: () -> String) {
    if (BuildConfig.DEBUG) {
        throw DebugException(message.invoke())
    }
}

inline fun debugWarning(message: () -> String) {
    if (BuildConfig.DEBUG) {
        logError(DebugException(message.invoke()))
    }
}

inline fun debugAssert(assert: () -> Boolean, message: () -> String) {
    if (BuildConfig.DEBUG && assert.invoke()) {
        throw DebugException(message.invoke())
    }
}

inline fun debugWarning(assert: () -> Boolean, message: () -> String) {
    if (BuildConfig.DEBUG && assert.invoke()) {
        logError(DebugException(message.invoke()))
    }
}

fun <T> LifecycleOwner.observe(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.observe(this) { it?.let { t -> action(t) } }
}

fun <T> LifecycleOwner.observeDirectly(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.observe(this) { it?.let { t -> action(t) } }
    val currentValue = liveData.value
    if (currentValue != null)
        action(currentValue)
}

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Failure(
        val isNetworkError: Boolean,
        val errorCode: Int?,
        val errorResponse: Any?, //ResponseBody
        val errorString: String,
    ) : Resource<Nothing>()

    data class Loading(val url: String? = null) : Resource<Nothing>()
}

fun logError(throwable: Throwable) {
    Log.d("ApiError", "-------------------------------------------------------------------")
    Log.d("ApiError", "safeApiCall: " + throwable.localizedMessage)
    Log.d("ApiError", "safeApiCall: " + throwable.message)
    throwable.printStackTrace()
    Log.d("ApiError", "-------------------------------------------------------------------")
}

fun <T> normalSafeApiCall(apiCall: () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

suspend fun <T> suspendSafeApiCall(apiCall: suspend () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

fun <T> safeFail(throwable: Throwable): Resource<T> {
    val stackTraceMsg =
        (throwable.localizedMessage ?: "") + "\n\n" + throwable.stackTrace.joinToString(
            separator = "\n"
        ) {
            "${it.fileName} ${it.lineNumber}"
        }
    return Resource.Failure(false, null, null, stackTraceMsg)
}

suspend fun <T> safeApiCall(
    apiCall: suspend () -> T,
): Resource<T> {
    return withContext(Dispatchers.IO) {
        try {
            Resource.Success(apiCall.invoke())
        } catch (throwable: Throwable) {
            logError(throwable)
            when (throwable) {
                is NullPointerException -> {
                    for (line in throwable.stackTrace) {
                        if (line?.fileName?.endsWith("provider.kt", ignoreCase = true) == true) {
                            return@withContext Resource.Failure(
                                false,
                                null,
                                null,
                                "NullPointerException at ${line.fileName} ${line.lineNumber}\nSite might have updated or added Cloudflare/DDOS protection"
                            )
                        }
                    }
                    safeFail(throwable)
                }
                is SocketTimeoutException -> {
                    Resource.Failure(
                        true,
                        null,
                        null,
                        "Connection Timeout\nPlease try again later."
                    )
                }
                is HttpException -> {
                    Resource.Failure(
                        false,
                        throwable.statusCode,
                        null,
                        throwable.message ?: "HttpException"
                    )
                }
                is UnknownHostException -> {
                    Resource.Failure(true, null, null, "Cannot connect to server, try again later.")
                }
                is ErrorLoadingException -> {
                    Resource.Failure(
                        true,
                        null,
                        null,
                        throwable.message ?: "Error loading, try again later."
                    )
                }
                is NotImplementedError -> {
                    Resource.Failure(false, null, null, "This operation is not implemented.")
                }
                is SSLHandshakeException -> {
                    Resource.Failure(
                        true,
                        null,
                        null,
                        (throwable.message ?: "SSLHandshakeException") + "\nTry again later."
                    )
                }
                else -> safeFail(throwable)
            }
        }
    }
}