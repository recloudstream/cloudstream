package com.lagradost.cloudstream3.syncproviders

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.lagradost.cloudstream3.mvvm.launchSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.time.Duration.Companion.seconds

interface BackupAPI<LOGIN_DATA> {
    companion object {
        val UPLOAD_THROTTLE = 10.seconds
        val DOWNLOAD_THROTTLE = 60.seconds

        fun createBackupScheduler() = Scheduler<Pair<String, Boolean>>(
            UPLOAD_THROTTLE.inWholeMilliseconds
        ) { input ->
            if (input == null) {
                throw IllegalStateException()
            }

            AccountManager.BackupApis.forEach { it.addToQueue(input.first, input.second) }
        }

        fun SharedPreferences.attachListener(isSettings: Boolean = true): Pair<SharedPreferences, Scheduler<Pair<String, Boolean>>> {
            val scheduler = createBackupScheduler()
            registerOnSharedPreferenceChangeListener { _, key ->
                scheduler.work(Pair(key, isSettings))
            }

            return Pair(
                this,
                scheduler
            )
        }
    }

    fun downloadSyncData()
    fun uploadSyncData()
    fun shouldUpdate(changedKey: String, isSettings: Boolean): Boolean

    fun Context.mergeBackup(incomingData: String)
    fun Context.createBackup(loginData: LOGIN_DATA)

    var uploadJob: Job?
    fun addToQueue(changedKey: String, isSettings: Boolean) {
        if (!shouldUpdate(changedKey, isSettings)) {
            Log.d("SYNC_API", "upload not required, data is same")
            return
        }

        if (uploadJob != null && uploadJob!!.isActive) {
            Log.d("SYNC_API", "upload is canceled, scheduling new")
            uploadJob?.cancel()
        }

        // we should ensure job will before app is closed
        uploadJob = CoroutineScope(Dispatchers.IO).launchSafe {
            Log.d("SYNC_API", "upload is running now")
            uploadSyncData()
        }
    }


    class Scheduler<INPUT>(
        private val throttleTimeMs: Long,
        private val onWork: (INPUT?) -> Unit
    ) {
        private companion object {
            var SCHEDULER_ID = 1
        }

        private val id = SCHEDULER_ID++
        private val handler = Handler(Looper.getMainLooper())
        private var runnable: Runnable? = null

        fun work(input: INPUT? = null) {
            Log.d("SYNC_API", "[$id] wants to schedule")
            throttle(input)
        }

        fun workNow(input: INPUT? = null) {
            Log.d("SYNC_API", "[$id] runs immediate")
            stop()
            onWork(input)
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
                Log.d("SYNC_API", "[$id] schedule success")
                onWork(input)
            }
            handler.postDelayed(runnable!!, throttleTimeMs)
        }
    }
}
