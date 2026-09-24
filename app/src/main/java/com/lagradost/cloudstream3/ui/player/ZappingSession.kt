package com.lagradost.cloudstream3.ui.player

/**
 * Identifies a live-zapping session without putting the full channel list in a Bundle.
 */
data class ZappingSession(
    val uuid: String,
    val context: ZappingContext,
) {
    fun register() {
        ZappingSessionStore.put(uuid, context)
    }

    fun current(): ZappingContext? = ZappingSessionStore.get(uuid)

    fun select(index: Int): ZappingContext? = ZappingSessionStore.updateIndex(uuid, index)

    fun selectPrevious(): ZappingContext? {
        val state = current() ?: return null
        return ZappingSessionStore.updateIndex(uuid, state.previousIndex())
    }

    fun selectNext(): ZappingContext? {
        val state = current() ?: return null
        return ZappingSessionStore.updateIndex(uuid, state.nextIndex())
    }

    fun close() {
        ZappingSessionStore.remove(uuid)
    }
}
