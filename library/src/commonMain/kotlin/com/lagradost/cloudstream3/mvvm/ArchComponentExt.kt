package com.lagradost.cloudstream3.mvvm

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.lagradost.api.Log
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.AppDebug
import com.lagradost.cloudstream3.utils.Coroutines.ioWork
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

const val DEBUG_EXCEPTION = "THIS IS A DEBUG EXCEPTION!"
const val DEBUG_PRINT = "DEBUG PRINT"

class DebugException(message: String) : Exception("$DEBUG_EXCEPTION\n$message")

inline fun debugException(message: () -> String) {
    if (AppDebug.isDebug) {
        throw DebugException(message.invoke())
    }
}

inline fun debugPrint(tag: String = DEBUG_PRINT, message: () -> String) {
    if (AppDebug.isDebug) {
        Log.d(tag, message.invoke())
    }
}

inline fun debugWarning(message: () -> String) {
    if (AppDebug.isDebug) {
        logError(DebugException(message.invoke()))
    }
}

inline fun debugAssert(assert: () -> Boolean, message: () -> String) {
    if (AppDebug.isDebug && assert.invoke()) {
        throw DebugException(message.invoke())
    }
}

inline fun debugWarning(assert: () -> Boolean, message: () -> String) {
    if (AppDebug.isDebug && assert.invoke()) {
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
            return if (value != null) {
                Success(value)
            } else {
                throwAbleToResource(result.exceptionOrNull() ?: Exception("this should not be possible"))
            }
        }
    }
}

fun logError(throwable: Throwable) {
    Log.d("ApiError", "-------------------------------------------------------------------")
    Log.d("ApiError", "safeApiCall: " + throwable.message)
    throwable.printStackTrace()
    Log.d("ApiError", "-------------------------------------------------------------------")
}

@Deprecated(
    "Outdated function, use `safe` instead",
    replaceWith = ReplaceWith("safe"),
    level = DeprecationLevel.ERROR
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
    "Outdated function, use `safeAsync` instead",
    replaceWith = ReplaceWith("safeAsync"),
    level = DeprecationLevel.ERROR
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
    return (this.message ?: "") + (this.cause?.getAllMessages()?.let { "\n$it" } ?: "")
}

fun Throwable.getStackTracePretty(showMessage: Boolean = true): String {
    val prefix = if (showMessage) this.message?.let { "\n$it" } ?: "" else ""
    return prefix + this.stackTraceToString()
        .lines()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("at ")) trimmed.removePrefix("at ") else null
        }
        .joinToString("\n")
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

expect fun <T> platformThrowAbleToResource(throwable: Throwable): Resource<T>

fun <T> throwAbleToResource(
    throwable: Throwable
): Resource<T> {
    return when (throwable) {
        is NullPointerException -> {
            val traceLine = throwable.stackTraceToString()
                .lines()
                .firstOrNull { it.contains("provider.kt", ignoreCase = true) }
            if (traceLine != null) {
                return Resource.Failure(
                    false,
                    "NullPointerException at $traceLine\nSite might have updated or added Cloudflare/DDOS protection"
                )
            }
            safeFail(throwable)
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

        is CancellationException -> {
            throwable.cause?.let {
                throwAbleToResource(it)
            } ?: safeFail(throwable)
        }

        else -> platformThrowAbleToResource(throwable)
    }
}

@AnyThread
suspend fun <T> safeApiCall(
    @WorkerThread apiCall: suspend () -> T,
): Resource<T> {
    return apiCall.ioWork {
        try {
            Resource.Success(it())
        } catch (throwable: Throwable) {
            logError(throwable)
            throwAbleToResource(throwable)
        }
    }
}
