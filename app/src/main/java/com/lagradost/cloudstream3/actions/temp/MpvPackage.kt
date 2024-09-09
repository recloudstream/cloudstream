package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import com.lagradost.api.Log
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.actions.makeTempM3U8Intent
import com.lagradost.cloudstream3.actions.updateDurationAndPosition
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.ExtractorLinkType

class MpvYTDLPackage : MpvPackage("MPV YTDL", "is.xyz.mpv.ytdl") {
    override val sourceTypes = setOf(
        ExtractorLinkType.VIDEO,
        ExtractorLinkType.DASH,
        ExtractorLinkType.M3U8
    )
}

open class MpvPackage(appName: String = "MPV", packageName: String = "is.xyz.mpv"): OpenInAppAction(
    txt(appName),
    packageName,
    "is.xyz.mpv.MPVActivity"
) {
    override val sourceTypes = setOf(
        ExtractorLinkType.VIDEO,
        ExtractorLinkType.DASH,
        ExtractorLinkType.M3U8
    )

    override fun putExtra(
        activity: Activity,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        intent.apply {
            putExtra("subs", result.subs.map { it.url.toUri() }.toTypedArray())
            putExtra("subs.name", result.subs.map { it.name }.toTypedArray())
            putExtra("subs.filename", result.subs.map { it.name }.toTypedArray())
            makeTempM3U8Intent(activity, this, result)
            putExtra("secure_uri", true)
            putExtra("return_result", true)
            val position = getViewPos(video.id)?.position
            if (position != null)
                putExtra("position", position.toInt())
        }
    }

    override fun onResult(activity: Activity, intent: Intent?) {
        val position = intent?.getIntExtra("position", -1) ?: -1
        val duration = intent?.getIntExtra("duration", -1) ?: -1
        Log.d("MPV", "Position: $position, Duration: $duration")
        updateDurationAndPosition(position.toLong(), duration.toLong())
    }
}