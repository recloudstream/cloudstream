package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.*
import java.util.Collections.synchronizedList

expect fun runOnMainThreadNative(work: (() -> Unit))
object Coroutines {
    fun <T> T.main(work: suspend ((T) -> Unit)): Job {
        val value = this
        return CoroutineScope(Dispatchers.Main).launchSafe {
            work(value)
        }
    }

    fun <T> T.ioSafe(work: suspend (CoroutineScope.(T) -> Unit)): Job {
        val value = this

        return CoroutineScope(Dispatchers.IO).launchSafe {
            work(value)
        }
    }

    suspend fun <T, V> V.ioWorkSafe(work: suspend (CoroutineScope.(V) -> T)): T? {
        val value = this
        return withContext(Dispatchers.IO) {
            try {
                work(value)
            } catch (e: Exception) {
                logError(e)
                null
            }
        }
    }

    suspend fun <T, V> V.ioWork(work: suspend (CoroutineScope.(V) -> T)): T {
        val value = this
        return withContext(Dispatchers.IO) {
            work(value)
        }
    }

    suspend fun <T, V> V.mainWork(work: suspend (CoroutineScope.(V) -> T)): T {
        val value = this
        return withContext(Dispatchers.Main) {
            work(value)
        }
    }

    fun runOnMainThread(work: (() -> Unit)) {
        runOnMainThreadNative(work)
    }

    /**
     * Safe to add and remove how you want
     * If you want to iterate over the list then you need to do:
     * synchronized(allProviders) { code here }
     **/
    fun <T> threadSafeListOf(vararg items: T): MutableList<T> {
        return synchronizedList(items.toMutableList())
    }
}