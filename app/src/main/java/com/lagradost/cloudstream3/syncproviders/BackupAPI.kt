package com.lagradost.cloudstream3.syncproviders

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.BackupUtils.getBackup
import com.lagradost.cloudstream3.utils.BackupUtils.restore
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.skyscreamer.jsonassert.JSONCompare
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.JSONCompareResult
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.seconds

interface BackupAPI<LOGIN_DATA> {
    data class JSONComparison(
        val failed: Boolean,
        val result: JSONCompareResult?
    )

    data class PreferencesSchedulerData<T>(
        val syncPrefs: SharedPreferences,
        val storeKey: String,
        val oldValue: T,
        val newValue: T,
        val source: BackupUtils.RestoreSource
    )

    data class SharedPreferencesWithListener(
        val self: SharedPreferences,
        val scheduler: Scheduler<PreferencesSchedulerData<*>>
    )

    companion object {
        const val LOG_KEY = "BACKUP"
        const val SYNC_HISTORY_PREFIX = "_hs/"

        // Can be called in high frequency (for now) because current implementation uses google
        // cloud project per user so there is no way to hit quota. Later we should implement
        // some kind of adaptive throttling which will increase decrease throttle time based
        // on factors like: live devices, quota limits, etc
        val UPLOAD_THROTTLE = 10.seconds
        val DOWNLOAD_THROTTLE = 60.seconds
        // add to queue may be called frequently
        private val ioScope = CoroutineScope(Dispatchers.IO)

        fun SharedPreferences.logHistoryChanged(path: String, source: BackupUtils.RestoreSource) {
            edit().putLong("${source.syncPrefix}$path", System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * isActive is recommended to be overridden to verifiy if BackupApi is being used. if manager
     * is not set up it won't write sync data.
     * @see Scheduler.Companion.createBackupScheduler
     * @see SharedPreferences.logHistoryChanged
     */
    var isActive: Boolean?
    fun updateApiActiveState() {
        this.isActive = this.isActive()
    }
    fun isActive(): Boolean
    /**
     * Should download data from API and call Context.mergeBackup(incomingData: String). If data
     * does not exist on the api uploadSyncData() is recommended to call. Should be called with
     * overwrite=true when user ads new account so it would accept changes from API
     * @see Context.mergeBackup
     * @see uploadSyncData
     */
    fun downloadSyncData(overwrite: Boolean)

    /**
     * Should upload data to API and call Context.createBackup(loginData: LOGIN_DATA)
     * @see Context.createBackup(loginData: LOGIN_DATA)
     */
    fun uploadSyncData()


    fun Context.createBackup(loginData: LOGIN_DATA)
    fun Context.mergeBackup(incomingData: String, overwrite: Boolean) {
        val newData = DataStore.mapper.readValue<BackupUtils.BackupFile>(incomingData)
        if (overwrite) {
            Log.d(LOG_KEY, "overwriting data")
            restore(newData)

            return
        }

        val keysToUpdate = getKeysToUpdate(getBackup(), newData)
        if (keysToUpdate.isEmpty()) {
            Log.d(LOG_KEY, "remote data is up to date, sync not needed")
            return
        }


        Log.d(LOG_KEY, incomingData)
        restore(newData, keysToUpdate)
    }

    var willQueueSoon: Boolean?
    var uploadJob: Job?
    fun shouldUpdate(changedKey: String, isSettings: Boolean): Boolean
    fun addToQueue(changedKey: String, isSettings: Boolean) {

        if (!shouldUpdate(changedKey, isSettings)) {
            willQueueSoon = false
            Log.d(LOG_KEY, "upload not required, data is same")
            return
        }

        addToQueueNow()
    }
    fun addToQueueNow() {
        if (uploadJob != null && uploadJob!!.isActive) {
            Log.d(LOG_KEY, "upload is canceled, scheduling new")
            uploadJob?.cancel()
        }

        uploadJob = ioScope.launchSafe {
            willQueueSoon = false
            Log.d(LOG_KEY, "upload is running now")
            uploadSyncData()
        }
    }

    fun compareJson(old: String, new: String): JSONComparison {
        var result: JSONCompareResult?

        val executionTime = measureTimeMillis {
            result = try {
                JSONCompare.compareJSON(old, new, JSONCompareMode.NON_EXTENSIBLE)
            } catch (e: Exception) {
                null
            }
        }

        val failed = result?.failed() ?: true
        Log.d(LOG_KEY, "JSON comparison took $executionTime ms, compareFailed=$failed, result=$result")

        return JSONComparison(failed, result)
    }

    fun getKeysToUpdate(
        currentData: BackupUtils.BackupFile,
        newData: BackupUtils.BackupFile
    ): Set<String> {
        val currentSync = getSyncKeys(currentData)
        val newSync = getSyncKeys(newData)

        val changedKeys = newSync.filter {
            val localTimestamp = currentSync[it.key] ?: 0L
            it.value > localTimestamp
        }.keys

        val onlyLocalKeys = currentSync.keys.filter { !newSync.containsKey(it) }
        val missingKeys = getAllMissingKeys(currentData, newData)

        return (missingKeys + onlyLocalKeys + changedKeys).toSet()
    }

    private fun getSyncKeys(data: BackupUtils.BackupFile) =
        data.syncMeta._Long.orEmpty().filter { it.key.startsWith(SYNC_HISTORY_PREFIX) }


    private fun getAllMissingKeys(
        old: BackupUtils.BackupFile,
        new: BackupUtils.BackupFile
    ): List<String> = BackupUtils.RestoreSource
        .values()
        .filter { it != BackupUtils.RestoreSource.SYNC }
        .fold(mutableListOf()) { acc, source ->
            acc.addAll(getMissingKeysPrefixed(source, old, new))
            acc
        }

    private fun getMissingKeysPrefixed(
        restoreSource: BackupUtils.RestoreSource,
        old: BackupUtils.BackupFile,
        new: BackupUtils.BackupFile
    ): List<String> {
        val oldSource = old.getData(restoreSource)
        val newSource = new.getData(restoreSource)
        val prefixToMatch = restoreSource.syncPrefix

        return listOf(
            *getMissing(oldSource._Bool, newSource._Bool),
            *getMissing(oldSource._Long, newSource._Long),
            *getMissing(oldSource._Float, newSource._Float),
            *getMissing(oldSource._Int, newSource._Int),
            *getMissing(oldSource._String, newSource._String),
            *getMissing(oldSource._StringSet, newSource._StringSet),
        ).map {
            prefixToMatch + it
        }
    }


    private fun getMissing(old: Map<String, *>?, new: Map<String, *>?): Array<String> =
        (new.orEmpty().keys - old.orEmpty().keys)
            .toTypedArray()

}
