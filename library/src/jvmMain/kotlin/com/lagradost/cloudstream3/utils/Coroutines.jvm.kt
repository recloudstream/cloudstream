package com.lagradost.cloudstream3.utils

actual fun runOnMainThreadNative(work: () -> Unit) {
    work.invoke()
}