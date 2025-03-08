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
import androidx.core.content.ContextCompat
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.services.PackageInstallerService
import com.lagradost.cloudstream3.utils.Coroutines.main
import java.io.InputStream

const val INSTALL_ACTION = "ApkInstaller.INSTALL_ACTION"

class ApkInstaller(private val service: PackageInstallerService) {
    companion object {
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
        Finished,
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
                    // Use the new getParcelableExtra method for API 33+ and fallback for older APIs
                    val userAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
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
            session.openWrite(context.packageName, 0, size).use { outputStream ->
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
            val installIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(service, PackageInstallerService::class.java).setAction(INSTALL_ACTION)
            } else Intent(INSTALL_ACTION)
            val installFlags = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> PendingIntent.FLAG_MUTABLE
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> PendingIntent.FLAG_IMMUTABLE
                else -> 0
            }
            val intentSender = PendingIntent.getBroadcast(
                service, activeSession, installIntent, installFlags
            ).intentSender
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.packageManager.canRequestPackageInstalls()
            ) {
                delayedInstaller = DelayedInstaller(session, intentSender)
                main {
                    Toast.makeText(context, R.string.delayed_update_notice, Toast.LENGTH_LONG).show()
                }
            } else {
                installProgressStatus.invoke(InstallProgressStatus.Installing)
                session.commit(intentSender)
            }
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
        registerInstallActionReceiver()
    }

    private fun registerInstallActionReceiver() {
        if (!isReceiverRegistered) {
            val intentFilter = IntentFilter().apply {
                addAction(INSTALL_ACTION)
            }
            Log.d(TAG, "Registering install action event receiver")
            context?.let {
                ContextCompat.registerReceiver(
                    it,
                    installActionReceiver,
                    intentFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
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