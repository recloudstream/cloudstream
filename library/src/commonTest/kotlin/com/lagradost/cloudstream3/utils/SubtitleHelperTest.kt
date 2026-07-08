package com.lagradost.cloudstream3.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@Suppress("DEPRECATION")
class SubtitleHelperTest {

    @Test
    fun codeToEnglishNameResolvesIso6391() {
        assertEquals("English", SubtitleHelper.fromTagToEnglishLanguageName("en"))
        assertEquals("French", SubtitleHelper.fromTagToEnglishLanguageName("fr"))
        assertEquals("German", SubtitleHelper.fromTagToEnglishLanguageName("de"))
    }

    @Test
    fun codeToEnglishNameIsCaseInsensitive() {
        assertEquals("English", SubtitleHelper.fromTagToEnglishLanguageName("EN"))
    }

    @Test
    fun codeToEnglishNameReturnsNullForUnknownOrBlank() {
        assertNull(SubtitleHelper.fromTagToEnglishLanguageName("zzz"))
        assertNull(SubtitleHelper.fromTagToEnglishLanguageName(""))
        assertNull(SubtitleHelper.fromTagToEnglishLanguageName(null))
        // A single char is below the minimum length and must not match.
        assertNull(SubtitleHelper.fromTagToEnglishLanguageName("e"))
    }

    @Test
    fun languageNameToIetfTag() {
        assertEquals("en", SubtitleHelper.fromLanguageToTagIETF("English"))
        assertEquals("es", SubtitleHelper.fromLanguageToTagIETF("Spanish"))
    }

    @Test
    fun nativeNameToIetfTag() {
        assertEquals("es", SubtitleHelper.fromLanguageToTagIETF("Español"))
        assertEquals("de", SubtitleHelper.fromLanguageToTagIETF("Deutsch"))
    }

    @Test
    fun languageNameToIetfTagStripsGarbageSuffix() {
        assertEquals("en", SubtitleHelper.fromLanguageToTagIETF("English (original audio)"))
        assertEquals("en", SubtitleHelper.fromLanguageToTagIETF("English 123"))
    }

    @Test
    fun languageNameToIetfTagHalfMatch() {
        // Exact matching cannot resolve this, but a half match should.
        assertNull(SubtitleHelper.fromLanguageToTagIETF("Englishhh", halfMatch = false))
        assertEquals("en", SubtitleHelper.fromLanguageToTagIETF("Englishhh", halfMatch = true))
    }

    @Test
    fun deprecatedTwoLetterHelpersResolveNames() {
        assertEquals("English", SubtitleHelper.fromTwoLettersToLanguage("en"))
        assertEquals("en", SubtitleHelper.fromLanguageToTwoLetters("English", false))
    }

    @Test
    fun openSubtitlesTagLookup() {
        assertEquals("af", SubtitleHelper.fromCodeToOpenSubtitlesTag("af"))
    }

    @Test
    fun isWellFormedTagRejectsBlankOrTooShortInput() {
        assertFalse(SubtitleHelper.isWellFormedTagIETF(null))
        assertFalse(SubtitleHelper.isWellFormedTagIETF(""))
        assertFalse(SubtitleHelper.isWellFormedTagIETF("e"))
    }

    @Test
    fun getFlagFromIsoCountryCode() {
        // Regional indicator symbols for "US".
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8", SubtitleHelper.getFlagFromIso("us"))
    }

    @Test
    fun getFlagFromIsoLanguageTagWithCountry() {
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8", SubtitleHelper.getFlagFromIso("en-US"))
    }

    @Test
    fun getFlagFromIsoSpecialCasedQt() {
        // "qt" is special-cased to a gorilla emoji.
        assertEquals("\uD83E\uDD8D", SubtitleHelper.getFlagFromIso("qt"))
    }

    @Test
    fun getFlagFromIsoReturnsNullForBlankOrTooShort() {
        assertNull(SubtitleHelper.getFlagFromIso(null))
        assertNull(SubtitleHelper.getFlagFromIso(""))
        assertNull(SubtitleHelper.getFlagFromIso("x"))
    }
}
