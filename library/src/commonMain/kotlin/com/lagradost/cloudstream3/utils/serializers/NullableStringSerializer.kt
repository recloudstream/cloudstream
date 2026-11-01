package com.lagradost.cloudstream3.utils.serializers

import com.lagradost.cloudstream3.Prerelease
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Convenience serializer for nullable String fields that combines
 * ACCEPT_EMPTY_STRING_AS_NULL_OBJECT with null passthrough.
 * An empty JSON string (`""`) or JSON null is decoded as null.
 *
 * Usage:
 *
 *   @Serializable
 *   data class MyData(
 *       @Serializable(with = NullableStringSerializer::class)
 *       @SerialName("title") val title: String? = null,
 *   )
 */
@Prerelease
object NullableStringSerializer : KSerializer<String?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.content.ifEmpty { null }
            JsonNull -> null
            else -> jsonDecoder.json.decodeFromJsonElement(String.serializer(), element)
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        @OptIn(ExperimentalSerializationApi::class) // encodeNull is experimental for now
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}
