package com.lagradost.cloudstream3.utils.serializers

import com.lagradost.cloudstream3.Prerelease
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * Replicates Jackson's @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) behaviour.
 * Properties in [keysToIgnore] are deserialized normally but omitted from serialized output.
 *
 * Usage:
 *
 *   @OptIn(ExperimentalSerializationApi::class)
 *   @KeepGeneratedSerializer
 *   @Serializable(with = MyData.Serializer::class)
 *   data class MyData(
 *       val fieldA: String = "",
 *       val fieldB: String = "",
 *   ) {
 *       object Serializer : WriteOnlySerializer<MyData>(
 *           MyData.generatedSerializer(),
 *           setOf("fieldB"),
 *       )
 *   }
 */
@Prerelease
abstract class WriteOnlySerializer<T : Any>(
    tSerializer: KSerializer<T>,
    private val keysToIgnore: Set<String>,
) : JsonTransformingSerializer<T>(tSerializer) {

    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        return JsonObject(element.filterKeys { it !in keysToIgnore })
    }
}
