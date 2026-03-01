package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.ui.player.DualSubtitleComposer
import com.lagradost.cloudstream3.ui.player.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DualSubtitleComposerTest {
    @Test
    fun `compose merges overlapping cues into secondary plus primary order`() {
        val primary = listOf(
            SubtitleCue(startTimeMs = 1000, durationMs = 2000, text = listOf("Primary A")),
            SubtitleCue(startTimeMs = 3000, durationMs = 2000, text = listOf("Primary B"))
        )
        val secondary = listOf(
            SubtitleCue(startTimeMs = 2000, durationMs = 2000, text = listOf("Secondary A"))
        )

        val result = DualSubtitleComposer.compose(primary, secondary)
        assertEquals(4, result.size)

        assertEquals(1000, result[0].startTimeMs)
        assertEquals(2000, result[0].endTimeMs)
        assertEquals("Primary A", result[0].text)

        assertEquals(2000, result[1].startTimeMs)
        assertEquals(3000, result[1].endTimeMs)
        assertEquals("Secondary A\nPrimary A", result[1].text)

        assertEquals(3000, result[2].startTimeMs)
        assertEquals(4000, result[2].endTimeMs)
        assertEquals("Secondary A\nPrimary B", result[2].text)

        assertEquals(4000, result[3].startTimeMs)
        assertEquals(5000, result[3].endTimeMs)
        assertEquals("Primary B", result[3].text)
    }

    @Test
    fun `compose keeps short cues by using exact boundaries`() {
        val primary = listOf(
            SubtitleCue(startTimeMs = 1000, durationMs = 5000, text = listOf("Long Primary"))
        )
        val secondary = listOf(
            SubtitleCue(startTimeMs = 1234, durationMs = 80, text = listOf("Short Secondary"))
        )

        val result = DualSubtitleComposer.compose(primary, secondary)
        assertTrue(result.any { it.startTimeMs == 1234L && it.endTimeMs == 1314L })
        assertTrue(result.any { it.text == "Short Secondary\nLong Primary" })
    }

    @Test
    fun `compose merges adjacent segments with same text`() {
        val primary = listOf(
            SubtitleCue(startTimeMs = 1000, durationMs = 1000, text = listOf("Same")),
            SubtitleCue(startTimeMs = 2000, durationMs = 1000, text = listOf("Same"))
        )

        val result = DualSubtitleComposer.compose(primary, emptyList())
        assertEquals(1, result.size)
        assertEquals(1000, result[0].startTimeMs)
        assertEquals(3000, result[0].endTimeMs)
        assertEquals("Same", result[0].text)
    }

    @Test
    fun `compose returns empty list for empty or invalid cues`() {
        assertTrue(DualSubtitleComposer.compose(emptyList(), emptyList()).isEmpty())

        val invalid = listOf(
            SubtitleCue(startTimeMs = 1000, durationMs = 0, text = listOf("Invalid"))
        )
        assertTrue(DualSubtitleComposer.compose(invalid, emptyList()).isEmpty())
    }
}
