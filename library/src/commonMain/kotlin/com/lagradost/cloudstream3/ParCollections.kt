package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.*
import kotlin.coroutines.cancellation.CancellationException

/**
 * Short for "Asynchronous Map", runs on all values concurrently,
 * this means that if you are not doing networking, you should use a regular map
 */
@Throws(CancellationException::class)
suspend fun <K, V, R> Map<out K, V>.amap(f: suspend (Map.Entry<K, V>) -> R): List<R> =
    coroutineScope {
        ensureActive()
        map { async { f(it) } }.map { it.await() }
    }

/**
 * Short for "Asynchronous Parallel Map", but is not really parallel, only concurrent.
 */
@Deprecated(
    "This blocks with runBlocking, and should not be used inside a suspended context",
    replaceWith = ReplaceWith("amap(f)", "com.lagradost.cloudstream3.amap")
)
@Throws(CancellationException::class)
fun <K, V, R> Map<out K, V>.apmap(f: suspend (Map.Entry<K, V>) -> R): List<R> = runBlocking {
    map { async { f(it) } }.map { it.await() }
}

/**
 * Short for "Asynchronous Map", runs on all values concurrently,
 * this means that if you are not doing networking, you should use a regular map
 */
@Throws(CancellationException::class)
suspend fun <A, B> List<A>.amap(f: suspend (A) -> B): List<B> =
    coroutineScope {
        ensureActive()
        map { async { f(it) } }.map { it.await() }
    }

/**
 * Short for "Asynchronous Parallel Map", but is not really parallel, only concurrent.
 */
@Deprecated(
    "This blocks with runBlocking, and should not be used inside a suspended context",
    replaceWith = ReplaceWith("amap(f)", "com.lagradost.cloudstream3.amap")
)
@Throws(CancellationException::class)
fun <A, B> List<A>.apmap(f: suspend (A) -> B): List<B> = runBlocking {
    map { async { f(it) } }.map { it.await() }
}

/**
 * Short for "Asynchronous Parallel Map" with an Index, but is not really parallel, only concurrent.
 */
@Deprecated(
    "This blocks with runBlocking, and should not be used inside a suspended context",
    replaceWith = ReplaceWith("amapIndexed(f)", "com.lagradost.cloudstream3.amapIndexed")
)
@Throws(CancellationException::class)
fun <A, B> List<A>.apmapIndexed(f: suspend (index: Int, A) -> B): List<B> = runBlocking {
    mapIndexed { index, a -> async { f(index, a) } }.map { it.await() }
}

/**
 * Short for "Asynchronous Map" with an Index, runs on all values concurrently,
 * this means that if you are not doing networking, you should use a regular mapIndexed
 */
@Throws(CancellationException::class)
suspend fun <A, B> List<A>.amapIndexed(f: suspend (index: Int, A) -> B): List<B> =
    coroutineScope {
        ensureActive()
        mapIndexed { index, a -> async { f(index, a) } }.map { it.await() }
    }

/**
 * Short for "Argument Asynchronous Map" because it allows for a variadic number of paramaters.
 *
 * Runs all different functions at the same time and awaits for all to be finished, then returns
 * a list of all those items or null if they fail. However Unit is often used.
 */
@Deprecated(
    "This blocks with runBlocking, and should not be used inside a suspended context",
    replaceWith = ReplaceWith("runAllAsync(transforms)", "com.lagradost.cloudstream3.runAllAsync")
)
@Throws(CancellationException::class)
fun <R> argamap(
    vararg transforms: suspend () -> R,
) : List<R?> = runBlocking {
    transforms.map {
        async {
            try {
                it.invoke()
            } catch (e: Exception) {
                logError(e)
                null
            }
        }
    }.map { it.await() }
}

/**
 * Runs all different functions at the same time and awaits for all to be finished, then returns
 * a list of all those items or null if they fail. However Unit is often used.
 */
@Throws(CancellationException::class)
suspend fun <R> runAllAsync(
    vararg transforms: suspend () -> R,
) : List<R?> = coroutineScope {
    ensureActive()
    transforms.map { fn ->
        async {
            try {
                fn.invoke()
            } catch (e: Exception) {
                logError(e)
                null
            }
        }
    }.map { it.await() }
}