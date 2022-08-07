package com.lagradost.cloudstream3.utils

import android.os.Handler
import android.os.Looper
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.*

object Coroutines {
    fun <T> T.main(work: suspend ((T) -> Unit)): Job {
        val value = this
        return CoroutineScope(Dispatchers.Main).launch {
            work(value)
        }
    }

    fun <T> T.ioSafe(work: suspend (CoroutineScope.(T) -> Unit)): Job {
        val value = this

        return CoroutineScope(Dispatchers.IO).launch {
            try {
                work(value)
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    suspend fun <T, V> V.ioWork(work: suspend (CoroutineScope.(V) -> T)): T {
        val value = this
        return withContext(Dispatchers.IO) {
            work(value)
        }
    }

    fun runOnMainThread(work: (() -> Unit)) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            work()
        }
    }
}