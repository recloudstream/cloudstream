package com.lagradost.cloudstream3.ui.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink

data class MultiStreamSlot(
    val slotIndex: Int,
    var title: String? = null,
    var url: String? = null,
    var isMuted: Boolean = true,
    var isOffline: Boolean = false,
    var isFocused: Boolean = false,
    var initialLink: ExtractorLink? = null,
    var player: CS3IPlayer? = null,
    var playerView: PlayerView? = null,
    val attemptedSourceUrls: MutableSet<String> = mutableSetOf(),
)

object MultiPlayerManager {
    const val MAX_SLOTS = 9

    var availableSources: List<ExtractorLink> = emptyList()
    var availableEpisodes: List<ResultEpisode> = emptyList()
    var currentHomeSectionName: String? = null
    var currentHomeSectionStreams: List<SearchResponse> = emptyList()

    var currentMaxHeight: Int = 720 // Default 720p cap for multi-stream to eliminate lag

    fun setGlobalQualityCap(height: Int) {
        currentMaxHeight = height
        for (i in 0 until MAX_SLOTS) {
            val slot = slots[i]
            if (height != Int.MAX_VALUE) {
                slot.player?.setMaxVideoSize(height * 16 / 9, height)
            } else {
                slot.player?.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            }
        }
    }

    fun setAvailableSourcesAndEpisodes(sources: List<ExtractorLink>, episodes: List<ResultEpisode>) {
        availableSources = sources
        availableEpisodes = episodes
    }

    fun setCurrentHomeSection(name: String, streams: List<SearchResponse>) {
        currentHomeSectionName = name
        currentHomeSectionStreams = streams.toList()
    }

    val slots = Array(MAX_SLOTS) { slotIndex ->
        MultiStreamSlot(slotIndex = slotIndex)
    }

    var activeFocusSlotIndex: Int = 0

    fun setMainStream(link: ExtractorLink, title: String) {
        if (slots[0].url != link.url || slots[0].player != null) closeSlot(0)
        activeFocusSlotIndex = 0
        val slot0 = slots[0]
        slot0.title = title
        slot0.url = link.url
        slot0.isOffline = false
        slot0.isMuted = false
        slot0.isFocused = true
        slot0.initialLink = link
        slot0.attemptedSourceUrls.clear()
        slot0.attemptedSourceUrls.add(link.url)
    }

    fun getSlot(index: Int): MultiStreamSlot? {
        if (index !in 0 until MAX_SLOTS) return null
        return slots[index]
    }

    private fun handleSlotStreamError(slotIndex: Int, context: Context) {
        val slot = getSlot(slotIndex) ?: return
        val alternativeSource = availableSources.firstOrNull {
            it.url != slot.url && it.url !in slot.attemptedSourceUrls
        }
        if (alternativeSource != null && slot.playerView != null) {
            android.util.Log.d("MultiPlayerManager", "Slot $slotIndex stream failed. Auto-switching to live extension feed: ${alternativeSource.name}")
            loadStream(context, slotIndex, alternativeSource.name, alternativeSource, slot.playerView!!)
        } else {
            setSlotOffline(slotIndex)
        }
    }

    fun loadStream(
        context: Context,
        slotIndex: Int,
        title: String,
        link: ExtractorLink,
        playerView: PlayerView,
        autoPlay: Boolean = true
    ) {
        if (slotIndex !in 0 until MAX_SLOTS) return

        val slot = slots[slotIndex]
        if (slot.url != link.url) slot.attemptedSourceUrls.clear()
        slot.attemptedSourceUrls.add(link.url)
        slot.title = title
        slot.url = link.url
        slot.initialLink = link
        slot.isOffline = false
        slot.playerView = playerView

        val player = slot.player ?: CS3IPlayer().also { slot.player = it }
        player.initCallbacks(
            eventHandler = { event ->
                if (slot.player !== player || slot.url != link.url) return@initCallbacks
                if (event is PlayerAttachedEvent) {
                    playerView.player = event.player as? ExoPlayer ?: player.getExoPlayer()
                    player.volume = if (slot.isMuted) 0f else 1f
                } else if (event is ErrorEvent) {
                    android.util.Log.w("MultiPlayerManager", "Slot $slotIndex playback error: ${event.error}. Attempting extension feed fallback...")
                    handleSlotStreamError(slotIndex, context)
                } else if (event is VideoEndedEvent) {
                    setSlotOffline(slotIndex)
                }
            }
        )

        playerView.player = player.getExoPlayer()

        // Only active focused slot plays audio initially
        val isMuted = slotIndex != activeFocusSlotIndex
        slot.isMuted = isMuted

        // Apply resolution cap to prevent lag
        if (currentMaxHeight != Int.MAX_VALUE) {
            player.setMaxVideoSize(currentMaxHeight * 16 / 9, currentMaxHeight)
        }

        player.loadPlayer(
            context = context,
            sameEpisode = false,
            link = link,
            data = null,
            startPosition = null,
            subtitles = emptySet(),
            subtitle = null,
            autoPlay = autoPlay,
            preview = false
        )

        player.volume = if (isMuted) 0f else 1f
    }

    fun toggleMute(slotIndex: Int): Boolean {
        val slot = getSlot(slotIndex) ?: return false
        val player = slot.player ?: return false
        player.handleEvent(CSPlayerEvent.ToggleMute)
        slot.isMuted = !slot.isMuted
        return slot.isMuted
    }

    fun setVolume(slotIndex: Int, volume: Float) {
        val slot = getSlot(slotIndex) ?: return
        val player = slot.player ?: return
        val clamped = volume.coerceIn(0f, 1f)
        player.volume = clamped
        slot.isMuted = clamped <= 0f
    }

    fun getVolume(slotIndex: Int): Float {
        val slot = getSlot(slotIndex) ?: return 0f
        return slot.player?.volume ?: if (slot.isMuted) 0f else 1f
    }

    fun setMuted(slotIndex: Int, mute: Boolean) {
        val slot = getSlot(slotIndex) ?: return
        val player = slot.player ?: return
        if (slot.isMuted != mute) {
            player.handleEvent(CSPlayerEvent.ToggleMute)
            slot.isMuted = mute
        }
    }

    fun setFocusedSlot(slotIndex: Int) {
        if (slotIndex !in 0 until MAX_SLOTS) return
        if (slots[slotIndex].url == null || slots[slotIndex].isOffline) return
        activeFocusSlotIndex = slotIndex
        for (i in 0 until MAX_SLOTS) {
            val slot = slots[i]
            slot.isFocused = i == slotIndex
            // Unmute focused stream, mute non-focused streams
            setMuted(i, i != slotIndex)
        }
    }

    fun setSlotOffline(slotIndex: Int) {
        val slot = getSlot(slotIndex) ?: return
        slot.isOffline = true
        slot.player?.release()
        slot.playerView?.player = null
        slot.player = null
        if (activeFocusSlotIndex == slotIndex) {
            val nextFocus = slots.firstOrNull { it.url != null && !it.isOffline }
            activeFocusSlotIndex = nextFocus?.slotIndex ?: 0
            slots.forEach { it.isFocused = it.slotIndex == activeFocusSlotIndex }
            slots.forEach { setMuted(it.slotIndex, it.slotIndex != activeFocusSlotIndex) }
        }
    }

    fun closeSlot(slotIndex: Int) {
        val slot = getSlot(slotIndex) ?: return
        android.util.Log.d("MultiPlayerManager", "Closing slot $slotIndex (${slot.title})")
        slot.player?.release()
        slot.player = null
        slot.playerView?.player = null
        slot.playerView = null
        slot.title = null
        slot.url = null
        slot.isOffline = false
        slot.isMuted = true
        slot.isFocused = false
        slot.initialLink = null
        slot.attemptedSourceUrls.clear()
        if (activeFocusSlotIndex == slotIndex) {
            val nextFocus = slots.firstOrNull { it.slotIndex != slotIndex && it.url != null && !it.isOffline }
            activeFocusSlotIndex = nextFocus?.slotIndex ?: 0
            slots.forEach { it.isFocused = it.slotIndex == activeFocusSlotIndex }
            slots.forEach { setMuted(it.slotIndex, it.slotIndex != activeFocusSlotIndex) }
        }
    }

    fun releaseAll() {
        android.util.Log.d("MultiPlayerManager", "Releasing all $MAX_SLOTS multi-stream slots")
        for (i in 0 until MAX_SLOTS) {
            closeSlot(i)
        }
        activeFocusSlotIndex = 0
    }
}
