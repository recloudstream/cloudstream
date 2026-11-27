package com.lagradost.cloudstream3.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.lagradost.cloudstream3.utils.getSafeParcelableExtra

/** https://medium.com/@solrudev/painless-building-of-an-android-package-installer-app-d5a09b5df432 */
class PackageInstallerStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                intent.getSafeParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { userAction->
                    userAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(userAction)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // do something on success
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                println("PackageInstallerStatusReceiver: status=$status, message=$message")
            }
        }
    }
}