package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.services.PackageInstallerService

class UpdateProgressActivity : Activity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var progressPercentageText: TextView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.update_progress_dialog)
        progressBar = findViewById(R.id.update_progress_bar)
        progressPercentageText = findViewById(R.id.update_progress_percentage_text)
        statusText = findViewById(R.id.update_status_text)
        updateProgress(0)
    }

    private fun updateProgress(progress: Int) {
        progressBar.progress = progress
        progressPercentageText.text = getString(R.string.update_progress_percentage, progress)
    }

    fun updateStatus(status: String) {
        statusText.text = status
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val progress = intent.getFloatExtra("progress", 0f)
            updateProgress((progress * 100).toInt())
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val statusString = intent.getStringExtra("status")
            val status = statusString?.let { ApkInstaller.InstallProgressStatus.valueOf(it) }
            status?.let {
                updateStatus(getString(getStatusStringResource(it)))
            }
        }
    }

    private fun getStatusStringResource(status: ApkInstaller.InstallProgressStatus): Int {
        return when (status) {
            ApkInstaller.InstallProgressStatus.Downloading -> R.string.update_notification_downloading
            ApkInstaller.InstallProgressStatus.Installing -> R.string.install_update
            ApkInstaller.InstallProgressStatus.Preparing -> R.string.install_update
            ApkInstaller.InstallProgressStatus.Finished -> R.string.download_done
            ApkInstaller.InstallProgressStatus.Failed -> R.string.update_notification_failed
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStart() {
        super.onStart()
        registerReceiver(
            progressReceiver,
            IntentFilter(PackageInstallerService.PROGRESS_UPDATE_ACTION),
            Context.RECEIVER_NOT_EXPORTED
        )
        registerReceiver(
            statusReceiver,
            IntentFilter(PackageInstallerService.STATUS_UPDATE_ACTION),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(progressReceiver)
        unregisterReceiver(statusReceiver)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, UpdateProgressActivity::class.java)
        }
    }
}