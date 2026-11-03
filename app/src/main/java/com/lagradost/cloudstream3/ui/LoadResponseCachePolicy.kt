package com.lagradost.cloudstream3.ui

internal object LoadResponseCachePolicy {
    fun isFresh(savedAt: Long, now: Long, unavailable: Boolean): Boolean {
        val age = now - savedAt
        return age >= 0 && age < if (unavailable) 30 else 600
    }
}
