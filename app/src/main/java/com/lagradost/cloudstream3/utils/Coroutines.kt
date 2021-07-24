package com.lagradost.cloudstream3.utils

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object Coroutines {
    fun main(work: suspend (() -> Unit)) : Job {
        return CoroutineScope(Dispatchers.Main).launch {
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