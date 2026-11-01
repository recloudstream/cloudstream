package com.lagradost.cloudstream3.utils.serializers

import com.lagradost.cloudstream3.Prerelease
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Replicates Jackson's ACCEPT_FLOAT_AS_INT behaviour for Long fields.
 * A floating-point JSON number is truncated to Long by dropping the
 * fractional part, exactly like Jackson's default truncation behaviour.
 *
 * Usage:
 *
 *   @Serializable
 *   data class MyData(
 *       @Serializable(with = FloatAsLongSerializer::class)
 *       @SerialName("timestamp") val timestamp: Long = 0L,
 *   )
 */
@Prerelease
object FloatAsLongSerializer : KSerializer<Long> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FloatAsLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonPrimitive) return 0L
        return element.longOrNull ?: element.doubleOrNull?.toLong() ?: 0L
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}
