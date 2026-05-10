package com.lagradost.cloudstream3.utils

import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.*
import java.util.Collections.synchronizedList

@AnyThread
expect fun runOnMainThreadNative(@MainThread work: (() -> Unit))
object Coroutines {
    @AnyThread
    fun <T> T.main(@MainThread work: suspend ((T) -> Unit)): Job {
        val value = this
        return CoroutineScope(Dispatchers.Main).launchSafe {
            work(value)
        }
    }

    @AnyThread
    fun <T> T.ioSafe(@WorkerThread work: suspend (CoroutineScope.(T) -> Unit)): Job {
        val value = this
        return CoroutineScope(Dispatchers.IO).launchSafe {
            work(value)
        }
    }

    @AnyThread
    suspend fun <T, V> V.ioWorkSafe(@WorkerThread work: suspend (CoroutineScope.(V) -> T)): T? {
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

    @AnyThread
    suspend fun <T, V> V.ioWork(@WorkerThread work: suspend (CoroutineScope.(V) -> T)): T {
        val value = this
        return withContext(Dispatchers.IO) {
            work(value)
        }
    }

    @AnyThread
    suspend fun <T, V> V.mainWork(@MainThread work: suspend (CoroutineScope.(V) -> T)): T {
        val value = this
        return withContext(Dispatchers.Main) {
            work(value)
        }
    }

    @AnyThread
    fun runOnMainThread(@MainThread work: (() -> Unit)) {
        runOnMainThreadNative(work)
    }

    /**
     * Safe to add and remove how you want
     * If you want to iterate over the list then you need to do:
     * synchronized(allProviders) { code here }
     */
    fun <T> threadSafeListOf(vararg items: T): MutableList<T> {
        return synchronizedList(items.toMutableList())
    }
}
