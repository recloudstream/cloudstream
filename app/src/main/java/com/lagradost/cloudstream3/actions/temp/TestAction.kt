package com.lagradost.cloudstream3.actions.temp

import android.app.Activity
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.txt
import com.lagradost.cloudstream3.utils.AppUtils.toJson

class TestAction: VideoClickAction() {
    override val name = txt("Test action")

    override fun shouldShow(activity: Activity?, video: ResultEpisode): Boolean {
        return true
    }

    override fun runAction(
        activity: Activity?,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        // Show dialog box
        val text = """
Result links:
${result.links.joinToString("\n") { if (it.url.length > 50) it.url.take(25) + "..." + it.url.takeLast(25) else it.url }}
Result subs:
${result.subs.joinToString("\n") { if (it.url.length > 50) it.url.take(25) + "..." + it.url.takeLast(25) else it.url }}
Video:
${video.toJson()}
        """.trim()


        activity?.runOnUiThread {
            val dialog = android.app.AlertDialog.Builder(activity)
            dialog.apply {
                setTitle("Action: ${video.name}")
                setMessage(text)
                setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
            }
            dialog.show()
        }

    }
}