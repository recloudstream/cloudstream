package com.lagradost.cloudstream3.syncproviders.google

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.BackupUtils

data class SyncMetadata(
    @JsonProperty("updated_at") val updatedAt: Long = 0
)

object SyncUtils {
    fun convertLegacyToShards(backup: BackupUtils.BackupFile): Pair<SyncManager.Shard, SyncManager.Shard> {
        val datastoreData = mutableMapOf<String, Any>()
        val settingsData = mutableMapOf<String, Any>()
        
        fun flatten(vars: BackupUtils.BackupVars?, target: MutableMap<String, Any>) {
            vars?.bool?.forEach { (k, v) -> target[k] = v }
            vars?.int?.forEach { (k, v) -> target[k] = v }
            vars?.string?.forEach { (k, v) -> target[k] = v }
            vars?.float?.forEach { (k, v) -> target[k] = v }
            vars?.long?.forEach { (k, v) -> target[k] = v }
            vars?.stringSet?.forEach { (k, v) -> target[k] = v ?: emptySet<String>() }
        }

        flatten(backup.datastore, datastoreData)
        flatten(backup.settings, settingsData)

        return SyncManager.Shard(1, datastoreData) to SyncManager.Shard(1, settingsData)
    }
}
