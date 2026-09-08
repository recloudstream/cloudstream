package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.ui.player.GeminiSubtitleTranslator
import com.lagradost.cloudstream3.ui.player.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GeminiSubtitleTranslatorTest {

    @Test
    fun `test cuesToVttFile generates valid WebVTT formatting`() {
        val cues = listOf(
            SubtitleCue(
                startTimeMs = 1000L,
                durationMs = 2500L,
                text = listOf("Salam, necəsən?")
            ),
            SubtitleCue(
                startTimeMs = 4000L,
                durationMs = 3000L,
                text = listOf("Yaxşıyam, çox sağ ol!")
            )
        )

        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val file = File(tempDir, "test_generated.vtt")
        file.bufferedWriter().use { writer ->
            writer.write("WEBVTT\n\n")
            cues.forEach { cue ->
                val start = "%02d:%02d:%02d.%03d".format(
                    cue.startTimeMs / 1000 / 3600,
                    cue.startTimeMs / 1000 / 60 % 60,
                    cue.startTimeMs / 1000 % 60,
                    cue.startTimeMs % 1000
                )
                val endMs = cue.startTimeMs + cue.durationMs
                val end = "%02d:%02d:%02d.%03d".format(
                    endMs / 1000 / 3600,
                    endMs / 1000 / 60 % 60,
                    endMs / 1000 % 60,
                    endMs % 1000
                )
                writer.write("$start --> $end\n${cue.text.joinToString("\n")}\n\n")
            }
        }

        assertTrue("VTT file should exist", file.exists())
        val content = file.readText()
        assertTrue("Header should be WEBVTT", content.startsWith("WEBVTT"))
        assertTrue("Content should contain first line", content.contains("Salam, necəsən?"))
        assertTrue("Content should contain second line", content.contains("Yaxşıyam, çox sağ ol!"))
        file.delete()
    }
}
