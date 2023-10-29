package com.lagradost.cloudstream3.utils

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.BackupAPI
import com.lagradost.cloudstream3.syncproviders.IBackupAPI
import com.lagradost.cloudstream3.syncproviders.IBackupAPI.Companion.logHistoryChanged
import com.lagradost.cloudstream3.ui.home.HOME_BOOKMARK_VALUE_LIST
import com.lagradost.cloudstream3.ui.player.PLAYBACK_SPEED_KEY
import com.lagradost.cloudstream3.ui.player.RESIZE_MODE_KEY
import com.lagradost.cloudstream3.utils.BackupUtils.nonTransferableKeys
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.runBlocking

class Scheduler<INPUT>(
    private val throttleTimeMs: Long,
    private val onWork: suspend (INPUT) -> Unit,
    private val beforeWork: (suspend (INPUT?) -> Unit)? = null,
    private val canWork: (suspend (INPUT) -> Boolean)? = null
) {
    companion object {
        var SCHEDULER_ID = 1

        // these will not run upload scheduler, however only `nonTransferableKeys` are not stored
        private val invalidUploadTriggerKeys = listOf(
            *nonTransferableKeys.toTypedArray(),
            VideoDownloadManager.KEY_DOWNLOAD_INFO,
            DOWNLOAD_HEADER_CACHE,
            PLAYBACK_SPEED_KEY,
            HOME_BOOKMARK_VALUE_LIST,
            RESIZE_MODE_KEY,
        )
        private val invalidUploadTriggerKeysRegex = listOf(
            // These trigger automatically every time a show is opened, way too often.
            Regex("""^\d+/$RESULT_SEASON/"""),
            Regex("""^\d+/$RESULT_EPISODE/"""),
            Regex("""^\d+/$RESULT_DUB/"""),
        )

        fun createBackupScheduler() = Scheduler<IBackupAPI.PreferencesSchedulerData<*>>(
            BackupAPI.UPLOAD_THROTTLE.inWholeMilliseconds,
            onWork = { input ->
                AccountManager.BackupApis.forEach { api ->
                    api.scheduleUpload(
                        input.storeKey,
                        input.source == BackupUtils.RestoreSource.SETTINGS
                    )
                }
            },
            beforeWork = { _ ->
                AccountManager.BackupApis.filter { api ->
                    api.isReady()
                }.forEach {
                    it.willUploadSoon = true
                }
            },
            canWork = { input ->
                val hasSomeActiveManagers = AccountManager.BackupApis.any { it.isReady() }
                if (!hasSomeActiveManagers) {
                    return@Scheduler false
                }

                val valueDidNotChange = input.oldValue == input.newValue
                if (valueDidNotChange) {
                    return@Scheduler false
                }

                // Do not sync account preferences
                val isAccountKey = AccountManager.accountManagers.any {
                    input.storeKey.startsWith("${it.accountId}/")
                }
                if (isAccountKey) {
                    return@Scheduler false
                }

                val hasInvalidKey = invalidUploadTriggerKeys.any { key ->
                    input.storeKey.startsWith(key)
                } || invalidUploadTriggerKeysRegex.any { keyRegex ->
                    input.storeKey.contains(keyRegex)
                }

                if (hasInvalidKey) {
                    return@Scheduler false
                }

                input.syncPrefs.logHistoryChanged(input.storeKey, input.source)
                return@Scheduler true
            }
        )

        // Common usage is `val settingsManager = PreferenceManager.getDefaultSharedPreferences(this).attachListener().self`
        // which means it is mostly used for settings preferences, therefore we use `isSettings: Boolean = true`, be careful
        // if you need to directly access `context.getSharedPreferences` (without using DataStore) and dont forget to turn it off
        fun SharedPreferences.attachBackupListener(
            source: BackupUtils.RestoreSource = BackupUtils.RestoreSource.SETTINGS,
            syncPrefs: SharedPreferences
        ): IBackupAPI.SharedPreferencesWithListener {
            val scheduler = createBackupScheduler()

            var lastValue = all
            registerOnSharedPreferenceChangeListener { sharedPreferences, storeKey ->
                ioSafe {
                    scheduler.work(
                        IBackupAPI.PreferencesSchedulerData(
                            syncPrefs,
                            storeKey,
                            lastValue[storeKey],
                            sharedPreferences.all[storeKey],
                            source
                        )
                    )
                }
                lastValue = sharedPreferences.all
            }

            return IBackupAPI.SharedPreferencesWithListener(this, scheduler)
        }

        fun SharedPreferences.attachBackupListener(syncPrefs: SharedPreferences): IBackupAPI.SharedPreferencesWithListener {
            return attachBackupListener(BackupUtils.RestoreSource.SETTINGS, syncPrefs)
        }
    }

    private val id = SCHEDULER_ID++
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    suspend fun work(input: INPUT): Boolean {
        if (canWork?.invoke(input) == false) {
            // Log.d(LOG_KEY, "[$id] cannot schedule [${input}]")
            return false
        }

        Log.d(BackupAPI.LOG_KEY, "[$id] wants to schedule [${input}]")
        beforeWork?.invoke(input)
        throttle(input)

        return true
    }

    suspend fun workNow(input: INPUT): Boolean {
        if (canWork?.invoke(input) == false) {
            Log.d(BackupAPI.LOG_KEY, "[$id] cannot run immediate [${input}]")
            return false
        }


        Log.d(BackupAPI.LOG_KEY, "[$id] runs immediate [${input}]")
        beforeWork?.invoke(input)
        stop()
        onWork(input)

        return true
    }

    fun stop() {
        runnable?.let {
            handler.removeCallbacks(it)
            runnable = null
        }
    }

    /**
     * Prevents spamming the service by only allowing one job every throttleTimeMs
     * @see throttleTimeMs
     */
    private suspend fun throttle(input: INPUT) {
        stop()

        runnable = Runnable {
            Log.d(BackupAPI.LOG_KEY, "[$id] schedule success")
            runBlocking {
                onWork(input)
            }
        }.also { run ->
            handler.postDelayed(run, throttleTimeMs)
        }
    }
}