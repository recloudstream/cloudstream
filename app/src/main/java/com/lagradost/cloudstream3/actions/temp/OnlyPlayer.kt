package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.lagradost.cloudstream3.actions.OpenInAppAction
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.txt

/** https://github.com/Kindness-Kismet/only_player/tree/main
 * https://github.com/Kindness-Kismet/only_player/blob/main/feature/player/src/main/java/one/only/player/feature/player/PlayerActivity.kt */
class OnlyPlayer : OpenInAppAction(
    txt("Only Player"),
    "one.only.player",
    intentClass = "one.only.player.feature.player.PlayerActivity"
) {
    override val oneSource = true
    override suspend fun putExtra(
        context: Context,
        intent: Intent,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        /** https://github.com/Kindness-Kismet/only_player/blob/d3f55049a2913fa762d31b311146073cc2da46cb/app/src/main/java/one/only/player/navigation/CloudNavGraph.kt#L39 */
        intent.apply {
            val link = result.links[index!!]
            setData(link.url.toUri())

            putExtra("headers", Bundle().apply {
                for ((key, value) in link.headers) {
                    putExtra(key, value)
                }
            })
        }
    }

    override fun onResult(activity: Activity, intent: Intent?) {
        /* onResult does not get called */
    }
}