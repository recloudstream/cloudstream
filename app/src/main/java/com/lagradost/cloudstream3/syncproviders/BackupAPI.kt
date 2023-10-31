package com.lagradost.cloudstream3.syncproviders

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.BackupUtils.getBackup
import com.lagradost.cloudstream3.utils.BackupUtils.restore
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore
import com.lagradost.cloudstream3.utils.Scheduler
import kotlinx.coroutines.Job
import org.skyscreamer.jsonassert.JSONCompare
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.JSONCompareResult
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.minutes

interface RemoteFile {
    class Error(val message: String? = null, val throwable: Throwable? = null) : RemoteFile
    class NotFound : RemoteFile
    class Success(val remoteData: String) : RemoteFile
}

/**
 * Safe wrapper for the backup api to be used outside the class without fear
 * of causing crashes.
 */
interface SafeBackupAPI {
    /**
     * @return true if the service is ready for uploads and downloads.
     * This includes a login check.
     */
    suspend fun getIsReady(): Boolean
    suspend fun scheduleDownload(runNow: Boolean = false)
    fun getIsLoggedIn(): Boolean
    fun scheduleUpload()
    fun scheduleUpload(changedKey: String, isSettings: Boolean)

    /**
     * Warns the service that an upload is incoming. Used to prevent simultaneous download and upload.
     */
    fun setIsUploadingSoon()
}

/**
 * Easy interface to implement remote sync by only implementing the download and upload part of the service.
 * @see BackupAPI for how the methods get used
 */
interface IBackupAPI<LOGIN_DATA> {
    /**
     * Gets the user login info for uploading and downloading the backup.
     * If null no backup or download will be run.
     */
    suspend fun getLoginData(): LOGIN_DATA?

    /**
     * Additional check if the backup operation should be run.
     * Return false here to deny any backup work.
     */
    suspend fun isReady(): Boolean = true

    /**
     * Get the backup file as a string from the remote storage.
     * @see RemoteFile.Success
     * @see RemoteFile.Error
     * @see RemoteFile.NotFound
     */
    suspend fun getRemoteFile(context: Context, loginData: LOGIN_DATA): RemoteFile

    suspend fun uploadFile(
        context: Context,
        backupJson: String,
        loginData: LOGIN_DATA
    )
}

/**
 * Helper class for the IBackupAPI which implements a scheduler, logging and checks.
 * This makes the individual backup service implementations easier and more lightweight.
 */
abstract class BackupAPI<LOGIN_DATA>(defIndex: Int) : IBackupAPI<LOGIN_DATA>,
    AccountManager(defIndex), SafeBackupAPI {
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

        // Can be called in high frequency (for now) because current implementation uses google
        // cloud project per user so there is no way to hit quota. Later we should implement
        // some kind of adaptive throttling which will increase decrease throttle time based
        // on factors like: live devices, quota limits, etc
        val UPLOAD_THROTTLE = 5.minutes
        val DOWNLOAD_THROTTLE = 5.minutes

        const val SYNC_HISTORY_PREFIX = "_hs/"

        fun SharedPreferences.logHistoryChanged(
            path: String,
            source: BackupUtils.RestoreSource
        ) {
            edit().putLong("${source.syncPrefix}$path", System.currentTimeMillis()).apply()
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
            Log.d(
                LOG_KEY,
                "JSON comparison took $executionTime ms, compareFailed=$failed, result=$result"
            )

            return JSONComparison(failed, result)
        }

        private fun getSyncKeys(data: BackupUtils.BackupFile) =
            data.syncMeta._Long.orEmpty().filter { it.key.startsWith(SYNC_HISTORY_PREFIX) }

        /**
         * Merges the backup data with the app data.
         * @param overwrite if true it overwrites all data same as restoring from a backup.
         * if false it only updates outdated keys. Should be true on first initialization.
         */
        private fun mergeBackup(context: Context, incomingData: String, overwrite: Boolean) {
            val newData = DataStore.mapper.readValue<BackupUtils.BackupFile>(incomingData)
            if (overwrite) {
                Log.d(LOG_KEY, "overwriting data")
                context.restore(newData)

                return
            }

            val keysToUpdate = getKeysToUpdate(getBackup(context), newData)
            if (keysToUpdate.isEmpty()) {
                Log.d(LOG_KEY, "remote data is up to date, sync not needed")
                return
            }

            Log.d(LOG_KEY, incomingData)
            context.restore(newData, keysToUpdate)
        }

        private fun getKeysToUpdate(
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
            (new.orEmpty().keys - old.orEmpty().keys).toTypedArray()
    }

    /**
     * Cached last uploaded json file, to prevent unnecessary uploads.
     */
    private var lastBackupJson: String? = null

    /**
     * Continually tries to download from the service.
     */
    private val continuousDownloader = Scheduler<Boolean>(
        DOWNLOAD_THROTTLE.inWholeMilliseconds,
        onWork = { overwrite ->
            if (uploadJob?.isActive == true || willUploadSoon == true) {
                uploadJob?.invokeOnCompletion {
                    Log.d(LOG_KEY, "${this.name}: upload is running, reschedule download")
                    ioSafe {
                        scheduleDownload(false, overwrite)
                    }
                }
            } else {
                Log.d(LOG_KEY, "${this.name}: downloadSyncData will run")
                val context = AcraApplication.context ?: return@Scheduler
                mergeRemoteBackup(context, overwrite)
            }
        }
    )

    suspend fun scheduleDownload(runNow: Boolean = false, overwrite: Boolean = false) {
        if (runNow) {
            continuousDownloader.workNow(overwrite)
        } else {
            continuousDownloader.work(overwrite)
        }
    }

    private var willUploadSoon: Boolean? = null
    private var uploadJob: Job? = null

    private fun shouldUploadBackup(): Boolean {
        val ctx = AcraApplication.context ?: return false

        val newBackup = getBackup(ctx).toJson()
        return compareJson(lastBackupJson ?: "", newBackup).failed
    }

    override fun scheduleUpload() {
        normalSafeApiCall {
            if (!shouldUploadBackup()) {
                willUploadSoon = false
                Log.d(LOG_KEY, "${this.name}: upload not required, data is same")
                return@normalSafeApiCall
            }

            upload()
        }
    }

    // changedKey and isSettings is currently unused, might be useful for more efficient update checker.
    override fun scheduleUpload(changedKey: String, isSettings: Boolean) {
        normalSafeApiCall {
            scheduleUpload()
        }
    }

    private fun upload() {
        if (uploadJob != null && uploadJob!!.isActive) {
            Log.d(LOG_KEY, "${this.name}: upload is canceled, scheduling new")
            uploadJob?.cancel()
        }

        val context = AcraApplication.context ?: return
        uploadJob = ioSafe {
            willUploadSoon = false
            Log.d(LOG_KEY, "$name: uploadBackup is launched")
            uploadBackup(context)
        }
    }

    /**
     * Uploads the app data to the service if the api is ready and has login data.
     * @see isReady
     * @see getLoginData
     */
    private suspend fun uploadBackup(context: Context) {
        val isReady = isReady()
        if (!isReady) {
            Log.d(LOG_KEY, "${this.name}: uploadBackup is not ready yet")
            return
        }

        val loginData = getLoginData()
        if (loginData == null) {
            Log.d(LOG_KEY, "${this.name}: uploadBackup did not get loginData")
            return
        }

        val backupFile = getBackup(context).toJson()
        lastBackupJson = backupFile
        Log.d(LOG_KEY, "${this.name}: uploadFile is now running")
        uploadFile(context, backupFile, loginData)
        Log.d(LOG_KEY, "${this.name}: uploadFile finished")
    }

    /**
     * Gets the remote backup and properly handle any errors, including uploading the backup
     * if no remote file was found.
     */
    private suspend fun getRemoteBackup(context: Context): String? {
        if (!isReady()) {
            Log.d(LOG_KEY, "${this.name}: getRemoteBackup is not ready yet")
            return null
        }

        val loginData = getLoginData()
        if (loginData == null) {
            Log.d(LOG_KEY, "${this.name}: getRemoteBackup did not get loginData")
            return null
        }

        return when (val remoteFile = getRemoteFile(context, loginData)) {
            is RemoteFile.NotFound -> {
                Log.d(LOG_KEY, "${this.name}: Remote file not found. Uploading file.")
                uploadBackup(context)
                null
            }

            is RemoteFile.Success -> {
                Log.d(LOG_KEY, "${this.name}: Remote file found.")
                remoteFile.remoteData
            }

            is RemoteFile.Error -> {
                Log.d(
                    LOG_KEY,
                    "${this.name}: getRemoteFile failed with message: ${remoteFile.message}."
                )
                remoteFile.throwable?.let { error -> logError(error) }
                null
            }

            else -> {
                val message = "${this.name}: Unexpected remote file!"
                debugException { message }
                Log.d(LOG_KEY, message)
                null
            }
        }
    }

    /**
     * Gets the remote backup and merges it with the local data.
     * Also saves a cached to prevent unnecessary uploading.
     * @see getRemoteBackup
     * @see mergeBackup
     */
    private suspend fun mergeRemoteBackup(context: Context, overwrite: Boolean) {
        val remoteData = getRemoteBackup(context) ?: return
        lastBackupJson = remoteData
        mergeBackup(context, remoteData, overwrite)
    }


    // ------ SafeBackupAPI wrappers ------
    override suspend fun getIsReady(): Boolean {
        return suspendSafeApiCall {
            isReady()
        } ?: false
    }

    override fun getIsLoggedIn(): Boolean {
        return normalSafeApiCall { loginInfo() } != null
    }

    override fun setIsUploadingSoon() {
        willUploadSoon = true
    }

    override suspend fun scheduleDownload(runNow: Boolean) {
        suspendSafeApiCall {
            scheduleDownload(runNow, false)
        }
    }
}