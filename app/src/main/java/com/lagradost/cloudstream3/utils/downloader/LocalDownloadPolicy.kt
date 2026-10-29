package com.lagradost.cloudstream3.utils.downloader

internal object LocalDownloadPolicy {
    fun isComplete(length: Long, expected: Long, completedLength: Long?, busy: Boolean): Boolean =
        !busy && length > 0 && (completedLength == length || (expected > 0 && length == expected))
}
