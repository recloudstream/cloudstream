package com.lagradost.cloudstream3

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitUrlParametersTest {

    @Test
    fun splitsBasicParameters() {
        val url = Url("https://example.com/path?foo=bar&baz=qux")
        val result = splitUrlParameters(url)
        assertEquals(mapOf("foo" to "bar", "baz" to "qux"), result)
    }

    @Test
    fun decodesUrlEncodedKeysAndValues() {
        val url = Url("https://example.com/path?na%20me=hello%20world&sp%26ec=a%2Bb")
        val result = splitUrlParameters(url)
        assertEquals(mapOf("na me" to "hello world", "sp&ec" to "a+b"), result)
    }

    @Test
    fun returnsEmptyMapWhenThereAreNoParameters() {
        val url = Url("https://example.com/path")
        val result = splitUrlParameters(url)
        assertTrue(result.isEmpty())
    }

    @Test
    fun keepsOnlyFirstValueForRepeatedKeys() {
        val url = Url("https://example.com/path?a=1&a=2&a=3")
        val result = splitUrlParameters(url)
        assertEquals(mapOf("a" to "1"), result)
    }

    @Test
    fun handlesParameterWithNoValue() {
        val url = Url("https://example.com/path?flag&foo=bar")
        val result = splitUrlParameters(url)
        assertEquals("bar", result["foo"])
        assertEquals("", result["flag"])
    }

    @Test
    fun stringOverloadSplitsBasicParameters() {
        val result = splitUrlParameters("https://example.com/path?foo=bar&baz=qux")
        assertEquals(mapOf("foo" to "bar", "baz" to "qux"), result)
    }

    @Test
    fun stringOverloadDecodesUrlEncodedKeysAndValues() {
        val result = splitUrlParameters("https://example.com/path?na%20me=hello%20world&sp%26ec=a%2Bb")
        assertEquals(mapOf("na me" to "hello world", "sp&ec" to "a+b"), result)
    }

    @Test
    fun stringOverloadReturnsEmptyMapWhenThereAreNoParameters() {
        val result = splitUrlParameters("https://example.com/path")
        assertTrue(result.isEmpty())
    }

    @Test
    fun stringOverloadKeepsOnlyFirstValueForRepeatedKeys() {
        val result = splitUrlParameters("https://example.com/path?a=1&a=2&a=3")
        assertEquals(mapOf("a" to "1"), result)
    }

    @Test
    fun stringOverloadHandlesParameterWithNoValue() {
        val result = splitUrlParameters("https://example.com/path?flag&foo=bar")
        assertEquals("bar", result["foo"])
        assertEquals("", result["flag"])
    }
}
