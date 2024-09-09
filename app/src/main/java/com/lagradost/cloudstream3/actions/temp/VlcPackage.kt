package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.lagradost.api.Log
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.actions.makeTempM3U8Intent
import com.lagradost.cloudstream3.actions.updateDurationAndPosition
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos

class VlcPackage: OpenInAppAction(
    appName = txt("VLC"),
    packageName = "org.videolan.vlc",
    intentClass = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        "org.videolan.vlc.gui.video.VideoPlayerActivity"
    } else {
        null
    },
    action = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        "org.videolan.vlc.player.result"
    } else {
        Intent.ACTION_VIEW
    }
) {
    override val oneSource = false

    // https://wiki.videolan.org/Android_Player_Intents/
    override fun putExtra(
        activity: Activity,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {

        makeTempM3U8Intent(activity, intent, result)

        val position = getViewPos(video.id)?.position ?: 0L

        intent.putExtra("from_start", false)
        intent.putExtra("position", position)
    }

    override fun onResult(activity: Activity, intent: Intent?) {
        val position = intent?.getLongExtra("extra_position", -1) ?: -1
        val duration = intent?.getLongExtra("extra_duration", -1) ?: -1
        Log.d("VLC", "Position: $position, Duration: $duration")
        updateDurationAndPosition(position, duration)
    }

}