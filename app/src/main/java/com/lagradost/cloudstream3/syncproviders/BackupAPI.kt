package com.lagradost.cloudstream3.syncproviders

import android.content.Context
import com.lagradost.cloudstream3.mvvm.launchSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

interface BackupAPI<LOGIN_DATA> {
    private companion object {
        val DEBOUNCE_TIME_MS = 15.seconds
    }

    fun downloadSyncData()
    fun uploadSyncData()
    fun shouldUpdate(): Boolean

    fun Context.mergeBackup(incomingData: String)
    fun Context.createBackup(loginData: LOGIN_DATA)

    var uploadJob: Job?
    fun addToQueue() {
        if (!shouldUpdate()) {
            return
        }

        uploadJob?.cancel()
        uploadJob = CoroutineScope(Dispatchers.IO).launchSafe {
            delay(DEBOUNCE_TIME_MS)
            uploadSyncData()
        }
    }

}