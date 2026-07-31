package com.lagradost.cloudstream3.utils.serializers

import android.net.Uri
import com.lagradost.cloudstream3.InternalAPI
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Custom KSerializer for Android's [Uri] type.
 *
 * Uri is an Android platform type and cannot be annotated with @Serializable directly.
 * Registering it in a SerializersModule globally would require a custom module passed to
 * every Json instance, which adds hidden coupling. This serializer is also used sparingly
 * across the codebase, so the overhead of a global registration isn't justified.
 * Instead, we keep it explicit so that each usage site opts in intentionally and the
 * serialization behavior remains visible.
 *
 * Usage:
 *
 *   @Serializable
 *   data class MyData(
 *       @Serializable(with = UriSerializer::class)
 *       val uri: Uri,
 *   )
 */
@InternalAPI
object UriSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Uri {
        return Uri.parse(decoder.decodeString())
    }
}
