package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.actions.updateDurationAndPosition
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.txt

/**
 * https://github.com/nova-video-player/aos-Video/blob/v6.6/AndroidManifest.xml
 */
object NovaPlayer : OpenInAppAction(
    appName = txt("NOVA Video Player"),
    packageName = "org.courville.nova",
    intentClass = "com.archos.mediacenter.video.player.PlayerActivity"
) {
    override val oneSource = true

    override suspend fun putExtra(
        context: Context,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        intent.setDataAndType((result.links[index!!].url).toUri(), "video/*")
        intent.putExtra("title",video.name)
        intent.putExtra("return_result",true)
        val position = getViewPos(video.id)?.position ?: 0L
        /**
         * https://github.com/nova-video-player/aos-Video/blob/v6.6/src/main/java/com/archos/mediacenter/video/player/ExternalResumeIntent.java#L39
         */
        intent.putExtra("position", position)
    }

    override fun onResult(activity: Activity, intent: Intent?) {
        if (intent == null) return
        val position = intent.getIntExtra("position", -1)
        val duration = intent.getIntExtra("duration", -1)
        updateDurationAndPosition(position.toLong(), duration.toLong())
    }
}