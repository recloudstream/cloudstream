package com.lagradost.cloudstream3.ui.player

import java.util.Locale
import java.util.TreeSet

data class DualSubtitleSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
)

object DualSubtitleComposer {
    private const val WEBVTT_HEADER = "WEBVTT\n\n"

    fun compose(
        primaryCues: List<SubtitleCue>,
        secondaryCues: List<SubtitleCue>,
    ): List<DualSubtitleSegment> {
        val boundaries = TreeSet<Long>()
        (primaryCues + secondaryCues).forEach { cue ->
            if (cue.endTimeMs > cue.startTimeMs) {
                boundaries.add(cue.startTimeMs)
                boundaries.add(cue.endTimeMs)
            }
        }

        if (boundaries.size < 2) return emptyList()

        val points = boundaries.toList()
        val segments = ArrayList<DualSubtitleSegment>()
        for (i in 0 until points.lastIndex) {
            val start = points[i]
            val end = points[i + 1]
            if (end <= start) continue

            val primaryText = getActiveText(primaryCues, start)
            val secondaryText = getActiveText(secondaryCues, start)
            if (primaryText.isBlank() && secondaryText.isBlank()) continue

            val mergedText = buildString {
                if (secondaryText.isNotBlank()) append(secondaryText)
                if (primaryText.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(primaryText)
                }
            }.trim()
            if (mergedText.isBlank()) continue

            val previous = segments.lastOrNull()
            if (previous != null && previous.endTimeMs == start && previous.text == mergedText) {
                segments[segments.lastIndex] = previous.copy(endTimeMs = end)
            } else {
                segments.add(DualSubtitleSegment(start, end, mergedText))
            }
        }

        return segments
    }

    fun toWebVtt(segments: List<DualSubtitleSegment>): String {
        if (segments.isEmpty()) return WEBVTT_HEADER

        val output = StringBuilder(WEBVTT_HEADER)
        segments.forEachIndexed { index, segment ->
            output.append(index + 1).append('\n')
            output.append(formatTimestamp(segment.startTimeMs))
                .append(" --> ")
                .append(formatTimestamp(segment.endTimeMs))
                .append('\n')
                .append(segment.text)
                .append("\n\n")
        }
        return output.toString()
    }

    private fun getActiveText(cues: List<SubtitleCue>, timeMs: Long): String {
        val lines = LinkedHashSet<String>()
        cues.forEach { cue ->
            if (timeMs in cue.startTimeMs until cue.endTimeMs) {
                cue.text.forEach { line ->
                    val cleaned = line.trim()
                    if (cleaned.isNotEmpty()) lines.add(cleaned)
                }
            }
        }
        return lines.joinToString("\n")
    }

    private fun formatTimestamp(timeMs: Long): String {
        val clamped = timeMs.coerceAtLeast(0)
        val hours = clamped / 3_600_000
        val minutes = (clamped % 3_600_000) / 60_000
        val seconds = (clamped % 60_000) / 1_000
        val millis = clamped % 1_000
        return String.format(
            Locale.US,
            "%02d:%02d:%02d.%03d",
            hours,
            minutes,
            seconds,
            millis
        )
    }
}
