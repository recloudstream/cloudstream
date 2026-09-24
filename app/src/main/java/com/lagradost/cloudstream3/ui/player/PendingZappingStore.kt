package com.lagradost.cloudstream3.ui.player

import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived bridge between a live card opened from Home and the player session created later.
 *
 * The channel list stays in memory instead of being serialized into ResultFragment arguments.
 * Entries are consumed once the matching live result reaches the player launch path.
 */
object PendingZappingStore {
    private const val MAX_PENDING_AGE_MS = 5 * 60 * 1000L

    private data class Key(
        val url: String,
        val apiName: String,
    )

    private data class Entry(
        val context: ZappingContext,
        val createdAtMs: Long,
    )

    private val pending = ConcurrentHashMap<Key, Entry>()

    fun put(url: String, apiName: String, context: ZappingContext) {
        pending[Key(url, apiName)] = Entry(context, System.currentTimeMillis())
    }

    fun peek(url: String, apiName: String): ZappingContext? {
        return pending[Key(url, apiName)]?.takeUnless(::isExpired)?.context
    }

    fun consume(url: String, apiName: String, channelName: String? = null): ZappingContext? {
        val exactKey = Key(url, apiName)
        pending.remove(exactKey)?.let { entry ->
            return entry.takeUnless(::isExpired)?.context
        }

        // A provider can normalize the load URL between the Home SearchResponse and the
        // LoadResponse used by ResultViewModel2. Prefer a channel URL match, then only fall back
        // to a single pending context for that provider so two unrelated Home rows cannot mix.
        val candidates = pending.entries.filter { it.key.apiName == apiName }
        val matching = candidates.firstOrNull { (_, entry) ->
            !isExpired(entry) && entry.context.channels.any { channel ->
                channel.url.trimEnd('/') == url.trimEnd('/') ||
                    channel.name.equals(channelName, ignoreCase = true)
            }
        } ?: candidates.singleOrNull { (_, entry) -> !isExpired(entry) }

        if (matching != null && pending.remove(matching.key, matching.value)) {
            return matching.value.context
        }
        return null
    }

    fun remove(url: String, apiName: String): ZappingContext? {
        return pending.remove(Key(url, apiName))?.context
    }

    fun clear() {
        pending.clear()
    }

    private fun isExpired(entry: Entry): Boolean {
        return System.currentTimeMillis() - entry.createdAtMs > MAX_PENDING_AGE_MS
    }
}
