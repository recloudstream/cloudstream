package com.lagradost.cloudstream3.utils

internal object HardwareImageFailure {
    fun isRecoverable(error: Throwable): Boolean {
        val visited = HashSet<Throwable>()
        var current: Throwable? = error
        var recoverable = false
        while (current != null && visited.add(current)) {
            if (current is Error || current is java.util.concurrent.CancellationException) return false
            val message = current.message.orEmpty().lowercase()
            if (("hardware" in message && "bitmap" in message) ||
                "ahardwarebuffer" in message ||
                ("graphicbuffer" in message && "allocat" in message)) recoverable = true
            current = current.cause
        }
        return recoverable
    }
}
