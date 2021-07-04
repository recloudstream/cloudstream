package com.lagradost.cloudstream3.utils

import android.app.IntentService
import android.content.Intent

class VideoDownloadService : IntentService("DownloadService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val id = intent.getIntExtra("id", -1)
            val type = intent.getStringExtra("type")
            if (id != -1 && type != null) {

            }
        }
    }
}