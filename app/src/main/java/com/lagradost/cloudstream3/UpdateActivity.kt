package com.lagradost.cloudstream3

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class UpdateActivity : AppCompatActivity() {
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Clear any existing notification (optional)
        NotificationHelper.clearUpdateNotification(this)

        uiScope.launch {
            // Replace this with your real update-starting code (start a service or WorkManager)
            withContext(Dispatchers.IO) {
                runUpdateNow()
            }

            // Close the app UI after initiating update
            finishAffinity()
        }
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun runUpdateNow() {
        // TODO: Replace with actual update logic (start your UpdateService or UpdateManager)
        Thread.sleep(200)
    }
}
