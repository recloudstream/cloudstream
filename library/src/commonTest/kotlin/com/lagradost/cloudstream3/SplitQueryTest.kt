package com.lagradost.cloudstream3

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitQueryTest {

    @Test
    fun splitsBasicQueryParameters() {
        val url = Url("https://example.com/path?foo=bar&baz=qux")
        val result = splitQuery(url)
        assertEquals(mapOf("foo" to "bar", "baz" to "qux"), result)
    }

    @Test
    fun decodesUrlEncodedKeysAndValues() {
        val url = Url("https://example.com/path?na%20me=hello%20world&sp%26ec=a%2Bb")
        val result = splitQuery(url)
        assertEquals(mapOf("na me" to "hello world", "sp&ec" to "a+b"), result)
    }

    @Test
    fun returnsEmptyMapWhenThereIsNoQueryString() {
        val url = Url("https://example.com/path")
        val result = splitQuery(url)
        assertTrue(result.isEmpty())
    }

    @Test
    fun keepsOnlyFirstValueForRepeatedKeys() {
        val url = Url("https://example.com/path?a=1&a=2&a=3")
        val result = splitQuery(url)
        assertEquals(mapOf("a" to "1"), result)
    }

    @Test
    fun handlesParameterWithNoValue() {
        val url = Url("https://example.com/path?flag&foo=bar")
        val result = splitQuery(url)
        assertEquals("bar", result["foo"])
        assertEquals("", result["flag"])
    }

    @Test
    fun stringOverloadSplitsBasicQueryParameters() {
        val result = splitQuery("https://example.com/path?foo=bar&baz=qux")
        assertEquals(mapOf("foo" to "bar", "baz" to "qux"), result)
    }

    @Test
    fun stringOverloadDecodesUrlEncodedKeysAndValues() {
        val result = splitQuery("https://example.com/path?na%20me=hello%20world&sp%26ec=a%2Bb")
        assertEquals(mapOf("na me" to "hello world", "sp&ec" to "a+b"), result)
    }

    @Test
    fun stringOverloadReturnsEmptyMapWhenThereIsNoQueryString() {
        val result = splitQuery("https://example.com/path")
        assertTrue(result.isEmpty())
    }

    @Test
    fun stringOverloadKeepsOnlyFirstValueForRepeatedKeys() {
        val result = splitQuery("https://example.com/path?a=1&a=2&a=3")
        assertEquals(mapOf("a" to "1"), result)
    }

    @Test
    fun stringOverloadHandlesParameterWithNoValue() {
        val result = splitQuery("https://example.com/path?flag&foo=bar")
        assertEquals("bar", result["foo"])
        assertEquals("", result["flag"])
    }
}
