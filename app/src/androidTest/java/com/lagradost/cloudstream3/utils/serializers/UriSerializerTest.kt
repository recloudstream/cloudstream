package com.lagradost.cloudstream3.utils.serializers

import android.net.Uri
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Serializable
data class UriData(
    @Serializable(with = UriSerializer::class)
    @SerialName("uri") val uri: Uri = Uri.EMPTY,
)

class UriSerializerTest {

    @Test
    fun uriSerializerSerializesUriToString() {
        val data = UriData(uri = Uri.parse("https://example.com/path?query=1"))
        val result = data.toJson()
        assertTrue(result.contains("https://example.com/path?query=1"))
    }

    @Test
    fun uriSerializerDeserializesStringToUri() {
        val input = """{"uri":"https://example.com/path?query=1"}"""
        val result = parseJson<UriData>(input)
        assertEquals(Uri.parse("https://example.com/path?query=1"), result.uri)
    }

    @Test
    fun uriSerializerRoundtripsCorrectly() {
        val data = UriData(uri = Uri.parse("https://example.com/path?query=1"))
        val encoded = data.toJson()
        val decoded = parseJson<UriData>(encoded)
        assertEquals(data.uri, decoded.uri)
    }
}
