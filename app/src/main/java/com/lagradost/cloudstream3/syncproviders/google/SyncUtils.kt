package com.lagradost.cloudstream3.syncproviders.google

import com.fasterxml.jackson.annotation.JsonProperty

data class SyncMetadata(
    @JsonProperty("updated_at") val updatedAt: Long = 0,
    @JsonProperty("tombstones") val tombstones: Set<String> = emptySet()
)

data class SyncShard(
    @JsonProperty("data") val data: Map<String, String> = emptyMap(),
    @JsonProperty("metadata") val metadata: Map<String, Long> = emptyMap()
)

object SyncUtils {
    fun mergeShards(
        localData: Map<String, String>,
        localMetadata: Map<String, Long>,
        remoteData: Map<String, String>,
        remoteMetadata: Map<String, Long>,
        tombstones: Set<String>
    ): Pair<Map<String, String>, Map<String, Long>> {
        val mergedData = localData.toMutableMap()
        val mergedMetadata = localMetadata.toMutableMap()

        // Handle remote changes
        remoteData.forEach { (key, remoteValue) ->
            if (tombstones.contains(key)) {
                mergedData.remove(key)
                mergedMetadata.remove(key)
                return@forEach
            }

            val remoteTime = remoteMetadata[key] ?: 0L
            val localTime = mergedMetadata[key] ?: 0L

            if (remoteTime > localTime) {
                mergedData[key] = remoteValue
                mergedMetadata[key] = remoteTime
            }
        }

        // Apply tombstones to local data
        tombstones.forEach { key ->
            mergedData.remove(key)
            mergedMetadata.remove(key)
        }

        return mergedData to mergedMetadata
    }

    fun getLocalTimestamp(key: String, metadata: Map<String, Long>): Long {
        return metadata[key] ?: 0L
    }
}
