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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
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
        if (serializer != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                return json.encodeToString(serializer as KSerializer<Any>, this)
            } catch (e: SerializationException) {
                logError(e)
                return mapper.writeValueAsString(this)
            }
        }
        // Handle generic collection/map types where type params are erased at runtime
        // and no serializer can be found via reflection alone
        return try {
            @Suppress("UNCHECKED_CAST")
            when (this) {
                is Array<*> -> json.encodeToString(ListSerializer(elementSerializer()), this.toList())
                is Set<*> -> json.encodeToString(SetSerializer(elementSerializer()), this)
                is List<*> -> json.encodeToString(ListSerializer(elementSerializer()), this)
                is Collection<*> -> json.encodeToString(ListSerializer(elementSerializer()), this.toList())
                is Map<*, *> -> json.encodeToString(MapSerializer(String.serializer(), valueSerializer()), this as Map<String, Any?>)
                else -> mapper.writeValueAsString(this)
            }
        } catch (e: SerializationException) {
            logError(e)
            mapper.writeValueAsString(this)
        }
    }

    private fun elementSerializerForClass(kClass: KClass<*>): KSerializer<Any?> {
        val serializer = kClass.serializerOrNull()
            ?: json.serializersModule.getContextual(kClass)
            ?: throw SerializationException("No serializer found for element type ${kClass.simpleName}")
        @Suppress("UNCHECKED_CAST")
        return serializer as KSerializer<Any?>
    }

    private fun Collection<*>.elementSerializer(): KSerializer<Any?> {
        val elementClass = this.firstOrNull()?.let { it::class } ?: String::class
        return elementSerializerForClass(elementClass)
    }

    private fun Array<*>.elementSerializer(): KSerializer<Any?> {
        val elementClass = this.firstOrNull()?.let { it::class } ?: String::class
        return elementSerializerForClass(elementClass)
    }

    private fun Map<*, *>.valueSerializer(): KSerializer<Any?> {
        val elementClass = this.values.firstOrNull()?.let { it::class } ?: String::class
        return elementSerializerForClass(elementClass)
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
        val serializer = runCatching { serializer<T>() }.getOrNull()
            ?: json.serializersModule.getContextual(T::class)

        // Prefer Kotlin Serialization over Jackson
        if (serializer != null) {
            try {
                return json.decodeFromString(serializer, value)
            } catch (e: SerializationException) {
                logError(e)
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
