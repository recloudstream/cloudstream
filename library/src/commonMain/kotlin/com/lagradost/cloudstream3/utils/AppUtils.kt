package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.InternalAPI
import com.lagradost.cloudstream3.json
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KClass

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
object AppUtils {
    /** Any object as a JSON string */
    fun Any.toJson(): String {
        if (this is String) return this
        return toJsonLiteral()
    }

    /** Sometimes we want to encode as JSON even if it is already a String. */
    @InternalAPI
    fun Any.toJsonLiteral(): String {
        // @Serializable generates a serializer at compile time; contextual serializers are
        // registered manually in serializersModule, we need both to support all cases
        val serializer =
            this::class.serializerOrNull() ?: json.serializersModule.getContextual(this::class)
        return if (serializer != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                json.encodeToString(serializer as KSerializer<Any>, this)
            } catch (e: SerializationException) {
                logError(e)
                mapper.writeValueAsString(this)
            }
        } else {
            mapper.writeValueAsString(this)
        }
    }

    @InternalAPI
    fun <T : Any> parseJson(value: String, kClass: KClass<T>): T {
        val serializer = kClass.serializerOrNull() ?: json.serializersModule.getContextual(kClass)
        if (serializer != null) {
            try {
                return json.decodeFromString(serializer, value)
            } catch (e: SerializationException) {
                logError(e)
            }
        }

        return mapper.readValue(value, kClass.java)
    }

    // This is inlined code and can easily cause breakage in extensions!
    // Watch out when editing this to make sure stable also supports all inlined code!
    inline fun <reified T : Any> parseJson(value: String): T {
        // @Serializable generates a serializer at compile time; contextual serializers are
        // registered manually in serializersModule, we need both to support all cases
        val serializer = runCatching { serializer<T>() }
            .recoverCatching { json.serializersModule.getContextual(T::class) }
            .getOrNull()

        // Prefer Kotlin Serialization over Jackson
        if (serializer != null) {
            try {
                return json.decodeFromString(serializer, value)
            } catch (e: SerializationException) {
                logError(e)
            } catch (_: Throwable) {
                // Pass, the above code will trigger a NoSuchMethodError on stable due to our previously undefined json variable
            }
        }

        return mapper.readValue(value)
    }

    @Deprecated(
        "This overload was only ever used for BasePlugin.Manifest which has since been migrated. " +
                "No other code should be using this. Use reader.readText() and call parseJson(String) instead.",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("parseJson<T>(reader.readText())")
    )
    inline fun <reified T> parseJson(reader: java.io.Reader, valueType: Class<T>): T {
        // Reader-based parsing has no kotlinx equivalent, fall back to Jackson
        return mapper.readValue(reader, valueType)
    }

    inline fun <reified T> tryParseJson(value: String?): T? {
        return try {
            parseJson(value ?: return null)
        } catch (_: Exception) {
            null
        }
    }
}
