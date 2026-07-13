package com.lagradost.cloudstream3.ui.player.live

import androidx.media3.common.C
import androidx.media3.common.Player
import java.lang.ref.WeakReference

// How much margin from the live point is still considered "live"
const val LIVE_MARGIN = 6_000L

// How many ms should we be behind the real live point?
// Too low, and we cannot pre-buffer
// Too high, and we are no longer live
const val PREFERRED_LIVE_OFFSET = 5_000L

// An extra offset from the optimal calculated timestamp
// This is to account for chunk updates not always being the same size
const val CHUNK_VARIANCE = 3000L

// A livestream chunk from the player, the time we get it and the duration can be used to calculate
// the expected live timestamp.
class LivestreamChunk(
    durationMs: Long, val receiveTimeMs: Long = System.currentTimeMillis()
) {
    // We want to be PREFERRED_LIVE_OFFSET ms after the latest update, but we cannot be ahead of the middle point.
    // If we are ahead of the middle point we will reach the end before the new chunk is expected to be released.
    val targetPosition = maxOf(0,minOf(
        durationMs - PREFERRED_LIVE_OFFSET,
        durationMs / 2 - CHUNK_VARIANCE
    ))

    fun isPositionLive(position: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val livePosition = targetPosition + (currentTime - receiveTimeMs)
        val withinLive = position + LIVE_MARGIN > livePosition - PREFERRED_LIVE_OFFSET
        // println("Position: $position, livePosition: ${livePosition}, Behind live: ${livePosition-position}, within live: $withinLive")
        return withinLive
    }

    fun getTimeAheadOfLive(position: Long): Long {
        val currentTime = System.currentTimeMillis()
        val livePosition = targetPosition + (currentTime - receiveTimeMs)
        // println("Ahead of live: ${position-livePosition}")
        return position - livePosition
    }
}

// There are two types of livestreams we need to manage
// 1. A livestream with no history, a continually sliding window.
// This livestream has no currentLiveOffset, which means we need to calculate
// the real live point based on when we receive the latest update and the size of that update.
// 2. A livestream with history.
// This livestream has a currentLiveOffset and therefore requires no calculation to get the live point.
// currentLiveOffset can however be inaccurate, and we need to be able to fall back to manual calculations.
class LiveManager {
    private var _currentPlayer: WeakReference<Player>? = null
    val currentPlayer: Player? get() = _currentPlayer?.get()

    constructor(player: Player?) {
        _currentPlayer = WeakReference(player)
    }

    private var lastLivestreamChunk: LivestreamChunk? = null

    fun submitLivestreamChunk(chunk: LivestreamChunk) {
        lastLivestreamChunk = chunk
    }

    /** Returns how much a position is ahead of the calculated live window. Returns 0 if not ahead of live window */
    fun getTimeAheadOfLive(position: Long): Long {
        val player = currentPlayer ?: return 0
        if (!player.isCurrentMediaItemDynamic || player.duration == C.TIME_UNSET) return 0

        // If the currentLiveOffset is wrong we fall back to manual calculations
        val ahead = if (player.currentLiveOffset != C.TIME_UNSET && player.currentLiveOffset < player.duration) {
            val relativeOffset = player.currentLiveOffset - player.currentPosition + position
            PREFERRED_LIVE_OFFSET - relativeOffset
        } else {
            lastLivestreamChunk?.getTimeAheadOfLive(position) ?: 0
        }

        // Ensure min of 0
        return maxOf(0, ahead)
    }

    /** Check if the stream is currently at the expected live edge, with margins */
    fun isAtLiveEdge(): Boolean {
        val player = currentPlayer ?: return false
        if (!player.isCurrentMediaItemDynamic || player.duration == C.TIME_UNSET) return false

        // If the currentLiveOffset is wrong we fall back to manual calculations
        return if (player.currentLiveOffset != C.TIME_UNSET && player.currentLiveOffset < player.duration) {
            player.currentLiveOffset < LIVE_MARGIN + PREFERRED_LIVE_OFFSET
        } else {
            lastLivestreamChunk?.isPositionLive(player.currentPosition) == true
        }
    }
}