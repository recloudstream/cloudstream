package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.*

//https://stackoverflow.com/questions/34697828/parallel-operations-on-kotlin-collections
/*
fun <T, R> Iterable<T>.pmap(
    numThreads: Int = maxOf(Runtime.getRuntime().availableProcessors() - 2, 1),
    exec: ExecutorService = Executors.newFixedThreadPool(numThreads),
    transform: (T) -> R,
): List<R> {

    // default size is just an inlined version of kotlin.collections.collectionSizeOrDefault
    val defaultSize = if (this is Collection<*>) this.size else 10
    val destination = Collections.synchronizedList(ArrayList<R>(defaultSize))

    for (item in this) {
        exec.submit { destination.add(transform(item)) }
    }

    exec.shutdown()
    exec.awaitTermination(1, TimeUnit.DAYS)

    return ArrayList<R>(destination)
}*/


@OptIn(DelicateCoroutinesApi::class)
suspend fun <K, V, R> Map<out K, V>.amap(f: suspend (Map.Entry<K, V>) -> R): List<R> =
    with(CoroutineScope(GlobalScope.coroutineContext)) {
        map { async { f(it) } }.map { it.await() }
    }

fun <K, V, R> Map<out K, V>.apmap(f: suspend (Map.Entry<K, V>) -> R): List<R> = runBlocking {
    map { async { f(it) } }.map { it.await() }
}


@OptIn(DelicateCoroutinesApi::class)
suspend fun <A, B> List<A>.amap(f: suspend (A) -> B): List<B> =
    with(CoroutineScope(GlobalScope.coroutineContext)) {
        map { async { f(it) } }.map { it.await() }
    }


fun <A, B> List<A>.apmap(f: suspend (A) -> B): List<B> = runBlocking {
    map { async { f(it) } }.map { it.await() }
}

fun <A, B> List<A>.apmapIndexed(f: suspend (index: Int, A) -> B): List<B> = runBlocking {
    mapIndexed { index, a -> async { f(index, a) } }.map { it.await() }
}

@OptIn(DelicateCoroutinesApi::class)
suspend fun <A, B> List<A>.amapIndexed(f: suspend (index: Int, A) -> B): List<B> =
    with(CoroutineScope(GlobalScope.coroutineContext)) {
        mapIndexed { index, a -> async { f(index, a) } }.map { it.await() }
    }

// run code in parallel
/*fun <R> argpmap(
    vararg transforms: () -> R,
    numThreads: Int = maxOf(Runtime.getRuntime().availableProcessors() - 2, 1),
    exec: ExecutorService = Executors.newFixedThreadPool(numThreads)
) {
    for (item in transforms) {
        exec.submit { item.invoke() }
    }

    exec.shutdown()
    exec.awaitTermination(1, TimeUnit.DAYS)
}*/

// built in try catch
fun <R> argamap(
    vararg transforms: suspend () -> R,
) = runBlocking {
    transforms.map {
        async {
            try {
                it.invoke()
            } catch (e: Exception) {
                logError(e)
            }
        }
    }.map { it.await() }
}