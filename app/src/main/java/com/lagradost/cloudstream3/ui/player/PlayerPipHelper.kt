package com.lagradost.cloudstream3.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import com.lagradost.cloudstream3.R

class PlayerPipHelper {
    companion object {
        private fun getPen(activity: Activity, code: Int): PendingIntent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getBroadcast(
                    activity,
                    code,
                    Intent("media_control").putExtra("control_type", code),
                    PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getBroadcast(
                    activity,
                    code,
                    Intent("media_control").putExtra("control_type", code),
                    0
                )
            }
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
        fun updatePIPModeActions(activity: Activity, isPlaying: Boolean) {
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
            activity.setPictureInPictureParams(
                PictureInPictureParams.Builder().setActions(actions).build()
            )
        }
    }
}