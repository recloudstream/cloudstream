package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.lagradost.api.Log
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.actions.makeTempM3U8Intent
import com.lagradost.cloudstream3.actions.updateDurationAndPosition
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.ExtractorLinkType

// https://github.com/mpv-android/mpv-android/blob/0eb3cdc6f1632636b9c30d52ec50e4b017661980/app/src/main/java/is/xyz/mpv/MPVActivity.kt#L904
// https://mpv-android.github.io/mpv-android/intent.html

//https://github.com/marlboro-advance/mpvEx
class MpvExPackage: MpvPackage("mpvEx","app.marlboroadvance.mpvex","app.marlboroadvance.mpvex.ui.player.PlayerActivity")

class MpvYTDLPackage : MpvPackage("MPV YTDL", "is.xyz.mpv.ytdl") {
    override val sourceTypes = setOf(
        ExtractorLinkType.VIDEO,
        ExtractorLinkType.DASH,
        ExtractorLinkType.M3U8
    )
}

open class MpvPackage(appName: String = "MPV", packageName: String = "is.xyz.mpv",intentClass:String = "is.xyz.mpv.MPVActivity"): OpenInAppAction(
    txt(appName),
    packageName,
    intentClass
) {
    override val oneSource = true // mpv has poor playlist support on TV
    override suspend fun putExtra(
        context: Context,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        intent.apply {
            putExtra("subs", result.subs.map { it.url.toUri() }.toTypedArray())
            putExtra("title", video.name)

            if (index != null) {
                setDataAndType((result.links.getOrNull(index)?.url ?: return).toUri(), "video/*")
            } else {
                makeTempM3U8Intent(context, this, result)
            }

            val position = getViewPos(video.id)?.position
            if (position != null)
                putExtra("position", position.toInt())

            putExtra("secure_uri", true)
        }
    }

    override fun onResult(activity: Activity, intent: Intent?) {
        val position = intent?.getIntExtra("position", -1) ?: -1
        val duration = intent?.getIntExtra("duration", -1) ?: -1
        Log.d("MPV", "Position: $position, Duration: $duration")
        updateDurationAndPosition(position.toLong(), duration.toLong())
    }
}