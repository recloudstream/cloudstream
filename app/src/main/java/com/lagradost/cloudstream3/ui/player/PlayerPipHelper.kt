package com.lagradost.cloudstream3.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.mvvm.normalSafeApiCall
import kotlin.math.roundToInt

class PlayerPipHelper {
    companion object {
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

        @RequiresApi(Build.VERSION_CODES.O)
        fun updatePIPModeActions(activity: Activity, isPlaying: Boolean, aspectRatio: Rational?) {
            val actions: ArrayList<RemoteAction> = ArrayList()
            actions.add(
                getRemoteAction(
                    activity,
                    R.drawable.go_back_30,
                    R.string.go_back_30,
                    CSPlayerEvent.SeekBack
                )
            )

            if (isPlaying) {
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

            // Nessecary to prevent crashing.
            val mixAspectRatio = 0.41841f // ~1/2.39
            val maxAspectRatio = 2.39f // widescreen standard
            val ratioAccuracy = 100000 // To convert the float to int

            // java.lang.IllegalArgumentException: setPictureInPictureParams: Aspect ratio is too extreme (must be between 0.418410 and 2.390000)
            val fixedRational =
                aspectRatio?.toFloat()?.coerceIn(mixAspectRatio, maxAspectRatio)?.let {
                    Rational((it * ratioAccuracy).roundToInt(), ratioAccuracy)
                }

            normalSafeApiCall {
                activity.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                setSeamlessResizeEnabled(true)
                                setAutoEnterEnabled(isPlaying)
                            }
                        }
                        .setAspectRatio(fixedRational)
                        .setActions(actions)
                        .build()
                )
            }
        }
    }
}