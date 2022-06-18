package com.lagradost.cloudstream3.utils

import android.os.Handler
import android.os.Looper
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.*

object Coroutines {
    fun main(work: suspend (() -> Unit)): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            work()
        }
    }

    fun ioSafe(work: suspend (() -> Unit)): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                work()
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    suspend fun <T> ioWork(work: suspend (() -> T)): T {
        return withContext(Dispatchers.IO) {
            work()
        }
    }

    fun runOnMainThread(work: (() -> Unit)) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            work()
        }
    }
}