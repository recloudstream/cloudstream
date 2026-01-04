package com.lagradost.api

import android.util.Log

actual object Log {
    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    actual fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    actual fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    actual fun e(tag: String, message: String) {
        Log.e(tag, message)
    }
}