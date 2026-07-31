package com.lagradost.cloudstream3.utils

import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val workerDispatcher: CoroutineDispatcher = Dispatchers.Default
internal actual annotation class WorkerThread()

@AnyThread
actual fun runOnMainThreadNative(@MainThread work: () -> Unit) {
    work.invoke()
}
