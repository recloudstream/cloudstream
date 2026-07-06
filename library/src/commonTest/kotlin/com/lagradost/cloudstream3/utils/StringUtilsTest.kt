package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri
import kotlin.test.Test
import kotlin.test.assertEquals

class StringUtilsTest {

    @Test
    fun encodesSpacesAsPlus() {
        assertEquals("a+b", "a b".encodeUri())
    }

    @Test
    fun encodesReservedCharacters() {
        assertEquals("a%2Fb", "a/b".encodeUri())
        assertEquals("a%26b%3Dc", "a&b=c".encodeUri())
    }

    @Test
    fun decodesPlusAsSpace() {
        assertEquals("a b", "a+b".decodeUri())
    }

    @Test
    fun decodesPercentEscapes() {
        assertEquals("a/b", "a%2Fb".decodeUri())
    }

    @Test
    fun encodeThenDecodeRoundTrips() {
        val original = "hello world & special = ?/#"
        assertEquals(original, original.encodeUri().decodeUri())
    }

    @Test
    fun encodeThenDecodePreservesUnicode() {
        val original = "café Ünïcödé 日本語"
        assertEquals(original, original.encodeUri().decodeUri())
    }
}
