package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.ui.player.StuckBufferingWatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the stall-detection tick logic of [StuckBufferingWatcher] with a fake clock.
 * Uses the test-only constructor that skips the settings read and coroutine polling.
 * The watcher only reports stuck-state changes (button on/off); it never auto-switches.
 */
class StuckBufferingWatcherTest {

    private class TestableWatcher(
        var buffering: Boolean = false,
        var position: Long = 0L,
        var timeMs: Long = 0L,
    ) {
        /** Sequence of [StuckBufferingWatcher] stuck-state transition callbacks. */
        val stuckEvents: MutableList<Boolean> = mutableListOf()
        val watcher = StuckBufferingWatcher(
            isBuffering = { buffering },
            currentPosition = { position },
            onStuckChanged = { stuckEvents.add(it) },
            timeSource = { timeMs },
            enabled = true,
        )

        fun tick() = watcher.tick()
    }

    private fun TestableWatcher.advance(ms: Long) {
        timeMs += ms
        tick()
    }

    @Test
    fun `does not report occasional short stalls`() {
        val w = TestableWatcher()
        // 1 buffering tick out of every 3, always making progress: ~33s -> 11 buffered ticks
        repeat(11) {
            w.buffering = true
            w.advance(1_000)
            w.buffering = false
            w.advance(1_000)
            w.advance(1_000)
        }
        assertTrue(w.stuckEvents.isEmpty())
    }

    @Test
    fun `reports stuck after ten seconds of buffering without progress`() {
        val w = TestableWatcher()
        w.buffering = true
        w.position = 5_000
        w.advance(1_000) // progress tick: arms the baseline
        w.advance(1_000) // no progress: stuck clock starts
        assertTrue(w.stuckEvents.isEmpty())
        w.advance(10_000) // 10s stuck -> report
        assertEquals(listOf(true), w.stuckEvents)
    }

    @Test
    fun `reports stuck once per stall episode then recovers`() {
        val w = TestableWatcher()
        w.buffering = true
        w.position = 0
        w.advance(11_000) // arm
        w.advance(11_000) // stuck -> [true]
        w.advance(11_000) // still stalled: no repeat while the button stays up
        assertEquals(listOf(true), w.stuckEvents)
        // playback recovers on its own: tell the UI to hide the button
        w.buffering = false
        w.advance(1_000)
        assertEquals(listOf(true, false), w.stuckEvents)
        // and a later stall reports again
        w.buffering = true
        w.advance(11_000)
        w.advance(11_000)
        assertEquals(listOf(true, false, true), w.stuckEvents)
    }

    @Test
    fun `reports when buffering dominates even with progress between stalls`() {
        val w = TestableWatcher()
        // buffering every tick (choppy stream), tiny progress each time: never "stuck",
        // but 15 buffered seconds inside a minute -> report
        repeat(15) {
            w.buffering = true
            w.position += 250
            w.advance(1_000)
        }
        assertEquals(listOf(true), w.stuckEvents)
    }

    @Test
    fun `seek into an unbuffered region reports stuck only after the seek grace`() {
        val w = TestableWatcher()
        w.buffering = false
        w.position = 90_000
        w.advance(1_000) // playing
        w.position = 10_000 // user scrubs back; parks buffering at the new point
        w.advance(1_000) // backward jump: arms the grace, re-baselines
        w.buffering = true
        repeat(14) { w.advance(1_000) } // parked buffering at the target
        assertTrue("a parked seek must not instantly look like a dead source", w.stuckEvents.isEmpty())
        // but an unreasonable wait still counts as stuck
        w.advance(1_000) // grace expires, stall clock arms
        w.advance(11_000)
        assertEquals(listOf(true), w.stuckEvents)
    }

    @Test
    fun `manual source pick clears stall tracking and withdraws the button`() {
        val w = TestableWatcher()
        w.buffering = true
        w.position = 0
        w.advance(11_000) // arm
        w.advance(11_000) // stuck -> button shown
        assertEquals(listOf(true), w.stuckEvents)
        // the user picks a new source: loadLink resets stall tracking
        w.watcher.resetStallTracking()
        assertEquals(listOf(true, false), w.stuckEvents)
        w.advance(9_000) // new source buffers 9s: under the stall timeout, no report
        assertEquals("fresh source must not inherit the stall clock", listOf(true, false), w.stuckEvents)
    }

    @Test
    fun `not buffering resets the stuck clock`() {
        val w = TestableWatcher()
        w.buffering = true
        w.position = 0
        w.advance(9_000)
        w.buffering = false
        w.advance(1_000)
        w.buffering = true
        w.advance(9_000)
        assertTrue("clock must reset when playback recovers", w.stuckEvents.isEmpty())
        w.advance(10_000)
        assertEquals(listOf(true), w.stuckEvents)
    }

    @Test
    fun `host extraction handles common url shapes`() {
        assertEquals("vadsur.stream", StuckBufferingWatcher.hostOrNull("https://vadsur.stream/x/y.m3u8"))
        assertEquals("vadsur.stream", StuckBufferingWatcher.hostOrNull("//vadsur.stream/x"))
        assertEquals("cdn.example.com", StuckBufferingWatcher.hostOrNull("https://cdn.example.com:8443/a"))
        assertNull(StuckBufferingWatcher.hostOrNull("about:blank"))
    }
}
