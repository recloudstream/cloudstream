package com.lagradost.cloudstream3.utils

import android.os.Handler
import android.os.Looper
import androidx.annotation.AnyThread
import androidx.annotation.MainThread

@AnyThread
actual fun runOnMainThreadNative(@MainThread work: () -> Unit) {
    val mainHandler = Handler(Looper.getMainLooper())
    mainHandler.post {
        work()
    }
}
