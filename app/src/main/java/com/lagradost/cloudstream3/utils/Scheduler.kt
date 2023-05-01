package com.lagradost.cloudstream3.utils

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.BackupAPI
import com.lagradost.cloudstream3.syncproviders.BackupAPI.Companion.logHistoryChanged
import com.lagradost.cloudstream3.ui.player.PLAYBACK_SPEED_KEY
import com.lagradost.cloudstream3.ui.player.RESIZE_MODE_KEY

class Scheduler<INPUT>(
    private val throttleTimeMs: Long,
    private val onWork: (INPUT?) -> Unit,
    private val canWork: ((INPUT?) -> Boolean)? = null
) {
    companion object {
        var SCHEDULER_ID = 1

        fun createBackupScheduler() = Scheduler<BackupAPI.PreferencesSchedulerData>(
            BackupAPI.UPLOAD_THROTTLE.inWholeMilliseconds,
            onWork = { input ->
                if (input == null) {
                    throw IllegalStateException()
                }

                AccountManager.BackupApis.forEach {
                    it.addToQueue(
                        input.storeKey,
                        input.isSettings
                    )
                }
            },
            canWork = { input ->
                if (input == null) {
                    throw IllegalStateException()
                }

                val invalidKeys = listOf(
                    VideoDownloadManager.KEY_DOWNLOAD_INFO,
                    PLAYBACK_SPEED_KEY,
                    RESIZE_MODE_KEY
                )

                return@Scheduler !invalidKeys.contains(input.storeKey)
            }
        )

        // Common usage is `val settingsManager = PreferenceManager.getDefaultSharedPreferences(this).attachListener().self`
        // which means it is mostly used for settings preferences, therefore we use `isSettings: Boolean = true`, be careful
        // if you need to directly access `context.getSharedPreferences` (without using DataStore) and dont forget to turn it off
        fun SharedPreferences.attachBackupListener(
            isSettings: Boolean = true,
            syncPrefs: SharedPreferences
        ): BackupAPI.SharedPreferencesWithListener {
            val scheduler = createBackupScheduler()
            registerOnSharedPreferenceChangeListener { _, storeKey ->
                val success =
                    scheduler.work(
                        BackupAPI.PreferencesSchedulerData(
                            syncPrefs,
                            storeKey,
                            isSettings
                        )
                    )

                if (success) {
                    syncPrefs.logHistoryChanged(storeKey, BackupUtils.RestoreSource.SETTINGS)
                }
            }

            return BackupAPI.SharedPreferencesWithListener(this, scheduler)
        }

        fun SharedPreferences.attachBackupListener(syncPrefs: SharedPreferences): BackupAPI.SharedPreferencesWithListener {
            return attachBackupListener(true, syncPrefs)
        }
    }

    private val id = SCHEDULER_ID++
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    fun work(input: INPUT? = null): Boolean {
        if (canWork?.invoke(input) == false) {
            // Log.d(LOG_KEY, "[$id] cannot schedule [${input}]")
            return false
        }

        Log.d(BackupAPI.LOG_KEY, "[$id] wants to schedule [${input}]")
        throttle(input)

        return true
    }

    fun workNow(input: INPUT? = null): Boolean {
        if (canWork?.invoke(input) == false) {
            Log.d(BackupAPI.LOG_KEY, "[$id] cannot run immediate [${input}]")
            return false
        }


        Log.d(BackupAPI.LOG_KEY, "[$id] runs immediate [${input}]")
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

    private fun throttle(input: INPUT?) {
        stop()

        runnable = Runnable {
            Log.d(BackupAPI.LOG_KEY, "[$id] schedule success")
            onWork(input)
        }
        handler.postDelayed(runnable!!, throttleTimeMs)
    }
}