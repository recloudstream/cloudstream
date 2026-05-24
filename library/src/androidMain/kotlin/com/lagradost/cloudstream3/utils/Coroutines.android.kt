package com.lagradost.cloudstream3.utils

import android.os.Handler
import android.os.Looper
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@AnyThread
actual fun runOnMainThreadNative(@MainThread work: () -> Unit) {
    val mainHandler = Handler(Looper.getMainLooper())
    mainHandler.post {
        work()
    }
}

actual val workerDispatcher: CoroutineDispatcher = Dispatchers.IO
