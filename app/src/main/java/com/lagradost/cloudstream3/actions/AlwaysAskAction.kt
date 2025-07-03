package com.lagradost.cloudstream3.actions

import android.content.Context
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.txt

class AlwaysAskAction : VideoClickAction() {
    override val name = txt(R.string.player_settings_always_ask)
    override val isPlayer = true
    
    override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean {
        // This action should always be available as a player option
        return true
    }
    
    override suspend fun runAction(
        context: Context?,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?
    ) {
        // This is handled specially in ResultViewModel2.kt by detecting the AlwaysAskAction
        // and showing the player selection dialog instead of executing the action directly
        throw NotImplementedError("AlwaysAskAction is handled specially by the calling code")
    }
}
