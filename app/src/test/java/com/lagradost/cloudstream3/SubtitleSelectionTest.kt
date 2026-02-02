package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.SubtitleOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ensure partial subtitle language finding is reliable. */
class SubtitleLanguageTagTest {
    fun getQuickSubtitle(originalName: String, languageCode: String?): SubtitleData {
        return SubtitleData(
            originalName = originalName,
            nameSuffix = "1",
            url = "https://example.com/test.vtt",
            origin = SubtitleOrigin.URL,
            mimeType = "text/vtt",
            headers = emptyMap(),
            languageCode = languageCode
        )
    }

    @Test
    fun `returns languageCode directly if already valid IETF tag`() {
        val subtitle = getQuickSubtitle(
            originalName = "Anything",
            languageCode = "en"
        )

        assertEquals("en", subtitle.getIETF_tag())
    }

    @Test
    fun `matches exact language name`() {
        val subtitle = getQuickSubtitle(
            originalName = "English",
            languageCode = null
        )

        assertEquals("en", subtitle.getIETF_tag())
    }

    @Test
    fun `matches native language name`() {
        val subtitle = getQuickSubtitle(
            originalName = "Español",
            languageCode = null
        )

        assertEquals("es", subtitle.getIETF_tag())
    }

    @Test
    fun `matches fuzzy partial language name`() {
        val subtitle = getQuickSubtitle(
            originalName = "English [SUB]",
            languageCode = null
        )

        assertEquals("en", subtitle.getIETF_tag())
    }

    @Test
    fun `returns null when no language matches`() {
        val subtitle = getQuickSubtitle(
            originalName = "Klingon",
            languageCode = null
        )

        assertNull(subtitle.getIETF_tag())
    }


    @Test
    fun `returns the correct language variant`() {
        val subtitle1 = getQuickSubtitle(
            originalName = "Chinese",
            languageCode = null
        )
        val subtitle2 = getQuickSubtitle(
            originalName = "Chinese (subtitle)",
            languageCode = null
        )
        val subtitleSimplified1 = getQuickSubtitle(
            originalName = "Chinese (simplified)",
            languageCode = null
        )
        val subtitleSimplified2 = getQuickSubtitle(
            originalName = "Chinese - simplified",
            languageCode = null
        )
        val subtitleSimplified3 = getQuickSubtitle(
            originalName = "Chinese simplified",
            languageCode = "zhh"
        )
        val subtitleSimplified4 = getQuickSubtitle(
            originalName = "Chinese (simplified)2",
            languageCode = "zh-hans"
        )
        val subtitleSimplified5 = getQuickSubtitle(
            originalName = "汉语",
            languageCode = null
        )
        val subtitleSimplified6 = getQuickSubtitle(
            originalName = "",
            languageCode = "zh-hans"
        )
        assertEquals("zh", subtitle1.getIETF_tag())
        assertEquals("zh", subtitle2.getIETF_tag())
        assertEquals("zh-hans", subtitleSimplified1.getIETF_tag())
        assertEquals("zh-hans", subtitleSimplified2.getIETF_tag())
        assertEquals("zh-hans", subtitleSimplified3.getIETF_tag())
        assertEquals("zh-hans", subtitleSimplified4.getIETF_tag())
        assertEquals("zh-hans", subtitleSimplified5.getIETF_tag())
        assertEquals("zh-hans", subtitleSimplified6.getIETF_tag())
    }


    @Test
    fun `returns exact language matches`() {
        val subtitle = getQuickSubtitle(
            originalName = "en",
            languageCode = null
        )

        assertEquals("en", subtitle.getIETF_tag())
    }


    @Test
    fun `returns partial language matches`() {
        val subtitle = getQuickSubtitle(
            originalName = "Englis",
            languageCode = null
        )

        assertEquals("en", subtitle.getIETF_tag())
    }
}

