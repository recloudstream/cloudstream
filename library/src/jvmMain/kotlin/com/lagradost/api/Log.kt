package com.lagradost.api

actual object Log {
    actual fun d(tag: String, message: String) {
        println("DEBUG $tag: $message")
    }

    actual fun i(tag: String, message: String) {
        println("INFO $tag: $message")
    }

    actual fun w(tag: String, message: String) {
        println("WARNING $tag: $message")
    }

    actual fun e(tag: String, message: String) {
        println("ERROR $tag: $message")
    }
}