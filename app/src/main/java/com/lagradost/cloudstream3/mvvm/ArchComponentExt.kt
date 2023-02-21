package com.lagradost.cloudstream3.mvvm

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.bumptech.glide.load.HttpException
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.ErrorLoadingException
import kotlinx.coroutines.*
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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

fun <T> LifecycleOwner.observe(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.observe(this) { it?.let { t -> action(t) } }
}

fun <T> LifecycleOwner.observeNullable(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.observe(this) { action(it) }
}

inline fun <reified T : Any> some(value: T?): Some<T> {
    return if (value == null) {
        Some.None
    } else {
        Some.Success(value)
    }
}

sealed class Some<out T> {
    data class Success<out T>(val value: T) : Some<T>()
    object None : Some<Nothing>()

    override fun toString(): String {
        return when (this) {
            is None -> "None"
            is Success -> "Some(${value.toString()})"
        }
    }
}

sealed class ResourceSome<out T> {
    data class Success<out T>(val value: T) : ResourceSome<T>()
    object None : ResourceSome<Nothing>()
    data class Loading(val data: Any? = null) : ResourceSome<Nothing>()
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
    return Resource.Failure(false, null, null, stackTraceMsg)
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
                is SocketTimeoutException, is InterruptedIOException -> {
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
                        (throwable.message ?: "SSLHandshakeException") + "\nTry a VPN or DNS."
                    )
                }
                else -> safeFail(throwable)
            }
        }
    }
}