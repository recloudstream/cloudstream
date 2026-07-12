package com.lagradost.cloudstream3.utils.serializers

import android.net.Uri
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = NonEmptyData.Serializer::class)
data class NonEmptyData(
    val title: String = "",
    val tags: List<String> = emptyList(),
    val meta: Map<String, String> = emptyMap(),
    val name: String = "hello",
) {
    object Serializer : NonEmptySerializer<NonEmptyData>(NonEmptyData.generatedSerializer())
}

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = WriteOnlyData.Serializer::class)
data class WriteOnlyData(
    val fieldA: String = "",
    val fieldB: String = "",
) {
    object Serializer : WriteOnlySerializer<WriteOnlyData>(
        WriteOnlyData.generatedSerializer(),
        setOf("fieldB"),
    )
}

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = MultiWriteOnly.Serializer::class)
data class MultiWriteOnly(
    val fieldA: String = "",
    val fieldB: String = "",
    val fieldC: String = "",
) {
    object Serializer : WriteOnlySerializer<MultiWriteOnly>(
        MultiWriteOnly.generatedSerializer(),
        setOf("fieldB", "fieldC"),
    )
}

@Serializable
data class UriData(
    @Serializable(with = UriSerializer::class)
    val uri: Uri = Uri.EMPTY,
)

class SerializerTest {

    @Test
    fun nonEmptySerializerOmitsEmptyStrings() {
        val data = NonEmptyData(title = "", name = "hello")
        val result = data.toJson()
        assertFalse(result.contains("title"))
        assertTrue(result.contains("name"))
    }

    @Test
    fun nonEmptySerializerOmitsEmptyLists() {
        val data = NonEmptyData(tags = emptyList(), name = "hello")
        val result = data.toJson()
        assertFalse(result.contains("tags"))
    }

    @Test
    fun nonEmptySerializerOmitsEmptyMaps() {
        val data = NonEmptyData(meta = emptyMap(), name = "hello")
        val result = data.toJson()
        assertFalse(result.contains("meta"))
    }

    @Test
    fun nonEmptySerializerKeepsNonEmptyFields() {
        val data = NonEmptyData(title = "hello", tags = listOf("a"), meta = mapOf("k" to "v"))
        val result = data.toJson()
        assertTrue(result.contains("title"))
        assertTrue(result.contains("tags"))
        assertTrue(result.contains("meta"))
    }

    @Test
    fun nonEmptySerializerDoesNotAffectDeserialization() {
        val input = """{"title":"hello","tags":["a"],"meta":{"k":"v"},"name":"world"}"""
        val result = parseJson<NonEmptyData>(input)
        assertEquals("hello", result.title)
        assertEquals(listOf("a"), result.tags)
        assertEquals(mapOf("k" to "v"), result.meta)
        assertEquals("world", result.name)
    }

    @Test
    fun writeOnlySerializerOmitsFieldOnSerialize() {
        val data = WriteOnlyData(fieldA = "hello", fieldB = "secret")
        val result = data.toJson()
        assertTrue(result.contains("fieldA"))
        assertFalse(result.contains("fieldB"))
    }

    @Test
    fun writeOnlySerializerDeserializesNormally() {
        val input = """{"fieldA":"hello","fieldB":"secret"}"""
        val result = parseJson<WriteOnlyData>(input)
        assertEquals("hello", result.fieldA)
        assertEquals("secret", result.fieldB)
    }

    @Test
    fun writeOnlySerializerDeserializesMissingAsDefault() {
        val input = """{"fieldA":"hello"}"""
        val result = parseJson<WriteOnlyData>(input)
        assertEquals("hello", result.fieldA)
        assertEquals("", result.fieldB)
    }

    @Test
    fun writeOnlySerializerHandlesMultipleKeys() {
        val data = MultiWriteOnly(fieldA = "hello", fieldB = "secret1", fieldC = "secret2")
        val result = data.toJson()
        assertTrue(result.contains("fieldA"))
        assertFalse(result.contains("fieldB"))
        assertFalse(result.contains("fieldC"))
    }

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
