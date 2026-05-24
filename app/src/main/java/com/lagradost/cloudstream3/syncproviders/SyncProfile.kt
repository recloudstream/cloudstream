package com.lagradost.cloudstream3.syncproviders

import androidx.annotation.Keep

@Keep
data class SyncProfile(
    var id: String = "",
    var name: String = "",
    var avatarUrl: String? = null, // Can be custom path or drawable resource name
    var pinHash: String? = null,   // SHA-256 Pin hash, null if not locked
    var color: Int? = null,        // Accent color (ARGB Int or hex)
    var lastUsed: Long = 0L
) {
    val isLocked: Boolean
        get() = !pinHash.isNullOrBlank()
}
