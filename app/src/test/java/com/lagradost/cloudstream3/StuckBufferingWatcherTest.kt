package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.ui.player.StuckBufferingWatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the stall-detection tick logic of [StuckBufferingWatcher] with a fake clock.
 * Uses the test-only constructor that skips the settings read and coroutine polling.
 */
class StuckBufferingWatcherTest {

    private class TestableWatcher(
        var buffering: Boolean = false,
        var position: Long = 0L,
        var timeMs: Long = 0L,
    ) {
        val fired: MutableList<Long> = mutableListOf()
        val watcher = StuckBufferingWatcher(
            isBuffering = { buffering },
            currentPosition = { position },
            onSwitchSource = { fired.add(timeMs) },
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
    fun `does not fire for occasional short stalls`() {
        val w = TestableWatcher()
        // 1 buffering tick out of every 3, always making progress: ~33s -> 10 buffered ticks
        repeat(11) {
            w.buffering = true
            w.advance(1_000)
            w.buffering = false
            w.advance(1_000)
            w.advance(1_000)
        }
        assertTrue(w.fired.isEmpty())
    }

    @Test
    fun `fires after ten seconds of buffering without progress`() {
        val w = TestableWatcher()
        w.buffering = true
        w.position = 5_000
        w.advance(1_000) // progress tick: arms the baseline
        w.advance(1_000) // no progress: stuck clock starts
        assertTrue(w.fired.isEmpty())
        w.advance(10_000) // 10s stuck -> fire
        assertEquals(1, w.fired.size)
    }

    @Test
    fun `fires when buffering dominates even with progress between stalls`() {
        val w = TestableWatcher()
        // buffering every tick (choppy stream), tiny progress each time: never "stuck",
        // but 15 buffered seconds inside a minute -> fire
        repeat(15) {
            w.buffering = true
            w.position += 250
            w.advance(1_000)
        }
        assertEquals(1, w.fired.size)
    }

    @Test
    fun `cooldown prevents immediate second switch`() {
        val w = TestableWatcher()
        w.buffering = true
        w.position = 0
        w.advance(11_000) // arm
        w.advance(11_000) // fire #1
        assertEquals(1, w.fired.size)
        // stuck again right away
        w.advance(11_000)
        assertTrue("cooldown must block", w.fired.size == 1)
        // after 60s cooldown, allowed again
        w.advance(60_000)
        w.advance(11_000)
        assertEquals(2, w.fired.size)
    }

    @Test
    fun `max three switches per session`() {
        val w = TestableWatcher()
        w.buffering = true
        w.position = 0
        repeat(3) {
            w.advance(11_000) // arm
            w.advance(11_000) // fire
            w.advance(60_000) // wait out cooldown
        }
        assertEquals(3, w.fired.size)
        w.advance(11_000)
        w.advance(11_000)
        w.advance(60_000)
        w.advance(11_000)
        w.advance(11_000)
        assertEquals("session cap must hold", 3, w.fired.size)
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
        assertTrue("clock must reset when playback recovers", w.fired.isEmpty())
        w.advance(10_000)
        assertEquals(1, w.fired.size)
    }

    @Test
    fun `host extraction handles common url shapes`() {
        assertEquals("vadsur.stream", StuckBufferingWatcher.hostOrNull("https://vadsur.stream/x/y.m3u8"))
        assertEquals("vadsur.stream", StuckBufferingWatcher.hostOrNull("//vadsur.stream/x"))
        assertEquals("cdn.example.com", StuckBufferingWatcher.hostOrNull("https://cdn.example.com:8443/a"))
        assertNull(StuckBufferingWatcher.hostOrNull("about:blank"))
    }
}
