package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.utils.StringUtils.decodeUrl
import com.lagradost.cloudstream3.utils.StringUtils.encodeUrl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * We test numerous edge cases here as well because some URLs
 * are weird actually using things like control chars,
 * or emojis in them.
 */
class StringUtilsTest {

    @Test
    fun encodeUrlPlainAsciiIsUnchanged() {
        assertEquals("hello", "hello".encodeUrl())
    }

    @Test
    fun encodeUrlEmptyStringStaysEmpty() {
        assertEquals("", "".encodeUrl())
    }

    @Test
    fun encodeUrlSingleSpaceBecomesPercent20() {
        assertEquals("%20", " ".encodeUrl())
    }

    @Test
    fun encodeUrlMultipleSpacesAreAllEncoded() {
        assertEquals("%20%20%20", "   ".encodeUrl())
    }

    @Test
    fun encodeUrlAmpersandIsEncoded() {
        assertEquals("foo%26bar", "foo&bar".encodeUrl())
    }

    @Test
    fun encodeUrlEqualsSignIsEncoded() {
        assertEquals("key%3Dvalue", "key=value".encodeUrl())
    }

    @Test
    fun encodeUrlPlusSignIsEncoded() {
        assertEquals("a%2Bb", "a+b".encodeUrl())
    }

    @Test
    fun encodeUrlHashIsEncoded() {
        assertEquals("%23anchor", "#anchor".encodeUrl())
    }

    @Test
    fun encodeUrlQuestionMarkIsEncoded() {
        assertEquals("what%3Fever", "what?ever".encodeUrl())
    }

    @Test
    fun encodeUrlSlashIsEncoded() {
        assertEquals("path%2Fto%2Ffile", "path/to/file".encodeUrl())
    }

    @Test
    fun encodeUrlPercentSignItselfIsEncoded() {
        assertEquals("100%25", "100%".encodeUrl())
    }

    @Test
    fun encodeUrlAlreadyPercentEncodedSequenceIsReEncoded() {
        // "%20" should become "%2520" because % = %25
        assertEquals("%2520", "%20".encodeUrl())
    }

    @Test
    fun encodeUrlNonAsciiLatinCharactersAreEncoded() {
        assertEquals("caf%C3%A9", "café".encodeUrl())
    }

    @Test
    fun encodeUrlCjkCharactersAreEncoded() {
        assertEquals("%E6%97%A5%E6%9C%AC%E8%AA%9E", "日本語".encodeUrl())
    }

    @Test
    fun encodeUrlEmojiIsEncoded() {
        assertEquals("%F0%9F%98%80", "\uD83D\uDE00".encodeUrl()) // 😀
    }

    @Test
    fun encodeUrlFullUrlLikeStringEncodesReservedChars() {
        assertEquals(
            "https%3A%2F%2Fexample.com%2Fpath%3Fq%3Dhello%20world%26lang%3Den",
            "https://example.com/path?q=hello world&lang=en".encodeUrl()
        )
    }

    @Test
    fun encodeUrlUnreservedCharsAreNotEncoded() {
        // Per https://datatracker.ietf.org/doc/html/rfc3986#section-2.3
        val unreserved = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~"
        assertEquals(unreserved, unreserved.encodeUrl())
    }

    @Test
    fun encodeUrlTabCharacterIsEncoded() {
        assertEquals("a%09b", "a\tb".encodeUrl())
    }

    @Test
    fun encodeUrlNewlineCharacterIsEncoded() {
        assertEquals("a%0Ab", "a\nb".encodeUrl())
    }

    @Test
    fun encodeUrlCarriageReturnIsEncoded() {
        assertEquals("a%0Db", "a\rb".encodeUrl())
    }

    @Test
    fun encodeUrlLongStringDoesNotThrow() {
        assertEquals("a".repeat(10_000), "a".repeat(10_000).encodeUrl())
    }

    @Test
    fun encodeUrlCommonSpecialCharsAreEncoded() {
        assertEquals(
            "%21%40%23%24%25%5E%26%2A%28%29-_%3D%2B%5B%5D%7B%7D%7C%3B%3A%27%2C.%3C%3E%3F%2F%60~%5C%20%22",
            "!@#\$%^&*()-_=+[]{}|;:',.<>?/`~\\ \"".encodeUrl()
        )
    }

    @Test
    fun encodeUrlEmojiSurrogatePairRoundTripsCleanly() {
        val emoji = "😊"
        assertEquals(emoji, emoji.encodeUrl().decodeUrl())
    }

    @Test
    fun decodeUrlPlainStringIsUnchanged() {
        assertEquals("hello", "hello".decodeUrl())
    }

    @Test
    fun decodeUrlEmptyStringStaysEmpty() {
        assertEquals("", "".decodeUrl())
    }

    @Test
    fun decodeUrlPercent20BecomesSpace() {
        assertEquals(" ", "%20".decodeUrl())
    }

    @Test
    fun decodeUrlPlusSignStaysAsPlus() {
        // This might unexpectedly differ some day.
        assertEquals("+", "+".decodeUrl())
    }

    @Test
    fun decodeUrlPercent2BBecomesLiteralPlus() {
        assertEquals("+", "%2B".decodeUrl())
    }

    @Test
    fun decodeUrlPercent26BecomesAmpersand() {
        assertEquals("&", "%26".decodeUrl())
    }

    @Test
    fun decodeUrlPercent3DBecomesEquals() {
        assertEquals("=", "%3D".decodeUrl())
    }

    @Test
    fun decodeUrlPercent23BecomesHash() {
        assertEquals("#", "%23".decodeUrl())
    }

    @Test
    fun decodeUrlPercent25BecomesPercent() {
        assertEquals("%", "%25".decodeUrl())
    }

    @Test
    fun decodeUrlPercent2FBecomesSlash() {
        assertEquals("/", "%2F".decodeUrl())
    }

    @Test
    fun decodeUrlLowercaseHexWorks() {
        assertEquals(" ", "%20".decodeUrl())
        assertEquals("/", "%2f".decodeUrl())
    }

    @Test
    fun decodeUrlMultiByteUtf8SequenceDecodesCorrectly() {
        // é in UTF-8 is %C3%A9
        assertEquals("é", "%C3%A9".decodeUrl())
    }

    @Test
    fun decodeUrlCjkEncodedSequenceDecodesToOriginal() {
        // 日 in UTF-8 is %E6%97%A5
        assertEquals("日", "%E6%97%A5".decodeUrl())
    }

    @Test
    fun decodeUrlEmojiEncodedSequenceDecodesToOriginal() {
        // 😀 in UTF-8 is %F0%9F%98%80
        assertEquals("😀", "%F0%9F%98%80".decodeUrl())
    }

    @Test
    fun decodeUrlMixedEncodedAndPlainText() {
        assertEquals("hello world&more", "hello%20world%26more".decodeUrl())
    }

    @Test
    fun decodeUrlMultiplePlusSignsStayAsPlus() {
        assertEquals("a+b+c", "a+b+c".decodeUrl())
    }

    @Test
    fun decodeUrlLongEncodedStringDoesNotThrow() {
        assertEquals(" ".repeat(3_000), "%20".repeat(3_000).decodeUrl())
    }

    @Test
    fun decodeUrlConsecutiveEncodedChars() {
        assertEquals("&&", "%26%26".decodeUrl())
    }

    @Test
    fun roundTripPlainAscii() {
        val original = "hello world"
        assertEquals(original, original.encodeUrl().decodeUrl())
    }

    @Test
    fun roundTripEmptyString() {
        assertEquals("", "".encodeUrl().decodeUrl())
    }

    @Test
    fun roundTripAllCommonSpecialChars() {
        val original = "!@#\$%^&*()-_=+[]{}|;:',.<>?/`~\\ \""
        assertEquals(original, original.encodeUrl().decodeUrl())
    }

    @Test
    fun roundTripUrlLikeString() {
        val original = "https://example.com/search?q=hello world&lang=en#section"
        assertEquals(original, original.encodeUrl().decodeUrl())
    }

    @Test
    fun roundTripLatinExtendedCharacters() {
        val original = "àáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ"
        assertEquals(original, original.encodeUrl().decodeUrl())
    }

    @Test
    fun roundTripCjkCharacters() {
        val original = "日本語テスト"
        assertEquals(original, original.encodeUrl().decodeUrl())
    }

    @Test
    fun roundTripArabicCharacters() {
        val original = "مرحبا بالعالم"
        assertEquals(original, original.encodeUrl().decodeUrl())
    }

    @Test
    fun roundTripEmoji() {
        val original = "😀🎉🔥💯"
        assertEquals(original, original.encodeUrl().decodeUrl())
    }

    @Test
    fun roundTripNewlinesAndControlChars() {
        val original = "line1\nline2\r\nline3\ttabbed"
        assertEquals(original, original.encodeUrl().decodeUrl())
    }

    @Test
    fun roundTripOnlySpecialChars() {
        val original = "?&=+#%"
        assertEquals(original, original.encodeUrl().decodeUrl())
    }

    @Test
    fun roundTripLongMixedString() {
        val original = ("Hello World! " +
                "Ünïcödé & symbols: <>\"/\\|?* " +
                "CJK: 日本語 Emoji: 🚀").repeat(50)
        assertEquals(original, original.encodeUrl().decodeUrl())
    }
}
