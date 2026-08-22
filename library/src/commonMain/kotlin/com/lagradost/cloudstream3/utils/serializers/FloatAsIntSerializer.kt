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
import kotlinx.serialization.json.intOrNull

/**
 * Replicates Jackson's ACCEPT_FLOAT_AS_INT behaviour for Int fields.
 * A floating-point JSON number is truncated to Int by dropping the
 * fractional part, exactly like Jackson's default truncation behaviour.
 *
 * Usage:
 *
 *   @Serializable
 *   data class MyData(
 *       @Serializable(with = FloatAsIntSerializer::class)
 *       @SerialName("count") val count: Int = 0,
 *   )
 */
@Prerelease
object FloatAsIntSerializer : KSerializer<Int> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FloatAsInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonPrimitive) return 0
        return element.intOrNull ?: element.doubleOrNull?.toInt() ?: 0
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}
