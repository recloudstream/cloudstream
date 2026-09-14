package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodePlaybackSelectionTest {
    private fun source(quality: Int): VideoLink = runBlocking {
        newExtractorLink(
            source = "MailRu",
            name = "MailRu",
            url = "https://example.com/$quality.m3u8",
            type = ExtractorLinkType.M3U8,
        ) {
            this.quality = quality
        } to null
    }

    private fun subtitle(
        suffix: String = "1",
        url: String = "https://example.com/subtitle.vtt",
    ) = SubtitleData(
        originalName = "English",
        nameSuffix = suffix,
        url = url,
        origin = SubtitleOrigin.URL,
        mimeType = "text/vtt",
        headers = emptyMap(),
        languageCode = "en",
    )

    @Test
    fun `source selection preserves quality`() {
        val selected = source(480)
        val matching = source(480)

        assertSame(
            matching,
            findEpisodeSource(
                listOf(source(1080), matching),
                selected.toEpisodeSourceSelection(),
            ),
        )
    }

    @Test
    fun `source selection rejects a different quality with the same name`() {
        val selection = source(480).toEpisodeSourceSelection()

        assertNull(findEpisodeSource(listOf(source(1080)), selection))
    }

    @Test
    fun `subtitle selection distinguishes unavailable from explicitly disabled`() {
        assertNull(createEpisodeSubtitleSelection(null, subtitlesDisabled = false))
        val disabledSelection =
            createEpisodeSubtitleSelection(null, subtitlesDisabled = true)
        assertEquals(
            EpisodeSubtitleSelection.Disabled,
            disabledSelection,
        )

        val resolution = resolveEpisodeSubtitle(emptySet(), disabledSelection) { subtitle() }
        assertNull(resolution.subtitle)
        assertTrue(resolution.subtitlesDisabled)
    }

    @Test
    fun `selected subtitle takes priority over disabled state`() {
        val selection = createEpisodeSubtitleSelection(
            subtitle(),
            subtitlesDisabled = true,
        )

        assertTrue(selection is EpisodeSubtitleSelection.Selected)
    }

    @Test
    fun `subtitle selection survives episode url changes`() {
        val selected = subtitle(url = "https://example.com/episode-1.vtt")
        val matching = subtitle(url = "https://example.com/episode-2.vtt")

        assertSame(
            matching,
            findEpisodeSubtitle(
                setOf(matching),
                createEpisodeSubtitleSelection(selected, subtitlesDisabled = false),
            ),
        )
    }

    @Test
    fun `subtitle selection rejects a different suffix`() {
        val selection = createEpisodeSubtitleSelection(
            subtitle(suffix = "2"),
            subtitlesDisabled = false,
        )

        assertNull(findEpisodeSubtitle(setOf(subtitle(suffix = "1")), selection))
    }

    @Test
    fun `missing selected subtitle falls back to automatic language`() {
        val automaticSubtitle = subtitle(suffix = "1")
        val selection = createEpisodeSubtitleSelection(
            subtitle(suffix = "2"),
            subtitlesDisabled = false,
        )
        val resolution = resolveEpisodeSubtitle(
            setOf(automaticSubtitle),
            selection,
        ) { automaticSubtitle }

        assertSame(automaticSubtitle, resolution.subtitle)
        assertFalse(resolution.selectionMatched)
        assertFalse(resolution.subtitlesDisabled)
    }
}
