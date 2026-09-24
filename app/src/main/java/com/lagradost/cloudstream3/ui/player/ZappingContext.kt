package com.lagradost.cloudstream3.ui.player

/**
 * Transient navigation state for live-channel zapping.
 *
 * This intentionally stays independent from Android Bundle serialization. The player keeps the
 * context in memory for the lifetime of the playback session, just like VideoGenerator instances.
 */
data class ZappingContext(
    val channels: List<ZappingChannel>,
    val currentIndex: Int,
) {
    init {
        require(channels.isNotEmpty()) { "ZappingContext requires at least one channel" }
        require(currentIndex in channels.indices) { "currentIndex must point to a channel" }
    }

    fun previousIndex(): Int = (currentIndex - 1 + channels.size) % channels.size

    fun nextIndex(): Int = (currentIndex + 1) % channels.size
}

data class ZappingChannel(
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String? = null,
)
