package com.lagradost.cloudstream3.actions.temp

import android.content.Context
import android.content.Intent
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.makeTempM3U8Intent
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.txt

class TestAction: VideoClickAction() {
    override val name = txt("Test action")

    override fun shouldShow(context: Context?, video: ResultEpisode?) = true

    override fun runAction(
        context: Context?,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        if (context == null) return
        val i = Intent(Intent.ACTION_VIEW)
        makeTempM3U8Intent(context, i, result)
        context.startActivity(i)
    }
}