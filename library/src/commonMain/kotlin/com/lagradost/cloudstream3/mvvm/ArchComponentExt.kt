package com.lagradost.cloudstream3.mvvm

import com.lagradost.api.BuildConfig
import com.lagradost.api.Log
import com.lagradost.cloudstream3.ErrorLoadingException
import kotlinx.coroutines.*
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.full.NoSuchPropertyException

const val DEBUG_EXCEPTION = "THIS IS A DEBUG EXCEPTION!"
const val DEBUG_PRINT = "DEBUG PRINT"

class DebugException(message: String) : Exception("$DEBUG_EXCEPTION\n$message")

inline fun debugException(message: () -> String) {
    if (BuildConfig.DEBUG) {
        throw DebugException(message.invoke())
    }
}

inline fun debugPrint(tag: String = DEBUG_PRINT, message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d(tag, message.invoke())
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

sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Failure(
        val isNetworkError: Boolean,
        val errorString: String,
    ) : Resource<Nothing>()

    data class Loading(val url: String? = null) : Resource<Nothing>()

    companion object {
        fun <T> fromResult(result: Result<T>) : Resource<T> {
            val value = result.getOrNull()
            return if(value != null) {
                Success(value)
            } else {
                throwAbleToResource(result.exceptionOrNull() ?: Exception("this should not be possible"))
            }
        }
    }
}

fun logError(throwable: Throwable) {
    Log.d("ApiError", "-------------------------------------------------------------------")
    Log.d("ApiError", "safeApiCall: " + throwable.localizedMessage)
    Log.d("ApiError", "safeApiCall: " + throwable.message)
    throwable.printStackTrace()
    Log.d("ApiError", "-------------------------------------------------------------------")
}

@Deprecated(
    "Outdated function, use `safe` instead when the new stable is released",
    ReplaceWith("safe"),
    level = DeprecationLevel.WARNING
)
fun <T> normalSafeApiCall(apiCall: () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

/** Catches any exception (or error) and only logs it.
 * Will return null on exceptions. */
fun <T> safe(apiCall: () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

/** Catches any exception (or error) and only logs it.
 * Will return null on exceptions. */
suspend fun <T> safeAsync(apiCall: suspend () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

@Deprecated(
    "Outdated function, use `safeAsync` instead when the new stable is released",
    ReplaceWith("safeAsync"),
    level = DeprecationLevel.WARNING
)
suspend fun <T> suspendSafeApiCall(apiCall: suspend () -> T): T? {
    return try {
        apiCall.invoke()
    } catch (throwable: Throwable) {
        logError(throwable)
        return null
    }
}

fun Throwable.getAllMessages(): String {
    return (this.localizedMessage ?: "") + (this.cause?.getAllMessages()?.let { "\n$it" } ?: "")
}

fun Throwable.getStackTracePretty(showMessage: Boolean = true): String {
    val prefix = if (showMessage) this.localizedMessage?.let { "\n$it" } ?: "" else ""
    return prefix + this.stackTrace.joinToString(
        separator = "\n"
    ) {
        "${it.fileName} ${it.lineNumber}"
    }
}

fun <T> safeFail(throwable: Throwable): Resource<T> {
    val stackTraceMsg = throwable.getStackTracePretty()
    return Resource.Failure(false, stackTraceMsg)
}

fun CoroutineScope.launchSafe(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val obj: suspend CoroutineScope.() -> Unit = {
        try {
            block()
        } catch (throwable: Throwable) {
            logError(throwable)
        }
    }

    return this.launch(context, start, obj)
}

fun <T> throwAbleToResource(
    throwable: Throwable
): Resource<T> {
    return when (throwable) {
        is NoSuchMethodException, is NoSuchFieldException, is NoSuchMethodError, is NoSuchFieldError, is NoSuchPropertyException -> {
            Resource.Failure(
                false,
                "App or extension is outdated, update the app or try pre-release.\n${throwable.message}" // todo add exact version?
            )
        }

        is NullPointerException -> {
            for (line in throwable.stackTrace) {
                if (line?.fileName?.endsWith("provider.kt", ignoreCase = true) == true) {
                    return Resource.Failure(
                        false,
                        "NullPointerException at ${line.fileName} ${line.lineNumber}\nSite might have updated or added Cloudflare/DDOS protection"
                    )
                }
            }
            safeFail(throwable)
        }

        is SocketTimeoutException, is InterruptedIOException -> {
            Resource.Failure(
                true,
                "Connection Timeout\nPlease try again later."
            )
        }
//        is HttpException -> {
//            Resource.Failure(
//                false,
//                throwable.statusCode,
//                null,
//                throwable.message ?: "HttpException"
//            )
//        }
        is UnknownHostException -> {
            Resource.Failure(
                true,
                "Cannot connect to server, try again later.\n${throwable.message}"
            )
        }

        is ErrorLoadingException -> {
            Resource.Failure(
                true,
                throwable.message ?: "Error loading, try again later."
            )
        }

        is NotImplementedError -> {
            Resource.Failure(false, "This operation is not implemented.")
        }

        is SSLHandshakeException -> {
            Resource.Failure(
                true,
                (throwable.message ?: "SSLHandshakeException") + "\nTry a VPN or DNS."
            )
        }

        is CancellationException -> {
            throwable.cause?.let {
                throwAbleToResource(it)
            } ?: safeFail(throwable)
        }

        else -> safeFail(throwable)
    }
}

suspend fun <T> safeApiCall(
    apiCall: suspend () -> T,
): Resource<T> {
    return withContext(Dispatchers.IO) {
        try {
            Resource.Success(apiCall.invoke())
        } catch (throwable: Throwable) {
            logError(throwable)
            throwAbleToResource(throwable)
        }
    }
}
