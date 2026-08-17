package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.lagradost.api.Log
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.actions.updateDurationAndPosition
import com.lagradost.cloudstream3.isEpisodeBased
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.DataStoreHelper.getViewPos
import com.lagradost.cloudstream3.utils.txt

/** https://github.com/Riteshp2001/mpvRx
 *
 * https://github.com/Riteshp2001/mpvRx/blob/00e0c5e803ab53e5757426cbf2248448ba1f49bf/app/src/main/java/app/gyrolet/mpvrx/utils/media/MediaUtils.kt#L132
 * https://github.com/Riteshp2001/mpvRx/blob/00e0c5e803ab53e5757426cbf2248448ba1f49bf/app/src/main/java/app/gyrolet/mpvrx/utils/media/MediaUtils.kt#L56
 * */
class MpvRxPackage : OpenInAppAction(
    appName = txt("mpvRx"),
    packageName = "app.gyrolet.mpvrx",
    intentClass = "app.gyrolet.mpvrx.ui.player.PlayerActivity"
) {
    override val oneSource = true
    override suspend fun putExtra(
        context: Context,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        intent.apply {
            putExtra("title", video.name)
            val link = result.links[index!!]
            val headers = link.headers

            setData(link.url.toUri())
            if (headers.isNotEmpty()) {
                // PlayerActivity expects a flat array: [key1, value1, key2, value2, ...]
                val flat = headers.entries.flatMap { listOf(it.key, it.value) }.toTypedArray()
                intent.putExtra("headers", flat)
            }
            /*val subs = result.subs // disabled due to https://github.com/Riteshp2001/mpvRx/issues/146
            intent.putExtra("subs", subs.map { it.url.toUri() }.toTypedArray())
            intent.putExtra(
                "subs.titles",
                subs.map { it.name }.toTypedArray(),
            )
            intent.putExtra(
                "subs.langs",
                subs.map { it.languageCode }.toTypedArray(),
            )
            val selected = subs.firstOrNull { it.matchesLanguageCode("en") }?.url?.toUri()
            intent.putExtra("subs.enable", selected?.let { arrayOf(it) } ?: arrayOf<Uri>() )*/

            if (video.tvType.isEpisodeBased()) {
                video.season?.let { intent.putExtra("introdb_season", it) }
                video.episode.let { intent.putExtra("introdb_episode", it) }
            }

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