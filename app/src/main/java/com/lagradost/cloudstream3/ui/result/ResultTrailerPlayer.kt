package com.lagradost.cloudstream3.ui.result

import android.content.res.Configuration
import android.graphics.Rect
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.discord.panels.PanelsChildGestureRegionObserver
import com.lagradost.cloudstream3.ui.player.SubtitleData
import kotlinx.android.synthetic.main.fragment_trailer.*

open class ResultTrailerPlayer : com.lagradost.cloudstream3.ui.player.FullScreenPlayer(),
    PanelsChildGestureRegionObserver.GestureRegionsListener {

    override var lockRotation = false
    override var isFullScreenPlayer = false
    override var hasPipModeSupport = false

    companion object {
        const val TAG = "RESULT_TRAILER"
    }

    var playerWidthHeight: Pair<Int, Int>? = null

    override fun nextEpisode() {}

    override fun prevEpisode() {}

    override fun playerPositionChanged(posDur: Pair<Long, Long>) {}

    override fun nextMirror() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        uiReset()
        fixPlayerSize()
    }

    private fun fixPlayerSize() {
        playerWidthHeight?.let { (w, h) ->
            val orientation = this.resources.configuration?.orientation ?: return

            val sw = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                screenWidth
            } else {
                screenHeight
            }

            player_background?.apply {
                isVisible = true
                layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, sw * h / w)
            }
        }
    }

    override fun playerDimensionsLoaded(widthHeight: Pair<Int, Int>) {
        playerWidthHeight = widthHeight
        fixPlayerSize()
    }

    override fun subtitlesChanged() {}

    override fun embeddedSubtitlesFetched(subtitles: List<SubtitleData>) {}

    override fun exitedPipMode() {}

    override fun onGestureRegionsUpdate(gestureRegions: List<Rect>) {}
}