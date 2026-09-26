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
 * Watches the player for a stuck buffering state and asks to switch to the next ranked
 * source. Two triggers:
 *  - a single stall: buffering with no position advance for [STALL_TIMEOUT_MS]
 *  - repeated short stalls: buffering for [BUFFERING_TICKS_THRESHOLD] of the last 60s
 *
 * Reuses the existing nextMirror()/loadLink(sameEpisode=true) machinery — no new player
 * path, no probing, no health scoring (those are later PRs). Polls once per second while
 * active, which is cheap compared to the decoding work.
 *
 * All automatic behavior is opt-in [PREF_KEY] (default off): with it off the watcher
 * does nothing and today's behavior is untouched.
 */
class StuckBufferingWatcher(
    private val isBuffering: () -> Boolean,
    private val currentPosition: () -> Long,
    private val onSwitchSource: () -> Unit,
    /** Injectable for tests; defaults to wall clock. */
    private val timeSource: () -> Long = System::currentTimeMillis,
    /** Injectable for tests; null means read the user setting. */
    private val enabled: Boolean? = null,
) {
    companion object {
        const val TAG = "StuckBuffering"

        private const val PREF_KEY = "source_auto_switch_enabled"
        private const val FAILURE_KEY_PREFIX = "source_failure_host_"

        private const val POLL_INTERVAL_MS = 1_000L
        private const val STALL_TIMEOUT_MS = 10_000L // buffering w/o progress -> stuck
        private const val BUFFERING_WINDOW_MS = 60_000L
        private const val BUFFERING_TICKS_THRESHOLD = 15 // ~25% of the time buffering
        private const val SWITCH_COOLDOWN_MS = 60_000L
        private const val MAX_SWITCHES_PER_SESSION = 3

        /** Watching this much content past a switch means it worked: budget resets. */
        private const val SWITCH_SUCCESS_PROGRESS_MS = 30_000L

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

    // Start outside cooldown so the first tick can fire
    private var lastSwitchAtMs: Long = timeSource() - SWITCH_COOLDOWN_MS
    private var switchesThisSession: Int = 0

    /** Position where the last switch fired; progress past +[SWITCH_SUCCESS_PROGRESS_MS] resets the budget. */
    private var lastSwitchPositionMs: Long = 0L

    /**
     * Start watching. Polls once per second; each tick checks whether the player is
     * buffering and whether the position has advanced. Fires at most
     * [MAX_SWITCHES_PER_SESSION] times in a row, with [SWITCH_COOLDOWN_MS] between
     * firings — but each switch that leads to real playback progress resets the
     * counter, so working sources are effectively unlimited. Only a cycle where
     * nothing ever plays is capped.
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

    /** Reset session counters, e.g. when a new episode starts. */
    fun reset() {
        switchesThisSession = 0
        resetStallTracking()
    }

    /**
     * Clear the stall clock and buffering window without touching the switch budget —
     * for when the user manually picks a source and the old source's stall history
     * must not fire against the fresh one.
     */
    fun resetStallTracking() {
        stuckSinceMs = 0L
        bufferingTicks.clear()
    }

    internal fun tick() {
        try {
            if (!(enabled ?: isEnabled())) return

            val now = timeSource()
            if (now - lastSwitchAtMs < SWITCH_COOLDOWN_MS) return

            val position = currentPosition()
            val buffering = isBuffering()
            updateBufferingWindow(buffering, now)
            updateStallClock(position, buffering, now)

            // A switch that got us watching again earns a fresh budget: position moved
            // well past where the last switch fired.
            if (position - lastSwitchPositionMs >= SWITCH_SUCCESS_PROGRESS_MS) {
                switchesThisSession = 0
            }

            if (switchesThisSession >= MAX_SWITCHES_PER_SESSION) return
            if (isStuck(now, buffering)) fireSwitch(position, now)
        } catch (e: Exception) {
            Log.e(TAG, "tick failed", e)
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

    private fun isStuck(now: Long, buffering: Boolean): Boolean {
        val singleStall = stuckSinceMs != 0L && now - stuckSinceMs >= STALL_TIMEOUT_MS
        // ponytail: repeated-stall trigger ignores user scrubbing; cooldown + budget-reset
        // bound the damage, revisit only if scrub-heavy usage misfires
        val repeatedStalls = bufferingTicks.size >= BUFFERING_TICKS_THRESHOLD
        return singleStall || repeatedStalls
    }

    private fun fireSwitch(position: Long, now: Long) {
        Log.i(TAG, "Stuck at ${position}ms, switching source (${switchesThisSession + 1}/$MAX_SWITCHES_PER_SESSION)")
        stuckSinceMs = 0L
        bufferingTicks.clear() // hysteresis: window restarts after a switch
        lastSwitchAtMs = now
        lastSwitchPositionMs = position
        switchesThisSession++
        onSwitchSource()
    }
}
