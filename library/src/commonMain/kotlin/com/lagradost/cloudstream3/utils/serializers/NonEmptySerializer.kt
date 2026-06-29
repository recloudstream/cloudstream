package com.lagradost.cloudstream3.utils.serializers

import com.lagradost.cloudstream3.Prerelease
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * Replicates Jackson's @JsonInclude(JsonInclude.Include.NON_EMPTY) behaviour.
 * Strips null, empty strings, empty arrays, and empty objects from the serialized
 * output. Requires the enclosing Json instance to have encodeDefaults = true,
 * which is already in our default global Json instance.
 *
 * Usage:
 *
 *   @OptIn(ExperimentalSerializationApi::class) // KeepGeneratedSerializer is an experimental annotation for now
 *   @KeepGeneratedSerializer
 *   @Serializable(with = MyData.Serializer::class)
 *   data class MyData(
 *       @SerialName("tags") val tags: List<String> = emptyList(),
 *       @SerialName("title") val title: String = "",
 *       @SerialName("meta") val meta: Map<String, String> = emptyMap(),
 *   ) {
 *       object Serializer : NonEmptySerializer<MyData>(MyData.generatedSerializer())
 *   }
 */
@Prerelease
abstract class NonEmptySerializer<T : Any>(tSerializer: KSerializer<T>) :
    JsonTransformingSerializer<T>(tSerializer) {

    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        return JsonObject(element.filterValues { value ->
            when (value) {
                is JsonPrimitive -> value.content.isNotEmpty()
                is JsonArray -> value.isNotEmpty()
                is JsonObject -> value.isNotEmpty()
                JsonNull -> false
            }
        })
    }
}
