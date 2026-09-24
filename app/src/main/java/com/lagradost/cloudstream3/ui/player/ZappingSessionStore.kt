package com.lagradost.cloudstream3.ui.player

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory zapping state keyed by the same playback UUID used by GeneratorPlayer.
 *
 * Keeping this outside Bundles avoids serializing an entire live-channel category and mirrors the
 * lifetime semantics of GeneratorPlayer's transient VideoGenerator storage.
 */
object ZappingSessionStore {
    private val sessions = ConcurrentHashMap<String, ZappingContext>()

    fun put(uuid: String, context: ZappingContext) {
        sessions[uuid] = context
    }

    fun get(uuid: String): ZappingContext? = sessions[uuid]

    fun remove(uuid: String): ZappingContext? = sessions.remove(uuid)

    fun updateIndex(uuid: String, index: Int): ZappingContext? {
        return sessions.computeIfPresent(uuid) { _, current ->
            require(index in current.channels.indices) { "index must point to a channel" }
            current.copy(currentIndex = index)
        }
    }
}
