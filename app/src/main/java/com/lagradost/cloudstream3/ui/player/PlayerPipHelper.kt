package com.lagradost.cloudstream3.ui.player

import android.app.Activity
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import kotlin.math.roundToInt

object PlayerPipHelper {
    /** Is pip (Player in Player) supported, and enabled? */
    fun Context.isPIPPossible() : Boolean {
        return try {
            this.hasPIPEnabled() && this.hasPIPFeature()
        } catch (t : Throwable) {
            // While both hasPIPEnabled and hasPIPFeature should never throw, this catches it just in case
            logError(t)
            false
        }
    }

    /** Is pip enabled in app settings? */
    private fun Context.hasPIPEnabled(): Boolean {
        return try {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            settingsManager?.getBoolean(
                getString(R.string.pip_enabled_key),
                true
            ) ?: true
        } catch (e: Exception) {
            logError(e)
            false
        }
    }


    /**
     * Is pip supported by the OS?
     *
     * Source:
     * https://stackoverflow.com/questions/52594181/how-to-know-if-user-has-disabled-picture-in-picture-feature-permission
     * https://developer.android.com/guide/topics/ui/picture-in-picture
     * */
    private fun Context.hasPIPFeature(): Boolean =
        // OS Support
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                // Might have the feature, but OS blocked due to power drain
                this.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
                // Might have been disabled by the user
                this.hasPIPPermission()

    /** Is pip enabled in the OS settings? */
    private fun Context.hasPIPPermission(): Boolean {
        val appOps =
            getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                android.os.Process.myUid(),
                packageName
            ) == AppOpsManager.MODE_ALLOWED
        } else true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getPen(activity: Activity, code: Int): PendingIntent {
        return PendingIntent.getBroadcast(
            activity,
            code,
            Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, code),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getRemoteAction(
        activity: Activity,
        id: Int,
        @StringRes title: Int,
        event: CSPlayerEvent
    ): RemoteAction {
        val text = activity.getString(title)
        return RemoteAction(
            Icon.createWithResource(activity, id),
            text,
            text,
            getPen(activity, event.value)
        )
    }

    fun updatePIPModeActions(
        activity: Activity?,
        status: CSPlayerLoading,
        pipEnabled: Boolean,
        aspectRatio: Rational?
    ) {
        // Is it even desired to enter pip mode right now if we ignore all settings?
        // This does not check for isPIPPossible as that is deferred to later
        val isPipDesired = when (status) {
            CSPlayerLoading.IsBuffering, CSPlayerLoading.IsPlaying -> pipEnabled
            else -> false
        }

        // On lower api ver setPictureInPictureParams is not supported,
        // so we enter pip manually in onUserLeaveHint
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            CommonActivity.isPipDesired = isPipDesired
            return
        }

        if(activity == null) return

        val actions: ArrayList<RemoteAction> = ArrayList()
        actions.add(
            getRemoteAction(
                activity,
                R.drawable.baseline_headphones_24,
                R.string.audio_singluar,
                CSPlayerEvent.PlayAsAudio
            )
        )
        /*actions.add(
            getRemoteAction(
                activity,
                R.drawable.go_back_30,
                R.string.go_back_30,
                CSPlayerEvent.SeekBack
            )
        )*/

        if (status == CSPlayerLoading.IsPlaying) {
            actions.add(
                getRemoteAction(
                    activity,
                    R.drawable.netflix_pause,
                    R.string.pause,
                    CSPlayerEvent.Pause
                )
            )
        } else {
            actions.add(
                getRemoteAction(
                    activity,
                    R.drawable.ic_baseline_play_arrow_24,
                    R.string.pause,
                    CSPlayerEvent.Play
                )
            )
        }

        actions.add(
            getRemoteAction(
                activity,
                R.drawable.go_forward_30,
                R.string.go_forward_30,
                CSPlayerEvent.SeekForward
            )
        )

        // Necessary to prevent crashing.
        val mixAspectRatio = 0.41841f // ~1/2.39
        val maxAspectRatio = 2.39f // widescreen standard
        val ratioAccuracy = 100000 // To convert the float to int

        // java.lang.IllegalArgumentException: setPictureInPictureParams: Aspect ratio is too extreme
        // (must be between 0.418410 and 2.390000)
        val fixedRational =
            aspectRatio?.toFloat()?.coerceIn(mixAspectRatio, maxAspectRatio)?.let {
                Rational((it * ratioAccuracy).roundToInt(), ratioAccuracy)
            }

        safe {
            activity.setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setSeamlessResizeEnabled(true)
                            setAutoEnterEnabled(isPipDesired && activity.isPIPPossible())
                        } else {
                            // We enter pip manually in onUserLeaveHint as the smooth transition
                            // is not supported yet
                            CommonActivity.isPipDesired = isPipDesired
                        }
                    }
                    .setAspectRatio(fixedRational)
                    .setActions(actions)
                    .build()
            )
        }
    }

}
