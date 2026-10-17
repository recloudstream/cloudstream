package com.lagradost.cloudstream3.ui.player

import android.util.Log
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches the player for a stuck buffering state and tells the UI to surface a manual
 * "switch source" button — it never switches on its own. The player already auto-switches
 * on hard errors, and silencing stalls is better solved by giving the user the one-tap
 * escape hatch than by grabbing the next ranked source from behind them.
 *
 * Stuck means either:
 *  - a single stall: buffering with no position advance for [STALL_TIMEOUT_MS]
 *  - repeated short stalls: buffering for [BUFFERING_TICKS_THRESHOLD] of the last 60s
 *
 * A scrub cannot be told apart from a dead source by position alone (both park the player
 * somewhere buffering), so a seek-like jump between ticks only buys the new point a
 * [SEEK_GRACE_MS] grace window instead of nothing; after that a still-frozen player is
 * treated as stuck.
 *
 * Reuses the existing nextMirror()/loadLink(sameEpisode=true) machinery on the button
 * click. Polls once per second while active, which is cheap compared to the decoding work.
 *
 * All behavior is opt-in [PREF_KEY] (default off): with it off the watcher is inert and
 * today's behavior is untouched.
 */
class StuckBufferingWatcher(
    private val isBuffering: () -> Boolean,
    private val currentPosition: () -> Long,
    /** Called when the stuck state changes: true = show the button, false = hide it. */
    private val onStuckChanged: (stuck: Boolean) -> Unit,
    /** Injectable for tests; defaults to wall clock. */
    private val timeSource: () -> Long = System::currentTimeMillis,
    /** Injectable for tests; null means read the user setting. */
    private val enabled: Boolean? = null,
) {
    companion object {
        const val TAG = "StuckBuffering"

        private const val PREF_KEY = "stuck_source_button_enabled"
        private const val FAILURE_KEY_PREFIX = "source_failure_host_"

        private const val POLL_INTERVAL_MS = 1_000L
        private const val STALL_TIMEOUT_MS = 10_000L // buffering w/o progress -> stuck
        private const val BUFFERING_WINDOW_MS = 60_000L
        private const val BUFFERING_TICKS_THRESHOLD = 15 // ~25% of the time buffering

        /** More than this much movement between 1s ticks is a seek, not playback. */
        private const val SEEK_JUMP_MS = 3_000L

        /** How long a seek's new position gets to buffer before it counts as stuck. */
        private const val SEEK_GRACE_MS = 15_000L

        fun isEnabled(): Boolean = try {
            val ctx = CloudStreamApp.context ?: return false
            PreferenceManager.getDefaultSharedPreferences(ctx)
                .getBoolean(PREF_KEY, false)
        } catch (_: Exception) {
            false
        }

        internal fun hostOrNull(url: String): String? = try {
            url.substringAfter("//").substringBefore('/').substringBefore(':').lowercase()
                .takeIf { it.contains('.') } // real hosters have a TLD; filters "about:blank" etc.
        } catch (_: Exception) {
            null
        }

        /** Best-effort local per-host failure counter; safe no-op without a context. */
        fun recordHostFailure(url: String?) {
            val host = url?.let { hostOrNull(it) } ?: return
            val ctx = CloudStreamApp.context ?: return
            try {
                val key = FAILURE_KEY_PREFIX + host
                ctx.setKey(key, (ctx.getKey<Int>(key) ?: 0) + 1)
            } catch (_: Exception) {
                // stats are best effort
            }
        }
    }

    private var pollJob: Job? = null
    private var lastProgressMs: Long = 0L
    private var stuckSinceMs: Long = 0L
    private val bufferingTicks = ArrayDeque<Long>()

    /** Previous tick's position for seek-jump detection; -1 = first tick just records. */
    private var lastSeekCheckMs: Long = -1L

    /** Ticks before this moment are ignored: the user just seeked, buffering there is expected. */
    private var seekGraceUntilMs: Long = 0L

    /** Whether the user has been handed the button for the current stall (hysteresis). */
    private var stuckNotified = false

    /**
     * Start watching. Polls once per second; each tick checks whether the player is
     * buffering and whether the position has advanced. Calls [onStuckChanged] on
     * transitions only — once per stall episode, again with false on recovery, a
     * manual/automatic source change, or a new seek.
     */
    fun start() {
        pollJob?.cancel()
        pollJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                tick()
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        reset()
    }

    /** Reset session state, e.g. when a new episode starts. */
    fun reset() {
        resetStallTracking()
    }

    /**
     * Clear the stall clock, buffering window and stuck state — for when a link loads
     * (user pick, error failover, button switch): the old source's stall history must
     * not act against the fresh one.
     */
    fun resetStallTracking() {
        stuckSinceMs = 0L
        bufferingTicks.clear()
        // -1 so the next tick re-baselines instead of misreading the fresh source's
        // position as a seek jump.
        lastSeekCheckMs = -1L
        seekGraceUntilMs = 0L
        setStuckNotified(false)
    }

    internal fun tick() {
        try {
            if (!(enabled ?: isEnabled())) return

            val now = timeSource()
            val position = currentPosition()
            val buffering = isBuffering()

            // Seek-like jump between ticks (scrub, skip intro, D-pad seek): the player
            // can sit buffering at the new point for a long time and that is
            // indistinguishable from a dead source by position alone. Don't count it as
            // stall history or as budget-earning playback — only park the clock.
            if (lastSeekCheckMs >= 0L &&
                (position < lastSeekCheckMs || position - lastSeekCheckMs > SEEK_JUMP_MS)
            ) {
                seekGraceUntilMs = now + SEEK_GRACE_MS
            }
            lastSeekCheckMs = position
            if (now < seekGraceUntilMs) {
                lastProgressMs = position
                stuckSinceMs = 0L
                setStuckNotified(false)
                return
            }

            updateBufferingWindow(buffering, now)
            updateStallClock(position, buffering, now)

            setStuckNotified(isStuck(now))
        } catch (e: Exception) {
            Log.e(TAG, "tick failed", e)
        }
    }

    private fun setStuckNotified(stuck: Boolean) {
        if (stuck != stuckNotified) {
            stuckNotified = stuck
            onStuckChanged(stuck)
        }
    }

    private fun updateBufferingWindow(buffering: Boolean, now: Long) {
        if (buffering) {
            bufferingTicks.addLast(now)
        }
        while (bufferingTicks.isNotEmpty() && now - bufferingTicks.first() > BUFFERING_WINDOW_MS) {
            bufferingTicks.removeFirst()
        }
    }

    private fun updateStallClock(position: Long, buffering: Boolean, now: Long) {
        when {
            !buffering -> {
                stuckSinceMs = 0L
                lastProgressMs = position
            }
            position > lastProgressMs -> {
                // buffering but still making progress (normal startup / fast network)
                lastProgressMs = position
                stuckSinceMs = 0L
            }
            stuckSinceMs == 0L -> stuckSinceMs = now
        }
    }

    private fun isStuck(now: Long): Boolean {
        val singleStall = stuckSinceMs != 0L && now - stuckSinceMs >= STALL_TIMEOUT_MS
        val repeatedStalls = bufferingTicks.size >= BUFFERING_TICKS_THRESHOLD
        return singleStall || repeatedStalls
    }
}
