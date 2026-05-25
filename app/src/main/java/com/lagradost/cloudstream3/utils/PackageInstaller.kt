package com.lagradost.cloudstream3.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.services.PackageInstallerService
import com.lagradost.cloudstream3.utils.Coroutines.main
import java.io.InputStream

const val INSTALL_ACTION = "ApkInstaller.INSTALL_ACTION"

class ApkInstaller(private val service: PackageInstallerService) {

    companion object {
        /**
         * Used for postponed installations
         **/
        var delayedInstaller: DelayedInstaller? = null
        private var isReceiverRegistered = false
        private const val TAG = "ApkInstaller"
    }

    inner class DelayedInstaller(
        private val session: PackageInstaller.Session,
        private val intent: IntentSender
    ) {
        fun startInstallation(): Boolean {
            return try {
                session.commit(intent)
                true
            } catch (e: Exception) {
                logError(e)
                false
            }.also { delayedInstaller = null }
        }
    }

    private val packageInstaller = service.packageManager.packageInstaller

    enum class InstallProgressStatus {
        Preparing,
        Downloading,
        Installing,
        Failed,
    }

    private val installActionReceiver = object : BroadcastReceiver() {
        @SuppressLint("UnsafeIntentLaunch")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
            )) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val userAction = intent.getSafeParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    userAction?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(userAction)
                }
            }
        }
    }

    fun installApk(
        context: Context,
        inputStream: InputStream,
        size: Long,
        installProgress: (bytesRead: Int) -> Unit,
        installProgressStatus: (InstallProgressStatus) -> Unit
    ) {
        installProgressStatus.invoke(InstallProgressStatus.Preparing)
        var activeSession: Int? = null

        try {
            val installParams =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                installParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }

            activeSession = packageInstaller.createSession(installParams)
            installParams.setSize(size)

            val session = packageInstaller.openSession(activeSession)
            installProgressStatus.invoke(InstallProgressStatus.Downloading)

            session.openWrite(context.packageName, 0, size)
                .use { outputStream ->
                    val buffer = ByteArray(4 * 1024)
                    var bytesRead = inputStream.read(buffer)

                    while (bytesRead >= 0) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesRead = inputStream.read(buffer)
                        installProgress.invoke(bytesRead)
                    }

                    session.fsync(outputStream)
                    inputStream.close()
                }

            // We must set the package for implicit broadcasts on modern Android
            val installIntent = Intent(INSTALL_ACTION).setPackage(service.packageName)

            val installFlags = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                else -> PendingIntent.FLAG_UPDATE_CURRENT
            }

            val intentSender = PendingIntent.getBroadcast(
                service, activeSession, installIntent, installFlags
            ).intentSender

            installProgressStatus.invoke(InstallProgressStatus.Installing)
            session.commit(intentSender)
        } catch (e: Exception) {
            logError(e)

            service.unregisterReceiver(installActionReceiver)
            installProgressStatus.invoke(InstallProgressStatus.Failed)

            activeSession?.let { sessionId ->
                packageInstaller.abandonSession(sessionId)
            }
        }
    }

    init {
        // Might be dangerous
        registerInstallActionReceiver()
    }

    private fun registerInstallActionReceiver() {
        if (!isReceiverRegistered) {
            val intentFilter = IntentFilter().apply {
                addAction(INSTALL_ACTION)
            }
            Log.d(TAG, "Registering install action event receiver")
            context?.registerBroadcastReceiver(installActionReceiver, intentFilter)
            isReceiverRegistered = true
        }
    }

    fun unregisterInstallActionReceiver() {
        if (isReceiverRegistered) {
            Log.d(TAG, "Unregistering install action event receiver")
            try {
                context?.unregisterReceiver(installActionReceiver)
            } catch (e: Exception) {
                logError(e)
            }
            isReceiverRegistered = false
        }
    }
}
