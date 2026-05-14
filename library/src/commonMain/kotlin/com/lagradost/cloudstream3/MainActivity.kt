package com.lagradost.cloudstream3

import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KClass

// Short name for requests client to make it nicer to use
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
private val jsonResponseParser = object : ResponseParser {
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        // @Serializable generates a serializer at compile time; contextual serializers are
        // registered manually in serializersModule, we need both to support all cases
        val serializer = kClass.serializerOrNull() ?: json.serializersModule.getContextual(kClass)
        return if (serializer != null) {
            try {
                json.decodeFromString(serializer, text)
            } catch (_: Exception) {
                mapper.readValue(text, kClass.java)
            }
        } else {
            mapper.readValue(text, kClass.java)
        }
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return try {
            parse(text, kClass)
        } catch (_: Exception) {
            null
        }
    }

    override fun writeValueAsString(obj: Any): String {
        // @Serializable generates a serializer at compile time; contextual serializers are
        // registered manually in serializersModule, we need both to support all cases
        val serializer = obj::class.serializerOrNull() ?: json.serializersModule.getContextual(obj::class)
        return if (serializer != null) {
            try {
                // If it has a serializer, encode it safely via kotlinx.serialization
                json.encodeToString(JsonElement.serializer(), json.parseToJsonElement(obj.toString()))
            } catch (_: Exception) {
                mapper.writeValueAsString(obj)
            }
        } else {
            mapper.writeValueAsString(obj)
        }
    }
}

/** The default networking helper. This helper performs SSL checks.
 * If you need to make requests to websites with invalid SSL certificates use insecureApp instead. */
var app = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

/** Same as the default app networking helper, but this instance ignores SSL certificates.
 * This should NEVER be used for sensitive networking operations such as logins. Only use this when required. */
@Prerelease
@UnsafeSSL
var insecureApp = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}
