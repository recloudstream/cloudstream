package com.lagradost.cloudstream3.ui.player.live

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.lagradost.cloudstream3.mvvm.debugWarning
import java.util.WeakHashMap

object LiveHelper {
    private val liveManagers = WeakHashMap<Player, LiveManager>()
    private val listeners = WeakHashMap<Player, Player.Listener>()

    @OptIn(UnstableApi::class)

    fun registerPlayer(player: Player?) {
        if (player == null) {
            debugWarning { "LiveHelper registerPlayer called with null player!" }
            return
        }

        // Prevent duplicates
        if (liveManagers.contains(player) || listeners.contains(player)) {
            return
        }

        val liveManager = LiveManager(player)
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                val window = Timeline.Window()
                timeline.getWindow(player.currentMediaItemIndex, window)
                if (window.isDynamic) {
                    liveManager.submitLivestreamChunk(LivestreamChunk(window.durationMs))
                }
                super.onTimelineChanged(timeline, reason)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                val timeAheadOfLive = liveManager.getTimeAheadOfLive(newPosition.positionMs)

                // Seek back to the optimal live spot
                if (timeAheadOfLive > 100) {
                    player.seekTo(newPosition.positionMs - timeAheadOfLive)
                }
            }
        }

        synchronized(liveManagers) {
            liveManagers[player] = liveManager
        }
        synchronized(listeners) {
            player.addListener(listener)
            listeners[player] = listener
        }
    }

    fun unregisterPlayer(player: Player?) {
        if (player == null) {
            debugWarning { "LiveHelper unregisterPlayer called with null player!" }
            return
        }
        // Prevent duplicates
        if (!liveManagers.contains(player) && !listeners.contains(player)) {
            return
        }

        synchronized(liveManagers) {
            liveManagers.remove(player)
        }
        synchronized(listeners) {
            listeners[player]?.let {
                player.removeListener(it)
            }
            listeners.remove(player)
        }
    }

    fun getLiveManager(player: Player?) = liveManagers[player]
}