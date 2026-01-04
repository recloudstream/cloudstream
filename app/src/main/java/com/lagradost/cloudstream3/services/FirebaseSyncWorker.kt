package com.lagradost.cloudstream3.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class FirebaseSyncWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val forceUpload = inputData.getBoolean("force_upload", false)
            
            Log.d("FirebaseSyncWorker", "Executing sync (force=$forceUpload)")
            
            // We call the core upload logic here.
            // uploadSync is now a suspend function, so it fits perfectly here.
            val success = SyncRepoService.uploadSync(applicationContext, forceUpload = forceUpload)
            
            if (success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e("FirebaseSyncWorker", "Error in sync worker", e)
            Result.retry()
        }
    }
}
