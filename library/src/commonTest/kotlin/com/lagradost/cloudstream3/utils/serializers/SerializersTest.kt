package com.lagradost.cloudstream3.utils.serializers

import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
@KeepGeneratedSerializer
@Serializable(with = NonEmptyData.Serializer::class)
data class NonEmptyData(
    @SerialName("title") val title: String = "",
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("meta") val meta: Map<String, String> = emptyMap(),
    @SerialName("name") val name: String = "hello",
) {
    object Serializer : NonEmptySerializer<NonEmptyData>(NonEmptyData.generatedSerializer())
}

@OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
@KeepGeneratedSerializer
@Serializable(with = WriteOnlyData.Serializer::class)
data class WriteOnlyData(
    @SerialName("fieldA") val fieldA: String = "",
    @SerialName("fieldB") val fieldB: String = "",
) {
    object Serializer : WriteOnlySerializer<WriteOnlyData>(
        WriteOnlyData.generatedSerializer(),
        setOf("fieldB"),
    )
}

@OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
@KeepGeneratedSerializer
@Serializable(with = MultiWriteOnly.Serializer::class)
data class MultiWriteOnly(
    @SerialName("fieldA") val fieldA: String = "",
    @SerialName("fieldB") val fieldB: String = "",
    @SerialName("fieldC") val fieldC: String = "",
) {
    object Serializer : WriteOnlySerializer<MultiWriteOnly>(
        MultiWriteOnly.generatedSerializer(),
        setOf("fieldB", "fieldC"),
    )
}

@OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
@KeepGeneratedSerializer
@Serializable(with = SingleValueData.Serializer::class)
data class SingleValueData(
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("nums") val nums: List<Int> = emptyList(),
) {
    object Serializer : JsonTransformSerializer<SingleValueData>(
        SingleValueData.generatedSerializer(),
        singleValueAsListKeys = setOf("tags", "nums"),
    )
}

@Serializable
data class NestedMeta(
    @SerialName("key") val key: String = "",
)

@OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
@KeepGeneratedSerializer
@Serializable(with = EmptyArrayData.Serializer::class)
data class EmptyArrayData(
    @SerialName("meta") val meta: NestedMeta? = null,
    @SerialName("other") val other: String = "hello",
) {
    object Serializer : JsonTransformSerializer<EmptyArrayData>(
        EmptyArrayData.generatedSerializer(),
        emptyArrayAsNullKeys = setOf("meta"),
    )
}

@OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
@KeepGeneratedSerializer
@Serializable(with = EmptyStringData.Serializer::class)
data class EmptyStringData(
    @SerialName("title") val title: String? = null,
    @SerialName("episode") val episode: NestedMeta? = null,
    @SerialName("keep") val keep: String? = "hello",
) {
    object Serializer : JsonTransformSerializer<EmptyStringData>(
        EmptyStringData.generatedSerializer(),
        emptyStringAsNullKeys = setOf("title", "episode", "keep"),
    )
}

@OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
@KeepGeneratedSerializer
@Serializable(with = SingleValueOrNullData.Serializer::class)
data class SingleValueOrNullData(
    @SerialName("tags") val tags: List<String>? = null,
    @SerialName("nums") val nums: List<Int>? = null,
    @SerialName("other") val other: String = "hello",
) {
    object Serializer : JsonTransformSerializer<SingleValueOrNullData>(
        SingleValueOrNullData.generatedSerializer(),
        singleValueAsListKeys = setOf("tags", "nums"),
        emptyArrayAsNullKeys = setOf("tags", "nums"),
    )
}

@OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
@KeepGeneratedSerializer
@Serializable(with = NullableFieldsData.Serializer::class)
data class NullableFieldsData(
    @SerialName("title") val title: String? = null,
    @SerialName("meta") val meta: NestedMeta? = null,
    @SerialName("other") val other: String = "hello",
) {
    object Serializer : JsonTransformSerializer<NullableFieldsData>(
        NullableFieldsData.generatedSerializer(),
        emptyStringAsNullKeys = setOf("title"),
        emptyArrayAsNullKeys = setOf("meta"),
    )
}

@OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
@KeepGeneratedSerializer
@Serializable(with = SingleValueOrEmptyStringData.Serializer::class)
data class SingleValueOrEmptyStringData(
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("title") val title: String? = null,
) {
    object Serializer : JsonTransformSerializer<SingleValueOrEmptyStringData>(
        SingleValueOrEmptyStringData.generatedSerializer(),
        singleValueAsListKeys = setOf("tags"),
        emptyStringAsNullKeys = setOf("title"),
    )
}

@OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
@KeepGeneratedSerializer
@Serializable(with = AllTransformsData.Serializer::class)
data class AllTransformsData(
    @SerialName("tags") val tags: List<String>? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("meta") val meta: NestedMeta? = null,
    @SerialName("other") val other: String = "hello",
) {
    object Serializer : JsonTransformSerializer<AllTransformsData>(
        AllTransformsData.generatedSerializer(),
        singleValueAsListKeys = setOf("tags"),
        emptyArrayAsNullKeys = setOf("tags", "meta"),
        emptyStringAsNullKeys = setOf("title", "tags"),
    )
}

@Serializable
data class FloatIntData(
    @Serializable(with = FloatAsIntSerializer::class)
    @SerialName("count") val count: Int = 0,
)

@Serializable
data class FloatLongData(
    @Serializable(with = FloatAsLongSerializer::class)
    @SerialName("timestamp") val timestamp: Long = 0L,
)

@Serializable
data class NullableStringData(
    @Serializable(with = NullableStringSerializer::class)
    @SerialName("title") val title: String? = null,
)

class SerializersTest {

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
    fun singleValueAsListDecodesArrayNormally() {
        val input = """{"tags":["a","b"],"nums":[1,2]}"""
        val result = parseJson<SingleValueData>(input)
        assertEquals(listOf("a", "b"), result.tags)
        assertEquals(listOf(1, 2), result.nums)
    }

    @Test
    fun singleValueAsListWrapsBareStringInList() {
        val input = """{"tags":"a","nums":[1]}"""
        val result = parseJson<SingleValueData>(input)
        assertEquals(listOf("a"), result.tags)
    }

    @Test
    fun singleValueAsListWrapsBareIntInList() {
        val input = """{"tags":[],"nums":42}"""
        val result = parseJson<SingleValueData>(input)
        assertEquals(listOf(42), result.nums)
    }

    @Test
    fun singleValueAsListDecodesNullAsEmptyList() {
        val input = """{"tags":null,"nums":[]}"""
        val result = parseJson<SingleValueData>(input)
        assertEquals(emptyList<String>(), result.tags)
    }

    @Test
    fun singleValueAsListRoundtripsCorrectly() {
        val data = SingleValueData(tags = listOf("x", "y"), nums = listOf(1, 2))
        val encoded = data.toJson()
        val decoded = parseJson<SingleValueData>(encoded)
        assertEquals(data, decoded)
    }

    @Test
    fun emptyArrayAsNullDecodesEmptyArrayAsNull() {
        val input = """{"meta":[],"other":"hello"}"""
        val result = parseJson<EmptyArrayData>(input)
        assertNull(result.meta)
    }

    @Test
    fun emptyArrayAsNullDecodesNullAsNull() {
        val input = """{"meta":null,"other":"hello"}"""
        val result = parseJson<EmptyArrayData>(input)
        assertNull(result.meta)
    }

    @Test
    fun emptyArrayAsNullDecodesObjectNormally() {
        val input = """{"meta":{"key":"value"},"other":"hello"}"""
        val result = parseJson<EmptyArrayData>(input)
        assertEquals(NestedMeta("value"), result.meta)
    }

    @Test
    fun emptyArrayAsNullDoesNotAffectOtherFields() {
        val input = """{"meta":[],"other":"world"}"""
        val result = parseJson<EmptyArrayData>(input)
        assertEquals("world", result.other)
    }

    @Test
    fun emptyArrayAsNullRoundtripsCorrectly() {
        val data = EmptyArrayData(meta = NestedMeta("test"), other = "hello")
        val encoded = data.toJson()
        val decoded = parseJson<EmptyArrayData>(encoded)
        assertEquals(data, decoded)
    }

    @Test
    fun emptyStringAsNullDecodesEmptyStringAsNull() {
        val input = """{"title":"","episode":null,"keep":"hello"}"""
        val result = parseJson<EmptyStringData>(input)
        assertNull(result.title)
    }

    @Test
    fun emptyStringAsNullDecodesNullAsNull() {
        val input = """{"title":null,"episode":null,"keep":"hello"}"""
        val result = parseJson<EmptyStringData>(input)
        assertNull(result.title)
    }

    @Test
    fun emptyStringAsNullKeepsNonEmptyString() {
        val input = """{"title":"hello","episode":null,"keep":"world"}"""
        val result = parseJson<EmptyStringData>(input)
        assertEquals("hello", result.title)
    }

    @Test
    fun emptyStringAsNullDecodesObjectNormally() {
        val input = """{"title":null,"episode":{"key":"value"},"keep":"hello"}"""
        val result = parseJson<EmptyStringData>(input)
        assertEquals(NestedMeta("value"), result.episode)
    }

    @Test
    fun emptyStringAsNullDoesNotAffectNonEmptyFields() {
        val input = """{"title":"","episode":null,"keep":"world"}"""
        val result = parseJson<EmptyStringData>(input)
        assertEquals("world", result.keep)
    }

    @Test
    fun emptyStringAsNullRoundtripsCorrectly() {
        val data = EmptyStringData(title = "hello", episode = NestedMeta("x"), keep = "world")
        val encoded = data.toJson()
        val decoded = parseJson<EmptyStringData>(encoded)
        assertEquals(data, decoded)
    }

    @Test
    fun singleValueOrNullWrapsBareValueInList() {
        val input = """{"tags":"a","nums":1,"other":"hello"}"""
        val result = parseJson<SingleValueOrNullData>(input)
        assertEquals(listOf("a"), result.tags)
        assertEquals(listOf(1), result.nums)
    }

    @Test
    fun singleValueOrNullTreatsEmptyArrayAsNull() {
        val input = """{"tags":[],"nums":[],"other":"hello"}"""
        val result = parseJson<SingleValueOrNullData>(input)
        assertNull(result.tags)
        assertNull(result.nums)
    }

    @Test
    fun singleValueOrNullDecodesArrayNormally() {
        val input = """{"tags":["a","b"],"nums":[1,2],"other":"hello"}"""
        val result = parseJson<SingleValueOrNullData>(input)
        assertEquals(listOf("a", "b"), result.tags)
        assertEquals(listOf(1, 2), result.nums)
    }

    @Test
    fun singleValueOrNullDoesNotAffectOtherFields() {
        val input = """{"tags":[],"nums":[],"other":"world"}"""
        val result = parseJson<SingleValueOrNullData>(input)
        assertEquals("world", result.other)
    }

    @Test
    fun nullableFieldsEmptyStringBecomesNull() {
        val input = """{"title":"","meta":{"key":"value"},"other":"hello"}"""
        val result = parseJson<NullableFieldsData>(input)
        assertNull(result.title)
        assertEquals(NestedMeta("value"), result.meta)
    }

    @Test
    fun nullableFieldsEmptyArrayBecomesNull() {
        val input = """{"title":"hello","meta":[],"other":"hello"}"""
        val result = parseJson<NullableFieldsData>(input)
        assertEquals("hello", result.title)
        assertNull(result.meta)
    }

    @Test
    fun nullableFieldsBothNullAtOnce() {
        val input = """{"title":"","meta":[],"other":"hello"}"""
        val result = parseJson<NullableFieldsData>(input)
        assertNull(result.title)
        assertNull(result.meta)
    }

    @Test
    fun nullableFieldsDoesNotAffectOtherFields() {
        val input = """{"title":"","meta":[],"other":"world"}"""
        val result = parseJson<NullableFieldsData>(input)
        assertEquals("world", result.other)
    }

    @Test
    fun nullableFieldsRoundtripsCorrectly() {
        val data = NullableFieldsData(title = "hello", meta = NestedMeta("x"), other = "world")
        val encoded = data.toJson()
        val decoded = parseJson<NullableFieldsData>(encoded)
        assertEquals(data, decoded)
    }

    @Test
    fun singleValueOrEmptyStringWrapsBareString() {
        val input = """{"tags":"a","title":"hello"}"""
        val result = parseJson<SingleValueOrEmptyStringData>(input)
        assertEquals(listOf("a"), result.tags)
        assertEquals("hello", result.title)
    }

    @Test
    fun singleValueOrEmptyStringTurnsEmptyTitleToNull() {
        val input = """{"tags":["a"],"title":""}"""
        val result = parseJson<SingleValueOrEmptyStringData>(input)
        assertEquals(listOf("a"), result.tags)
        assertNull(result.title)
    }

    @Test
    fun singleValueOrEmptyStringRoundtripsCorrectly() {
        val data = SingleValueOrEmptyStringData(tags = listOf("x"), title = "hello")
        val encoded = data.toJson()
        val decoded = parseJson<SingleValueOrEmptyStringData>(encoded)
        assertEquals(data, decoded)
    }

    @Test
    fun allTransformsWrapsBareStringInList() {
        val input = """{"tags":"a","title":"hello","meta":{"key":"value"},"other":"world"}"""
        val result = parseJson<AllTransformsData>(input)
        assertEquals(listOf("a"), result.tags)
    }

    @Test
    fun allTransformsEmptyArrayTagsBecomesNull() {
        val input = """{"tags":[],"title":"hello","meta":{"key":"value"},"other":"world"}"""
        val result = parseJson<AllTransformsData>(input)
        assertNull(result.tags)
    }

    @Test
    fun allTransformsEmptyMetaBecomesNull() {
        val input = """{"tags":["a"],"title":"hello","meta":[],"other":"world"}"""
        val result = parseJson<AllTransformsData>(input)
        assertNull(result.meta)
    }

    @Test
    fun allTransformsEmptyTitleBecomesNull() {
        val input = """{"tags":["a"],"title":"","meta":{"key":"value"},"other":"world"}"""
        val result = parseJson<AllTransformsData>(input)
        assertNull(result.title)
    }

    @Test
    fun allTransformsAllNullAtOnce() {
        val input = """{"tags":[],"title":"","meta":[],"other":"world"}"""
        val result = parseJson<AllTransformsData>(input)
        assertNull(result.tags)
        assertNull(result.title)
        assertNull(result.meta)
    }

    @Test
    fun allTransformsDoesNotAffectOtherFields() {
        val input = """{"tags":[],"title":"","meta":[],"other":"world"}"""
        val result = parseJson<AllTransformsData>(input)
        assertEquals("world", result.other)
    }

    @Test
    fun allTransformsRoundtripsCorrectly() {
        val data = AllTransformsData(tags = listOf("a"), title = "hello", meta = NestedMeta("x"), other = "world")
        val encoded = data.toJson()
        val decoded = parseJson<AllTransformsData>(encoded)
        assertEquals(data, decoded)
    }

    @Test
    fun floatAsIntTruncatesFloat() {
        val input = """{"count":3.9}"""
        val result = parseJson<FloatIntData>(input)
        assertEquals(3, result.count)
    }

    @Test
    fun floatAsIntDecodesIntNormally() {
        val input = """{"count":42}"""
        val result = parseJson<FloatIntData>(input)
        assertEquals(42, result.count)
    }

    @Test
    fun floatAsIntHandlesNegativeFloat() {
        val input = """{"count":-2.7}"""
        val result = parseJson<FloatIntData>(input)
        assertEquals(-2, result.count)
    }

    @Test
    fun floatAsIntRoundtripsCorrectly() {
        val data = FloatIntData(count = 7)
        val encoded = data.toJson()
        val decoded = parseJson<FloatIntData>(encoded)
        assertEquals(data, decoded)
    }

    @Test
    fun floatAsLongTruncatesFloat() {
        val input = """{"timestamp":1234567890.9}"""
        val result = parseJson<FloatLongData>(input)
        assertEquals(1234567890L, result.timestamp)
    }

    @Test
    fun floatAsLongDecodesLongNormally() {
        val input = """{"timestamp":9999999999}"""
        val result = parseJson<FloatLongData>(input)
        assertEquals(9999999999L, result.timestamp)
    }

    @Test
    fun floatAsLongHandlesNegativeFloat() {
        val input = """{"timestamp":-100.6}"""
        val result = parseJson<FloatLongData>(input)
        assertEquals(-100L, result.timestamp)
    }

    @Test
    fun floatAsLongRoundtripsCorrectly() {
        val data = FloatLongData(timestamp = 1700000000L)
        val encoded = data.toJson()
        val decoded = parseJson<FloatLongData>(encoded)
        assertEquals(data, decoded)
    }

    @Test
    fun nullableStringDecodesEmptyStringAsNull() {
        val input = """{"title":""}"""
        val result = parseJson<NullableStringData>(input)
        assertNull(result.title)
    }

    @Test
    fun nullableStringDecodesNullAsNull() {
        val input = """{"title":null}"""
        val result = parseJson<NullableStringData>(input)
        assertNull(result.title)
    }

    @Test
    fun nullableStringKeepsNonEmptyString() {
        val input = """{"title":"hello"}"""
        val result = parseJson<NullableStringData>(input)
        assertEquals("hello", result.title)
    }

    @Test
    fun nullableStringRoundtripsCorrectly() {
        val data = NullableStringData(title = "world")
        val encoded = data.toJson()
        val decoded = parseJson<NullableStringData>(encoded)
        assertEquals(data, decoded)
    }
}
